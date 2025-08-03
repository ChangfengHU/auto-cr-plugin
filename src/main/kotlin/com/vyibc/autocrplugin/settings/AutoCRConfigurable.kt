package com.vyibc.autocrplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.*
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.util.ui.JBUI
import javax.swing.*
import javax.swing.border.TitledBorder

/**
 * AI代码评审插件配置界面
 */
class AutoCRConfigurable(private val project: Project) : Configurable {
    
    private val settings = AutoCRSettings.getInstance(project)
    
    // AI供应商配置组件
    private val providerComboBox = ComboBox(arrayOf("OpenAI", "Anthropic", "Google", "Ollama"))
    
    // OpenAI配置
    private val openaiApiKeyField = JBPasswordField()
    private val openaiBaseUrlField = JBTextField("https://api.openai.com/v1")
    private val openaiModelComboBox = ComboBox(arrayOf("gpt-4o", "gpt-4-turbo", "gpt-3.5-turbo"))
    
    // Anthropic配置
    private val anthropicApiKeyField = JBPasswordField()
    private val anthropicModelComboBox = ComboBox(arrayOf(
        "claude-3-5-sonnet-20241022", 
        "claude-3-opus-20240229", 
        "claude-3-sonnet-20240229", 
        "claude-3-haiku-20240307"
    ))
    
    // Google配置
    private val googleApiKeyField = JBPasswordField()
    private val googleModelComboBox = ComboBox(arrayOf("gemini-1.5-pro", "gemini-1.5-flash", "gemini-pro"))
    
    // Ollama配置
    private val ollamaBaseUrlField = JBTextField("http://localhost:11434")
    private val ollamaModelComboBox = ComboBox(arrayOf("llama3.1:8b", "codellama:13b", "deepseek-coder:6.7b"))
    
    // 智能路由配置
    private val enableIntelligentRoutingCheckBox = JBCheckBox("启用智能路由", true)
    private val qualityWeightSlider = JSlider(0, 100, 40)
    private val speedWeightSlider = JSlider(0, 100, 30)
    private val costWeightSlider = JSlider(0, 100, 20)
    private val reliabilityWeightSlider = JSlider(0, 100, 10)
    private val maxCostField = JBTextField("0.10")
    
    // 上下文压缩配置
    private val enableCompressionCheckBox = JBCheckBox("启用上下文压缩", true)
    private val compressionStrategyComboBox = ComboBox(arrayOf("轻量压缩(90%)", "中等压缩(70%)", "重度压缩(40%)"))
    
    // 提示词配置
    private val enableCustomPromptCheckBox = JBCheckBox("使用自定义提示词", false)
    private val customSystemPromptArea = JBTextArea(5, 50)
    private val quickScreeningPromptArea = JBTextArea(3, 50)
    private val deepAnalysisPromptArea = JBTextArea(3, 50)
    
    // Neo4j配置
    private val neo4jEnabledCheckBox = JBCheckBox("启用Neo4j可视化", false)
    private val neo4jUriField = JBTextField("bolt://localhost:7687")
    private val neo4jUsernameField = JBTextField("neo4j")
    private val neo4jPasswordField = JBPasswordField()
    private val neo4jDatabaseField = JBTextField("neo4j")
    private val testNeo4jButton = JButton("测试连接")
    
    // 分析配置
    private val enableIntentAnalysisCheckBox = JBCheckBox("启用意图分析", true)
    private val enableRiskAnalysisCheckBox = JBCheckBox("启用风险分析", true)
    private val enableTestRecommendationCheckBox = JBCheckBox("启用测试建议", true)
    private val intentThresholdSlider = JSlider(0, 100, 60)
    private val riskThresholdSlider = JSlider(0, 100, 70)
    
    // 性能配置
    private val maxConcurrentField = JBTextField("3")
    private val timeoutField = JBTextField("300")
    private val enableProgressCheckBox = JBCheckBox("显示详细进度", true)
    
    private var panel: JPanel? = null
    
    override fun getDisplayName(): String = "AI代码评审"
    
