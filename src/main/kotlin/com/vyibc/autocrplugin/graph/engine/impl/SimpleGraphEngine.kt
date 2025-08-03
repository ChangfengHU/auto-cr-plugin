package com.vyibc.autocrplugin.graph.engine.impl

import com.vyibc.autocrplugin.graph.engine.*
import com.vyibc.autocrplugin.graph.model.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 简单的内存图引擎实现
 * 用于快速开发和测试，后续可以替换为TinkerGraph实现
 */
class SimpleGraphEngine : LocalGraphEngine {
    
    private val mutex = Mutex()
    
    // 存储节点
    private val methodNodes = mutableMapOf<String, MethodNode>()
    private val classNodes = mutableMapOf<String, ClassNode>()
    
    // 存储边
    private val callEdges = mutableListOf<CallsEdge>()
    private val implementsEdges = mutableListOf<ImplementsEdge>()
    private val dataFlowEdges = mutableListOf<DataFlowEdge>()
    
    // 热点路径缓存
    private val hotPathCache = mutableListOf<CallPath>()
    
    override suspend fun addMethodNode(node: MethodNode): Boolean = mutex.withLock {
        methodNodes[node.id] = node
        true
    }
    
    override suspend fun addClassNode(node: ClassNode): Boolean = mutex.withLock {
        classNodes[node.id] = node
        true
    }
    
    override suspend fun addCallEdge(edge: CallsEdge): Boolean = mutex.withLock {
        callEdges.add(edge)
        true
    }
    
    override suspend fun addImplementsEdge(edge: ImplementsEdge): Boolean = mutex.withLock {
        implementsEdges.add(edge)
        true
    }
    
    override suspend fun addDataFlowEdge(edge: DataFlowEdge): Boolean = mutex.withLock {
        dataFlowEdges.add(edge)
        true
    }
    
    override suspend fun getMethodNode(id: String): MethodNode? = mutex.withLock {
        methodNodes[id]
    }
    
    override suspend fun getClassNode(id: String): ClassNode? = mutex.withLock {
        classNodes[id]
    }
    
    override suspend fun getCallers(methodId: String): List<MethodNode> = mutex.withLock {
        callEdges
            .filter { it.callee.id == methodId }
            .map { it.caller }
            .distinct()
    }
    
    override suspend fun getCallees(methodId: String): List<MethodNode> = mutex.withLock {
        callEdges
            .filter { it.caller.id == methodId }
            .map { it.callee }
            .distinct()
    }
    
    override suspend fun findPaths(sourceId: String, targetId: String, maxDepth: Int): List<CallPath> = mutex.withLock {
        val paths = mutableListOf<CallPath>()
        
        // 简单的BFS实现
        val visited = mutableSetOf<String>()
        val queue = mutableListOf<Pair<String, List<MethodNode>>>()
        
        val sourceNode = methodNodes[sourceId] ?: return@withLock emptyList()
        queue.add(sourceId to listOf(sourceNode))
        
        while (queue.isNotEmpty()) {
            val (currentId, path) = queue.removeAt(0)
            if (path.size > maxDepth) continue
            
            if (currentId == targetId) {
                paths.add(CallPath(
                    id = "path_${paths.size}",
                    methods = path,
                    edges = buildEdges(path),
                    pathType = PathType.NEUTRAL_PATH
                ))
                continue
            }
            
            if (visited.add(currentId)) {
                getCallees(currentId).forEach { callee ->
                    if (!visited.contains(callee.id)) {
                        queue.add(callee.id to path + callee)
                    }
                }
            }
        }
        
        paths
    }
    
    override suspend fun getImpactRadius(methodId: String, maxDepth: Int): Set<MethodNode> = mutex.withLock {
        val visited = mutableSetOf<MethodNode>()
        val queue = mutableListOf<Pair<String, Int>>()
        queue.add(methodId to 0)
        
        while (queue.isNotEmpty()) {
            val (currentId, depth) = queue.removeAt(0)
            if (depth >= maxDepth) continue
            
            val node = methodNodes[currentId]
            if (node != null && visited.add(node)) {
                // Add callers and callees
                getCallers(currentId).forEach { caller ->
                    if (!visited.any { it.id == caller.id }) {
                        queue.add(caller.id to depth + 1)
                    }
                }
                getCallees(currentId).forEach { callee ->
                    if (!visited.any { it.id == callee.id }) {
                        queue.add(callee.id to depth + 1)
                    }
                }
            }
        }
        
        visited
    }
    
    override suspend fun getMethodCount(): Int = mutex.withLock {
        methodNodes.size
    }
    
    override suspend fun getClassCount(): Int = mutex.withLock {
        classNodes.size
    }
    
