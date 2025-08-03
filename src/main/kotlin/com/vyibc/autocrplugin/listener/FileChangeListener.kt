package com.vyibc.autocrplugin.listener

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.*
import com.intellij.psi.PsiManager
import com.vyibc.autocrplugin.cache.IntelligentCacheManager
import com.vyibc.autocrplugin.service.GraphUpdateService
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 文件变更监听器 - V5.1版本
 * 增量更新机制、热点路径缓存失效策略
 */
class FileChangeListener(
    private val project: Project,
    private val cacheManager: IntelligentCacheManager
) : VirtualFileListener {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val graphUpdateService by lazy { project.getService(GraphUpdateService::class.java) }
    private val hotspotPaths = ConcurrentHashMap<String, HotspotInfo>()
    
    // 变更批处理
    private val pendingChanges = mutableSetOf<FileChange>()
    private var batchJob: Job? = null
    
    override fun fileCreated(event: VirtualFileEvent) {
        handleFileChange(event.file, ChangeType.CREATED)
    }
    
    override fun fileDeleted(event: VirtualFileEvent) {
        handleFileChange(event.file, ChangeType.DELETED)
    }
    
    override fun contentsChanged(event: VirtualFileEvent) {
        handleFileChange(event.file, ChangeType.MODIFIED)
    }
    
    override fun fileMoved(event: VirtualFileMoveEvent) {
        handleFileChange(event.file, ChangeType.MOVED, event.oldParent.path + "/" + event.fileName)
    }
    
    override fun propertyChanged(event: VirtualFilePropertyEvent) {
        if (event.propertyName == VirtualFile.PROP_NAME) {
            handleFileChange(event.file, ChangeType.RENAMED, event.oldValue?.toString())
        }
    }
    
    /**
     * 处理文件变更
     */
    private fun handleFileChange(
        file: VirtualFile, 
        changeType: ChangeType, 
        oldPath: String? = null
    ) {
        // 过滤非代码文件
        if (!isRelevantFile(file)) return
        
        val change = FileChange(
            path = file.path,
            oldPath = oldPath,
            changeType = changeType,
            timestamp = System.currentTimeMillis()
        )
        
        synchronized(pendingChanges) {
            pendingChanges.add(change)
        }
        
        // 更新热点信息
        updateHotspotInfo(file.path)
        
        // 批处理变更
        scheduleBatchUpdate()
    }
    
    /**
     * 判断是否为相关文件
     */
    private fun isRelevantFile(file: VirtualFile): Boolean {
        if (file.isDirectory) return false
        
        val extension = file.extension?.lowercase() ?: return false
        return extension in setOf("java", "kt", "kts")
    }
    
    /**
     * 更新热点信息
     */
    private fun updateHotspotInfo(path: String) {
        val info = hotspotPaths.computeIfAbsent(path) { 
            HotspotInfo(path) 
        }
        info.incrementChangeCount()
        
        // 如果变更频率过高，标记为热点
        if (info.isHotspot()) {
            markAsHotspot(path)
        }
    }
    
    /**
     * 标记为热点路径
     */
    private fun markAsHotspot(path: String) {
        scope.launch {
            // 失效相关缓存
            cacheManager.invalidateByPath(path)
            
            // 通知图更新服务
            graphUpdateService?.markHotspot(path)
        }
    }
    
    /**
     * 批处理更新调度
     */
    private fun scheduleBatchUpdate() {
        batchJob?.cancel()
        batchJob = scope.launch {
            delay(BATCH_DELAY_MS)
            processBatchChanges()
        }
    }
    
    /**
     * 处理批量变更
     */
    private suspend fun processBatchChanges() {
        val changes = synchronized(pendingChanges) {
            val result = pendingChanges.toList()
            pendingChanges.clear()
            result
        }
        
        if (changes.isEmpty()) return
        
        withContext(Dispatchers.Default) {
            // 分组处理
            val grouped = changes.groupBy { it.changeType }
            
            // 处理删除
            grouped[ChangeType.DELETED]?.forEach { change ->
                handleDeletion(change)
            }
            
            // 处理创建
            grouped[ChangeType.CREATED]?.forEach { change ->
                handleCreation(change)
            }
            
            // 处理修改
            grouped[ChangeType.MODIFIED]?.forEach { change ->
                handleModification(change)
            }
            
            // 处理移动和重命名
            (grouped[ChangeType.MOVED] ?: emptyList())
                .plus(grouped[ChangeType.RENAMED] ?: emptyList())
                .forEach { change ->
                    handleMove(change)
                }
            
            // 触发增量分析
            graphUpdateService?.triggerIncrementalAnalysis(changes)
        }
    }
    
    /**
     * 处理文件删除
     */
    private suspend fun handleDeletion(change: FileChange) {
        // 清除缓存
        cacheManager.invalidateByPath(change.path)
        
        // 从热点路径中移除
        hotspotPaths.remove(change.path)
        
        // 通知图服务
        graphUpdateService?.removeNode(change.path)
    }
    
    /**
     * 处理文件创建
     */
    private suspend fun handleCreation(change: FileChange) {
        // 预热缓存（异步）
        scope.launch {
            val vf = VirtualFileManager.getInstance().findFileByUrl("file://${change.path}")
            if (vf != null) {
                val psiFile = PsiManager.getInstance(project).findFile(vf)
                if (psiFile != null) {
                    // 触发分析但不等待结果
                    graphUpdateService?.analyzeFile(psiFile)
                }
            }
        }
    }
    
    /**
     * 处理文件修改
     */
    private suspend fun handleModification(change: FileChange) {
        // 部分失效缓存
        cacheManager.partialInvalidate(change.path)
        
        // 如果是热点文件，优先处理
        if (hotspotPaths[change.path]?.isHotspot() == true) {
            graphUpdateService?.priorityUpdate(change.path)
        }
    }
    
    /**
     * 处理文件移动/重命名
     */
    private suspend fun handleMove(change: FileChange) {
        change.oldPath?.let { oldPath ->
            // 迁移缓存
            cacheManager.migratePath(oldPath, change.path)
            
            // 迁移热点信息
            hotspotPaths.remove(oldPath)?.let { info ->
                info.path = change.path
                hotspotPaths[change.path] = info
            }
            
            // 更新图节点
            graphUpdateService?.renameNode(oldPath, change.path)
        }
    }
    
    /**
     * 获取热点路径列表
     */
    fun getHotspotPaths(): List<String> {
        return hotspotPaths.values
            .filter { it.isHotspot() }
            .sortedByDescending { it.changeCount }
            .map { it.path }
    }
    
    /**
     * 重置热点统计
     */
    fun resetHotspots() {
        hotspotPaths.clear()
    }
    
    /**
     * 清理资源
     */
    fun dispose() {
        scope.cancel()
        pendingChanges.clear()
        hotspotPaths.clear()
    }
    
    companion object {
        const val BATCH_DELAY_MS = 500L // 批处理延迟
        const val HOTSPOT_THRESHOLD = 5 // 热点阈值
        const val HOTSPOT_WINDOW_MS = 60_000L // 热点时间窗口（1分钟）
    }
}

