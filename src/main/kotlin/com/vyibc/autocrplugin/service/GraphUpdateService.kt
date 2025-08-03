package com.vyibc.autocrplugin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.vyibc.autocrplugin.analyzer.PSIAnalysisEngine
import com.vyibc.autocrplugin.graph.engine.LocalGraphEngine
import com.vyibc.autocrplugin.listener.FileChange
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 图更新服务 - V5.1版本
 * 负责协调文件变更与图结构的增量更新
 */
@Service(Service.Level.PROJECT)
class GraphUpdateService(private val project: Project) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val graphEngine by lazy { project.getService(LocalGraphEngine::class.java) }
    private val psiAnalysisEngine by lazy { PSIAnalysisEngine(project) }
    
    // 热点路径管理
    private val hotspotPaths = ConcurrentHashMap<String, Long>()
    
    // 优先级队列
    private val priorityQueue = mutableListOf<String>()
    private val normalQueue = mutableListOf<String>()
    
    /**
     * 标记热点路径
     */
    suspend fun markHotspot(path: String) {
        hotspotPaths[path] = System.currentTimeMillis()
        
        // 将热点路径加入优先队列
        synchronized(priorityQueue) {
            if (path !in priorityQueue) {
                priorityQueue.add(path)
            }
        }
    }
    
    /**
     * 触发增量分析
     */
    suspend fun triggerIncrementalAnalysis(changes: List<FileChange>) {
        scope.launch {
            changes.forEach { change ->
                when (change.changeType) {
                    com.vyibc.autocrplugin.listener.ChangeType.DELETED -> removeNode(change.path)
                    com.vyibc.autocrplugin.listener.ChangeType.CREATED,
                    com.vyibc.autocrplugin.listener.ChangeType.MODIFIED -> scheduleAnalysis(change.path)
                    com.vyibc.autocrplugin.listener.ChangeType.MOVED,
                    com.vyibc.autocrplugin.listener.ChangeType.RENAMED -> {
                        change.oldPath?.let { renameNode(it, change.path) }
                    }
                }
            }
            
            // 处理队列
            processAnalysisQueues()
        }
    }
    
    /**
     * 删除节点
     */
    suspend fun removeNode(path: String) {
        withContext(Dispatchers.Default) {
            // 从图中删除相关节点
            graphEngine.removeFileNodes(path)
            
            // 清理热点信息
            hotspotPaths.remove(path)
        }
    }
    
    /**
     * 重命名节点
     */
    suspend fun renameNode(oldPath: String, newPath: String) {
        withContext(Dispatchers.Default) {
            // 更新图中的节点路径
            graphEngine.renameFileNodes(oldPath, newPath)
            
            // 迁移热点信息
            hotspotPaths.remove(oldPath)?.let { timestamp ->
                hotspotPaths[newPath] = timestamp
            }
        }
    }
    
    /**
     * 分析文件
     */
    suspend fun analyzeFile(psiFile: PsiFile) {
        withContext(Dispatchers.Default) {
            try {
                val analysisResult = psiAnalysisEngine.analyzeFile(psiFile)
                
                // 更新图结构
                graphEngine.updateFromAnalysis(analysisResult)
                
            } catch (e: Exception) {
                // 记录错误但不中断流程
                logError("Failed to analyze file: ${psiFile.virtualFile.path}", e)
            }
        }
    }
    
    /**
     * 优先更新
     */
    suspend fun priorityUpdate(path: String) {
        synchronized(priorityQueue) {
            if (path !in priorityQueue) {
                priorityQueue.add(path)
            }
            normalQueue.remove(path)
        }
    }
    
    /**
     * 调度分析
     */
    private fun scheduleAnalysis(path: String) {
        if (hotspotPaths.containsKey(path)) {
            synchronized(priorityQueue) {
                if (path !in priorityQueue) {
                    priorityQueue.add(path)
                }
            }
        } else {
            synchronized(normalQueue) {
                if (path !in normalQueue && path !in priorityQueue) {
                    normalQueue.add(path)
                }
            }
        }
    }
    
    /**
     * 处理分析队列
     */
    private suspend fun processAnalysisQueues() {
        // 优先处理热点文件
        while (priorityQueue.isNotEmpty()) {
            val path = synchronized(priorityQueue) {
                priorityQueue.removeFirstOrNull()
            } ?: break
            
            analyzePathSafely(path)
        }
        
        // 处理普通文件
        while (normalQueue.isNotEmpty()) {
            val path = synchronized(normalQueue) {
                normalQueue.removeFirstOrNull()
            } ?: break
            
            analyzePathSafely(path)
        }
    }
    
    /**
     * 安全分析路径
     */
    private suspend fun analyzePathSafely(path: String) {
        try {
            // 这里简化实现，实际应该通过VirtualFile获取PsiFile
            // 并调用analyzeFile方法
        } catch (e: Exception) {
            logError("Failed to analyze path: $path", e)
        }
    }
    
    /**
     * 记录错误
     */
    private fun logError(message: String, e: Exception) {
        // 简化实现：打印到控制台
        println("GraphUpdateService Error: $message - ${e.message}")
    }
    
    /**
     * 清理资源
     */
    fun dispose() {
        scope.cancel()
        priorityQueue.clear()
        normalQueue.clear()
        hotspotPaths.clear()
    }
}