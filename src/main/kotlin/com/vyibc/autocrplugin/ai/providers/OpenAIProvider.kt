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
 * OpenAI Provider - V5.1版本
 * 支持GPT-4o, GPT-4, GPT-3.5-turbo等模型
 */
class OpenAIProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1"
) : AIProvider {
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override val providerInfo = ProviderInfo(
        id = "openai",
        name = "OpenAI",
        description = "OpenAI GPT models for code review and analysis",
        website = "https://openai.com",
        apiBaseUrl = baseUrl,
        authType = AuthType.BEARER_TOKEN,
        rateLimit = RateLimit(
            requestsPerMinute = 3500,
            tokensPerMinute = 90000,
            requestsPerDay = 10000
        ),
        features = setOf(
            ProviderFeature.CHAT_COMPLETION,
            ProviderFeature.STREAMING,
            ProviderFeature.FUNCTION_CALLING,
            ProviderFeature.CODE_GENERATION
        )
    )
    
    override val supportedModels = listOf(
        ModelInfo(
            id = "gpt-4o",
            name = "GPT-4o",
            version = "2024-08-06",
            description = "Most advanced multimodal model, excellent for code review",
            maxTokens = 16384,
            contextWindow = 128000,
            supportedFeatures = setOf(
                ModelFeature.CHAT,
                ModelFeature.CODE_GENERATION,
                ModelFeature.CODE_EXPLANATION,
                ModelFeature.CODE_REVIEW,
                ModelFeature.ANALYSIS
            ),
            performance = ModelPerformance(
                averageLatency = 2.5,
                tokensPerSecond = 50.0,
                qualityScore = 0.95,
                reliabilityScore = 0.98
            ),
            pricing = PricingInfo(
                inputPricePerToken = 0.0025 / 1000,
                outputPricePerToken = 0.01 / 1000
            )
        ),
        ModelInfo(
            id = "gpt-4-turbo",
            name = "GPT-4 Turbo",
            version = "2024-04-09",
            description = "High-performance model for complex code analysis",
            maxTokens = 4096,
            contextWindow = 128000,
            supportedFeatures = setOf(
                ModelFeature.CHAT,
                ModelFeature.CODE_GENERATION,
                ModelFeature.CODE_EXPLANATION,
                ModelFeature.CODE_REVIEW,
                ModelFeature.ANALYSIS
            ),
            performance = ModelPerformance(
                averageLatency = 3.2,
                tokensPerSecond = 40.0,
                qualityScore = 0.93,
                reliabilityScore = 0.96
            ),
            pricing = PricingInfo(
                inputPricePerToken = 0.01 / 1000,
                outputPricePerToken = 0.03 / 1000
            )
        ),
        ModelInfo(
            id = "gpt-3.5-turbo",
            name = "GPT-3.5 Turbo",
            version = "0125",
            description = "Fast and cost-effective model for basic code review",
            maxTokens = 4096,
            contextWindow = 16385,
            supportedFeatures = setOf(
                ModelFeature.CHAT,
                ModelFeature.CODE_GENERATION,
                ModelFeature.CODE_EXPLANATION,
                ModelFeature.CODE_REVIEW
            ),
            performance = ModelPerformance(
                averageLatency = 1.8,
                tokensPerSecond = 70.0,
                qualityScore = 0.85,
                reliabilityScore = 0.94
            ),
            pricing = PricingInfo(
                inputPricePerToken = 0.0005 / 1000,
                outputPricePerToken = 0.0015 / 1000
            )
        )
    )
    
    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/models"))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun validateApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/models"))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
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
            supportsFunctionCalling = modelId.startsWith("gpt-4"),
            supportsImageInput = modelId == "gpt-4o",
            languages = setOf("en", "zh", "ja", "ko", "es", "fr", "de", "it", "pt", "ru"),
            codeLanguages = setOf("java", "kotlin", "javascript", "typescript", "python", "go", "rust", "c", "cpp")
        )
    }
    
    override suspend fun chat(request: ChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        val openAIRequest = OpenAIRequest(
            model = request.modelId,
            messages = request.messages.map { msg ->
                OpenAIMessage(
                    role = msg.role.name.lowercase(),
                    content = msg.content
                )
            },
            temperature = request.temperature,
            max_tokens = request.maxTokens,
            top_p = request.topP,
            stream = false
        )
        
        val requestBody = json.encodeToString(openAIRequest)
        
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/chat/completions"))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()
        
        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw RuntimeException("OpenAI API error: ${response.statusCode()} - ${response.body()}")
        }
        
        val openAIResponse = json.decodeFromString<OpenAIResponse>(response.body())
        
        ChatResponse(
            id = openAIResponse.id,
            content = openAIResponse.choices.firstOrNull()?.message?.content ?: "",
            model = openAIResponse.model,
            usage = TokenUsage(
                promptTokens = openAIResponse.usage.prompt_tokens,
                completionTokens = openAIResponse.usage.completion_tokens,
                totalTokens = openAIResponse.usage.total_tokens
            ),
            finishReason = when (openAIResponse.choices.firstOrNull()?.finish_reason) {
                "stop" -> FinishReason.COMPLETED
                "length" -> FinishReason.LENGTH_LIMIT
                else -> FinishReason.COMPLETED
            }
        )
    }
    
    override fun chatStream(request: ChatRequest): Flow<ChatStreamChunk> = flow {
        // 简化的流式实现
        val response = chat(request.copy(stream = false))
        
        // 模拟流式输出
        val content = response.content
        val chunkSize = 20
        
        for (i in content.indices step chunkSize) {
            val chunk = content.substring(i, minOf(i + chunkSize, content.length))
            val isComplete = i + chunkSize >= content.length
            
            emit(ChatStreamChunk(
                id = response.id,
                content = chunk,
                isComplete = isComplete,
                usage = if (isComplete) response.usage else null
            ))
            
            kotlinx.coroutines.delay(50) // 模拟网络延迟
        }
    }
    
    override suspend fun countTokens(text: String, modelId: String): Int {
        // 简化的token计算：英文约4字符=1token，中文约1.5字符=1token
        val englishChars = text.count { it.code < 128 }
        val nonEnglishChars = text.length - englishChars
        
        return (englishChars / 4) + (nonEnglishChars * 2 / 3)
    }
    
    override suspend fun getPricing(modelId: String): PricingInfo {
        return supportedModels.find { it.id == modelId }?.pricing
            ?: throw IllegalArgumentException("Unsupported model: $modelId")
    }
    
    override suspend fun getUsageStats(): UsageStats {
        // 简化实现，实际应该从OpenAI API获取使用统计
        return UsageStats(
            totalRequests = 0,
            totalTokens = 0,
            totalCost = 0.0,
            averageLatency = 2500.0,
            successRate = 0.98
        )
    }
}

// OpenAI API数据类

@Serializable
private data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Double? = null,
    val max_tokens: Int? = null,
    val top_p: Double? = null,
    val stream: Boolean = false
)

@Serializable
private data class OpenAIMessage(
    val role: String,
    val content: String
)

@Serializable
private data class OpenAIResponse(
    val id: String,
    val model: String,
    val choices: List<OpenAIChoice>,
    val usage: OpenAIUsage
)

@Serializable
private data class OpenAIChoice(
    val message: OpenAIMessage,
    val finish_reason: String? = null
)

@Serializable
private data class OpenAIUsage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)