package com.vyibc.autocrplugin.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * AI配置状态管理
 */
@State(
    name = "AutoCRSettings",
    storages = [Storage("auto-cr-settings.xml")]
)
@Service(Service.Level.PROJECT)
class AutoCRSettings : PersistentStateComponent<AutoCRSettings.State> {
    
    private var myState = State()
    
    override fun getState(): State = myState
    
    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }
    
    companion object {
        fun getInstance(project: Project): AutoCRSettings = project.service()
    }
    
    data class State(
        // AI供应商配置
        var selectedProvider: String = "openai",
        var openaiApiKey: String = "",
        var openaiBaseUrl: String = "https://api.openai.com/v1",
        var openaiModel: String = "gpt-4o",
        
        var anthropicApiKey: String = "",
        var anthropicModel: String = "claude-3-5-sonnet-20241022",
        
        var googleApiKey: String = "",
        var googleModel: String = "gemini-1.5-pro",
        
        var ollamaBaseUrl: String = "http://localhost:11434",
        var ollamaModel: String = "llama3.1:8b",
        
        // 智能路由配置
        var enableIntelligentRouting: Boolean = true,
        var qualityWeight: Double = 0.4,
        var speedWeight: Double = 0.3,
        var costWeight: Double = 0.2,
        var reliabilityWeight: Double = 0.1,
        var maxCostPerRequest: Double = 0.1,
        
        // 上下文压缩配置
        var compressionStrategy: String = "MEDIUM",
        var enableContextCompression: Boolean = true,
        
        // 提示词配置
        var customSystemPrompt: String = "",
        var enableCustomPrompt: Boolean = false,
        var quickScreeningPrompt: String = "",
        var deepAnalysisPrompt: String = "",
        
        // Neo4j配置
        var neo4jEnabled: Boolean = false,
        var neo4jUri: String = "bolt://localhost:7687",
        var neo4jUsername: String = "neo4j",
        var neo4jPassword: String = "",
        var neo4jDatabase: String = "neo4j",
        
        // 分析配置
        var enableIntentAnalysis: Boolean = true,
        var enableRiskAnalysis: Boolean = true,
        var enableTestRecommendation: Boolean = true,
        var intentThreshold: Double = 0.6,
        var riskThreshold: Double = 0.7,
        
        // 缓存配置
        var enableL1Cache: Boolean = true,
        var enableL2Cache: Boolean = true,
        var enableL3Cache: Boolean = true,
        var cacheExpiryHours: Int = 24,
        
        // 性能配置
        var maxConcurrentAnalysis: Int = 3,
        var analysisTimeoutSeconds: Int = 300,
        var enableProgressNotifications: Boolean = true,
        
        // 界面配置
        var showDetailedProgress: Boolean = true,
        var autoOpenReviewResults: Boolean = true,
        var defaultReviewScope: String = "CHANGED_FILES"
    )
    
    // 便捷访问方法
    fun getSelectedProviderConfig(): ProviderConfig {
        return when (myState.selectedProvider) {
            "openai" -> ProviderConfig(
                type = "openai",
                apiKey = myState.openaiApiKey,
                baseUrl = myState.openaiBaseUrl,
                model = myState.openaiModel
            )
            "anthropic" -> ProviderConfig(
                type = "anthropic", 
                apiKey = myState.anthropicApiKey,
                model = myState.anthropicModel
            )
            "google" -> ProviderConfig(
                type = "google",
                apiKey = myState.googleApiKey,
                model = myState.googleModel
            )
            "ollama" -> ProviderConfig(
                type = "ollama",
                baseUrl = myState.ollamaBaseUrl,
                model = myState.ollamaModel
            )
            else -> ProviderConfig(
                type = "openai",
                apiKey = myState.openaiApiKey,
                baseUrl = myState.openaiBaseUrl,
                model = myState.openaiModel
            )
        }
    }
    
    fun isProviderConfigured(provider: String): Boolean {
        return when (provider) {
            "openai" -> myState.openaiApiKey.isNotEmpty()
            "anthropic" -> myState.anthropicApiKey.isNotEmpty()
            "google" -> myState.googleApiKey.isNotEmpty()
            "ollama" -> myState.ollamaBaseUrl.isNotEmpty()
            else -> false
        }
    }
    
    fun getAvailableProviders(): List<String> {
        return listOf("openai", "anthropic", "google", "ollama").filter { isProviderConfigured(it) }
    }
}

/**
 * 供应商配置
 */
data class ProviderConfig(
    val type: String,
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = ""
)