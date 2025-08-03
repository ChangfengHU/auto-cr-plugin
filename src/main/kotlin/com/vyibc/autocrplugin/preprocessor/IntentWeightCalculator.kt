package com.vyibc.autocrplugin.preprocessor

import com.vyibc.autocrplugin.analyzer.CommitAnalysis
import com.vyibc.autocrplugin.analyzer.FileAnalysisResult
import com.vyibc.autocrplugin.analyzer.MethodAnalysis
import com.vyibc.autocrplugin.graph.engine.LocalGraphEngine
import com.vyibc.autocrplugin.graph.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * 意图权重计算器 - V5.1版本
 * 业务价值分析、实现完整性评估、代码质量评分算法
 */
class IntentWeightCalculator(
    private val graphEngine: LocalGraphEngine
) {
    
    /**
     * 计算调用路径的意图权重
     */
    suspend fun calculatePathIntentWeight(
        path: CallPath,
        commitHistory: List<CommitAnalysis>,
        fileAnalysisResults: List<FileAnalysisResult>
    ): IntentWeightResult = withContext(Dispatchers.Default) {
        
        // 1. 业务价值评分 (40% 权重)
        val businessValueScore = calculateBusinessValueScore(path, commitHistory)
        
        // 2. 实现完整性评分 (35% 权重)
        val implementationCompletenessScore = calculateImplementationCompletenessScore(path, fileAnalysisResults)
        
        // 3. 代码质量评分 (25% 权重)
        val codeQualityScore = calculateCodeQualityScore(path)
        
        // 综合计算意图权重
        val totalWeight = businessValueScore * 0.4 + 
                         implementationCompletenessScore * 0.35 + 
                         codeQualityScore * 0.25
        
        IntentWeightResult(
            totalWeight = totalWeight,
            businessValueScore = businessValueScore,
            implementationCompletenessScore = implementationCompletenessScore,
            codeQualityScore = codeQualityScore,
            confidence = calculateConfidence(path, commitHistory.size),
            details = IntentAnalysisDetails(
                pathLength = path.methods.size,
                businessKeywords = extractBusinessKeywords(path, commitHistory),
                qualityIndicators = extractQualityIndicators(path),
                completenessIndicators = extractCompletenessIndicators(path, fileAnalysisResults)
            )
        )
    }
    
    /**
     * 计算方法节点的意图权重
     */
    suspend fun calculateMethodIntentWeight(
        method: MethodNode,
        context: IntentCalculationContext
    ): MethodIntentWeight = withContext(Dispatchers.Default) {
        
        // 方法级别的业务价值评估
        val businessValue = calculateMethodBusinessValue(method, context)
        
        // 方法级别的实现完整性
        val completeness = calculateMethodCompleteness(method, context)
        
        // 方法级别的代码质量
        val quality = calculateMethodQuality(method)
        
        // 综合权重
        val weight = businessValue * 0.4 + completeness * 0.35 + quality * 0.25
        
        MethodIntentWeight(
            methodId = method.id,
            weight = weight,
            businessValue = businessValue,
            completeness = completeness,
            quality = quality,
            intentType = determineIntentType(method, context),
            confidence = calculateMethodConfidence(method, context)
        )
    }
    
    /**
     * 计算业务价值评分
     */
    private suspend fun calculateBusinessValueScore(
        path: CallPath,
        commitHistory: List<CommitAnalysis>
    ): Double {
        var score = 0.0
        
        // 1. 基于提交历史的业务关键词匹配 (30%)
        val businessKeywordScore = calculateBusinessKeywordScore(path, commitHistory)
        score += businessKeywordScore * 0.3
        
        // 2. 基于方法名和类名的业务语义分析 (25%)
        val semanticScore = calculateSemanticBusinessScore(path)
        score += semanticScore * 0.25
        
        // 3. 基于架构层级的业务重要性 (25%)
        val layerImportanceScore = calculateLayerImportanceScore(path)
        score += layerImportanceScore * 0.25
        
        // 4. 基于调用频率的业务活跃度 (20%)
        val activityScore = calculateBusinessActivityScore(path)
        score += activityScore * 0.2
        
        return minOf(1.0, maxOf(0.0, score))
    }
    
    /**
     * 计算实现完整性评分
     */
    private suspend fun calculateImplementationCompletenessScore(
        path: CallPath,
        fileAnalysisResults: List<FileAnalysisResult>
    ): Double {
        var score = 0.0
        
        // 1. 异常处理覆盖率 (30%)
        val exceptionHandlingScore = calculateExceptionHandlingScore(path)
        score += exceptionHandlingScore * 0.3
        
        // 2. 测试覆盖率 (25%)
        val testCoverageScore = calculateTestCoverageScore(path)
        score += testCoverageScore * 0.25
        
        // 3. 参数验证完整性 (20%)
        val validationScore = calculateValidationScore(path)
        score += validationScore * 0.2
        
        // 4. 日志记录完整性 (15%)
        val loggingScore = calculateLoggingScore(path)
        score += loggingScore * 0.15
        
        // 5. 资源管理完整性 (10%)
        val resourceManagementScore = calculateResourceManagementScore(path)
        score += resourceManagementScore * 0.1
        
        return minOf(1.0, maxOf(0.0, score))
    }
    
    /**
     * 计算代码质量评分
     */
    private suspend fun calculateCodeQualityScore(path: CallPath): Double {
        var score = 1.0 // 从满分开始，根据问题扣分
        
        // 1. 圈复杂度评估 (30%)
        val complexityPenalty = calculateComplexityPenalty(path)
        score -= complexityPenalty * 0.3
        
        // 2. 代码重复度评估 (25%)
        val duplicationPenalty = calculateDuplicationPenalty(path)
        score -= duplicationPenalty * 0.25
        
        // 3. 设计模式遵循度 (20%)
        val designPatternBonus = calculateDesignPatternBonus(path)
        score += designPatternBonus * 0.2
        
        // 4. 命名规范性 (15%)
        val namingScore = calculateNamingScore(path)
        score += (namingScore - 0.5) * 0.15
        
        // 5. 内聚性和耦合度 (10%)
        val cohesionCouplingScore = calculateCohesionCouplingScore(path)
        score += (cohesionCouplingScore - 0.5) * 0.1
        
        return minOf(1.0, maxOf(0.0, score))
    }
    
    /**
     * 计算业务关键词匹配分数
     */
    private fun calculateBusinessKeywordScore(
        path: CallPath,
        commitHistory: List<CommitAnalysis>
    ): Double {
        if (commitHistory.isEmpty()) return 0.5
        
        // 从提交历史中提取业务关键词
        val businessKeywords = commitHistory.flatMap { it.keywords }.toSet()
        
        // 从路径中提取方法和类名关键词
        val pathKeywords = mutableSetOf<String>()
        path.methods.forEach { method ->
            pathKeywords.addAll(extractKeywordsFromMethod(method))
        }
        
        // 计算关键词匹配度
        val matchCount = pathKeywords.intersect(businessKeywords).size
        val totalKeywords = maxOf(pathKeywords.size, 1)
        
        return minOf(1.0, matchCount.toDouble() / totalKeywords)
    }
    
    /**
     * 计算语义业务分数
     */
    private fun calculateSemanticBusinessScore(path: CallPath): Double {
        val businessTerms = setOf(
            "create", "update", "delete", "save", "process", "validate", "calculate",
            "generate", "import", "export", "sync", "notify", "schedule", "execute",
            "analyze", "report", "search", "filter", "transform", "convert",
            "business", "service", "manager", "processor", "handler", "controller"
        )
        
        var matchCount = 0
        var totalMethods = 0
        
        path.methods.forEach { method ->
            totalMethods++
            val methodName = method.methodName.lowercase()
            val className = method.id.substringBefore("#").substringAfterLast(".")
            
            if (businessTerms.any { term -> 
                methodName.contains(term) || className.lowercase().contains(term) 
            }) {
                matchCount++
            }
        }
        
        return if (totalMethods > 0) matchCount.toDouble() / totalMethods else 0.0
    }
    
    /**
     * 计算架构层级重要性分数
     */
    private fun calculateLayerImportanceScore(path: CallPath): Double {
        val layerWeights = mapOf(
            BlockType.CONTROLLER to 0.9,    // 控制器层最重要
            BlockType.SERVICE to 0.8,       // 业务服务层
            BlockType.REPOSITORY to 0.6,    // 数据访问层
            BlockType.COMPONENT to 0.7,     // 通用组件
            BlockType.UTIL to 0.3,          // 工具类较低
            BlockType.CONFIG to 0.4,        // 配置类
            BlockType.ENTITY to 0.5,        // 实体类
            BlockType.DTO to 0.4,           // 传输对象
            BlockType.VO to 0.4,            // 视图对象
            BlockType.OTHER to 0.2          // 其他类型最低
        )
        
        val pathWeight = path.methods.map { method ->
            layerWeights[method.blockType] ?: 0.2
        }.average()
        
        return pathWeight
    }
    
    /**
     * 计算业务活跃度分数
     */
    private suspend fun calculateBusinessActivityScore(path: CallPath): Double {
        // 基于方法的调用频率（入度）
        val activityScores = path.methods.map { method ->
            val inDegree = method.inDegree
            // 使用对数函数避免极值影响
            minOf(1.0, ln(inDegree + 1.0) / ln(10.0))
        }
        
        return activityScores.average()
    }
    
    /**
     * 计算异常处理分数
     */
    private fun calculateExceptionHandlingScore(path: CallPath): Double {
        // 简化实现：基于方法注解和名称判断异常处理
        val handledMethods = path.methods.count { method ->
            method.annotations.any { it.contains("throws", ignoreCase = true) } ||
            method.methodName.contains("try", ignoreCase = true) ||
            method.methodName.contains("handle", ignoreCase = true)
        }
        
        return handledMethods.toDouble() / maxOf(path.methods.size, 1)
    }
    
    /**
     * 计算测试覆盖率分数
     */
    private fun calculateTestCoverageScore(path: CallPath): Double {
        val testedMethods = path.methods.count { it.hasTests }
        return testedMethods.toDouble() / maxOf(path.methods.size, 1)
    }
    
    /**
     * 计算参数验证分数
     */
    private fun calculateValidationScore(path: CallPath): Double {
        val validatedMethods = path.methods.count { method ->
            method.annotations.any { 
                it.contains("valid", ignoreCase = true) || 
                it.contains("notnull", ignoreCase = true) ||
                it.contains("nullable", ignoreCase = true)
            } || method.methodName.contains("validate", ignoreCase = true)
        }
        
        return validatedMethods.toDouble() / maxOf(path.methods.size, 1)
    }
    
    /**
     * 计算日志记录分数
     */
    private fun calculateLoggingScore(path: CallPath): Double {
        // 简化实现：基于方法可能包含日志调用的推断
        val loggedMethods = path.methods.count { method ->
            method.outDegree > 0 && // 有对外调用，可能包含日志
            (method.blockType == BlockType.SERVICE || method.blockType == BlockType.CONTROLLER)
        }
        
        return loggedMethods.toDouble() / maxOf(path.methods.size, 1)
    }
    
    /**
     * 计算资源管理分数
     */
    private fun calculateResourceManagementScore(path: CallPath): Double {
        val resourceManagedMethods = path.methods.count { method ->
            method.annotations.any { 
                it.contains("transactional", ignoreCase = true) ||
                it.contains("closeable", ignoreCase = true) ||
                it.contains("autowired", ignoreCase = true)
            }
        }
        
        return resourceManagedMethods.toDouble() / maxOf(path.methods.size, 1)
    }
    
    /**
     * 计算复杂度惩罚
     */
    private fun calculateComplexityPenalty(path: CallPath): Double {
        val avgComplexity = path.methods.map { it.cyclomaticComplexity }.average()
        
        return when {
            avgComplexity <= 5 -> 0.0    // 低复杂度，无惩罚
            avgComplexity <= 10 -> 0.2   // 中等复杂度，轻微惩罚
            avgComplexity <= 15 -> 0.5   // 高复杂度，中等惩罚
            else -> 0.8                  // 极高复杂度，重度惩罚
        }
    }
    
    /**
     * 计算重复度惩罚
     */
    private fun calculateDuplicationPenalty(path: CallPath): Double {
        // 简化实现：基于方法签名相似度
        val signatures = path.methods.map { it.signature }
        val uniqueSignatures = signatures.toSet()
        
        val duplicationRatio = 1.0 - (uniqueSignatures.size.toDouble() / signatures.size)
        return duplicationRatio * 0.5 // 最大惩罚50%
    }
    
    /**
     * 计算设计模式奖励
     */
    private fun calculateDesignPatternBonus(path: CallPath): Double {
        // 统计路径中使用设计模式的类的比例
        val totalClasses = path.methods.map { it.id.substringBefore("#") }.toSet().size
        
        return 0.0 // 简化实现，暂时返回0
    }
    
    /**
     * 计算命名规范性分数
     */
    private fun calculateNamingScore(path: CallPath): Double {
        val goodNamingPatterns = listOf(
            Regex("[a-z][a-zA-Z0-9]*"),     // 驼峰命名
            Regex("(get|set|is|has|can)[A-Z].*"), // getter/setter模式
            Regex("(create|update|delete|find|save)[A-Z].*") // CRUD模式
        )
        
        val wellNamedMethods = path.methods.count { method ->
            goodNamingPatterns.any { pattern ->
                pattern.matches(method.methodName)
            }
        }
        
        return wellNamedMethods.toDouble() / maxOf(path.methods.size, 1)
    }
    
    /**
     * 计算内聚性和耦合度分数
     */
    private suspend fun calculateCohesionCouplingScore(path: CallPath): Double {
        // 简化实现：基于路径的连接性
        val pathConnectivity = calculatePathConnectivity(path)
        return pathConnectivity
    }
    
    /**
     * 计算路径连接性
     */
    private fun calculatePathConnectivity(path: CallPath): Double {
        if (path.methods.size <= 1) return 1.0
        
        val connections = path.edges.size
        val maxPossibleConnections = path.methods.size - 1
        
        return connections.toDouble() / maxPossibleConnections
    }
    
    /**
     * 计算置信度
     */
    private fun calculateConfidence(path: CallPath, commitHistorySize: Int): Double {
        // 基于多个因素计算置信度
        val pathLengthFactor = minOf(1.0, path.methods.size / 5.0) // 路径长度因子
        val historyFactor = minOf(1.0, commitHistorySize / 50.0)   // 历史数据因子
        val methodQualityFactor = path.methods.map { 
            if (it.hasTests) 1.0 else 0.5 
        }.average() // 方法质量因子
        
        return (pathLengthFactor + historyFactor + methodQualityFactor) / 3.0
    }
    
    /**
     * 从方法中提取关键词
     */
    private fun extractKeywordsFromMethod(method: MethodNode): Set<String> {
        val keywords = mutableSetOf<String>()
        
        // 从方法名提取
        keywords.addAll(splitCamelCase(method.methodName))
        
        // 从类名提取
        val className = method.id.substringBefore("#").substringAfterLast(".")
        keywords.addAll(splitCamelCase(className))
        
        return keywords
    }
    
    /**
     * 拆分驼峰命名
     */
    private fun splitCamelCase(text: String): List<String> {
        return text.split(Regex("(?=[A-Z])"))
            .filter { it.isNotBlank() && it.length > 2 }
            .map { it.lowercase() }
    }
    
    /**
     * 提取业务关键词
     */
    private fun extractBusinessKeywords(
        path: CallPath,
        commitHistory: List<CommitAnalysis>
    ): Set<String> {
        val keywords = mutableSetOf<String>()
        
        // 从路径提取
        path.methods.forEach { method ->
            keywords.addAll(extractKeywordsFromMethod(method))
        }
        
        // 从提交历史提取
        commitHistory.forEach { commit ->
            keywords.addAll(commit.keywords)
        }
        
        return keywords
    }
    
    /**
     * 提取质量指标
     */
    private fun extractQualityIndicators(path: CallPath): List<String> {
        val indicators = mutableListOf<String>()
        
        val avgComplexity = path.methods.map { it.cyclomaticComplexity }.average()
        when {
            avgComplexity <= 5 -> indicators.add("低复杂度")
            avgComplexity <= 10 -> indicators.add("中等复杂度")
            else -> indicators.add("高复杂度")
        }
        
        val testCoverage = path.methods.count { it.hasTests }.toDouble() / path.methods.size
        when {
            testCoverage >= 0.8 -> indicators.add("高测试覆盖率")
            testCoverage >= 0.5 -> indicators.add("中等测试覆盖率")
            else -> indicators.add("低测试覆盖率")
        }
        
        return indicators
    }
    
    /**
     * 提取完整性指标
     */
    private fun extractCompletenessIndicators(
        path: CallPath,
        fileAnalysisResults: List<FileAnalysisResult>
    ): List<String> {
        val indicators = mutableListOf<String>()
        
        // 检查异常处理
        val hasExceptionHandling = path.methods.any { method ->
            method.annotations.any { it.contains("throws", ignoreCase = true) }
        }
        if (hasExceptionHandling) {
            indicators.add("包含异常处理")
        }
        
        // 检查参数验证
        val hasValidation = path.methods.any { method ->
            method.annotations.any { it.contains("valid", ignoreCase = true) }
        }
        if (hasValidation) {
            indicators.add("包含参数验证")
        }
        
        return indicators
    }
    
    // 方法级别的计算函数
    private fun calculateMethodBusinessValue(method: MethodNode, context: IntentCalculationContext): Double {
        // 实现方法级别的业务价值计算
        return 0.5 // 简化实现
    }
    
    private fun calculateMethodCompleteness(method: MethodNode, context: IntentCalculationContext): Double {
        // 实现方法级别的完整性计算
        return if (method.hasTests) 0.8 else 0.4
    }
    
    private fun calculateMethodQuality(method: MethodNode): Double {
        // 基于复杂度计算质量分数
        return when {
            method.cyclomaticComplexity <= 5 -> 0.9
            method.cyclomaticComplexity <= 10 -> 0.7
            method.cyclomaticComplexity <= 15 -> 0.5
            else -> 0.3
        }
    }
    
    private fun determineIntentType(method: MethodNode, context: IntentCalculationContext): IntentType {
        return when {
            method.methodName.startsWith("get") -> IntentType.QUERY
            method.methodName.startsWith("create") || method.methodName.startsWith("save") -> IntentType.CREATE
            method.methodName.startsWith("update") -> IntentType.UPDATE
            method.methodName.startsWith("delete") -> IntentType.DELETE
            method.methodName.contains("process") || method.methodName.contains("execute") -> IntentType.PROCESS
            else -> IntentType.OTHER
        }
    }
    
    private fun calculateMethodConfidence(method: MethodNode, context: IntentCalculationContext): Double {
        // 基于方法的各种指标计算置信度
        var confidence = 0.5
        
        if (method.hasTests) confidence += 0.2
        if (method.annotations.isNotEmpty()) confidence += 0.1
        if (method.cyclomaticComplexity <= 10) confidence += 0.1
        if (method.inDegree > 0) confidence += 0.1
        
        return minOf(1.0, confidence)
    }
}