    override fun createComponent(): JComponent {
        loadSettings()
        setupEventListeners()
        
        panel = panel {
            // AI供应商配置
            group("AI供应商配置") {
                row("选择供应商:") {
                    cell(providerComboBox)
                    button("测试连接") {
                        testConnection()
                    }
                }
                
                // OpenAI配置面板
                collapsibleGroup("OpenAI配置") {
                    row("API Key:") {
                        cell(openaiApiKeyField).columns(COLUMNS_LARGE)
                    }
                    row("Base URL:") {
                        cell(openaiBaseUrlField).columns(COLUMNS_LARGE)
                    }
                    row("模型:") {
                        cell(openaiModelComboBox)
                    }
                }.expanded = (providerComboBox.selectedItem == "OpenAI")
                
                // Anthropic配置面板
                collapsibleGroup("Anthropic配置") {
                    row("API Key:") {
                        cell(anthropicApiKeyField).columns(COLUMNS_LARGE)
                    }
                    row("模型:") {
                        cell(anthropicModelComboBox)
                    }
                }.expanded = (providerComboBox.selectedItem == "Anthropic")
                
                // Google配置面板
                collapsibleGroup("Google配置") {
                    row("API Key:") {
                        cell(googleApiKeyField).columns(COLUMNS_LARGE)
                    }
                    row("模型:") {
                        cell(googleModelComboBox)
                    }
                }.expanded = (providerComboBox.selectedItem == "Google")
                
                // Ollama配置面板
                collapsibleGroup("Ollama配置") {
                    row("Base URL:") {
                        cell(ollamaBaseUrlField).columns(COLUMNS_LARGE)
                    }
                    row("模型:") {
                        cell(ollamaModelComboBox)
                    }
                }.expanded = (providerComboBox.selectedItem == "Ollama")
            }
            
            // 智能路由配置
            collapsibleGroup("智能路由配置") {
                row {
                    cell(enableIntelligentRoutingCheckBox)
                }
                row("质量权重 (${qualityWeightSlider.value}%):") {
                    cell(qualityWeightSlider)
                }
                row("速度权重 (${speedWeightSlider.value}%):") {
                    cell(speedWeightSlider)
                }
                row("成本权重 (${costWeightSlider.value}%):") {
                    cell(costWeightSlider)
                }
                row("可靠性权重 (${reliabilityWeightSlider.value}%):") {
                    cell(reliabilityWeightSlider)
                }
                row("最大单次成本($):") {
                    cell(maxCostField).columns(10)
                }
            }.expanded = false
            
            // 上下文压缩配置
            collapsibleGroup("上下文压缩配置") {
                row {
                    cell(enableCompressionCheckBox)
                }
                row("压缩策略:") {
                    cell(compressionStrategyComboBox)
                }
            }.expanded = false
            
            // 提示词配置
            collapsibleGroup("提示词配置") {
                row {
                    cell(enableCustomPromptCheckBox)
                }
                row("系统提示词:") {
                    scrollCell(customSystemPromptArea).columns(COLUMNS_LARGE)
                }
                
                row("快速筛选提示词:") {
                    scrollCell(quickScreeningPromptArea).columns(COLUMNS_LARGE)
                }
                
                row("深度分析提示词:") {
                    scrollCell(deepAnalysisPromptArea).columns(COLUMNS_LARGE)
                }
                
                row {
                    button("重置为默认") {
                        resetPromptsToDefault()
                    }
                    button("导入模板") {
                        importPromptTemplate()
                    }
                    button("导出配置") {
                        exportPromptConfig()
                    }
                }
            }.expanded = false
            
            // Neo4j配置
            collapsibleGroup("Neo4j可视化配置") {
                row {
                    cell(neo4jEnabledCheckBox)
                }
                row("连接URI:") {
                    cell(neo4jUriField).columns(COLUMNS_LARGE)
                }
                
                row("用户名:") {
                    cell(neo4jUsernameField)
                }
                
                row("密码:") {
                    cell(neo4jPasswordField)
                }
                
                row("数据库:") {
                    cell(neo4jDatabaseField)
                }
                
                row {
                    cell(testNeo4jButton)
                }
            }.expanded = false
            
            // 分析配置
            collapsibleGroup("分析配置") {
                row {
                    cell(enableIntentAnalysisCheckBox)
                    cell(enableRiskAnalysisCheckBox)
                    cell(enableTestRecommendationCheckBox)
                }
                row("意图分析阈值 (${intentThresholdSlider.value / 100.0}):") {
                    cell(intentThresholdSlider)
                }
                row("风险分析阈值 (${riskThresholdSlider.value / 100.0}):") {
                    cell(riskThresholdSlider)
                }
            }.expanded = false
            
            // 性能配置
            collapsibleGroup("性能配置") {
                row("最大并发分析数:") {
                    cell(maxConcurrentField).columns(10)
                }
                row("分析超时(秒):") {
                    cell(timeoutField).columns(10)
                }
                row {
                    cell(enableProgressCheckBox)
                }
            }.expanded = false
        }
        
        return panel!!
    }
    
    private fun setupEventListeners() {
        // 供应商选择变化时更新界面
        providerComboBox.addActionListener {
            updateProviderPanels()
        }
        
        // 权重滑块变化时更新标签
        qualityWeightSlider.addChangeListener {
            // 可以添加标签更新逻辑
        }
        
        // Neo4j测试连接
        testNeo4jButton.addActionListener {
            testNeo4jConnection()
        }
        
        // 自定义提示词开关
        enableCustomPromptCheckBox.addActionListener {
            updatePromptFields()
        }
    }
    
