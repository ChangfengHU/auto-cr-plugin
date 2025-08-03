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
 * Google Provider - V5.1版本
 * 支持Gemini模型系列
 */
class GoogleProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta"
) : AIProvider {
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override val providerInfo = ProviderInfo(
        id = "google",
        name = "Google AI",
        description = "Google Gemini models for code analysis and generation",
        website = "https://ai.google.dev",
        apiBaseUrl = baseUrl,
        authType = AuthType.API_KEY,
        rateLimit = RateLimit(
            requestsPerMinute = 1500,
            tokensPerMinute = 32000,
            requestsPerDay = 50000
        ),
        features = setOf(
            ProviderFeature.CHAT_COMPLETION,
            ProviderFeature.STREAMING,
            ProviderFeature.CODE_GENERATION,
            ProviderFeature.IMAGE_INPUT
        )
    )
    
    override val supportedModels = listOf(
        ModelInfo(
            id = "gemini-1.5-pro",
            name = "Gemini 1.5 Pro",
            version = "001",
            description = "Most capable multimodal model with 2M token context",
            maxTokens = 8192,
            contextWindow = 2000000,
            supportedFeatures = setOf(
                ModelFeature.CHAT,
                ModelFeature.CODE_GENERATION,
                ModelFeature.CODE_EXPLANATION,
                ModelFeature.CODE_REVIEW,
                ModelFeature.ANALYSIS
            ),
            performance = ModelPerformance(
                averageLatency = 4.1,
                tokensPerSecond = 42.0,
                qualityScore = 0.94,
                reliabilityScore = 0.95
            ),
            pricing = PricingInfo(
                inputPricePerToken = 3.5 / 1000000,
                outputPricePerToken = 10.5 / 1000000
            )
        ),
        ModelInfo(
            id = "gemini-1.5-flash",
            name = "Gemini 1.5 Flash",
            version = "001",
            description = "Fast and efficient model for code tasks",
            maxTokens = 8192,
            contextWindow = 1000000,
            supportedFeatures = setOf(
                ModelFeature.CHAT,
                ModelFeature.CODE_GENERATION,
                ModelFeature.CODE_EXPLANATION,
                ModelFeature.CODE_REVIEW
            ),
            performance = ModelPerformance(
                averageLatency = 1.8,
                tokensPerSecond = 75.0,
                qualityScore = 0.89,
                reliabilityScore = 0.93
            ),
            pricing = PricingInfo(
                inputPricePerToken = 0.075 / 1000000,
                outputPricePerToken = 0.3 / 1000000
            )
        ),
        ModelInfo(
            id = "gemini-pro",
            name = "Gemini Pro",
            version = "001",
            description = "High-quality model for complex reasoning",
            maxTokens = 2048,
            contextWindow = 30720,
            supportedFeatures = setOf(
                ModelFeature.CHAT,
                ModelFeature.CODE_GENERATION,
                ModelFeature.CODE_EXPLANATION,
                ModelFeature.CODE_REVIEW
            ),
            performance = ModelPerformance(
                averageLatency = 2.9,
                tokensPerSecond = 55.0,
                qualityScore = 0.91,
                reliabilityScore = 0.94
            ),
            pricing = PricingInfo(
                inputPricePerToken = 0.5 / 1000000,
                outputPricePerToken = 1.5 / 1000000
            )
        )
    )
    
    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/models?key=$apiKey"))
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
                .uri(URI.create("$baseUrl/models?key=$apiKey"))
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
            supportsFunctionCalling = false,
            supportsImageInput = modelId.contains("1.5"),
            languages = setOf("en", "zh", "ja", "ko", "es", "fr", "de", "it", "pt", "ru", "hi", "ar"),
            codeLanguages = setOf("java", "kotlin", "javascript", "typescript", "python", "go", "rust", "c", "cpp", "swift", "dart")
        )
    }
    
    override suspend fun chat(request: ChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        val geminiRequest = GeminiRequest(
            contents = request.messages.map { msg ->
                GeminiContent(
                    role = when (msg.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "model"
                        else -> "user"
                    },
                    parts = listOf(GeminiPart(text = msg.content))
                )
            },
            generationConfig = GeminiGenerationConfig(
                temperature = request.temperature,
                maxOutputTokens = request.maxTokens,
                topP = request.topP
            ),
            systemInstruction = request.systemPrompt?.let { 
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = it))
                )
            }
        )
        
        val requestBody = json.encodeToString(geminiRequest)
        
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/models/${request.modelId}:generateContent?key=$apiKey"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()
        
        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw RuntimeException("Google AI API error: ${response.statusCode()} - ${response.body()}")
        }
        
        val geminiResponse = json.decodeFromString<GeminiResponse>(response.body())
        
        ChatResponse(
            id = "gemini-${System.currentTimeMillis()}",
            content = geminiResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "",
            model = request.modelId,
            usage = TokenUsage(
                promptTokens = geminiResponse.usageMetadata?.promptTokenCount ?: 0,
                completionTokens = geminiResponse.usageMetadata?.candidatesTokenCount ?: 0,
                totalTokens = geminiResponse.usageMetadata?.totalTokenCount ?: 0
            ),
            finishReason = when (geminiResponse.candidates.firstOrNull()?.finishReason) {
                "STOP" -> FinishReason.COMPLETED
                "MAX_TOKENS" -> FinishReason.LENGTH_LIMIT
                "SAFETY" -> FinishReason.ERROR
                else -> FinishReason.COMPLETED
            }
        )
    }
    
    override fun chatStream(request: ChatRequest): Flow<ChatStreamChunk> = flow {
        // 简化的流式实现
        val response = chat(request.copy(stream = false))
        
        // 模拟流式输出
        val content = response.content
        val chunkSize = 25
        
        for (i in content.indices step chunkSize) {
            val chunk = content.substring(i, minOf(i + chunkSize, content.length))
            val isComplete = i + chunkSize >= content.length
            
            emit(ChatStreamChunk(
                id = response.id,
                content = chunk,
                isComplete = isComplete,
                usage = if (isComplete) response.usage else null
            ))
            
            kotlinx.coroutines.delay(60)
        }
    }
    
    override suspend fun countTokens(text: String, modelId: String): Int {
        // Gemini的token计算：英文约4字符=1token，中文约2字符=1token
        val englishChars = text.count { it.code < 128 }
        val nonEnglishChars = text.length - englishChars
        
        return (englishChars / 4) + (nonEnglishChars / 2)
    }
    
    override suspend fun getPricing(modelId: String): PricingInfo {
        return supportedModels.find { it.id == modelId }?.pricing
            ?: throw IllegalArgumentException("Unsupported model: $modelId")
    }
    
    override suspend fun getUsageStats(): UsageStats {
        return UsageStats(
            totalRequests = 0,
            totalTokens = 0,
            totalCost = 0.0,
            averageLatency = 2800.0,
            successRate = 0.95
        )
    }
}

