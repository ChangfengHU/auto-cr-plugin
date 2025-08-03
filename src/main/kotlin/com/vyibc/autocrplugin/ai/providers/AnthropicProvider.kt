package com.vyibc.autocrplugin.ai.providers

import com.vyibc.autocrplugin.ai.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration

/**
 * Anthropic Provider - V5.1版本
 * 支持Claude-3 Opus, Sonnet, Haiku等模型
 */
class AnthropicProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.anthropic.com/v1"
) : AIProvider {
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override val providerInfo = ProviderInfo(
        id = "anthropic",
        name = "Anthropic",
        description = "Claude models for advanced code analysis and review",
        website = "https://anthropic.com",
        apiBaseUrl = baseUrl,
        authType = AuthType.API_KEY,
        rateLimit = RateLimit(
            requestsPerMinute = 1000,
            tokensPerMinute = 400000,
            requestsPerDay = 50000
        ),
        features = setOf(
            ProviderFeature.CHAT_COMPLETION,
            ProviderFeature.STREAMING,
            ProviderFeature.CODE_GENERATION
        )
    )
    
    override val supportedModels = listOf(
        ModelInfo(
            id = "claude-3-5-sonnet-20241022",
            name = "Claude 3.5 Sonnet",
            version = "20241022",
            description = "Most intelligent model with best-in-class analysis and code review capabilities",
            maxTokens = 8192,
            contextWindow = 200000,
            supportedFeatures = setOf(
                ModelFeature.CHAT,
                ModelFeature.CODE_GENERATION,
                ModelFeature.CODE_EXPLANATION,
                ModelFeature.CODE_REVIEW,
                ModelFeature.ANALYSIS
            ),
            performance = ModelPerformance(
                averageLatency = 3.8,
                tokensPerSecond = 45.0,
                qualityScore = 0.98,
                reliabilityScore = 0.97
            ),
            pricing = PricingInfo(
                inputPricePerToken = 3.0 / 1000000,
                outputPricePerToken = 15.0 / 1000000
            )
        ),
        ModelInfo(
            id = "claude-3-opus-20240229",
            name = "Claude 3 Opus",
            version = "20240229",
            description = "Most powerful model for complex reasoning and analysis",
            maxTokens = 4096,
            contextWindow = 200000,
            supportedFeatures = setOf(
                ModelFeature.CHAT,
                ModelFeature.CODE_GENERATION,
                ModelFeature.CODE_EXPLANATION,
                ModelFeature.CODE_REVIEW,
                ModelFeature.ANALYSIS
            ),
            performance = ModelPerformance(
                averageLatency = 5.2,
                tokensPerSecond = 35.0,
                qualityScore = 0.96,
                reliabilityScore = 0.95
            ),
            pricing = PricingInfo(
                inputPricePerToken = 15.0 / 1000000,
                outputPricePerToken = 75.0 / 1000000
            )
        ),
        ModelInfo(
            id = "claude-3-sonnet-20240229",
            name = "Claude 3 Sonnet",
            version = "20240229",
            description = "Balanced model for speed and intelligence",
            maxTokens = 4096,
            contextWindow = 200000,
            supportedFeatures = setOf(
                ModelFeature.CHAT,
                ModelFeature.CODE_GENERATION,
                ModelFeature.CODE_EXPLANATION,
                ModelFeature.CODE_REVIEW,
                ModelFeature.ANALYSIS
            ),
            performance = ModelPerformance(
                averageLatency = 2.1,
                tokensPerSecond = 60.0,
                qualityScore = 0.91,
                reliabilityScore = 0.96
            ),
            pricing = PricingInfo(
                inputPricePerToken = 3.0 / 1000000,
                outputPricePerToken = 15.0 / 1000000
            )
        ),
        ModelInfo(
            id = "claude-3-haiku-20240307",
            name = "Claude 3 Haiku",
            version = "20240307",
            description = "Fast and cost-effective model for quick analysis",
            maxTokens = 4096,
            contextWindow = 200000,
            supportedFeatures = setOf(
                ModelFeature.CHAT,
                ModelFeature.CODE_GENERATION,
                ModelFeature.CODE_EXPLANATION,
                ModelFeature.CODE_REVIEW
            ),
            performance = ModelPerformance(
                averageLatency = 1.2,
                tokensPerSecond = 85.0,
                qualityScore = 0.86,
                reliabilityScore = 0.94
            ),
            pricing = PricingInfo(
                inputPricePerToken = 0.25 / 1000000,
                outputPricePerToken = 1.25 / 1000000
            )
        )
    )
    
    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/messages"))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                    {
                        "model": "claude-3-haiku-20240307",
                        "max_tokens": 10,
                        "messages": [{"role": "user", "content": "Hi"}]
                    }
                """.trimIndent()))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() in 200..299
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun validateApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/messages"))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                    {
                        "model": "claude-3-haiku-20240307",
                        "max_tokens": 1,
                        "messages": [{"role": "user", "content": "Test"}]
                    }
                """.trimIndent()))
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() in 200..299
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getModelCapabilities(modelId: String): ModelCapabilities {
        val model = supportedModels.find { it.id == modelId }
            ?: throw IllegalArgumentException("Unsupported model: $modelId")
        
        return ModelCapabilities(
            maxInputTokens = model.contextWindow - model.maxTokens,
            maxOutputTokens = model.maxTokens,
            supportsStreaming = true,
            supportsSystemPrompt = true,
            supportsFunctionCalling = false, // Claude暂不支持函数调用
            supportsImageInput = modelId.contains("opus") || modelId.contains("sonnet"),
            languages = setOf("en", "zh", "ja", "ko", "es", "fr", "de", "it", "pt", "ru", "ar"),
            codeLanguages = setOf("java", "kotlin", "javascript", "typescript", "python", "go", "rust", "c", "cpp", "swift", "scala")
        )
    }
    
    override suspend fun chat(request: ChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        val anthropicRequest = AnthropicRequest(
            model = request.modelId,
            max_tokens = request.maxTokens ?: 4096,
            messages = request.messages.map { msg ->
                AnthropicMessage(
                    role = when (msg.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        else -> "user"
                    },
                    content = msg.content
                )
            },
            temperature = request.temperature,
            top_p = request.topP,
            system = request.systemPrompt,
            stream = false
        )
        
        val requestBody = json.encodeToString(anthropicRequest)
        
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/messages"))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()
        
        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("Anthropic API error: ${response.statusCode()} - ${response.body()}")
        }
        
        val anthropicResponse = json.decodeFromString<AnthropicResponse>(response.body())
        
        ChatResponse(
            id = anthropicResponse.id,
            content = anthropicResponse.content.firstOrNull()?.text ?: "",
            model = anthropicResponse.model,
            usage = TokenUsage(
                promptTokens = anthropicResponse.usage.input_tokens,
                completionTokens = anthropicResponse.usage.output_tokens,
                totalTokens = anthropicResponse.usage.input_tokens + anthropicResponse.usage.output_tokens
            ),
            finishReason = when (anthropicResponse.stop_reason) {
                "end_turn" -> FinishReason.COMPLETED
                "max_tokens" -> FinishReason.LENGTH_LIMIT
                "stop_sequence" -> FinishReason.COMPLETED
                else -> FinishReason.COMPLETED
            }
        )
    }
    
    override fun chatStream(request: ChatRequest): Flow<ChatStreamChunk> = flow {
        // 简化的流式实现
        val response = chat(request.copy(stream = false))
        
        // 模拟流式输出
        val content = response.content
        val chunkSize = 15
        
        for (i in content.indices step chunkSize) {
            val chunk = content.substring(i, minOf(i + chunkSize, content.length))
            val isComplete = i + chunkSize >= content.length
            
            emit(ChatStreamChunk(
                id = response.id,
                content = chunk,
                isComplete = isComplete,
                usage = if (isComplete) response.usage else null
            ))
            
            kotlinx.coroutines.delay(80) // 模拟网络延迟
        }
    }
    
    override suspend fun countTokens(text: String, modelId: String): Int {
        // Claude的token计算：英文约3.5字符=1token，中文约1.3字符=1token
        val englishChars = text.count { it.code < 128 }
        val nonEnglishChars = text.length - englishChars
        
        return (englishChars / 3.5).toInt() + (nonEnglishChars / 1.3).toInt()
    }
    
    override suspend fun getPricing(modelId: String): PricingInfo {
        return supportedModels.find { it.id == modelId }?.pricing
            ?: throw IllegalArgumentException("Unsupported model: $modelId")
    }
    
    override suspend fun getUsageStats(): UsageStats {
        // 简化实现，实际应该从Anthropic API获取使用统计
        return UsageStats(
            totalRequests = 0,
            totalTokens = 0,
            totalCost = 0.0,
            averageLatency = 3000.0,
            successRate = 0.97
        )
    }
}

// Anthropic API数据类

@Serializable
private data class AnthropicRequest(
    val model: String,
    val max_tokens: Int,
    val messages: List<AnthropicMessage>,
    val temperature: Double? = null,
    val top_p: Double? = null,
    val system: String? = null,
    val stream: Boolean = false
)

@Serializable
private data class AnthropicMessage(
    val role: String,
    val content: String
)

@Serializable
private data class AnthropicResponse(
    val id: String,
    val model: String,
    val content: List<AnthropicContent>,
    val usage: AnthropicUsage,
    val stop_reason: String? = null
)

@Serializable
private data class AnthropicContent(
    val type: String,
    val text: String
)

@Serializable
private data class AnthropicUsage(
    val input_tokens: Int,
    val output_tokens: Int
)