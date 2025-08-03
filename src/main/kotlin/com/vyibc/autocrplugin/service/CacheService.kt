package com.vyibc.autocrplugin.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.vyibc.autocrplugin.graph.model.*
import java.time.Duration

/**
 * 缓存服务 - 简化版本用于修复编译错误
 */
class CacheService {
    
    // L1 内存缓存
    private val classCache: Cache<String, List<ClassNode>> = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofHours(24))
        .build()
    
    private val methodCache: Cache<String, List<MethodNode>> = Caffeine.newBuilder()
        .maximumSize(5000)
        .expireAfterWrite(Duration.ofHours(24))
        .build()
    
    private val callEdgeCache: Cache<String, List<CallsEdge>> = Caffeine.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(Duration.ofHours(24))
        .build()
    
    private val implementsEdgeCache: Cache<String, List<ImplementsEdge>> = Caffeine.newBuilder()
        .maximumSize(2000)
        .expireAfterWrite(Duration.ofHours(24))
        .build()
    
    private val dataFlowEdgeCache: Cache<String, List<DataFlowEdge>> = Caffeine.newBuilder()
        .maximumSize(5000)
        .expireAfterWrite(Duration.ofHours(24))
        .build()
    
    private val indexedProjects = mutableSetOf<String>()
    
    /**
     * 缓存项目类列表
     */
    fun putProjectClasses(projectName: String, classes: List<ClassNode>) {
        classCache.put(projectName, classes)
    }
    
    /**
     * 获取项目类列表
     */
    fun getProjectClasses(projectName: String): List<ClassNode>? {
        return classCache.getIfPresent(projectName)
    }
    
    /**
     * 缓存项目方法列表
     */
    fun putProjectMethods(projectName: String, methods: List<MethodNode>) {
        methodCache.put(projectName, methods)
    }
    
    /**
     * 获取项目方法列表
     */
    fun getProjectMethods(projectName: String): List<MethodNode>? {
        return methodCache.getIfPresent(projectName)
    }
    
    /**
     * 缓存调用边
     */
    fun putProjectCallEdges(projectName: String, edges: List<CallsEdge>) {
        callEdgeCache.put(projectName, edges)
    }
    
    /**
     * 获取调用边
     */
    fun getProjectCallEdges(projectName: String): List<CallsEdge>? {
        return callEdgeCache.getIfPresent(projectName)
    }
    
    /**
     * 缓存继承边
     */
    fun putProjectImplementsEdges(projectName: String, edges: List<ImplementsEdge>) {
        implementsEdgeCache.put(projectName, edges)
    }
    
    /**
     * 获取继承边
     */
    fun getProjectImplementsEdges(projectName: String): List<ImplementsEdge>? {
        return implementsEdgeCache.getIfPresent(projectName)
    }
    
    /**
     * 缓存数据流边
     */
    fun putProjectDataFlowEdges(projectName: String, edges: List<DataFlowEdge>) {
        dataFlowEdgeCache.put(projectName, edges)
    }
    
    /**
     * 获取数据流边
     */
    fun getProjectDataFlowEdges(projectName: String): List<DataFlowEdge>? {
        return dataFlowEdgeCache.getIfPresent(projectName)
    }
    
    /**
     * 标记项目已索引
     */
    fun markProjectIndexed(projectName: String) {
        indexedProjects.add(projectName)
    }
    
    /**
     * 检查项目是否已索引
     */
    fun isProjectIndexed(projectName: String): Boolean {
        return indexedProjects.contains(projectName)
    }
    
    /**
     * 清除项目缓存
     */
    fun clearProjectCache(projectName: String) {
        classCache.invalidate(projectName)
        methodCache.invalidate(projectName)
        callEdgeCache.invalidate(projectName)
        implementsEdgeCache.invalidate(projectName)
        dataFlowEdgeCache.invalidate(projectName)
        indexedProjects.remove(projectName)
    }
    
    /**
     * 清除所有缓存
     */
    fun clearAllCaches() {
        classCache.invalidateAll()
        methodCache.invalidateAll()
        callEdgeCache.invalidateAll()
        implementsEdgeCache.invalidateAll()
        dataFlowEdgeCache.invalidateAll()
        indexedProjects.clear()
    }
}