package com.vyibc.autocrplugin.preprocessor

import com.vyibc.autocrplugin.analyzer.FileAnalysisResult
import com.vyibc.autocrplugin.graph.engine.LocalGraphEngine
import com.vyibc.autocrplugin.graph.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * 风险权重计算器 - V5.1版本
 * 架构风险检测、爆炸半径计算、变更复杂度分析算法
 */
class RiskWeightCalculator(
    private val graphEngine: LocalGraphEngine
) {
    
    /**
     * 计算调用路径的风险权重
     */
    suspend fun calculatePathRiskWeight(
        path: CallPath,
        changedMethods: Set<String>,
        fileAnalysisResults: List<FileAnalysisResult>
    ): RiskWeightResult = withContext(Dispatchers.Default) {
        
        // 1. 架构风险评分 (35% 权重)
        val architecturalRiskScore = calculateArchitecturalRiskScore(path)
        
        // 2. 爆炸半径评分 (30% 权重)
        val blastRadiusScore = calculateBlastRadiusScore(path, changedMethods)
        
        // 3. 变更复杂度评分 (25% 权重)
        val changeComplexityScore = calculateChangeComplexityScore(path, changedMethods)
        
        // 4. 数据流风险评分 (10% 权重)
        val dataFlowRiskScore = calculateDataFlowRiskScore(path)
        
        // 综合计算风险权重
        val totalRisk = architecturalRiskScore * 0.35 +
                       blastRadiusScore * 0.30 +
                       changeComplexityScore * 0.25 +
                       dataFlowRiskScore * 0.10
        
        RiskWeightResult(
            totalRisk = totalRisk,
            architecturalRiskScore = architecturalRiskScore,
            blastRadiusScore = blastRadiusScore,
            changeComplexityScore = changeComplexityScore,
            dataFlowRiskScore = dataFlowRiskScore,
            riskLevel = determineRiskLevel(totalRisk),
            confidence = calculateRiskConfidence(path, changedMethods.size),
            details = RiskAnalysisDetails(
                criticalMethods = identifyCriticalMethods(path),
                riskFactors = identifyRiskFactors(path, changedMethods),
                mitigationSuggestions = generateMitigationSuggestions(path),
                impactedComponents = identifyImpactedComponents(path, changedMethods)
            )
        )
    }
    
    /**
     * 计算方法节点的风险权重
     */
    suspend fun calculateMethodRiskWeight(
        method: MethodNode,
        context: RiskCalculationContext
    ): MethodRiskWeight = withContext(Dispatchers.Default) {
        
        // 方法级别的架构风险
        val architecturalRisk = calculateMethodArchitecturalRisk(method, context)
        
        // 方法级别的复杂度风险
        val complexityRisk = calculateMethodComplexityRisk(method)
        
        // 方法级别的依赖风险
        val dependencyRisk = calculateMethodDependencyRisk(method, context)
        
        // 方法级别的变更历史风险
        val changeHistoryRisk = calculateMethodChangeHistoryRisk(method, context)
        
        // 综合风险权重
        val totalRisk = architecturalRisk * 0.3 +
                       complexityRisk * 0.25 +
                       dependencyRisk * 0.25 +
                       changeHistoryRisk * 0.2
        
        MethodRiskWeight(
            methodId = method.id,
            totalRisk = totalRisk,
            architecturalRisk = architecturalRisk,
            complexityRisk = complexityRisk,
            dependencyRisk = dependencyRisk,
            changeHistoryRisk = changeHistoryRisk,
            riskCategory = determineRiskCategory(method, totalRisk),
            criticality = calculateMethodCriticality(method, context)
        )
    }
    
    /**
     * 计算架构风险评分
     */
    private suspend fun calculateArchitecturalRiskScore(path: CallPath): Double {
        var riskScore = 0.0
        
        // 1. 跨层调用风险 (30%)
        val crossLayerRisk = calculateCrossLayerRisk(path)
        riskScore += crossLayerRisk * 0.3
        
        // 2. 循环依赖风险 (25%)
        val circularDependencyRisk = calculateCircularDependencyRisk(path)
        riskScore += circularDependencyRisk * 0.25
        
        // 3. 紧耦合风险 (25%)
        val tightCouplingRisk = calculateTightCouplingRisk(path)
        riskScore += tightCouplingRisk * 0.25
        
        // 4. 单点故障风险 (20%)
        val singlePointFailureRisk = calculateSinglePointFailureRisk(path)
        riskScore += singlePointFailureRisk * 0.2
        
        return minOf(1.0, maxOf(0.0, riskScore))
    }
    
    /**
     * 计算爆炸半径评分
     */
    private suspend fun calculateBlastRadiusScore(
        path: CallPath,
        changedMethods: Set<String>
    ): Double {
        // 找出路径中被修改的方法
        val changedMethodsInPath = path.methods.filter { it.id in changedMethods }
        if (changedMethodsInPath.isEmpty()) return 0.0
        
        var totalBlastRadius = 0.0
        
        changedMethodsInPath.forEach { changedMethod ->
            // 计算每个被修改方法的影响范围
            val impactRadius = calculateMethodImpactRadius(changedMethod, path)
            totalBlastRadius += impactRadius
        }
        
        // 标准化爆炸半径
        val normalizedRadius = totalBlastRadius / maxOf(changedMethodsInPath.size, 1)
        return minOf(1.0, normalizedRadius)
    }
    
    /**
     * 计算变更复杂度评分
     */
    private fun calculateChangeComplexityScore(
        path: CallPath,
        changedMethods: Set<String>
    ): Double {
        if (changedMethods.isEmpty()) return 0.0
        
        val changedMethodsInPath = path.methods.filter { it.id in changedMethods }
        if (changedMethodsInPath.isEmpty()) return 0.0
        
        // 计算变更复杂度的多个维度
        val complexityFactors = mutableListOf<Double>()
        
        // 1. 圈复杂度影响
        val avgComplexity = changedMethodsInPath.map { it.cyclomaticComplexity }.average()
        complexityFactors.add(minOf(1.0, avgComplexity / 20.0)) // 20为复杂度上限
        
        // 2. 代码行数影响
        val avgLinesOfCode = changedMethodsInPath.map { it.linesOfCode }.average()
        complexityFactors.add(minOf(1.0, avgLinesOfCode / 100.0)) // 100行为基准
        
        // 3. 参数数量影响
        val avgParamCount = changedMethodsInPath.map { it.paramTypes.size }.average()
        complexityFactors.add(minOf(1.0, avgParamCount / 10.0)) // 10个参数为基准
        
        // 4. 调用深度影响
        val maxDepthInPath = calculateMaxCallDepth(path, changedMethodsInPath)
        complexityFactors.add(minOf(1.0, maxDepthInPath / 10.0)) // 10层为基准
        
        return complexityFactors.average()
    }
    
    /**
     * 计算数据流风险评分
     */
    private suspend fun calculateDataFlowRiskScore(path: CallPath): Double {
        var riskScore = 0.0
        
        // 1. 全局状态修改风险
        val globalStateRisk = calculateGlobalStateRisk(path)
        riskScore += globalStateRisk * 0.4
        
        // 2. 并发访问风险
        val concurrencyRisk = calculateConcurrencyRisk(path)
        riskScore += concurrencyRisk * 0.3
        
        // 3. 数据一致性风险
        val consistencyRisk = calculateDataConsistencyRisk(path)
        riskScore += consistencyRisk * 0.3
        
        return minOf(1.0, maxOf(0.0, riskScore))
    }
    
    /**
     * 计算跨层调用风险
     */
    private fun calculateCrossLayerRisk(path: CallPath): Double {
        val layerHierarchy = mapOf(
            BlockType.CONTROLLER to 1,
            BlockType.SERVICE to 2,
            BlockType.REPOSITORY to 3,
            BlockType.ENTITY to 4,
            BlockType.UTIL to 5
        )
        
        var violations = 0
        var totalTransitions = 0
        
        for (i in 0 until path.methods.size - 1) {
            val currentMethod = path.methods[i]
            val nextMethod = path.methods[i + 1]
            
            val currentLayer = layerHierarchy[currentMethod.blockType] ?: 999
            val nextLayer = layerHierarchy[nextMethod.blockType] ?: 999
            
            totalTransitions++
            
            // 检查是否违反分层架构原则
            if (nextLayer < currentLayer) { // 下层调用上层
                violations++
            }
            if (abs(nextLayer - currentLayer) > 1) { // 跨越多层
                violations++
            }
        }
        
        return if (totalTransitions > 0) violations.toDouble() / totalTransitions else 0.0
    }
    
    /**
     * 计算循环依赖风险
     */
    private fun calculateCircularDependencyRisk(path: CallPath): Double {
        val methodIds = path.methods.map { it.id }
        val duplicates = methodIds.size - methodIds.toSet().size
        
        return minOf(1.0, duplicates.toDouble() / methodIds.size)
    }
    
    /**
     * 计算紧耦合风险
     */
    private fun calculateTightCouplingRisk(path: CallPath): Double {
        if (path.methods.size <= 1) return 0.0
        
        // 计算路径中方法的平均出度（调用其他方法的数量）
        val avgOutDegree = path.methods.map { it.outDegree }.average()
        
        // 计算路径密度
        val pathDensity = path.edges.size.toDouble() / (path.methods.size * (path.methods.size - 1) / 2)
        
        // 综合评估紧耦合风险
        val outDegreeRisk = minOf(1.0, avgOutDegree / 10.0) // 10个调用为基准
        val densityRisk = pathDensity
        
        return (outDegreeRisk + densityRisk) / 2.0
    }
    
    /**
     * 计算单点故障风险
     */
    private fun calculateSinglePointFailureRisk(path: CallPath): Double {
        // 识别路径中的关键节点（高入度的方法）
        val criticalMethods = path.methods.filter { it.inDegree > 5 } // 5个以上调用者认为是关键
        
        if (criticalMethods.isEmpty()) return 0.0
        
        // 计算关键方法在路径中的占比
        val criticalRatio = criticalMethods.size.toDouble() / path.methods.size
        
        // 计算最大入度
        val maxInDegree = path.methods.maxOfOrNull { it.inDegree } ?: 0
        val inDegreeRisk = minOf(1.0, maxInDegree / 20.0) // 20个调用者为高风险基准
        
        return (criticalRatio + inDegreeRisk) / 2.0
    }
    
    /**
     * 计算方法影响半径
     */
    private suspend fun calculateMethodImpactRadius(
        method: MethodNode,
        context: CallPath
    ): Double {
        // 基于方法在图中的位置计算影响半径
        val directCallers = method.inDegree
        val indirectImpact = calculateIndirectImpact(method)
        
        // 使用对数函数避免极值
        val directImpact = ln(directCallers + 1.0) / ln(10.0)
        val totalImpact = directImpact + indirectImpact * 0.5
        
        return minOf(1.0, totalImpact)
    }
    
    /**
     * 计算间接影响
     */
    private suspend fun calculateIndirectImpact(method: MethodNode): Double {
        // 简化实现：基于方法的复杂度和出度估算间接影响
        val complexityFactor = method.cyclomaticComplexity / 10.0
        val connectivityFactor = method.outDegree / 5.0
        
        return minOf(1.0, (complexityFactor + connectivityFactor) / 2.0)
    }
    
    /**
     * 计算最大调用深度
     */
    private fun calculateMaxCallDepth(
        path: CallPath,
        changedMethods: List<MethodNode>
    ): Double {
        // 简化实现：返回路径长度作为深度
        return path.methods.size.toDouble()
    }
    
    /**
     * 计算全局状态风险
     */
    private fun calculateGlobalStateRisk(path: CallPath): Double {
        // 检查是否涉及全局变量、静态变量、单例等
        val globalRiskMethods = path.methods.count { method ->
            method.annotations.any { 
                it.contains("singleton", ignoreCase = true) ||
                it.contains("component", ignoreCase = true) ||
                it.contains("bean", ignoreCase = true)
            } || method.methodName.contains("static", ignoreCase = true)
        }
        
        return globalRiskMethods.toDouble() / maxOf(path.methods.size, 1)
    }
    
    /**
     * 计算并发风险
     */
    private fun calculateConcurrencyRisk(path: CallPath): Double {
        val concurrentMethods = path.methods.count { method ->
            method.annotations.any {
                it.contains("async", ignoreCase = true) ||
                it.contains("synchronized", ignoreCase = true) ||
                it.contains("thread", ignoreCase = true) ||
                it.contains("concurrent", ignoreCase = true)
            }
        }
        
        return concurrentMethods.toDouble() / maxOf(path.methods.size, 1)
    }
    
    /**
     * 计算数据一致性风险
     */
    private fun calculateDataConsistencyRisk(path: CallPath): Double {
        val transactionalMethods = path.methods.count { method ->
            method.annotations.any {
                it.contains("transactional", ignoreCase = true)
            }
        }
        
        val dataAccessMethods = path.methods.count { method ->
            method.blockType == BlockType.REPOSITORY ||
            method.blockType == BlockType.MAPPER
        }
        
        // 如果有数据访问但没有事务管理，风险较高
        return if (dataAccessMethods > 0 && transactionalMethods == 0) {
            0.8
        } else if (dataAccessMethods > transactionalMethods) {
            0.5
        } else {
            0.2
        }
    }
    
    /**
     * 确定风险级别
     */
    private fun determineRiskLevel(totalRisk: Double): com.vyibc.autocrplugin.graph.engine.RiskLevel {
        return when {
            totalRisk >= 0.8 -> com.vyibc.autocrplugin.graph.engine.RiskLevel.CRITICAL
            totalRisk >= 0.6 -> com.vyibc.autocrplugin.graph.engine.RiskLevel.HIGH
            totalRisk >= 0.4 -> com.vyibc.autocrplugin.graph.engine.RiskLevel.MEDIUM
            else -> com.vyibc.autocrplugin.graph.engine.RiskLevel.LOW
        }
    }
    
    /**
     * 计算风险置信度
     */
    private fun calculateRiskConfidence(path: CallPath, changedMethodCount: Int): Double {
        val pathLengthFactor = minOf(1.0, path.methods.size / 10.0)
        val changesFactor = minOf(1.0, changedMethodCount / 5.0)
        val methodQualityFactor = path.methods.map { method ->
            if (method.hasTests && method.annotations.isNotEmpty()) 1.0 else 0.5
        }.average()
        
        return (pathLengthFactor + changesFactor + methodQualityFactor) / 3.0
    }
    
    /**
     * 识别关键方法
     */
    private fun identifyCriticalMethods(path: CallPath): List<String> {
        return path.methods.filter { method ->
            method.inDegree > 5 || // 高入度
            method.cyclomaticComplexity > 15 || // 高复杂度
            method.blockType == BlockType.CONTROLLER || // 入口层
            !method.hasTests // 无测试覆盖
        }.map { it.id }
    }
    
    /**
     * 识别风险因子
     */
    private fun identifyRiskFactors(path: CallPath, changedMethods: Set<String>): List<String> {
        val factors = mutableListOf<String>()
        
        if (path.methods.any { it.cyclomaticComplexity > 15 }) {
            factors.add("高复杂度方法")
        }
        
        if (path.methods.any { !it.hasTests }) {
            factors.add("缺少测试覆盖")
        }
        
        if (path.methods.any { it.inDegree > 10 }) {
            factors.add("高依赖方法")
        }
        
        val layerViolations = calculateCrossLayerRisk(path)
        if (layerViolations > 0.3) {
            factors.add("架构分层违规")
        }
        
        return factors
    }
    
    /**
     * 生成缓解建议
     */
    private fun generateMitigationSuggestions(path: CallPath): List<String> {
        val suggestions = mutableListOf<String>()
        
        val avgComplexity = path.methods.map { it.cyclomaticComplexity }.average()
        if (avgComplexity > 10) {
            suggestions.add("考虑重构高复杂度方法")
        }
        
        val testCoverage = path.methods.count { it.hasTests }.toDouble() / path.methods.size
        if (testCoverage < 0.7) {
            suggestions.add("增加单元测试覆盖率")
        }
        
        if (calculateCrossLayerRisk(path) > 0.3) {
            suggestions.add("遵循分层架构原则")
        }
        
        return suggestions
    }
    
    /**
     * 识别受影响的组件
     */
    private fun identifyImpactedComponents(path: CallPath, changedMethods: Set<String>): List<String> {
        val impactedClasses = path.methods
            .filter { it.id in changedMethods }
            .map { it.id.substringBefore("#") }
            .toSet()
        
        return impactedClasses.toList()
    }
    
    // 方法级别的风险计算
    private fun calculateMethodArchitecturalRisk(method: MethodNode, context: RiskCalculationContext): Double {
        var risk = 0.0
        
        // 基于架构层级评估
        val layerRisk = when (method.blockType) {
            BlockType.CONTROLLER -> 0.7 // 入口层风险较高
            BlockType.SERVICE -> 0.5    // 业务层中等风险
            BlockType.REPOSITORY -> 0.6  // 数据层较高风险
            else -> 0.3
        }
        risk += layerRisk * 0.5
        
        // 基于注解评估
        val hasTransactional = method.annotations.any { it.contains("transactional", ignoreCase = true) }
        if (hasTransactional) risk += 0.3
        
        return minOf(1.0, risk)
    }
    
    private fun calculateMethodComplexityRisk(method: MethodNode): Double {
        val complexityRisk = when {
            method.cyclomaticComplexity <= 5 -> 0.1
            method.cyclomaticComplexity <= 10 -> 0.3
            method.cyclomaticComplexity <= 15 -> 0.6
            else -> 0.9
        }
        
        val sizeRisk = when {
            method.linesOfCode <= 20 -> 0.1
            method.linesOfCode <= 50 -> 0.3
            method.linesOfCode <= 100 -> 0.6
            else -> 0.9
        }
        
        return (complexityRisk + sizeRisk) / 2.0
    }
    
    private fun calculateMethodDependencyRisk(method: MethodNode, context: RiskCalculationContext): Double {
        val inDegreeRisk = minOf(1.0, method.inDegree / 10.0)
        val outDegreeRisk = minOf(1.0, method.outDegree / 8.0)
        
        return (inDegreeRisk + outDegreeRisk) / 2.0
    }
    
    private fun calculateMethodChangeHistoryRisk(method: MethodNode, context: RiskCalculationContext): Double {
        // 简化实现：基于方法是否经常变更
        return 0.5 // 默认中等风险
    }
    
    private fun determineRiskCategory(method: MethodNode, totalRisk: Double): RiskCategory {
        return when {
            method.blockType == BlockType.CONTROLLER && totalRisk > 0.6 -> RiskCategory.ENTRY_POINT_RISK
            method.cyclomaticComplexity > 15 -> RiskCategory.COMPLEXITY_RISK
            method.inDegree > 10 -> RiskCategory.DEPENDENCY_RISK
            !method.hasTests && totalRisk > 0.5 -> RiskCategory.QUALITY_RISK
            else -> RiskCategory.GENERAL_RISK
        }
    }
    
    private fun calculateMethodCriticality(method: MethodNode, context: RiskCalculationContext): Double {
        val factors = listOf(
            method.inDegree / 10.0,
            if (method.blockType == BlockType.CONTROLLER) 1.0 else 0.5,
            method.cyclomaticComplexity / 20.0,
            if (!method.hasTests) 0.8 else 0.2
        )
        
        return minOf(1.0, factors.average())
    }
}

