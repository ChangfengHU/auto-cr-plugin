package com.vyibc.autocrplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.*

/**
 * 简化的代码评估设置配置页面
 */
class SimpleCodeReviewConfigurable : Configurable {

    private var settingsPanel: JPanel? = null
    private lateinit var enabledCheckBox: JBCheckBox
    private lateinit var minimumScoreField: JBTextField
    private lateinit var promptTextArea: JBTextArea

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

        // AI提示词配置
        val promptPanel = JPanel()
        promptPanel.layout = BoxLayout(promptPanel, BoxLayout.Y_AXIS)
        promptPanel.border = BorderFactory.createTitledBorder("AI分析提示词配置")

        val promptLabel = JLabel("自定义AI分析提示词 (留空使用默认提示词):")
        promptPanel.add(promptLabel)

        promptTextArea = JBTextArea()
        promptTextArea.rows = 8
        promptTextArea.lineWrap = true
        promptTextArea.wrapStyleWord = true
        promptTextArea.toolTipText = "自定义AI分析的提示词，可以针对特定的代码规范和安全要求进行调整"

        val scrollPane = JBScrollPane(promptTextArea)
        scrollPane.preferredSize = Dimension(600, 200)
        promptPanel.add(scrollPane)

        // 重置为默认提示词按钮
        val resetPromptPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val resetPromptButton = JButton("重置为默认提示词")
        resetPromptButton.addActionListener {
            promptTextArea.text = getDefaultPrompt()
        }
        resetPromptPanel.add(resetPromptButton)
        promptPanel.add(resetPromptPanel)

        mainPanel.add(promptPanel)

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
            <li>4. 可选：自定义AI分析提示词</li>
            <li>5. 右键点击选择 'Code Review' 进行评估</li>
            <li>6. 或使用快捷键: Ctrl+Alt+C</li>
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

    /**
     * 获取默认的AI分析提示词
     */
    private fun getDefaultPrompt(): String {
        return """
请对以下代码变更进行专业的代码评估(Code Review)，重点关注生产环境安全性和最佳实践：

## 🔍 重点检查项目：

### 🚨 生产环境危险操作
- Redis危险命令：keys、flushdb、flushall、config等
- 数据库全表扫描：select * without where、count(*)等
- 阻塞操作：同步IO、长时间循环等
- 资源泄漏：未关闭连接、内存泄漏等

### 🔒 安全问题
- SQL注入风险
- XSS攻击风险
- 敏感信息泄露（密码、token等）
- 权限控制缺失
- 输入验证不足

### 📊 性能问题
- N+1查询问题
- 不必要的数据库查询
- 低效的算法实现
- 内存使用不当
- 缓存使用不当

### 🏗️ 代码质量
- 代码重复
- 方法过长或过于复杂
- 命名不规范
- 异常处理不当
- 日志记录不足

### 🧪 测试覆盖
- 缺少单元测试
- 边界条件未测试
- 异常情况未覆盖

## 📋 评估要求：
1. 给出0-100的综合评分
2. 标注风险等级：LOW/MEDIUM/HIGH/CRITICAL
3. 列出具体问题和改进建议
4. 特别标注生产环境风险项

请用中文回复，格式要求：
- 总体评分：XX/100
- 风险等级：XXX
- 发现问题：X个
- 详细分析：...
        """.trimIndent()
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
                minimumScoreField.text != settings.minimumScore.toString() ||
                promptTextArea.text != settings.customPrompt
    }

    override fun apply() {
        val settings = CodeReviewSettings.getInstance()
        settings.deepSeekEnabled = enabledCheckBox.isSelected && deepSeekEnabledCheckBox.isSelected
        settings.deepSeekApiKey = String(deepSeekApiKeyField.password)
        settings.tongyiEnabled = enabledCheckBox.isSelected && tongyiEnabledCheckBox.isSelected
        settings.tongyiApiKey = String(tongyiApiKeyField.password)
        settings.geminiEnabled = enabledCheckBox.isSelected && geminiEnabledCheckBox.isSelected
        settings.geminiApiKey = String(geminiApiKeyField.password)
        settings.customPrompt = promptTextArea.text

        try {
            settings.minimumScore = minimumScoreField.text.toInt().coerceIn(0, 100)
        } catch (e: NumberFormatException) {
            settings.minimumScore = 60
        }
    }

    override fun reset() {
        val settings = CodeReviewSettings.getInstance()
        enabledCheckBox.isSelected = settings.deepSeekEnabled || settings.tongyiEnabled || settings.geminiEnabled
        deepSeekEnabledCheckBox.isSelected = settings.deepSeekEnabled
        deepSeekApiKeyField.text = settings.deepSeekApiKey
        tongyiEnabledCheckBox.isSelected = settings.tongyiEnabled
        tongyiApiKeyField.text = settings.tongyiApiKey
        geminiEnabledCheckBox.isSelected = settings.geminiEnabled
        geminiApiKeyField.text = settings.geminiApiKey
        minimumScoreField.text = settings.minimumScore.toString()

        // 如果没有自定义提示词，显示默认提示词
        promptTextArea.text = if (settings.customPrompt.isNotEmpty()) {
            settings.customPrompt
        } else {
            getDefaultPrompt()
        }
    }

    override fun disposeUIResources() {
        settingsPanel = null
    }
}