/**
 * Ollama Provider - V5.1版本
 * 支持本地部署的开源模型
 */
class OllamaProvider(
    private val baseUrl: String = "http://localhost:11434"
) : AIProvider {
    
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override val providerInfo = ProviderInfo(
        id = "ollama",
        name = "Ollama",
        description = "Local deployment of open-source models",
        website = "https://ollama.ai",
        apiBaseUrl = baseUrl,
        authType = AuthType.API_KEY, // 本地部署通常不需要认证
        rateLimit = RateLimit(
            requestsPerMinute = 10000, // 本地部署，限制较宽松
            tokensPerMinute = 500000
        ),
        features = setOf(
            ProviderFeature.CHAT_COMPLETION,
            ProviderFeature.STREAMING,
            ProviderFeature.CODE_GENERATION
        )
    )
    
    override val supportedModels = listOf(
        ModelInfo(
            id = "llama3.1:8b",
            name = "Llama 3.1 8B",
            version = "latest",
            description = "Meta's Llama 3.1 8B model for code tasks",
            maxTokens = 4096,
            contextWindow = 128000,
            supportedFeatures = setOf(
                ModelFeature.CHAT,
                ModelFeature.CODE_GENERATION,
                ModelFeature.CODE_EXPLANATION,
                ModelFeature.CODE_REVIEW
            ),
            performance = ModelPerformance(
                averageLatency = 8.5,
                tokensPerSecond = 25.0,
                qualityScore = 0.82,
                reliabilityScore = 0.90
            ),
            pricing = PricingInfo(
                inputPricePerToken = 0.0, // 本地部署免费
                outputPricePerToken = 0.0
            )
        ),
        ModelInfo(
            id = "codellama:13b",
            name = "CodeLlama 13B",
            version = "latest",
            description = "Specialized model for code generation and review",
            maxTokens = 4096,
            contextWindow = 16384,
            supportedFeatures = setOf(
                ModelFeature.CHAT,
                ModelFeature.CODE_GENERATION,
                ModelFeature.CODE_EXPLANATION,
                ModelFeature.CODE_REVIEW
            ),
            performance = ModelPerformance(
                averageLatency = 12.0,
                tokensPerSecond = 18.0,
                qualityScore = 0.87,
                reliabilityScore = 0.88
            ),
            pricing = PricingInfo(
                inputPricePerToken = 0.0,
                outputPricePerToken = 0.0
            )
        ),
        ModelInfo(
            id = "deepseek-coder:6.7b",
            name = "DeepSeek Coder 6.7B",
            version = "latest",
            description = "Efficient model for code analysis",
            maxTokens = 4096,
            contextWindow = 16384,
            supportedFeatures = setOf(
                ModelFeature.CHAT,
                ModelFeature.CODE_GENERATION,
                ModelFeature.CODE_EXPLANATION,
                ModelFeature.CODE_REVIEW
            ),
            performance = ModelPerformance(
                averageLatency = 6.2,
                tokensPerSecond = 30.0,
                qualityScore = 0.84,
                reliabilityScore = 0.89
            ),
            pricing = PricingInfo(
                inputPricePerToken = 0.0,
                outputPricePerToken = 0.0
            )
        )
    )
    
    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/api/tags"))
                .header("Content-Type", "application/json")
                .GET()
                .build()
            
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun validateApiKey(apiKey: String): Boolean {
        // Ollama通常不需要API Key
        return isAvailable()
    }
    
    override suspend fun getModelCapabilities(modelId: String): ModelCapabilities {
        val model = supportedModels.find { it.id == modelId }
            ?: throw IllegalArgumentException("Unsupported model: $modelId")
        
        return ModelCapabilities(
            maxInputTokens = model.contextWindow - model.maxTokens,
            maxOutputTokens = model.maxTokens,
            supportsStreaming = true,
            supportsSystemPrompt = true,
            supportsFunctionCalling = false,
            supportsImageInput = false,
            languages = setOf("en", "zh", "es", "fr", "de", "pt", "ru"),
            codeLanguages = setOf("java", "kotlin", "javascript", "typescript", "python", "go", "rust", "c", "cpp")
        )
    }
    
    override suspend fun chat(request: ChatRequest): ChatResponse = withContext(Dispatchers.IO) {
        val ollamaRequest = OllamaRequest(
            model = request.modelId,
            messages = request.messages.map { msg ->
                OllamaMessage(
                    role = msg.role.name.lowercase(),
                    content = msg.content
                )
            },
            stream = false,
            options = OllamaOptions(
                temperature = request.temperature,
                num_predict = request.maxTokens,
                top_p = request.topP
            )
        )
        
        val requestBody = json.encodeToString(ollamaRequest)
        
        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/chat"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()
        
        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() != 200) {
            throw RuntimeException("Ollama API error: ${response.statusCode()} - ${response.body()}")
        }
        
        val ollamaResponse = json.decodeFromString<OllamaResponse>(response.body())
        
        ChatResponse(
            id = "ollama-${System.currentTimeMillis()}",
            content = ollamaResponse.message.content,
            model = ollamaResponse.model,
            usage = TokenUsage(
                promptTokens = ollamaResponse.prompt_eval_count ?: 0,
                completionTokens = ollamaResponse.eval_count ?: 0,
                totalTokens = (ollamaResponse.prompt_eval_count ?: 0) + (ollamaResponse.eval_count ?: 0)
            ),
            finishReason = if (ollamaResponse.done) FinishReason.COMPLETED else FinishReason.ERROR
        )
    }
    
    override fun chatStream(request: ChatRequest): Flow<ChatStreamChunk> = flow {
        // 简化的流式实现
        val response = chat(request.copy(stream = false))
        
        // 模拟流式输出
        val content = response.content
        val chunkSize = 30
        
        for (i in content.indices step chunkSize) {
            val chunk = content.substring(i, minOf(i + chunkSize, content.length))
            val isComplete = i + chunkSize >= content.length
            
            emit(ChatStreamChunk(
                id = response.id,
                content = chunk,
                isComplete = isComplete,
                usage = if (isComplete) response.usage else null
            ))
            
            kotlinx.coroutines.delay(100)
        }
    }
    
    override suspend fun countTokens(text: String, modelId: String): Int {
        // 简化的token计算
        return text.split("\\s+".toRegex()).size
    }
    
    override suspend fun getPricing(modelId: String): PricingInfo {
        return supportedModels.find { it.id == modelId }?.pricing
            ?: throw IllegalArgumentException("Unsupported model: $modelId")
    }
    
    override suspend fun getUsageStats(): UsageStats {
        return UsageStats(
            totalRequests = 0,
            totalTokens = 0,
            totalCost = 0.0,
            averageLatency = 8000.0,
            successRate = 0.92
        )
    }
}