/**
 * 风险权重计算结果
 */
data class RiskWeightResult(
    val totalRisk: Double,                    // 总风险权重 [0.0, 1.0]
    val architecturalRiskScore: Double,       // 架构风险评分
    val blastRadiusScore: Double,            // 爆炸半径评分
    val changeComplexityScore: Double,        // 变更复杂度评分
    val dataFlowRiskScore: Double,           // 数据流风险评分
    val riskLevel: com.vyibc.autocrplugin.graph.engine.RiskLevel,                // 风险级别
    val confidence: Double,                  // 置信度
    val details: RiskAnalysisDetails         // 详细分析结果
)

/**
 * 方法风险权重
 */
data class MethodRiskWeight(
    val methodId: String,
    val totalRisk: Double,
    val architecturalRisk: Double,
    val complexityRisk: Double,
    val dependencyRisk: Double,
    val changeHistoryRisk: Double,
    val riskCategory: RiskCategory,
    val criticality: Double
)

/**
 * 风险分析详情
 */
data class RiskAnalysisDetails(
    val criticalMethods: List<String>,        // 关键方法列表
    val riskFactors: List<String>,           // 风险因子
    val mitigationSuggestions: List<String>, // 缓解建议
    val impactedComponents: List<String>     // 受影响的组件
)

/**
 * 风险计算上下文
 */
data class RiskCalculationContext(
    val changedMethods: Set<String>,
    val fileAnalysisResults: List<FileAnalysisResult>,
    val projectHistory: ProjectHistory
)

/**
 * 项目历史
 */
data class ProjectHistory(
    val hotspotMethods: Set<String>,         // 热点方法
    val frequentlyChangedFiles: Set<String>, // 频繁变更的文件
    val criticalPaths: List<CallPath>        // 关键路径
)

/**
 * 风险分类
 */
enum class RiskCategory {
    ENTRY_POINT_RISK,    // 入口点风险
    COMPLEXITY_RISK,     // 复杂度风险
    DEPENDENCY_RISK,     // 依赖风险
    QUALITY_RISK,        // 质量风险
    CONCURRENCY_RISK,    // 并发风险
    DATA_RISK,           // 数据风险
    GENERAL_RISK         // 一般风险
}