/**
 * 意图权重计算结果
 */
data class IntentWeightResult(
    val totalWeight: Double,              // 总权重 [0.0, 1.0]
    val businessValueScore: Double,       // 业务价值评分
    val implementationCompletenessScore: Double, // 实现完整性评分
    val codeQualityScore: Double,         // 代码质量评分
    val confidence: Double,               // 置信度
    val details: IntentAnalysisDetails    // 详细分析结果
)

/**
 * 方法意图权重
 */
data class MethodIntentWeight(
    val methodId: String,
    val weight: Double,
    val businessValue: Double,
    val completeness: Double,
    val quality: Double,
    val intentType: IntentType,
    val confidence: Double
)

/**
 * 意图分析详情
 */
data class IntentAnalysisDetails(
    val pathLength: Int,
    val businessKeywords: Set<String>,
    val qualityIndicators: List<String>,
    val completenessIndicators: List<String>
)

/**
 * 意图计算上下文
 */
data class IntentCalculationContext(
    val commitHistory: List<CommitAnalysis>,
    val fileAnalysisResults: List<FileAnalysisResult>,
    val projectContext: ProjectContext
)

/**
 * 项目上下文
 */
data class ProjectContext(
    val projectType: String,        // 项目类型：web, android, library等
    val businessDomain: String,     // 业务领域
    val teamSize: Int,              // 团队规模
    val codebaseSize: Int          // 代码库规模
)

/**
 * 意图类型
 */
enum class IntentType {
    QUERY,      // 查询操作
    CREATE,     // 创建操作
    UPDATE,     // 更新操作
    DELETE,     // 删除操作
    PROCESS,    // 处理操作
    VALIDATE,   // 验证操作
    TRANSFORM,  // 转换操作
    OTHER       // 其他操作
}