    private fun loadSettings() {
        val state = settings.state
        
        // 加载AI供应商配置
        providerComboBox.selectedItem = when (state.selectedProvider) {
            "openai" -> "OpenAI"
            "anthropic" -> "Anthropic"
            "google" -> "Google"
            "ollama" -> "Ollama"
            else -> "OpenAI"
        }
        
        // 加载各供应商配置
        openaiApiKeyField.text = state.openaiApiKey
        openaiBaseUrlField.text = state.openaiBaseUrl
        openaiModelComboBox.selectedItem = state.openaiModel
        
        anthropicApiKeyField.text = state.anthropicApiKey
        anthropicModelComboBox.selectedItem = state.anthropicModel
        
        googleApiKeyField.text = state.googleApiKey
        googleModelComboBox.selectedItem = state.googleModel
        
        ollamaBaseUrlField.text = state.ollamaBaseUrl
        ollamaModelComboBox.selectedItem = state.ollamaModel
        
        // 加载智能路由配置
        enableIntelligentRoutingCheckBox.isSelected = state.enableIntelligentRouting
        qualityWeightSlider.value = (state.qualityWeight * 100).toInt()
        speedWeightSlider.value = (state.speedWeight * 100).toInt()
        costWeightSlider.value = (state.costWeight * 100).toInt()
        reliabilityWeightSlider.value = (state.reliabilityWeight * 100).toInt()
        maxCostField.text = state.maxCostPerRequest.toString()
        
        // 加载压缩配置
        enableCompressionCheckBox.isSelected = state.enableContextCompression
        compressionStrategyComboBox.selectedItem = when (state.compressionStrategy) {
            "LIGHT" -> "轻量压缩(90%)"
            "MEDIUM" -> "中等压缩(70%)"
            "HEAVY" -> "重度压缩(40%)"
            else -> "中等压缩(70%)"
        }
        
        // 加载提示词配置
        enableCustomPromptCheckBox.isSelected = state.enableCustomPrompt
        customSystemPromptArea.text = state.customSystemPrompt
        quickScreeningPromptArea.text = state.quickScreeningPrompt
        deepAnalysisPromptArea.text = state.deepAnalysisPrompt
        
        // 加载Neo4j配置
        neo4jEnabledCheckBox.isSelected = state.neo4jEnabled
        neo4jUriField.text = state.neo4jUri
        neo4jUsernameField.text = state.neo4jUsername
        neo4jPasswordField.text = state.neo4jPassword
        neo4jDatabaseField.text = state.neo4jDatabase
        
        // 加载分析配置
        enableIntentAnalysisCheckBox.isSelected = state.enableIntentAnalysis
        enableRiskAnalysisCheckBox.isSelected = state.enableRiskAnalysis
        enableTestRecommendationCheckBox.isSelected = state.enableTestRecommendation
        intentThresholdSlider.value = (state.intentThreshold * 100).toInt()
        riskThresholdSlider.value = (state.riskThreshold * 100).toInt()
        
        // 加载性能配置
        maxConcurrentField.text = state.maxConcurrentAnalysis.toString()
        timeoutField.text = state.analysisTimeoutSeconds.toString()
        enableProgressCheckBox.isSelected = state.enableProgressNotifications
    }
    
    override fun isModified(): Boolean {
        val state = settings.state
        
        return state.selectedProvider != getSelectedProviderKey() ||
               state.openaiApiKey != String(openaiApiKeyField.password) ||
               state.openaiBaseUrl != openaiBaseUrlField.text ||
               state.openaiModel != openaiModelComboBox.selectedItem.toString() ||
               state.anthropicApiKey != String(anthropicApiKeyField.password) ||
               state.anthropicModel != anthropicModelComboBox.selectedItem.toString() ||
               state.googleApiKey != String(googleApiKeyField.password) ||
               state.googleModel != googleModelComboBox.selectedItem.toString() ||
               state.ollamaBaseUrl != ollamaBaseUrlField.text ||
               state.ollamaModel != ollamaModelComboBox.selectedItem.toString() ||
               state.enableIntelligentRouting != enableIntelligentRoutingCheckBox.isSelected ||
               state.enableContextCompression != enableCompressionCheckBox.isSelected ||
               state.enableCustomPrompt != enableCustomPromptCheckBox.isSelected ||
               state.neo4jEnabled != neo4jEnabledCheckBox.isSelected
    }
    
