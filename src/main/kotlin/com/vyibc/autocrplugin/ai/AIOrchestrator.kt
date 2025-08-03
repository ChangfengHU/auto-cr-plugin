package com.vyibc.autocrplugin.ai

import com.vyibc.autocrplugin.ai.providers.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.math.ln

/**
 * AI智能路由器 - V5.1版本
 * 基于预算、速度、质量的供应商选择
 */
class AIOrchestrator(
    private val config: OrchestratorConfig = OrchestratorConfig()
) {
    
    private val providers = mutableMapOf<String, AIProvider>()
    private val usageTracker = UsageTracker()
    
    /**
     * 注册AI供应商
     */
    fun registerProvider(provider: AIProvider) {
        providers[provider.providerInfo.id] = provider
    }
    
    /**
     * 获取所有可用的供应商
     */
    suspend fun getAvailableProviders(): List<ProviderInfo> {
        return providers.values.filter { it.isAvailable() }.map { it.providerInfo }
    }
    
    /**
     * 智能选择最佳供应商和模型
     */
    suspend fun selectBestProvider(
        request: ChatRequest,
        constraints: SelectionConstraints = SelectionConstraints()
    ): ProviderSelection = withContext(Dispatchers.Default) {
        
        val availableProviders = providers.values.filter { it.isAvailable() }
        if (availableProviders.isEmpty()) {
            throw IllegalStateException("No available AI providers")
        }
        
        // 获取所有候选模型
        val candidates = mutableListOf<ModelCandidate>()
        
        for (provider in availableProviders) {
            for (model in provider.supportedModels) {
                if (isModelSuitable(model, request, constraints)) {
                    val score = calculateModelScore(provider, model, request, constraints)
                    candidates.add(ModelCandidate(
                        provider = provider,
                        model = model,
                        score = score
                    ))
                }
            }
        }
        
        if (candidates.isEmpty()) {
            throw IllegalStateException("No suitable models found for the request")
        }
        
        // 选择得分最高的模型
        val bestCandidate = candidates.maxByOrNull { it.score }
            ?: throw IllegalStateException("Failed to select best candidate")
        
        ProviderSelection(
            provider = bestCandidate.provider,
            model = bestCandidate.model,
            score = bestCandidate.score,
            reasoning = generateSelectionReasoning(bestCandidate, candidates)
        )
    }
    
    /**
     * 执行AI请求（带智能路由）
     */
    suspend fun chat(
        request: ChatRequest,
        constraints: SelectionConstraints = SelectionConstraints()
    ): ChatResponse = withContext(Dispatchers.IO) {
        
        val selection = selectBestProvider(request, constraints)
        
        val startTime = System.currentTimeMillis()
        
        try {
            val response = selection.provider.chat(request.copy(modelId = selection.model.id))
            
            val duration = System.currentTimeMillis() - startTime
            
            // 记录使用统计
            usageTracker.recordUsage(
                providerId = selection.provider.providerInfo.id,
                modelId = selection.model.id,
                request = request,
                response = response,
                duration = duration
            )
            
            response
        } catch (e: Exception) {
            // 记录失败
            usageTracker.recordFailure(
                providerId = selection.provider.providerInfo.id,
                modelId = selection.model.id,
                error = e,
                duration = System.currentTimeMillis() - startTime
            )
            throw e
        }
    }
    
    /**
     * 流式聊天（带智能路由）
     */
    suspend fun chatStream(
        request: ChatRequest,
        constraints: SelectionConstraints = SelectionConstraints()
    ): Flow<ChatStreamChunk> {
        val selection = selectBestProvider(request, constraints)
        return selection.provider.chatStream(request.copy(modelId = selection.model.id))
    }
    
    /**
     * 获取使用统计
     */
    suspend fun getUsageStatistics(): OrchestratorUsageStats {
        return usageTracker.getStatistics()
    }
    
    /**
     * 判断模型是否适合请求
     */
    private suspend fun isModelSuitable(
        model: ModelInfo,
        request: ChatRequest,
        constraints: SelectionConstraints
    ): Boolean {
        // 检查上下文长度限制
        val estimatedTokens = estimateTokenCount(request)
        if (estimatedTokens > model.contextWindow) {
            return false
        }
        
        // 检查功能支持
        if (constraints.requiredFeatures.isNotEmpty()) {
            if (!model.supportedFeatures.containsAll(constraints.requiredFeatures)) {
                return false
            }
        }
        
        // 检查预算限制
        if (constraints.maxCostPerRequest != null) {
            val estimatedCost = calculateEstimatedCost(model, estimatedTokens)
            if (estimatedCost > constraints.maxCostPerRequest) {
                return false
            }
        }
        
        // 检查质量要求
        if (constraints.minQualityScore != null) {
            if (model.performance.qualityScore < constraints.minQualityScore) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * 计算模型评分
     */
    private suspend fun calculateModelScore(
        provider: AIProvider,
        model: ModelInfo,
        request: ChatRequest,
        constraints: SelectionConstraints
    ): Double {
        var score = 0.0
        
        // 质量评分 (40% 权重)
        val qualityScore = model.performance.qualityScore
        score += qualityScore * config.qualityWeight
        
        // 速度评分 (30% 权重)
        val speedScore = calculateSpeedScore(model.performance)
        score += speedScore * config.speedWeight
        
        // 成本效益评分 (20% 权重)
        val costScore = calculateCostScore(model, estimateTokenCount(request))
        score += costScore * config.costWeight
        
        // 可靠性评分 (10% 权重)
        val reliabilityScore = model.performance.reliabilityScore
        score += reliabilityScore * config.reliabilityWeight
        
        // 历史性能加权
        val historicalScore = usageTracker.getHistoricalScore(provider.providerInfo.id, model.id)
        score = score * 0.8 + historicalScore * 0.2
        
        // 特殊偏好处理
        if (constraints.preferredProviders.contains(provider.providerInfo.id)) {
            score *= 1.1 // 10% 偏好加成
        }
        
        return score
    }
    
    /**
     * 计算速度评分
     */
    private fun calculateSpeedScore(performance: ModelPerformance): Double {
        // 基于平均延迟和吞吐量计算速度评分
        val latencyScore = 1.0 / (1.0 + performance.averageLatency / 5.0) // 5秒为基准
        val throughputScore = minOf(1.0, performance.tokensPerSecond / 100.0) // 100 tokens/s为满分
        
        return (latencyScore + throughputScore) / 2.0
    }
    
    /**
     * 计算成本评分
     */
    private fun calculateCostScore(model: ModelInfo, estimatedTokens: Int): Double {
        val estimatedCost = calculateEstimatedCost(model, estimatedTokens)
        
        // 使用对数函数将成本转换为评分，成本越低评分越高
        return if (estimatedCost > 0) {
            maxOf(0.0, 1.0 - ln(estimatedCost * 1000 + 1) / 10.0)
        } else {
            1.0 // 免费模型满分
        }
    }
    
    /**
     * 估算token数量
     */
    private fun estimateTokenCount(request: ChatRequest): Int {
        val totalText = request.messages.joinToString(" ") { it.content } + 
                       (request.systemPrompt ?: "")
        
        // 简化估算：英文约4字符=1token，中文约2字符=1token
        val englishChars = totalText.count { it.code < 128 }
        val nonEnglishChars = totalText.length - englishChars
        
        return (englishChars / 4) + (nonEnglishChars / 2)
    }
    
    /**
     * 计算预估成本
     */
    private fun calculateEstimatedCost(model: ModelInfo, estimatedTokens: Int): Double {
        val inputCost = estimatedTokens * model.pricing.inputPricePerToken
        val outputCost = (estimatedTokens * 0.3) * model.pricing.outputPricePerToken // 假设输出是输入的30%
        
        return inputCost + outputCost
    }
    
    /**
     * 生成选择原因
     */
    private fun generateSelectionReasoning(
        bestCandidate: ModelCandidate,
        allCandidates: List<ModelCandidate>
    ): String {
        val provider = bestCandidate.provider.providerInfo
        val model = bestCandidate.model
        
        val reasons = mutableListOf<String>()
        
        // 质量优势
        val topQualityScore = allCandidates.maxOfOrNull { it.model.performance.qualityScore } ?: 0.0
        if (model.performance.qualityScore >= topQualityScore * 0.95) {
            reasons.add("高质量评分 (${String.format("%.1f", model.performance.qualityScore * 100)}%)")
        }
        
        // 速度优势
        val topSpeed = allCandidates.maxOfOrNull { it.model.performance.tokensPerSecond } ?: 0.0
        if (model.performance.tokensPerSecond >= topSpeed * 0.95) {
            reasons.add("快速响应 (${String.format("%.0f", model.performance.tokensPerSecond)} tokens/s)")
        }
        
        // 成本优势
        val lowestCost = allCandidates.minOfOrNull { 
            it.model.pricing.inputPricePerToken + it.model.pricing.outputPricePerToken 
        } ?: Double.MAX_VALUE
        val currentCost = model.pricing.inputPricePerToken + model.pricing.outputPricePerToken
        if (currentCost <= lowestCost * 1.05) {
            if (currentCost == 0.0) {
                reasons.add("免费使用")
            } else {
                reasons.add("成本效益最佳")
            }
        }
        
        return if (reasons.isNotEmpty()) {
            "选择 ${provider.name} ${model.name}: ${reasons.joinToString(", ")}"
        } else {
            "选择 ${provider.name} ${model.name}: 综合评分最高"
        }
    }
}

/**
 * 路由器配置
 */
data class OrchestratorConfig(
    val qualityWeight: Double = 0.4,        // 质量权重
    val speedWeight: Double = 0.3,          // 速度权重  
    val costWeight: Double = 0.2,           // 成本权重
    val reliabilityWeight: Double = 0.1,    // 可靠性权重
    val enableCaching: Boolean = true,      // 启用缓存
    val enableFallback: Boolean = true      // 启用失败转移
)

/**
 * 选择约束条件
 */
data class SelectionConstraints(
    val maxCostPerRequest: Double? = null,          // 最大单次请求成本
    val maxLatency: Double? = null,                 // 最大延迟（秒）
    val minQualityScore: Double? = null,            // 最小质量评分
    val requiredFeatures: Set<ModelFeature> = emptySet(), // 必需功能
    val preferredProviders: Set<String> = emptySet(), // 偏好供应商
    val excludedProviders: Set<String> = emptySet() // 排除供应商
)

/**
 * 供应商选择结果
 */
data class ProviderSelection(
    val provider: AIProvider,               // 选中的供应商
    val model: ModelInfo,                   // 选中的模型
    val score: Double,                      // 综合评分
    val reasoning: String                   // 选择原因
)

/**
 * 模型候选
 */
private data class ModelCandidate(
    val provider: AIProvider,
    val model: ModelInfo,
    val score: Double
)

/**
 * 使用追踪器
 */
class UsageTracker {
    private val usageHistory = mutableListOf<UsageRecord>()
    private val failureHistory = mutableListOf<FailureRecord>()
    
    /**
     * 记录使用情况
     */
    fun recordUsage(
        providerId: String,
        modelId: String,
        request: ChatRequest,
        response: ChatResponse,
        duration: Long
    ) {
        usageHistory.add(UsageRecord(
            providerId = providerId,
            modelId = modelId,
            requestTokens = response.usage.promptTokens,
            responseTokens = response.usage.completionTokens,
            duration = duration,
            timestamp = System.currentTimeMillis()
        ))
    }
    
    /**
     * 记录失败情况
     */
    fun recordFailure(
        providerId: String,
        modelId: String,
        error: Throwable,
        duration: Long
    ) {
        failureHistory.add(FailureRecord(
            providerId = providerId,
            modelId = modelId,
            error = error.message ?: "Unknown error",
            duration = duration,
            timestamp = System.currentTimeMillis()
        ))
    }
    
    /**
     * 获取历史评分
     */
    fun getHistoricalScore(providerId: String, modelId: String): Double {
        val recentUsage = usageHistory.filter { 
            it.providerId == providerId && it.modelId == modelId 
        }.takeLast(10)
        
        val recentFailures = failureHistory.filter { 
            it.providerId == providerId && it.modelId == modelId 
        }.takeLast(10)
        
        if (recentUsage.isEmpty() && recentFailures.isEmpty()) {
            return 0.5 // 默认中等评分
        }
        
        val totalRequests = recentUsage.size + recentFailures.size
        val successRate = recentUsage.size.toDouble() / totalRequests
        
        val avgLatency = if (recentUsage.isNotEmpty()) {
            recentUsage.map { it.duration }.average()
        } else {
            5000.0 // 假设失败请求延迟较高
        }
        
        // 综合成功率和延迟计算历史评分
        val latencyScore = 1.0 / (1.0 + avgLatency / 3000.0) // 3秒为基准
        return (successRate + latencyScore) / 2.0
    }
    
    /**
     * 获取统计信息
     */
    fun getStatistics(): OrchestratorUsageStats {
        val totalRequests = usageHistory.size + failureHistory.size
        val successfulRequests = usageHistory.size
        val failedRequests = failureHistory.size
        
        val avgLatency = if (usageHistory.isNotEmpty()) {
            usageHistory.map { it.duration }.average()
        } else 0.0
        
        val totalTokens = usageHistory.sumOf { it.requestTokens + it.responseTokens }
        
        return OrchestratorUsageStats(
            totalRequests = totalRequests,
            successfulRequests = successfulRequests,
            failedRequests = failedRequests,
            successRate = if (totalRequests > 0) successfulRequests.toDouble() / totalRequests else 0.0,
            averageLatency = avgLatency,
            totalTokensProcessed = totalTokens
        )
    }
}

/**
 * 使用记录
 */
private data class UsageRecord(
    val providerId: String,
    val modelId: String,
    val requestTokens: Int,
    val responseTokens: Int,
    val duration: Long,
    val timestamp: Long
)

/**
 * 失败记录
 */
private data class FailureRecord(
    val providerId: String,
    val modelId: String,
    val error: String,
    val duration: Long,
    val timestamp: Long
)

/**
 * 路由器使用统计
 */
data class OrchestratorUsageStats(
    val totalRequests: Int,
    val successfulRequests: Int,
    val failedRequests: Int,
    val successRate: Double,
    val averageLatency: Double,
    val totalTokensProcessed: Int
)