    override suspend fun clear() = mutex.withLock {
        methodNodes.clear()
        classNodes.clear()
        callEdges.clear()
        implementsEdges.clear()
        dataFlowEdges.clear()
        hotPathCache.clear()
    }
    
    override suspend fun <T> transaction(block: suspend GraphEngine.() -> T): T = mutex.withLock {
        block()
    }
    
    override suspend fun saveToFile(filePath: String): Boolean = mutex.withLock {
        // TODO: Implement serialization
        true
    }
    
    override suspend fun loadFromFile(filePath: String): Boolean = mutex.withLock {
        // TODO: Implement deserialization
        true
    }
    
    override suspend fun getHotPaths(limit: Int): List<CallPath> = mutex.withLock {
        hotPathCache.take(limit)
    }
    
    override suspend fun updateRiskScore(methodId: String, score: Double): Boolean = mutex.withLock {
        val node = methodNodes[methodId] ?: return@withLock false
        methodNodes[methodId] = node.copy(riskScore = score)
        true
    }
    
    override suspend fun incrementalUpdate(changes: List<FileChange>): UpdateResult = mutex.withLock {
        var affectedNodes = 0
        val errors = mutableListOf<String>()
        
        changes.forEach { change ->
            try {
                when (change.changeType) {
                    ChangeType.DELETED -> {
                        change.deletedMethods.forEach { methodId ->
                            if (methodNodes.remove(methodId) != null) {
                                affectedNodes++
                                // Remove related edges
                                callEdges.removeAll { it.caller.id == methodId || it.callee.id == methodId }
                                implementsEdges.removeAll { 
                                    it.interfaceMethod.id == methodId || it.implementationMethod.id == methodId 
                                }
                                dataFlowEdges.removeAll { it.source.id == methodId || it.target.id == methodId }
                            }
                        }
                    }
                    ChangeType.ADDED -> {
                        // External processing needed for new methods
                        affectedNodes += change.addedMethods.size
                    }
                    ChangeType.MODIFIED -> {
                        // External processing needed for modified methods
                        affectedNodes += change.modifiedMethods.size
                    }
                }
            } catch (e: Exception) {
                errors.add("Error updating ${change.filePath}: ${e.message}")
            }
        }
        
        UpdateResult(
            success = errors.isEmpty(),
            affectedNodes = affectedNodes,
            affectedEdges = 0, // Would need to track this properly
            errors = errors
        )
    }
    
    override suspend fun removeFileNodes(filePath: String) = mutex.withLock {
        val methodsToRemove = methodNodes.values.filter { it.filePath == filePath }.map { it.id }
        val classesToRemove = classNodes.values.filter { it.filePath == filePath }.map { it.id }
        
        methodsToRemove.forEach { methodId ->
            methodNodes.remove(methodId)
            callEdges.removeAll { it.caller.id == methodId || it.callee.id == methodId }
            implementsEdges.removeAll { it.interfaceMethod.id == methodId || it.implementationMethod.id == methodId }
            dataFlowEdges.removeAll { it.source.id == methodId || it.target.id == methodId }
        }
        
        classesToRemove.forEach { classId ->
            classNodes.remove(classId)
        }
    }
    
    override suspend fun renameFileNodes(oldPath: String, newPath: String) = mutex.withLock {
        methodNodes.values.filter { it.filePath == oldPath }.forEach { method ->
            methodNodes[method.id] = method.copy(filePath = newPath)
        }
        
        classNodes.values.filter { it.filePath == oldPath }.forEach { clazz ->
            classNodes[clazz.id] = clazz.copy(filePath = newPath)
        }
    }
    