/**
 * 文件变更信息
 */
data class FileChange(
    val path: String,
    val oldPath: String? = null,
    val changeType: ChangeType,
    val timestamp: Long
)

/**
 * 变更类型
 */
enum class ChangeType {
    CREATED,
    MODIFIED,
    DELETED,
    MOVED,
    RENAMED
}

/**
 * 热点信息
 */
class HotspotInfo(
    var path: String,
    private val windowMs: Long = FileChangeListener.HOTSPOT_WINDOW_MS
) {
    private val changes = mutableListOf<Long>()
    var changeCount: Int = 0
        private set
    
    fun incrementChangeCount() {
        val now = System.currentTimeMillis()
        
        // 清理过期的变更记录
        changes.removeAll { now - it > windowMs }
        
        // 添加新的变更
        changes.add(now)
        changeCount++
    }
    
    fun isHotspot(): Boolean {
        val now = System.currentTimeMillis()
        val recentChanges = changes.count { now - it <= windowMs }
        return recentChanges >= FileChangeListener.HOTSPOT_THRESHOLD
    }
    
    fun getChangeFrequency(): Double {
        if (changes.isEmpty()) return 0.0
        val now = System.currentTimeMillis()
        val recentChanges = changes.filter { now - it <= windowMs }
        return recentChanges.size.toDouble() / (windowMs / 1000.0) // 每秒变更次数
    }
}