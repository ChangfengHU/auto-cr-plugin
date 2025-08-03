package com.vyibc.autocrplugin.cache

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.vyibc.autocrplugin.graph.model.CallPath
import com.vyibc.autocrplugin.graph.model.MethodNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * 三级缓存管理器
 */
class IntelligentCacheManager(
    private val cacheDir: Path = Paths.get(System.getProperty("user.home"), ".autocr", "cache")
) {
    
    init {
        // 确保缓存目录存在
        Files.createDirectories(cacheDir)
    }
    
    // L1: 内存热点缓存 (最近访问的方法和路径)
    private val l1MethodCache: AsyncCache<String, MethodNode> = Caffeine.newBuilder()
        .maximumSize(5000)
        .expireAfterAccess(1, TimeUnit.HOURS)
        .recordStats()
        .buildAsync()
    
    private val l1PathCache: AsyncCache<String, CallPath> = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterAccess(1, TimeUnit.HOURS)
        .recordStats()
        .buildAsync()
    
    // L2: 磁盘持久化缓存 (完整项目图谱)
    // 这里使用简单的文件系统实现，实际可以使用更高效的存储方案
    
    // L3: AI结果缓存 (基于内容Hash + 模型版本)
    private val l3AICache: AsyncCache<String, AIAnalysisResult> = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(24, TimeUnit.HOURS)
        .recordStats()
        .buildAsync()
    
    /**
     * 获取方法节点 - 尝试从L1缓存获取
     */
    suspend fun getMethod(key: String): MethodNode? = withContext(Dispatchers.IO) {
        l1MethodCache.getIfPresent(key)?.await()
    }
    
    /**
     * 缓存方法节点到L1
     */
    suspend fun putMethod(key: String, method: MethodNode) = withContext(Dispatchers.IO) {
        l1MethodCache.put(key, CompletableFuture.completedFuture(method))
    }
    
    /**
     * 获取调用路径 - 尝试从L1缓存获取
     */
    suspend fun getPath(key: String): CallPath? = withContext(Dispatchers.IO) {
        l1PathCache.getIfPresent(key)?.await()
    }
    
    /**
     * 缓存调用路径到L1
     */
    suspend fun putPath(key: String, path: CallPath) = withContext(Dispatchers.IO) {
        l1PathCache.put(key, CompletableFuture.completedFuture(path))
    }
    
    /**
     * 获取AI分析结果 - 尝试从L3缓存获取
     */
    suspend fun getAIResult(key: String): AIAnalysisResult? = withContext(Dispatchers.IO) {
        l3AICache.getIfPresent(key)?.await()
    }
    
    /**
     * 缓存AI分析结果到L3
     */
    suspend fun putAIResult(key: String, result: AIAnalysisResult) = withContext(Dispatchers.IO) {
        l3AICache.put(key, CompletableFuture.completedFuture(result))
    }
    
    /**
     * 从L2磁盘缓存加载数据
     */
    suspend fun loadFromDisk(key: String): Any? = withContext(Dispatchers.IO) {
        val file = cacheDir.resolve("$key.cache").toFile()
        if (file.exists()) {
            try {
                ObjectInputStream(FileInputStream(file)).use { ois ->
                    ois.readObject()
                }
            } catch (e: Exception) {
                null
            }
        } else null
    }
    
    /**
     * 保存数据到L2磁盘缓存
     */
    suspend fun saveToDisk(key: String, data: Serializable) = withContext(Dispatchers.IO) {
        val file = cacheDir.resolve("$key.cache").toFile()
        try {
            ObjectOutputStream(FileOutputStream(file)).use { oos ->
                oos.writeObject(data)
            }
        } catch (e: Exception) {
            // 记录错误
        }
    }
    
    /**
     * 智能缓存失效 - 根据文件变更使相关缓存失效
     */
    suspend fun invalidateRelatedCache(changedFiles: List<String>) = withContext(Dispatchers.IO) {
        // 找出所有受影响的方法
        val affectedMethods = mutableSetOf<String>()
        
        // 简单实现：清除与变更文件相关的所有缓存
        changedFiles.forEach { filePath ->
            // 这里需要根据实际的键生成策略来实现
            // 暂时使用简单的包含匹配
            l1MethodCache.asMap().keys.forEach { key ->
                if (key.contains(filePath)) {
                    l1MethodCache.synchronous().invalidate(key)
                    affectedMethods.add(key)
                }
            }
        }
        
        // 清除受影响的路径缓存
        l1PathCache.asMap().keys.forEach { key ->
            l1PathCache.synchronous().invalidate(key)
        }
    }
    
    /**
     * 根据路径失效缓存
     */
    suspend fun invalidateByPath(path: String) = withContext(Dispatchers.IO) {
        // 失效方法缓存
        l1MethodCache.asMap().keys.forEach { key ->
            if (key.contains(path)) {
                l1MethodCache.synchronous().invalidate(key)
            }
        }
        
        // 失效路径缓存
        l1PathCache.asMap().keys.forEach { key ->
            if (key.contains(path)) {
                l1PathCache.synchronous().invalidate(key)
            }
        }
        
        // 失效AI缓存
        l3AICache.asMap().keys.forEach { key ->
            if (key.contains(path)) {
                l3AICache.synchronous().invalidate(key)
            }
        }
        
        // 删除磁盘缓存
        val fileKey = CacheKeyGenerator.generateFileKey(path)
        val cacheFile = cacheDir.resolve("$fileKey.cache").toFile()
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
    }
    
    /**
     * 部分失效缓存 - 保留一些仍然有效的缓存
     */
    suspend fun partialInvalidate(path: String) = withContext(Dispatchers.IO) {
        // 只失效方法实现相关的缓存，保留接口定义等稳定部分
        l1MethodCache.asMap().entries.forEach { (key, future) ->
            if (key.contains(path)) {
                try {
                    val method = future.get()
                    if (method != null && !method.isInterface) {
                        l1MethodCache.synchronous().invalidate(key)
                    }
                } catch (e: Exception) {
                    // 忽略获取失败的缓存项
                }
            }
        }
        
        // 失效所有路径缓存，因为调用关系可能改变
        l1PathCache.asMap().keys.filter { it.contains(path) }.forEach { key ->
            l1PathCache.synchronous().invalidate(key)
        }
    }
    
    /**
     * 迁移路径相关的缓存
     */
    suspend fun migratePath(oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        // 迁移方法缓存
        val methodEntries = l1MethodCache.asMap().entries.filter { it.key.contains(oldPath) }
        methodEntries.forEach { (key, future) ->
            try {
                val method = future.get()
                if (method != null) {
                    val newKey = key.replace(oldPath, newPath)
                    l1MethodCache.put(newKey, CompletableFuture.completedFuture(method))
                    l1MethodCache.synchronous().invalidate(key)
                }
            } catch (e: Exception) {
                // 忽略处理失败的项
            }
        }
        
        // 迁移路径缓存
        val pathEntries = l1PathCache.asMap().entries.filter { it.key.contains(oldPath) }
        pathEntries.forEach { (key, future) ->
            try {
                val path = future.get()
                if (path != null) {
                    val newKey = key.replace(oldPath, newPath)
                    l1PathCache.put(newKey, CompletableFuture.completedFuture(path))
                    l1PathCache.synchronous().invalidate(key)
                }
            } catch (e: Exception) {
                // 忽略处理失败的项
            }
        }
        
        // 迁移磁盘缓存
        val oldFileKey = CacheKeyGenerator.generateFileKey(oldPath)
        val newFileKey = CacheKeyGenerator.generateFileKey(newPath)
        val oldCacheFile = cacheDir.resolve("$oldFileKey.cache").toFile()
        val newCacheFile = cacheDir.resolve("$newFileKey.cache").toFile()
        
        if (oldCacheFile.exists()) {
            oldCacheFile.renameTo(newCacheFile)
        }
    }
    
    
    /**
     * 获取缓存统计信息
     */
    fun getCacheStatistics(): CacheStatistics {
        val l1MethodStats = l1MethodCache.synchronous().stats()
        val l1PathStats = l1PathCache.synchronous().stats()
        val l3AIStats = l3AICache.synchronous().stats()
        
        return CacheStatistics(
            l1MethodHitRate = l1MethodStats.hitRate(),
            l1MethodSize = l1MethodCache.synchronous().estimatedSize(),
            l1PathHitRate = l1PathStats.hitRate(),
            l1PathSize = l1PathCache.synchronous().estimatedSize(),
            l3AIHitRate = l3AIStats.hitRate(),
            l3AISize = l3AICache.synchronous().estimatedSize(),
            diskCacheSize = calculateDiskCacheSize()
        )
    }
    
    /**
     * 清空所有缓存
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        l1MethodCache.synchronous().invalidateAll()
        l1PathCache.synchronous().invalidateAll()
        l3AICache.synchronous().invalidateAll()
        
        // 清空磁盘缓存
        Files.walk(cacheDir).forEach { path ->
            if (Files.isRegularFile(path) && path.toString().endsWith(".cache")) {
                Files.deleteIfExists(path)
            }
        }
    }
    
    /**
     * 预热缓存 - 加载常用数据到内存
     */
    suspend fun warmUp(hotMethods: List<MethodNode>, hotPaths: List<CallPath>) = withContext(Dispatchers.IO) {
        hotMethods.forEach { method ->
            putMethod(method.id, method)
        }
        
        hotPaths.forEachIndexed { index, path ->
            putPath("hot_path_$index", path)
        }
    }
    
    private fun calculateDiskCacheSize(): Long {
        return try {
            Files.walk(cacheDir)
                .filter { Files.isRegularFile(it) }
                .mapToLong { Files.size(it) }
                .sum()
        } catch (e: Exception) {
            0L
        }
    }
}

