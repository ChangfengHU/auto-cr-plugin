package com.vyibc.autocrplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.*

/**
 * 简化的代码评估设置配置页面
 */
class SimpleCodeReviewConfigurable : Configurable {

    private var settingsPanel: JPanel? = null
    private lateinit var enabledCheckBox: JBCheckBox
    private lateinit var minimumScoreField: JBTextField

    // AI服务配置
    private lateinit var deepSeekEnabledCheckBox: JBCheckBox
    private lateinit var deepSeekApiKeyField: JBPasswordField
    private lateinit var tongyiEnabledCheckBox: JBCheckBox
    private lateinit var tongyiApiKeyField: JBPasswordField
    private lateinit var geminiEnabledCheckBox: JBCheckBox
    private lateinit var geminiApiKeyField: JBPasswordField

    override fun getDisplayName(): String = "Auto Code Review"

    override fun createComponent(): JComponent? {
        if (settingsPanel == null) {
            settingsPanel = createSettingsPanel()
        }
        return settingsPanel
    }

    private fun createSettingsPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        // 标题
        val titleLabel = JLabel("自动代码评估设置")
        titleLabel.font = titleLabel.font.deriveFont(titleLabel.font.style or java.awt.Font.BOLD, 16f)
        panel.add(titleLabel, BorderLayout.NORTH)

        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        mainPanel.border = BorderFactory.createEmptyBorder(20, 20, 20, 20)

        // 启用功能
        enabledCheckBox = JBCheckBox("启用自动代码评估")
        enabledCheckBox.isSelected = true
        mainPanel.add(enabledCheckBox)

        mainPanel.add(Box.createVerticalStrut(20))

        // AI服务配置
        val aiPanel = JPanel()
        aiPanel.layout = BoxLayout(aiPanel, BoxLayout.Y_AXIS)
        aiPanel.border = BorderFactory.createTitledBorder("AI服务配置 (选择一个)")

        // DeepSeek
        deepSeekEnabledCheckBox = JBCheckBox("DeepSeek (推荐)")
        deepSeekEnabledCheckBox.isSelected = true
        aiPanel.add(deepSeekEnabledCheckBox)

        val deepSeekPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        deepSeekPanel.add(JLabel("API Key:"))
        deepSeekApiKeyField = JBPasswordField()
        deepSeekApiKeyField.preferredSize = Dimension(300, 25)
        deepSeekPanel.add(deepSeekApiKeyField)
        deepSeekPanel.add(JLabel("<html><a href='https://platform.deepseek.com/api_keys'>获取</a></html>"))
        aiPanel.add(deepSeekPanel)

        aiPanel.add(Box.createVerticalStrut(10))

        // 通义千问
        tongyiEnabledCheckBox = JBCheckBox("阿里通义千问")
        aiPanel.add(tongyiEnabledCheckBox)

        val tongyiPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        tongyiPanel.add(JLabel("API Key:"))
        tongyiApiKeyField = JBPasswordField()
        tongyiApiKeyField.preferredSize = Dimension(300, 25)
        tongyiPanel.add(tongyiApiKeyField)
        tongyiPanel.add(JLabel("<html><a href='https://dashscope.aliyun.com/'>获取</a></html>"))
        aiPanel.add(tongyiPanel)

        aiPanel.add(Box.createVerticalStrut(10))

        // Gemini
        geminiEnabledCheckBox = JBCheckBox("Google Gemini")
        aiPanel.add(geminiEnabledCheckBox)

        val geminiPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        geminiPanel.add(JLabel("API Key:"))
        geminiApiKeyField = JBPasswordField()
        geminiApiKeyField.preferredSize = Dimension(300, 25)
        geminiPanel.add(geminiApiKeyField)
        geminiPanel.add(JLabel("<html><a href='https://makersuite.google.com/app/apikey'>获取</a></html>"))
        aiPanel.add(geminiPanel)

        mainPanel.add(aiPanel)

        mainPanel.add(Box.createVerticalStrut(20))

        // 评估设置
        val settingsPanel = JPanel()
        settingsPanel.layout = BoxLayout(settingsPanel, BoxLayout.Y_AXIS)
        settingsPanel.border = BorderFactory.createTitledBorder("评估设置")

        val scorePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        scorePanel.add(JLabel("最低评分要求 (0-100):"))
        minimumScoreField = JBTextField("60")
        minimumScoreField.preferredSize = Dimension(100, 25)
        minimumScoreField.toolTipText = "低于此评分的代码将被阻止提交"
        scorePanel.add(minimumScoreField)
        settingsPanel.add(scorePanel)

        mainPanel.add(settingsPanel)

        mainPanel.add(Box.createVerticalStrut(20))

        // 使用说明
        val infoPanel = JPanel(BorderLayout())
        val infoText = """
            <html>
            <h3>使用说明:</h3>
            <ul>
            <li>1. 启用自动代码评估功能</li>
            <li>2. 选择并配置一个AI服务的API Key</li>
            <li>3. 设置最低评分要求</li>
            <li>4. 右键点击选择 'Code Review' 进行评估</li>
            <li>5. 或使用快捷键: Ctrl+Alt+C</li>
            </ul>
            <p><b>注意:</b> 代码评估需要将代码发送给AI服务进行分析</p>
            </html>
        """.trimIndent()

        val infoLabel = JLabel(infoText)
        infoPanel.add(infoLabel, BorderLayout.CENTER)
        mainPanel.add(infoPanel)

        panel.add(mainPanel, BorderLayout.CENTER)

        return panel
    }

    override fun isModified(): Boolean {
        val settings = CodeReviewSettings.getInstance()
        return enabledCheckBox.isSelected != settings.deepSeekEnabled ||
                deepSeekEnabledCheckBox.isSelected != settings.deepSeekEnabled ||
                String(deepSeekApiKeyField.password) != settings.deepSeekApiKey ||
                tongyiEnabledCheckBox.isSelected != settings.tongyiEnabled ||
                String(tongyiApiKeyField.password) != settings.tongyiApiKey ||
                geminiEnabledCheckBox.isSelected != settings.geminiEnabled ||
                String(geminiApiKeyField.password) != settings.geminiApiKey ||
                minimumScoreField.text != settings.minimumScore.toString()
    }

    override fun apply() {
        val settings = CodeReviewSettings.getInstance()
        settings.deepSeekEnabled = enabledCheckBox.isSelected
        settings.deepSeekApiKey = String(apiKeyField.password)
        
        try {
            settings.minimumScore = minimumScoreField.text.toInt().coerceIn(0, 100)
        } catch (e: NumberFormatException) {
            settings.minimumScore = 60
        }
    }

    override fun reset() {
        val settings = CodeReviewSettings.getInstance()
        enabledCheckBox.isSelected = settings.deepSeekEnabled
        apiKeyField.text = settings.deepSeekApiKey
        minimumScoreField.text = settings.minimumScore.toString()
    }

    override fun disposeUIResources() {
        settingsPanel = null
    }
}
