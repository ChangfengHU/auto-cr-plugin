package com.vyibc.autocrplugin.preprocessor

import com.vyibc.autocrplugin.analyzer.CommitAnalysis
import com.vyibc.autocrplugin.analyzer.FileAnalysisResult
import com.vyibc.autocrplugin.graph.engine.LocalGraphEngine
import com.vyibc.autocrplugin.graph.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * 路径筛选和排序器 - V5.1版本
 * Golden Path和Risk Path识别、权重排序算法
 */
class PathFilterAndSorter(
    private val graphEngine: LocalGraphEngine,
    private val intentWeightCalculator: IntentWeightCalculator,
    private val riskWeightCalculator: RiskWeightCalculator
) {
    
    /**
     * 分析并排序调用路径
     */
    suspend fun analyzeAndSortPaths(
        paths: List<CallPath>,
        context: PathAnalysisContext
    ): PathAnalysisResult = withContext(Dispatchers.Default) {
        
        // 并行计算意图权重和风险权重
        val intentWeightsDeferred = async {
            calculatePathIntentWeights(paths, context)
        }
        
        val riskWeightsDeferred = async {
            calculatePathRiskWeights(paths, context)
        }
        
        val intentWeights = intentWeightsDeferred.await()
        val riskWeights = riskWeightsDeferred.await()
        
        // 分析路径并分类
        val analyzedPaths = paths.mapIndexed { index, path ->
            val intentWeight = intentWeights[index]
            val riskWeight = riskWeights[index]
            
            AnalyzedPath(
                path = path,
                intentWeight = intentWeight,
                riskWeight = riskWeight,
                pathType = determinePathType(intentWeight, riskWeight),
                priority = calculatePathPriority(intentWeight, riskWeight),
                confidence = calculatePathConfidence(path, intentWeight, riskWeight)
            )
        }
        
        // 筛选Golden Path和Risk Path
        val goldenPaths = filterGoldenPaths(analyzedPaths, context)
        val riskPaths = filterRiskPaths(analyzedPaths, context)
        val neutralPaths = filterNeutralPaths(analyzedPaths, goldenPaths + riskPaths)
        
        // 排序各类路径
        val sortedGoldenPaths = sortGoldenPaths(goldenPaths)
        val sortedRiskPaths = sortRiskPaths(riskPaths)
        val sortedNeutralPaths = sortNeutralPaths(neutralPaths)
        
        // 生成分析报告
        val analysisReport = generateAnalysisReport(
            sortedGoldenPaths, sortedRiskPaths, sortedNeutralPaths, context
        )
        
        PathAnalysisResult(
            goldenPaths = sortedGoldenPaths,
            riskPaths = sortedRiskPaths,
            neutralPaths = sortedNeutralPaths,
            analysisReport = analysisReport,
            totalAnalyzedPaths = paths.size,
            processingTimestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * 计算路径意图权重
     */
    private suspend fun calculatePathIntentWeights(
        paths: List<CallPath>,
        context: PathAnalysisContext
    ): List<IntentWeightResult> = withContext(Dispatchers.Default) {
        paths.map { path ->
            intentWeightCalculator.calculatePathIntentWeight(
                path = path,
                commitHistory = context.commitHistory,
                fileAnalysisResults = context.fileAnalysisResults
            )
        }
    }
    
    /**
     * 计算路径风险权重
     */
    private suspend fun calculatePathRiskWeights(
        paths: List<CallPath>,
        context: PathAnalysisContext
    ): List<RiskWeightResult> = withContext(Dispatchers.Default) {
        paths.map { path ->
            riskWeightCalculator.calculatePathRiskWeight(
                path = path,
                changedMethods = context.changedMethods,
                fileAnalysisResults = context.fileAnalysisResults
            )
        }
    }
    
    /**
     * 确定路径类型
     */
    private fun determinePathType(
        intentWeight: IntentWeightResult,
        riskWeight: RiskWeightResult
    ): PathType {
        val intentScore = intentWeight.totalWeight
        val riskScore = riskWeight.totalRisk
        
        return when {
            // Golden Path: 高意图权重 + 低风险
            intentScore >= 0.7 && riskScore <= 0.3 -> PathType.GOLDEN_PATH
            
            // Risk Path: 高风险权重，不论意图权重
            riskScore >= 0.6 -> PathType.RISK_PATH
            
            // Critical Path: 中高意图权重 + 中等风险
            intentScore >= 0.5 && riskScore >= 0.4 && riskScore <= 0.6 -> PathType.CRITICAL_PATH
            
            // Neutral Path: 其他情况
            else -> PathType.NEUTRAL_PATH
        }
    }
    
    /**
     * 计算路径优先级
     */
    private fun calculatePathPriority(
        intentWeight: IntentWeightResult,
        riskWeight: RiskWeightResult
    ): PathPriority {
        val combinedScore = intentWeight.totalWeight * 0.6 + (1.0 - riskWeight.totalRisk) * 0.4
        
        return when {
            combinedScore >= 0.8 -> PathPriority.CRITICAL
            combinedScore >= 0.6 -> PathPriority.HIGH
            combinedScore >= 0.4 -> PathPriority.MEDIUM
            else -> PathPriority.LOW
        }
    }
    
    /**
     * 计算路径置信度
     */
    private fun calculatePathConfidence(
        path: CallPath,
        intentWeight: IntentWeightResult,
        riskWeight: RiskWeightResult
    ): Double {
        val pathComplexityFactor = minOf(1.0, path.methods.size / 10.0)
        val intentConfidence = intentWeight.confidence
        val riskConfidence = riskWeight.confidence
        
        return (pathComplexityFactor + intentConfidence + riskConfidence) / 3.0
    }
    
    /**
     * 筛选Golden Path
     */
    private fun filterGoldenPaths(
        analyzedPaths: List<AnalyzedPath>,
        context: PathAnalysisContext
    ): List<AnalyzedPath> {
        return analyzedPaths.filter { analyzedPath ->
            val path = analyzedPath.path
            val intentWeight = analyzedPath.intentWeight
            val riskWeight = analyzedPath.riskWeight
            
            // Golden Path条件
            val hasHighIntentWeight = intentWeight.totalWeight >= 0.7
            val hasLowRisk = riskWeight.totalRisk <= 0.3
            val hasBusinessValue = intentWeight.businessValueScore >= 0.6
            val hasQualityCode = intentWeight.codeQualityScore >= 0.6
            val isWellTested = path.methods.count { it.hasTests }.toDouble() / path.methods.size >= 0.7
            
            // 额外的Golden Path特征
            val hasNewEndpoint = path.hasNewEndpoint()
            val isCoreBusinessEntity = path.isCoreBusinessEntity()
            val hasDataModelChanges = path.hasDataModelChanges()
            
            // 综合判断
            val baseCondition = hasHighIntentWeight && hasLowRisk
            val qualityCondition = hasBusinessValue && hasQualityCode
            val businessCondition = hasNewEndpoint || isCoreBusinessEntity || hasDataModelChanges
            
            baseCondition && (qualityCondition || businessCondition || isWellTested)
        }
    }
    
    /**
     * 筛选Risk Path
     */
    private fun filterRiskPaths(
        analyzedPaths: List<AnalyzedPath>,
        context: PathAnalysisContext
    ): List<AnalyzedPath> {
        return analyzedPaths.filter { analyzedPath ->
            val path = analyzedPath.path
            val riskWeight = analyzedPath.riskWeight
            
            // Risk Path条件
            val hasHighRisk = riskWeight.totalRisk >= 0.6
            val hasArchitecturalRisk = riskWeight.architecturalRiskScore >= 0.7
            val hasHighBlastRadius = riskWeight.blastRadiusScore >= 0.6
            val hasComplexChanges = riskWeight.changeComplexityScore >= 0.7
            
            // 架构风险特征
            val hasLayerViolation = path.hasLayerViolation()
            val hasCircularDependency = path.hasCircularDependency()
            val lackTestCoverage = path.methods.count { it.hasTests }.toDouble() / path.methods.size < 0.3
            
            // 业务风险特征
            val hasTransactionalOperations = path.hasTransactionalOperations()
            val hasExternalApiCalls = path.hasExternalApiCalls()
            val hasDatabaseOperations = path.hasDatabaseOperations()
            
            // 综合判断
            val riskCondition = hasHighRisk || hasArchitecturalRisk || hasHighBlastRadius || hasComplexChanges
            val structuralRisk = hasLayerViolation || hasCircularDependency || lackTestCoverage
            val businessRisk = hasTransactionalOperations && (hasExternalApiCalls || hasDatabaseOperations)
            
            riskCondition || structuralRisk || businessRisk
        }
    }
    
    /**
     * 筛选Neutral Path
     */
    private fun filterNeutralPaths(
        analyzedPaths: List<AnalyzedPath>,
        filteredPaths: List<AnalyzedPath>
    ): List<AnalyzedPath> {
        val filteredPathIds = filteredPaths.map { it.path.id }.toSet()
        return analyzedPaths.filter { it.path.id !in filteredPathIds }
    }
    
    /**
     * 排序Golden Path
     */
    private fun sortGoldenPaths(goldenPaths: List<AnalyzedPath>): List<AnalyzedPath> {
        return goldenPaths.sortedWith(compareByDescending<AnalyzedPath> { analyzedPath ->
            // 主要排序依据：业务价值 + 实现质量
            val intentWeight = analyzedPath.intentWeight
            val businessValue = intentWeight.businessValueScore * 0.4
            val completeness = intentWeight.implementationCompletenessScore * 0.3
            val quality = intentWeight.codeQualityScore * 0.3
            
            businessValue + completeness + quality
        }.thenByDescending { analyzedPath ->
            // 次要排序依据：置信度
            analyzedPath.confidence
        }.thenBy { analyzedPath ->
            // 第三排序依据：风险分数（越低越好）
            analyzedPath.riskWeight.totalRisk
        })
    }
    
    /**
     * 排序Risk Path
     */
    private fun sortRiskPaths(riskPaths: List<AnalyzedPath>): List<AnalyzedPath> {
        return riskPaths.sortedWith(compareByDescending<AnalyzedPath> { analyzedPath ->
            // 主要排序依据：风险权重
            val riskWeight = analyzedPath.riskWeight
            val architecturalRisk = riskWeight.architecturalRiskScore * 0.4
            val blastRadius = riskWeight.blastRadiusScore * 0.3
            val complexity = riskWeight.changeComplexityScore * 0.3
            
            architecturalRisk + blastRadius + complexity
        }.thenByDescending { analyzedPath ->
            // 次要排序依据：业务影响（有业务价值的风险路径优先处理）
            analyzedPath.intentWeight.businessValueScore
        }.thenByDescending { analyzedPath ->
            // 第三排序依据：置信度
            analyzedPath.confidence
        })
    }
    
    /**
     * 排序Neutral Path
     */
    private fun sortNeutralPaths(neutralPaths: List<AnalyzedPath>): List<AnalyzedPath> {
        return neutralPaths.sortedWith(compareByDescending<AnalyzedPath> { analyzedPath ->
            // 主要排序依据：综合权重（意图权重 - 风险权重）
            analyzedPath.intentWeight.totalWeight - analyzedPath.riskWeight.totalRisk
        }.thenByDescending { analyzedPath ->
            // 次要排序依据：业务价值
            analyzedPath.intentWeight.businessValueScore
        }.thenByDescending { analyzedPath ->
            // 第三排序依据：置信度
            analyzedPath.confidence
        })
    }
    
    /**
     * 生成分析报告
     */
    private fun generateAnalysisReport(
        goldenPaths: List<AnalyzedPath>,
        riskPaths: List<AnalyzedPath>,
        neutralPaths: List<AnalyzedPath>,
        context: PathAnalysisContext
    ): PathAnalysisReport {
        
        // 统计信息
        val totalPaths = goldenPaths.size + riskPaths.size + neutralPaths.size
        val goldenPathRatio = goldenPaths.size.toDouble() / totalPaths
        val riskPathRatio = riskPaths.size.toDouble() / totalPaths
        
        // 质量指标
        val avgIntentWeight = (goldenPaths + riskPaths + neutralPaths)
            .map { it.intentWeight.totalWeight }.average()
        val avgRiskWeight = (goldenPaths + riskPaths + neutralPaths)
            .map { it.riskWeight.totalRisk }.average()
        val avgConfidence = (goldenPaths + riskPaths + neutralPaths)
            .map { it.confidence }.average()
        
        // 关键发现
        val keyFindings = generateKeyFindings(goldenPaths, riskPaths, neutralPaths, context)
        
        // 建议
        val recommendations = generateRecommendations(goldenPaths, riskPaths, neutralPaths, context)
        
        return PathAnalysisReport(
            summary = PathAnalysisSummary(
                totalPaths = totalPaths,
                goldenPathCount = goldenPaths.size,
                riskPathCount = riskPaths.size,
                neutralPathCount = neutralPaths.size,
                goldenPathRatio = goldenPathRatio,
                riskPathRatio = riskPathRatio,
                avgIntentWeight = avgIntentWeight,
                avgRiskWeight = avgRiskWeight,
                avgConfidence = avgConfidence
            ),
            keyFindings = keyFindings,
            recommendations = recommendations,
            riskFactors = extractRiskFactors(riskPaths),
            opportunityAreas = extractOpportunityAreas(goldenPaths),
            qualityMetrics = calculateQualityMetrics(goldenPaths, riskPaths, neutralPaths)
        )
    }
    
    /**
     * 生成关键发现
     */
    private fun generateKeyFindings(
        goldenPaths: List<AnalyzedPath>,
        riskPaths: List<AnalyzedPath>,
        neutralPaths: List<AnalyzedPath>,
        context: PathAnalysisContext
    ): List<String> {
        val findings = mutableListOf<String>()
        
        // Golden Path发现
        if (goldenPaths.isNotEmpty()) {
            val topGoldenPath = goldenPaths.first()
            findings.add("发现 ${goldenPaths.size} 条黄金路径，最佳路径具有 ${String.format("%.1f", topGoldenPath.intentWeight.totalWeight * 100)}% 业务价值评分")
        }
        
        // Risk Path发现
        if (riskPaths.isNotEmpty()) {
            val topRiskPath = riskPaths.first()
            findings.add("识别 ${riskPaths.size} 条高风险路径，最高风险路径风险评分为 ${String.format("%.1f", topRiskPath.riskWeight.totalRisk * 100)}%")
            
            // 分析主要风险类型
            val mainRiskTypes = riskPaths.flatMap { it.riskWeight.details.riskFactors }.distinct()
            if (mainRiskTypes.isNotEmpty()) {
                findings.add("主要风险因素包括：${mainRiskTypes.take(3).joinToString("、")}")
            }
        }
        
        // 测试覆盖率发现
        val allPaths = goldenPaths + riskPaths + neutralPaths
        val totalMethods = allPaths.flatMap { it.path.methods }.size
        val testedMethods = allPaths.flatMap { it.path.methods }.count { it.hasTests }
        val testCoverage = testedMethods.toDouble() / totalMethods
        
        findings.add("整体测试覆盖率为 ${String.format("%.1f", testCoverage * 100)}%")
        
        // 架构质量发现
        val layerViolationPaths = allPaths.count { it.path.hasLayerViolation() }
        if (layerViolationPaths > 0) {
            findings.add("发现 $layerViolationPaths 条路径存在架构分层违规")
        }
        
        return findings
    }
    
    /**
     * 生成建议
     */
    private fun generateRecommendations(
        goldenPaths: List<AnalyzedPath>,
        riskPaths: List<AnalyzedPath>,
        neutralPaths: List<AnalyzedPath>,
        context: PathAnalysisContext
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        // 风险路径建议
        if (riskPaths.isNotEmpty()) {
            recommendations.add("优先审查和优化 ${minOf(riskPaths.size, 5)} 条最高风险路径")
            
            val highComplexityPaths = riskPaths.filter { 
                it.path.methods.any { method -> method.cyclomaticComplexity > 15 }
            }
            if (highComplexityPaths.isNotEmpty()) {
                recommendations.add("重构高复杂度方法，建议拆分为更小的函数")
            }
            
            val lowTestCoveragePaths = riskPaths.filter { path ->
                path.path.methods.count { it.hasTests }.toDouble() / path.path.methods.size < 0.5
            }
            if (lowTestCoveragePaths.isNotEmpty()) {
                recommendations.add("为风险路径增加单元测试，提高测试覆盖率")
            }
        }
        
        // 黄金路径建议
        if (goldenPaths.isNotEmpty()) {
            recommendations.add("重点保护和维护 ${goldenPaths.size} 条黄金路径的稳定性")
            recommendations.add("考虑将黄金路径的实现模式推广到其他类似功能")
        }
        
        // 中性路径建议
        if (neutralPaths.isNotEmpty()) {
            val improvablePaths = neutralPaths.filter { 
                it.intentWeight.totalWeight >= 0.4 && it.riskWeight.totalRisk <= 0.5 
            }
            if (improvablePaths.isNotEmpty()) {
                recommendations.add("${improvablePaths.size} 条中性路径具有提升为黄金路径的潜力")
            }
        }
        
        return recommendations
    }
    
    /**
     * 提取风险因子
     */
    private fun extractRiskFactors(riskPaths: List<AnalyzedPath>): List<String> {
        return riskPaths.flatMap { it.riskWeight.details.riskFactors }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(10)
            .map { "${it.first} (${it.second}次)" }
    }
    
    /**
     * 提取机会领域
     */
    private fun extractOpportunityAreas(goldenPaths: List<AnalyzedPath>): List<String> {
        val areas = mutableListOf<String>()
        
        val businessKeywords = goldenPaths.flatMap { 
            it.intentWeight.details.businessKeywords 
        }.groupingBy { it }.eachCount()
        
        val topBusinessAreas = businessKeywords.toList()
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
        
        if (topBusinessAreas.isNotEmpty()) {
            areas.add("核心业务领域：${topBusinessAreas.joinToString("、")}")
        }
        
        val wellTestedPaths = goldenPaths.filter { path ->
            path.path.methods.count { it.hasTests }.toDouble() / path.path.methods.size >= 0.8
        }
        
        if (wellTestedPaths.isNotEmpty()) {
            areas.add("高质量测试实践：${wellTestedPaths.size} 条路径具有优秀的测试覆盖")
        }
        
        return areas
    }
    
    /**
     * 计算质量指标
     */
    private fun calculateQualityMetrics(
        goldenPaths: List<AnalyzedPath>,
        riskPaths: List<AnalyzedPath>,
        neutralPaths: List<AnalyzedPath>
    ): QualityMetrics {
        val allPaths = goldenPaths + riskPaths + neutralPaths
        
        val codeQualityScore = allPaths.map { it.intentWeight.codeQualityScore }.average()
        val architecturalHealthScore = 1.0 - allPaths.map { it.riskWeight.architecturalRiskScore }.average()
        val testMaturityScore = allPaths.map { path ->
            path.path.methods.count { it.hasTests }.toDouble() / path.path.methods.size
        }.average()
        val businessAlignmentScore = allPaths.map { it.intentWeight.businessValueScore }.average()
        
        return QualityMetrics(
            codeQualityScore = codeQualityScore,
            architecturalHealthScore = architecturalHealthScore,
            testMaturityScore = testMaturityScore,
            businessAlignmentScore = businessAlignmentScore,
            overallQualityScore = (codeQualityScore + architecturalHealthScore + testMaturityScore + businessAlignmentScore) / 4.0
        )
    }
}

/**
 * 路径分析上下文
 */
data class PathAnalysisContext(
    val changedMethods: Set<String>,
    val commitHistory: List<CommitAnalysis>,
    val fileAnalysisResults: List<FileAnalysisResult>,
    val analysisThresholds: AnalysisThresholds = AnalysisThresholds()
)

/**
 * 分析阈值配置
 */
data class AnalysisThresholds(
    val goldenPathIntentThreshold: Double = 0.7,
    val goldenPathRiskThreshold: Double = 0.3,
    val riskPathRiskThreshold: Double = 0.6,
    val testCoverageThreshold: Double = 0.7,
    val complexityThreshold: Int = 15
)

/**
 * 已分析的路径
 */
data class AnalyzedPath(
    val path: CallPath,
    val intentWeight: IntentWeightResult,
    val riskWeight: RiskWeightResult,
    val pathType: PathType,
    val priority: PathPriority,
    val confidence: Double
)

/**
 * 路径分析结果
 */
data class PathAnalysisResult(
    val goldenPaths: List<AnalyzedPath>,
    val riskPaths: List<AnalyzedPath>,
    val neutralPaths: List<AnalyzedPath>,
    val analysisReport: PathAnalysisReport,
    val totalAnalyzedPaths: Int,
    val processingTimestamp: Long
)

/**
 * 路径分析报告
 */
data class PathAnalysisReport(
    val summary: PathAnalysisSummary,
    val keyFindings: List<String>,
    val recommendations: List<String>,
    val riskFactors: List<String>,
    val opportunityAreas: List<String>,
    val qualityMetrics: QualityMetrics
)

/**
 * 路径分析摘要
 */
data class PathAnalysisSummary(
    val totalPaths: Int,
    val goldenPathCount: Int,
    val riskPathCount: Int,
    val neutralPathCount: Int,
    val goldenPathRatio: Double,
    val riskPathRatio: Double,
    val avgIntentWeight: Double,
    val avgRiskWeight: Double,
    val avgConfidence: Double
)

/**
 * 质量指标
 */
data class QualityMetrics(
    val codeQualityScore: Double,
    val architecturalHealthScore: Double,
    val testMaturityScore: Double,
    val businessAlignmentScore: Double,
    val overallQualityScore: Double
)

/**
 * 路径优先级
 */
enum class PathPriority {
    LOW, MEDIUM, HIGH, CRITICAL
}