/**
 * 缓存统计信息
 */
data class CacheStatistics(
    val l1MethodHitRate: Double,
    val l1MethodSize: Long,
    val l1PathHitRate: Double,
    val l1PathSize: Long,
    val l3AIHitRate: Double,
    val l3AISize: Long,
    val diskCacheSize: Long
) {
    val totalMemoryUsage: Long
        get() = l1MethodSize + l1PathSize + l3AISize
    
    val overallHitRate: Double
        get() = (l1MethodHitRate + l1PathHitRate + l3AIHitRate) / 3.0
}

/**
 * AI分析结果
 */
data class AIAnalysisResult(
    val modelVersion: String,
    val analysisType: String,
    val result: String,
    val confidence: Double,
    val timestamp: Long
) : Serializable

/**
 * 缓存级别
 */
enum class CacheLevel {
    L1_MEMORY,      // 内存缓存
    L2_DISK,        // 磁盘缓存
    L3_AI_RESULT    // AI结果缓存
}

/**
 * 缓存键生成器
 */
object CacheKeyGenerator {
    
    /**
     * 生成方法缓存键
     */
    fun generateMethodKey(packageName: String, className: String, methodName: String, params: List<String>): String {
        return "${packageName}.${className}#${methodName}(${params.joinToString(",")})"
    }
    
    /**
     * 生成路径缓存键
     */
    fun generatePathKey(sourceId: String, targetId: String, pathType: String): String {
        return "path:$sourceId->$targetId:$pathType"
    }
    
    /**
     * 生成AI结果缓存键
     */
    fun generateAIResultKey(content: String, modelVersion: String, analysisType: String): String {
        val contentHash = content.hashCode()
        return "ai:$modelVersion:$analysisType:$contentHash"
    }
    
    /**
     * 生成文件缓存键
     */
    fun generateFileKey(filePath: String): String {
        return "file:${filePath.replace('/', '_')}"
    }
}