    override fun apply() {
        val state = settings.state
        
        // 保存AI供应商配置
        state.selectedProvider = getSelectedProviderKey()
        
        state.openaiApiKey = String(openaiApiKeyField.password)
        state.openaiBaseUrl = openaiBaseUrlField.text
        state.openaiModel = openaiModelComboBox.selectedItem.toString()
        
        state.anthropicApiKey = String(anthropicApiKeyField.password)
        state.anthropicModel = anthropicModelComboBox.selectedItem.toString()
        
        state.googleApiKey = String(googleApiKeyField.password)
        state.googleModel = googleModelComboBox.selectedItem.toString()
        
        state.ollamaBaseUrl = ollamaBaseUrlField.text
        state.ollamaModel = ollamaModelComboBox.selectedItem.toString()
        
        // 保存智能路由配置
        state.enableIntelligentRouting = enableIntelligentRoutingCheckBox.isSelected
        state.qualityWeight = qualityWeightSlider.value / 100.0
        state.speedWeight = speedWeightSlider.value / 100.0
        state.costWeight = costWeightSlider.value / 100.0
        state.reliabilityWeight = reliabilityWeightSlider.value / 100.0
        state.maxCostPerRequest = maxCostField.text.toDoubleOrNull() ?: 0.1
        
        // 保存压缩配置
        state.enableContextCompression = enableCompressionCheckBox.isSelected
        state.compressionStrategy = when (compressionStrategyComboBox.selectedItem.toString()) {
            "轻量压缩(90%)" -> "LIGHT"
            "中等压缩(70%)" -> "MEDIUM"
            "重度压缩(40%)" -> "HEAVY"
            else -> "MEDIUM"
        }
        
        // 保存提示词配置
        state.enableCustomPrompt = enableCustomPromptCheckBox.isSelected
        state.customSystemPrompt = customSystemPromptArea.text
        state.quickScreeningPrompt = quickScreeningPromptArea.text
        state.deepAnalysisPrompt = deepAnalysisPromptArea.text
        
        // 保存Neo4j配置
        state.neo4jEnabled = neo4jEnabledCheckBox.isSelected
        state.neo4jUri = neo4jUriField.text
        state.neo4jUsername = neo4jUsernameField.text
        state.neo4jPassword = String(neo4jPasswordField.password)
        state.neo4jDatabase = neo4jDatabaseField.text
        
        // 保存分析配置
        state.enableIntentAnalysis = enableIntentAnalysisCheckBox.isSelected
        state.enableRiskAnalysis = enableRiskAnalysisCheckBox.isSelected
        state.enableTestRecommendation = enableTestRecommendationCheckBox.isSelected
        state.intentThreshold = intentThresholdSlider.value / 100.0
        state.riskThreshold = riskThresholdSlider.value / 100.0
        
        // 保存性能配置
        state.maxConcurrentAnalysis = maxConcurrentField.text.toIntOrNull() ?: 3
        state.analysisTimeoutSeconds = timeoutField.text.toIntOrNull() ?: 300
        state.enableProgressNotifications = enableProgressCheckBox.isSelected
    }
    
    private fun getSelectedProviderKey(): String {
        return when (providerComboBox.selectedItem.toString()) {
            "OpenAI" -> "openai"
            "Anthropic" -> "anthropic"
            "Google" -> "google"
            "Ollama" -> "ollama"
            else -> "openai"
        }
    }
    
    private fun updateProviderPanels() {
        // 这里可以添加动态显示/隐藏供应商配置面板的逻辑
    }
    
    private fun testConnection() {
        // 测试当前选中的AI供应商连接
        val provider = getSelectedProviderKey()
        JOptionPane.showMessageDialog(
            panel,
            "正在测试 $provider 连接...\n(实际实现中这里会调用对应的API)",
            "测试连接",
            JOptionPane.INFORMATION_MESSAGE
        )
    }
    
    private fun testNeo4jConnection() {
        JOptionPane.showMessageDialog(
            panel,
            "正在测试Neo4j连接...\n(实际实现中这里会测试数据库连接)",
            "测试Neo4j连接",
            JOptionPane.INFORMATION_MESSAGE
        )
    }
    
    private fun resetPromptsToDefault() {
        customSystemPromptArea.text = ""
        quickScreeningPromptArea.text = ""
        deepAnalysisPromptArea.text = ""
    }
    
    private fun importPromptTemplate() {
        JOptionPane.showMessageDialog(
            panel,
            "导入提示词模板功能开发中...",
            "导入模板",
            JOptionPane.INFORMATION_MESSAGE
        )
    }
    
    private fun exportPromptConfig() {
        JOptionPane.showMessageDialog(
            panel,
            "导出提示词配置功能开发中...",
            "导出配置", 
            JOptionPane.INFORMATION_MESSAGE
        )
    }
    
    private fun updatePromptFields() {
        val enabled = enableCustomPromptCheckBox.isSelected
        customSystemPromptArea.isEnabled = enabled
        quickScreeningPromptArea.isEnabled = enabled
        deepAnalysisPromptArea.isEnabled = enabled
    }
}