// Google Gemini API数据类

@Serializable
private data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val systemInstruction: GeminiContent? = null
)

@Serializable
private data class GeminiContent(
    val role: String,
    val parts: List<GeminiPart>
)

@Serializable
private data class GeminiPart(
    val text: String
)

@Serializable
private data class GeminiGenerationConfig(
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val topP: Double? = null
)

@Serializable
private data class GeminiResponse(
    val candidates: List<GeminiCandidate>,
    val usageMetadata: GeminiUsageMetadata? = null
)

@Serializable
private data class GeminiCandidate(
    val content: GeminiContent,
    val finishReason: String? = null
)

@Serializable
private data class GeminiUsageMetadata(
    val promptTokenCount: Int,
    val candidatesTokenCount: Int,
    val totalTokenCount: Int
)

// Ollama API数据类

@Serializable
private data class OllamaRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val options: OllamaOptions? = null
)

@Serializable
private data class OllamaMessage(
    val role: String,
    val content: String
)

@Serializable
private data class OllamaOptions(
    val temperature: Double? = null,
    val num_predict: Int? = null,
    val top_p: Double? = null
)

@Serializable
private data class OllamaResponse(
    val model: String,
    val message: OllamaMessage,
    val done: Boolean,
    val prompt_eval_count: Int? = null,
    val eval_count: Int? = null
)