    override suspend fun updateFromAnalysis(analysisResult: com.vyibc.autocrplugin.analyzer.FileAnalysisResult) = mutex.withLock {
        // Update classes
        analysisResult.classes.forEach { classAnalysis ->
            val classNode = ClassNode(
                id = ClassNode.generateId(classAnalysis.packageName, classAnalysis.className),
                className = classAnalysis.className,
                packageName = classAnalysis.packageName,
                blockType = classAnalysis.blockType,
                isInterface = classAnalysis.isInterface,
                isAbstract = classAnalysis.isAbstract,
                filePath = analysisResult.filePath,
                implementedInterfaces = classAnalysis.implementedInterfaces,
                superClass = classAnalysis.superClass,
                annotations = classAnalysis.annotations,
                methodCount = classAnalysis.methodCount,
                fieldCount = classAnalysis.fieldCount,
                cohesion = classAnalysis.cohesion,
                coupling = classAnalysis.coupling,
                designPatterns = classAnalysis.designPatterns
            )
            classNodes[classNode.id] = classNode
        }
        
        // Update methods
        analysisResult.methods.forEach { methodAnalysis ->
            val methodNode = MethodNode(
                id = methodAnalysis.methodId,
                methodName = methodAnalysis.methodName,
                signature = methodAnalysis.signature,
                returnType = methodAnalysis.returnType,
                paramTypes = methodAnalysis.paramTypes,
                blockType = methodAnalysis.blockType,
                isInterface = methodAnalysis.isInterface,
                annotations = methodAnalysis.annotations,
                filePath = methodAnalysis.filePath,
                lineNumber = methodAnalysis.lineNumber,
                startLineNumber = methodAnalysis.startLine,
                endLineNumber = methodAnalysis.endLine,
                cyclomaticComplexity = methodAnalysis.cyclomaticComplexity,
                linesOfCode = methodAnalysis.linesOfCode,
                hasTests = methodAnalysis.hasTests
            )
            methodNodes[methodNode.id] = methodNode
        }
        
        // Update call relationships
        analysisResult.callRelationships.forEach { relationship ->
            val caller = methodNodes[relationship.callerId]
            val callee = methodNodes[relationship.calleeId]
            
            if (caller != null && callee != null) {
                val edge = CallsEdge(
                    caller = caller,
                    callee = callee,
                    callType = relationship.callType,
                    lineNumber = relationship.lineNumber,
                    isConditional = relationship.isConditional,
                    context = relationship.context,
                    intentWeight = 0.5,
                    riskWeight = 0.5
                )
                callEdges.add(edge)
            }
        }
    }
    
    override suspend fun getStatistics(): GraphStatistics = mutex.withLock {
        val complexities = methodNodes.values.map { it.cyclomaticComplexity.toDouble() }
        val avgComplexity = if (complexities.isNotEmpty()) complexities.average() else 0.0
        
        val riskDistribution = methodNodes.values.groupBy { 
            when {
                it.riskScore < 0.3 -> RiskLevel.LOW
                it.riskScore < 0.6 -> RiskLevel.MEDIUM
                it.riskScore < 0.8 -> RiskLevel.HIGH
                else -> RiskLevel.CRITICAL
            }
        }.mapValues { it.value.size }
        
        val hotspots = methodNodes.values
            .sortedByDescending { it.inDegree + it.outDegree }
            .take(10)
            .map { it.id }
        
        GraphStatistics(
            nodeCount = methodNodes.size + classNodes.size,
            edgeCount = callEdges.size + implementsEdges.size + dataFlowEdges.size,
            methodCount = methodNodes.size,
            classCount = classNodes.size,
            averageComplexity = avgComplexity,
            hotspotMethods = hotspots,
            riskDistribution = riskDistribution
        )
    }
    
    override suspend fun findCallPaths(
        startMethodId: String, 
        endMethodId: String, 
        maxDepth: Int
    ): List<CallPath> = findPaths(startMethodId, endMethodId, maxDepth)
    
    override suspend fun getDirectCallers(methodId: String): List<MethodNode> = getCallers(methodId)
    
    override suspend fun getDirectCallees(methodId: String): List<MethodNode> = getCallees(methodId)
    
    override suspend fun calculateRiskPropagation(
        methodId: String, 
        depth: Int
    ): Map<String, Double> = mutex.withLock {
        val riskMap = mutableMapOf<String, Double>()
        val visited = mutableSetOf<String>()
        val queue = mutableListOf<Triple<String, Int, Double>>() // id, depth, accumulated risk
        
        val initialNode = methodNodes[methodId] ?: return@withLock emptyMap()
        queue.add(Triple(methodId, 0, initialNode.riskScore))
        
        while (queue.isNotEmpty()) {
            val (currentId, currentDepth, accumulatedRisk) = queue.removeAt(0)
            
            if (currentDepth > depth || !visited.add(currentId)) continue
            
            riskMap[currentId] = accumulatedRisk
            
            // Propagate to callers with decay factor
            getCallers(currentId).forEach { caller ->
                if (!visited.contains(caller.id)) {
                    val decayFactor = 0.8.pow(currentDepth + 1)
                    queue.add(Triple(caller.id, currentDepth + 1, accumulatedRisk * decayFactor))
                }
            }
        }
        
        riskMap
    }
    
    override suspend fun addCallsEdge(edge: CallsEdge): Boolean = addCallEdge(edge)
    
    private fun Double.pow(n: Int): Double = Math.pow(this, n.toDouble())
    
    
    private fun buildEdges(path: List<MethodNode>): List<CallsEdge> {
        val edges = mutableListOf<CallsEdge>()
        for (i in 0 until path.size - 1) {
            val edge = callEdges.find { 
                it.caller.id == path[i].id && it.callee.id == path[i + 1].id 
            }
            edge?.let { edges.add(it) }
        }
        return edges
    }
}