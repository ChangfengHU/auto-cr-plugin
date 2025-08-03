package com.vyibc.autocrplugin.ai

import kotlinx.coroutines.flow.Flow

/**
 * AI Provider 接口 - V5.1版本
 * 统一AI供应商接口，支持多种模型和配置
 */
interface AIProvider {
    
    /**
     * 供应商信息
     */
    val providerInfo: ProviderInfo
    
    /**
     * 支持的模型列表
     */
    val supportedModels: List<ModelInfo>
    
    /**
     * 检查供应商是否可用
     */
    suspend fun isAvailable(): Boolean
    
    /**
     * 验证API密钥
     */
    suspend fun validateApiKey(apiKey: String): Boolean
    
    /**
     * 获取模型能力信息
     */
    suspend fun getModelCapabilities(modelId: String): ModelCapabilities
    
    /**
     * 发送聊天请求
     */
    suspend fun chat(request: ChatRequest): ChatResponse
    
    /**
     * 流式聊天请求
     */
    fun chatStream(request: ChatRequest): Flow<ChatStreamChunk>
    
    /**
     * 计算token数量
     */
    suspend fun countTokens(text: String, modelId: String): Int
    
    /**
     * 获取定价信息
     */
    suspend fun getPricing(modelId: String): PricingInfo
    
    /**
     * 获取使用统计
     */
    suspend fun getUsageStats(): UsageStats
}

/**
 * 供应商信息
 */
data class ProviderInfo(
    val id: String,                    // 供应商ID
    val name: String,                  // 供应商名称
    val description: String,           // 描述
    val website: String,               // 官网
    val apiBaseUrl: String,           // API基础URL
    val authType: AuthType,           // 认证类型
    val rateLimit: RateLimit,         // 速率限制
    val features: Set<ProviderFeature> // 支持的功能
)

/**
 * 模型信息
 */
data class ModelInfo(
    val id: String,                    // 模型ID
    val name: String,                  // 模型名称
    val version: String,               // 版本
    val description: String,           // 描述
    val maxTokens: Int,                // 最大token数
    val contextWindow: Int,            // 上下文窗口大小
    val supportedFeatures: Set<ModelFeature>, // 支持的功能
    val performance: ModelPerformance,  // 性能指标
    val pricing: PricingInfo           // 定价信息
)

/**
 * 模型能力
 */
data class ModelCapabilities(
    val maxInputTokens: Int,           // 最大输入token
    val maxOutputTokens: Int,          // 最大输出token
    val supportsStreaming: Boolean,    // 是否支持流式输出
    val supportsSystemPrompt: Boolean, // 是否支持系统提示
    val supportsFunctionCalling: Boolean, // 是否支持函数调用
    val supportsImageInput: Boolean,   // 是否支持图像输入
    val languages: Set<String>,        // 支持的语言
    val codeLanguages: Set<String>     // 支持的编程语言
)

/**
 * 聊天请求
 */
data class ChatRequest(
    val modelId: String,               // 模型ID
    val messages: List<ChatMessage>,   // 消息列表
    val systemPrompt: String? = null,  // 系统提示
    val temperature: Double = 0.7,     // 温度参数
    val maxTokens: Int? = null,        // 最大输出token
    val topP: Double? = null,          // Top-p参数
    val stream: Boolean = false,       // 是否流式输出
    val metadata: Map<String, String> = emptyMap() // 元数据
)

/**
 * 聊天消息
 */
data class ChatMessage(
    val role: MessageRole,             // 角色
    val content: String,               // 内容
    val name: String? = null,          // 发送者名称
    val metadata: Map<String, Any> = emptyMap() // 元数据
)

/**
 * 聊天响应
 */
data class ChatResponse(
    val id: String,                    // 响应ID
    val content: String,               // 响应内容
    val model: String,                 // 使用的模型
    val usage: TokenUsage,             // Token使用情况
    val finishReason: FinishReason,    // 完成原因
    val metadata: Map<String, Any> = emptyMap() // 元数据
)

/**
 * 流式聊天块
 */
data class ChatStreamChunk(
    val id: String,                    // 块ID
    val content: String,               // 内容块
    val isComplete: Boolean,           // 是否完成
    val usage: TokenUsage? = null,     // Token使用情况
    val metadata: Map<String, Any> = emptyMap() // 元数据
)

/**
 * Token使用情况
 */
data class TokenUsage(
    val promptTokens: Int,             // 输入token数
    val completionTokens: Int,         // 输出token数
    val totalTokens: Int               // 总token数
)

/**
 * 定价信息
 */
data class PricingInfo(
    val inputPricePerToken: Double,    // 输入token单价(美元)
    val outputPricePerToken: Double,   // 输出token单价(美元)
    val currency: String = "USD",      // 货币单位
    val lastUpdated: Long = System.currentTimeMillis() // 最后更新时间
)

/**
 * 使用统计
 */
data class UsageStats(
    val totalRequests: Int,            // 总请求数
    val totalTokens: Int,              // 总token数
    val totalCost: Double,             // 总费用
    val averageLatency: Double,        // 平均延迟(毫秒)
    val successRate: Double,           // 成功率
    val period: String = "today"       // 统计周期
)

/**
 * 速率限制
 */
data class RateLimit(
    val requestsPerMinute: Int,        // 每分钟请求数
    val tokensPerMinute: Int,          // 每分钟token数
    val requestsPerDay: Int? = null,   // 每天请求数
    val tokensPerDay: Int? = null      // 每天token数
)

/**
 * 模型性能
 */
data class ModelPerformance(
    val averageLatency: Double,        // 平均延迟(秒)
    val tokensPerSecond: Double,       // 每秒token数
    val qualityScore: Double,          // 质量评分(0-1)
    val reliabilityScore: Double       // 可靠性评分(0-1)
)

// 枚举定义

/**
 * 认证类型
 */
enum class AuthType {
    API_KEY,        // API密钥
    BEARER_TOKEN,   // Bearer Token
    OAUTH2,         // OAuth2
    BASIC_AUTH      // 基础认证
}

/**
 * 供应商功能
 */
enum class ProviderFeature {
    CHAT_COMPLETION,    // 聊天完成
    STREAMING,          // 流式输出
    FUNCTION_CALLING,   // 函数调用
    IMAGE_INPUT,        // 图像输入
    CODE_GENERATION,    // 代码生成
    TRANSLATION,        // 翻译
    EMBEDDINGS         // 向量嵌入
}

/**
 * 模型功能
 */
enum class ModelFeature {
    CHAT,              // 聊天
    COMPLETION,        // 文本完成
    CODE_GENERATION,   // 代码生成
    CODE_EXPLANATION,  // 代码解释
    CODE_REVIEW,       // 代码审查
    TRANSLATION,       // 翻译
    SUMMARIZATION,     // 摘要
    ANALYSIS          // 分析
}

/**
 * 消息角色
 */
enum class MessageRole {
    USER,       // 用户
    ASSISTANT,  // 助手
    SYSTEM,     // 系统
    FUNCTION    // 函数
}

/**
 * 完成原因
 */
enum class FinishReason {
    COMPLETED,      // 正常完成
    LENGTH_LIMIT,   // 长度限制
    TIMEOUT,        // 超时
    ERROR,          // 错误
    CANCELLED       // 取消
}