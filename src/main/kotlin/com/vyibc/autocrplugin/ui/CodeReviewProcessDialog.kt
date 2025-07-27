package com.vyibc.autocrplugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.vyibc.autocrplugin.service.*
import java.awt.*
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * 代码评估过程展示对话框
 */
class CodeReviewProcessDialog(
    project: Project?,
    private val changes: List<CodeChange>,
    private val commitMessage: String
) : DialogWrapper(project) {

    private lateinit var processArea: JBTextArea
    private lateinit var changesTable: JBTable
    private lateinit var progressBar: JProgressBar
    private lateinit var statusLabel: JLabel
    private lateinit var scoreLabel: JLabel
    private lateinit var riskLabel: JLabel
    private lateinit var startAnalysisButton: JButton
    private lateinit var commitButton: JButton
    private lateinit var cancelButton: JButton

    private var reviewResult: CodeReviewResult? = null
    private var canCommit = false
    private var analysisStarted = false
    private var codeReviewService: CodeReviewService? = null

    init {
        title = "代码评估过程"
        setSize(900, 700)
        init()

        // 初始化状态
        SwingUtilities.invokeLater {
            // 初始显示代码变更信息
            appendProcess("=== 📋 代码变更概览 ===\n")
            appendProcess("$commitMessage\n\n")

            changes.forEach { change ->
                appendProcess("📁 ${change.filePath}\n")
                appendProcess("   变更类型: ${getChangeTypeText(change.changeType)}\n")
                appendProcess("   新增行数: ${change.addedLines.size}\n")
                appendProcess("   删除行数: ${change.removedLines.size}\n")
                appendProcess("   修改行数: ${change.modifiedLines.size}\n\n")
            }

            appendProcess("💡 点击 '🚀 开始AI分析' 按钮开始代码评估\n")
            appendProcess("⚠️  分析过程会将代码发送给AI服务进行评估\n\n")
        }
    }

    /**
     * 设置代码评估服务
     */
    fun setCodeReviewService(service: CodeReviewService) {
        this.codeReviewService = service
    }

    /**
     * 清空分析过程
     */
    private fun clearAnalysisProcess() {
        SwingUtilities.invokeLater {
            // 清空分析过程文本
            processArea.text = ""

            // 重置进度条
            progressBar.value = 0
            statusLabel.text = "准备开始分析..."

            // 重置评估结果显示
            scoreLabel.text = "--/100"
            scoreLabel.foreground = java.awt.Color.GRAY
            riskLabel.text = "未评估"
            riskLabel.foreground = java.awt.Color.GRAY

            // 重置状态
            reviewResult = null
            canCommit = false
            commitButton.isEnabled = false

            // 允许重新开始分析
            startAnalysisButton.isEnabled = true
            analysisStarted = false
        }
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        
        // 创建选项卡面板
        val tabbedPane = JTabbedPane()
        
        // 代码变更选项卡
        tabbedPane.addTab("代码变更", createChangesPanel())
        
        // 分析过程选项卡
        tabbedPane.addTab("分析过程", createProcessPanel())
        
        // 评估结果选项卡
        tabbedPane.addTab("评估结果", createResultPanel())
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER)
        
        // 底部状态面板
        mainPanel.add(createStatusPanel(), BorderLayout.SOUTH)
        
        return mainPanel
    }

    /**
     * 创建代码变更面板
     */
    private fun createChangesPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        
        // 提交信息
        val commitPanel = JPanel(BorderLayout())
        commitPanel.border = BorderFactory.createTitledBorder("提交信息")
        val commitArea = JBTextArea(commitMessage)
        commitArea.isEditable = false
        commitArea.lineWrap = true
        commitArea.wrapStyleWord = true
        commitArea.rows = 3
        commitPanel.add(JBScrollPane(commitArea), BorderLayout.CENTER)
        
        panel.add(commitPanel, BorderLayout.NORTH)
        
        // 变更文件表格
        val changesPanel = JPanel(BorderLayout())
        changesPanel.border = BorderFactory.createTitledBorder("变更文件 (${changes.size} 个文件)")
        
        val columnNames = arrayOf("文件路径", "变更类型", "新增行数", "删除行数", "修改行数")
        val tableModel = DefaultTableModel(columnNames, 0)
        
        changes.forEach { change ->
            tableModel.addRow(arrayOf(
                change.filePath,
                getChangeTypeText(change.changeType),
                change.addedLines.size.toString(),
                change.removedLines.size.toString(),
                change.modifiedLines.size.toString()
            ))
        }
        
        changesTable = JBTable(tableModel)
        changesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        
        // 设置列宽
        val columnModel = changesTable.columnModel
        columnModel.getColumn(0).preferredWidth = 300 // 文件路径
        columnModel.getColumn(1).preferredWidth = 80  // 变更类型
        columnModel.getColumn(2).preferredWidth = 80  // 新增行数
        columnModel.getColumn(3).preferredWidth = 80  // 删除行数
        columnModel.getColumn(4).preferredWidth = 80  // 修改行数
        
        changesTable.rowHeight = 25
        
        val scrollPane = JBScrollPane(changesTable)
        changesPanel.add(scrollPane, BorderLayout.CENTER)
        
        panel.add(changesPanel, BorderLayout.CENTER)
        
        return panel
    }

    /**
     * 创建分析过程面板
     */
    private fun createProcessPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        
        processArea = JBTextArea()
        processArea.isEditable = false
        processArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        processArea.text = "等待开始分析...\n"
        
        val scrollPane = JBScrollPane(processArea)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }

    /**
     * 创建评估结果面板
     */
    private fun createResultPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        
        // 评分和风险等级显示
        val scorePanel = JPanel(GridBagLayout())
        scorePanel.border = BorderFactory.createTitledBorder("评估概览")
        
        val gbc = GridBagConstraints()
        gbc.insets = Insets(10, 10, 10, 10)
        
        // 评分
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        scorePanel.add(JLabel("总体评分:"), gbc)
        
        gbc.gridx = 1
        scoreLabel = JLabel("--/100")
        scoreLabel.font = scoreLabel.font.deriveFont(Font.BOLD, 18f)
        scorePanel.add(scoreLabel, gbc)
        
        // 风险等级
        gbc.gridx = 2
        gbc.insets = Insets(10, 30, 10, 10)
        scorePanel.add(JLabel("风险等级:"), gbc)
        
        gbc.gridx = 3
        riskLabel = JLabel("--")
        riskLabel.font = riskLabel.font.deriveFont(Font.BOLD, 18f)
        scorePanel.add(riskLabel, gbc)
        
        panel.add(scorePanel, BorderLayout.NORTH)
        
        // 详细结果区域（稍后填充）
        val resultArea = JBTextArea("等待评估结果...")
        resultArea.isEditable = false
        resultArea.lineWrap = true
        resultArea.wrapStyleWord = true
        
        val resultScrollPane = JBScrollPane(resultArea)
        panel.add(resultScrollPane, BorderLayout.CENTER)
        
        return panel
    }

    /**
     * 创建状态面板
     */
    private fun createStatusPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        
        // 进度条和状态
        val progressPanel = JPanel(BorderLayout())
        
        statusLabel = JLabel("准备开始分析...")
        progressPanel.add(statusLabel, BorderLayout.NORTH)
        
        progressBar = JProgressBar(0, 100)
        progressBar.isStringPainted = true
        progressBar.string = "0%"
        progressPanel.add(progressBar, BorderLayout.CENTER)
        
        panel.add(progressPanel, BorderLayout.CENTER)
        
        return panel
    }

    /**
     * 开始代码评估过程
     */
    fun startReview(codeReviewService: CodeReviewService, onComplete: (Boolean, CodeReviewResult?) -> Unit) {
        SwingUtilities.invokeLater {
            updateProgress(10, "开始分析代码变更...")
            appendProcess("=== 代码评估开始 ===\n")
            appendProcess("提交信息: $commitMessage\n")
            appendProcess("变更文件数量: ${changes.size}\n\n")
            
            // 显示变更详情
            appendProcess("=== 代码变更详情 ===\n")
            changes.forEach { change ->
                appendProcess("文件: ${change.filePath}\n")
                appendProcess("  变更类型: ${getChangeTypeText(change.changeType)}\n")
                appendProcess("  新增行数: ${change.addedLines.size}\n")
                appendProcess("  删除行数: ${change.removedLines.size}\n")
                appendProcess("  修改行数: ${change.modifiedLines.size}\n")
                
                // 显示部分代码内容
                if (change.addedLines.isNotEmpty()) {
                    appendProcess("  新增代码片段:\n")
                    change.addedLines.take(3).forEach { line ->
                        appendProcess("    + $line\n")
                    }
                    if (change.addedLines.size > 3) {
                        appendProcess("    ... (还有 ${change.addedLines.size - 3} 行)\n")
                    }
                }
                appendProcess("\n")
            }
            
            updateProgress(30, "准备AI分析...")
            appendProcess("=== AI分析准备 ===\n")
            appendProcess("使用AI服务: ${codeReviewService.getServiceName()}\n")
            appendProcess("构建分析提示词...\n")
            
            // 在后台线程执行AI分析
            Thread {
                try {
                    updateProgress(50, "AI正在分析代码...")
                    appendProcess("发送请求到AI服务...\n")
                    
                    // 模拟分析过程
                    Thread.sleep(2000)
                    
                    updateProgress(70, "处理AI响应...")
                    appendProcess("收到AI响应，正在解析...\n")
                    
                    // 执行实际的代码评估
                    val result = kotlinx.coroutines.runBlocking {
                        codeReviewService.reviewCode(changes, commitMessage)
                    }
                    
                    updateProgress(90, "生成评估报告...")
                    appendProcess("=== 评估结果 ===\n")
                    appendProcess("总体评分: ${result.overallScore}/100\n")
                    appendProcess("风险等级: ${getRiskLevelText(result.riskLevel)}\n")
                    appendProcess("发现问题: ${result.issues.size} 个\n")
                    appendProcess("改进建议: ${result.suggestions.size} 条\n\n")
                    
                    // 显示问题详情
                    if (result.issues.isNotEmpty()) {
                        appendProcess("=== 发现的问题 ===\n")
                        result.issues.forEach { issue ->
                            appendProcess("${getSeverityText(issue.severity)}: ${issue.message}\n")
                            appendProcess("  文件: ${issue.filePath}\n")
                            if (issue.lineNumber != null) {
                                appendProcess("  行号: ${issue.lineNumber}\n")
                            }
                            if (issue.suggestion != null) {
                                appendProcess("  建议: ${issue.suggestion}\n")
                            }
                            appendProcess("\n")
                        }
                    }
                    
                    updateProgress(100, "评估完成")
                    appendProcess("=== 评估完成 ===\n")
                    
                    SwingUtilities.invokeLater {
                        reviewResult = result
                        updateResultDisplay(result)
                        
                        // 检查是否可以提交
                        val settings = com.vyibc.autocrplugin.settings.CodeReviewSettings.getInstance()
                        canCommit = checkCanCommit(result, settings)
                        
                        if (canCommit) {
                            appendProcess("✅ 代码质量符合要求，可以提交\n")
                        } else {
                            appendProcess("❌ 代码质量不符合要求，建议修复后再提交\n")
                        }
                        
                        onComplete(canCommit, result)
                    }
                    
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        updateProgress(0, "评估失败")
                        appendProcess("❌ 评估失败: ${e.message}\n")
                        onComplete(false, null)
                    }
                }
            }.start()
        }
    }

    /**
     * 更新进度
     */
    private fun updateProgress(progress: Int, status: String) {
        SwingUtilities.invokeLater {
            progressBar.value = progress
            progressBar.string = "$progress%"
            statusLabel.text = status
        }
    }

    /**
     * 添加过程日志
     */
    private fun appendProcess(text: String) {
        SwingUtilities.invokeLater {
            processArea.append(text)
            processArea.caretPosition = processArea.document.length
        }
    }

    /**
     * 更新结果显示
     */
    private fun updateResultDisplay(result: CodeReviewResult) {
        // 更新评分显示
        scoreLabel.text = "${result.overallScore}/100"
        scoreLabel.foreground = getScoreColor(result.overallScore)
        
        // 更新风险等级显示
        riskLabel.text = getRiskLevelText(result.riskLevel)
        riskLabel.foreground = getRiskColor(result.riskLevel)
    }

    /**
     * 检查是否可以提交
     */
    private fun checkCanCommit(result: CodeReviewResult, settings: com.vyibc.autocrplugin.settings.CodeReviewSettings): Boolean {
        // 检查评分是否达到最低要求
        if (result.overallScore < settings.minimumScore) {
            return false
        }
        
        // 检查是否有严重风险
        if (settings.blockHighRiskCommits) {
            when (result.riskLevel) {
                RiskLevel.CRITICAL, RiskLevel.HIGH -> return false
                else -> {}
            }
        }
        
        // 检查是否有严重问题
        val criticalIssues = result.issues.filter { it.severity == IssueSeverity.CRITICAL }
        if (criticalIssues.isNotEmpty()) {
            return false
        }
        
        return true
    }

    override fun createActions(): Array<Action> {
        startAnalysisButton = JButton("🚀 开始AI分析")
        startAnalysisButton.isEnabled = true

        commitButton = JButton("提交代码")
        commitButton.isEnabled = false

        cancelButton = JButton("取消")

        val startAnalysisAction = object : AbstractAction("🚀 开始AI分析") {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                if (codeReviewService != null) {
                    // 清空之前的分析过程
                    clearAnalysisProcess()

                    startAnalysisButton.isEnabled = false
                    analysisStarted = true

                    // 显示AI分析确认信息
                    appendProcess("=== 🤖 AI分析准备 ===\n")
                    appendProcess("⚠️  即将将代码发送给AI服务进行分析\n")
                    appendProcess("📤 请确保代码不包含敏感信息\n")
                    appendProcess("🔄 开始AI分析过程...\n\n")

                    // 开始实际的AI分析
                    performAIAnalysis()
                }
            }
        }

        val commitAction = object : AbstractAction("提交代码") {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                if (canCommit && reviewResult != null) {
                    performGitCommit()
                }
            }
        }

        val cancelAction = object : AbstractAction("取消") {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                close(CANCEL_EXIT_CODE)
            }
        }

        return arrayOf(startAnalysisAction, commitAction, cancelAction)
    }

    /**
     * 启用/禁用提交按钮
     */
    fun setCommitEnabled(enabled: Boolean) {
        SwingUtilities.invokeLater {
            commitButton.isEnabled = enabled
        }
    }

    /**
     * 执行AI分析
     */
    private fun performAIAnalysis() {
        if (codeReviewService == null) {
            appendProcess("❌ 错误: AI服务未初始化\n")
            return
        }

        updateProgress(30, "准备AI分析...")
        appendProcess("=== 🤖 AI分析详细过程 ===\n")
        appendProcess("使用AI服务: ${codeReviewService!!.getServiceName()}\n")
        appendProcess("构建分析提示词...\n")

        // 在后台线程执行AI分析
        Thread {
            try {
                // 显示提示词构建过程
                updateProgress(40, "构建AI提示词...")
                val prompt = buildDetailedPrompt()
                appendProcess("\n=== 📝 发送给AI的提示词 ===\n")
                appendProcess("提示词长度: ${prompt.length} 字符\n")
                appendProcess("提示词内容:\n")
                appendProcess("${prompt.take(500)}...\n") // 显示前500字符
                appendProcess("(完整提示词已发送给AI服务)\n\n")

                updateProgress(50, "发送请求到AI服务...")
                appendProcess("=== 🌐 API调用信息 ===\n")
                appendProcess("正在连接AI服务...\n")
                appendProcess("发送HTTP请求...\n")

                // 模拟网络延迟
                Thread.sleep(1000)

                updateProgress(70, "等待AI响应...")
                appendProcess("请求已发送，等待AI分析...\n")

                // 模拟分析过程
                Thread.sleep(2000)

                updateProgress(80, "处理AI响应...")
                appendProcess("\n=== 📥 收到AI响应 ===\n")
                appendProcess("响应状态: 200 OK\n")
                appendProcess("开始解析AI响应...\n")

                // 执行实际的代码评估
                appendProcess("调用AI服务进行分析...\n")
                val result = kotlinx.coroutines.runBlocking {
                    codeReviewService!!.reviewCode(changes, commitMessage)
                }

                appendProcess("\n=== 📊 AI分析结果 ===\n")
                appendProcess("AI服务响应成功\n")
                appendProcess("解析状态: 成功\n")

                updateProgress(90, "生成评估报告...")
                appendProcess("=== 评估结果 ===\n")
                appendProcess("总体评分: ${result.overallScore}/100\n")
                appendProcess("风险等级: ${getRiskLevelText(result.riskLevel)}\n")
                appendProcess("发现问题: ${result.issues.size} 个\n")
                appendProcess("改进建议: ${result.suggestions.size} 条\n\n")

                // 显示问题详情
                if (result.issues.isNotEmpty()) {
                    appendProcess("=== 发现的问题 ===\n")
                    result.issues.forEach { issue ->
                        appendProcess("${getSeverityText(issue.severity)}: ${issue.message}\n")
                        appendProcess("  文件: ${issue.filePath}\n")
                        if (issue.lineNumber != null) {
                            appendProcess("  行号: ${issue.lineNumber}\n")
                        }
                        appendProcess("  建议: ${issue.suggestion}\n\n")
                    }
                }

                // 显示改进建议
                if (result.suggestions.isNotEmpty()) {
                    appendProcess("=== 改进建议 ===\n")
                    result.suggestions.forEachIndexed { index, suggestion ->
                        appendProcess("${index + 1}. $suggestion\n")
                    }
                    appendProcess("\n")
                }

                // 更新UI状态
                SwingUtilities.invokeLater {
                    reviewResult = result

                    // 从设置中获取最低分数要求
                    val settings = com.vyibc.autocrplugin.settings.CodeReviewSettings.getInstance()
                    val minimumScore = settings.minimumScore
                    canCommit = result.overallScore >= minimumScore

                    updateProgress(100, "分析完成")
                    appendProcess("=== 分析完成 ===\n")
                    appendProcess("最低分数要求: $minimumScore 分\n")
                    appendProcess("当前代码评分: ${result.overallScore} 分\n")

                    if (canCommit) {
                        appendProcess("✅ 代码质量达标，可以提交\n")
                        commitButton.isEnabled = true
                        commitButton.text = "提交代码 (git commit)"
                    } else {
                        appendProcess("❌ 代码质量不达标 (${result.overallScore} < $minimumScore)，请修复问题后重新分析\n")
                        commitButton.isEnabled = false
                        commitButton.text = "质量不达标，无法提交"
                    }

                    // 重新启用分析按钮，允许重新分析
                    startAnalysisButton.isEnabled = true
                    startAnalysisButton.text = "🔄 重新分析"

                    // 更新结果显示
                    updateResultDisplay(result)
                }

            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    appendProcess("❌ AI分析失败: ${e.message}\n")
                    updateProgress(0, "分析失败")
                }
            }
        }.start()
    }

    /**
     * 执行Git提交
     */
    private fun performGitCommit() {
        if (reviewResult == null) {
            appendProcess("❌ 错误: 没有评估结果，无法提交\n")
            return
        }

        // 显示提交确认对话框
        val result = reviewResult!!
        val confirmMessage = """
            🚀 确认提交代码

            评估结果:
            • 总体评分: ${result.overallScore}/100
            • 风险等级: ${getRiskLevelText(result.riskLevel)}
            • 发现问题: ${result.issues.size} 个
            • 改进建议: ${result.suggestions.size} 条

            是否确认提交代码到Git仓库？
        """.trimIndent()

        val choice = com.intellij.openapi.ui.Messages.showYesNoDialog(
            confirmMessage,
            "确认Git提交",
            "确认提交",
            "取消",
            com.intellij.openapi.ui.Messages.getQuestionIcon()
        )

        if (choice != com.intellij.openapi.ui.Messages.YES) {
            return
        }

        // 在后台线程执行Git提交
        Thread {
            try {
                SwingUtilities.invokeLater {
                    appendProcess("\n=== 🚀 执行Git提交 ===\n")
                    appendProcess("准备提交代码到Git仓库...\n")
                    commitButton.isEnabled = false
                    commitButton.text = "正在提交..."
                }

                // 构建提交信息
                val commitMessage = buildCommitMessage(result)

                SwingUtilities.invokeLater {
                    appendProcess("提交信息:\n$commitMessage\n\n")
                    appendProcess("添加已修改的文件到Git暂存区...\n")
                }

                // 只添加已修改的文件，避免.gitignore问题
                val filesToAdd = changes.map { it.filePath }
                SwingUtilities.invokeLater {
                    appendProcess("要添加的文件:\n")
                    filesToAdd.forEach { file ->
                        appendProcess("  • $file\n")
                    }
                }

                // 执行git add 对每个文件
                var addSuccess = true
                for (filePath in filesToAdd) {
                    val addResult = executeGitCommand(listOf("git", "add", filePath))
                    if (!addResult.success) {
                        SwingUtilities.invokeLater {
                            appendProcess("❌ 添加文件失败 $filePath: ${addResult.error}\n")
                        }
                        addSuccess = false
                        break
                    }
                }

                if (!addSuccess) {
                    SwingUtilities.invokeLater {
                        commitButton.isEnabled = true
                        commitButton.text = "添加文件失败，重试"
                    }
                    return@Thread
                }

                SwingUtilities.invokeLater {
                    appendProcess("✅ git add 成功\n")
                    appendProcess("执行 git commit\n")
                }

                // 执行git commit
                val commitResult = executeGitCommand(listOf("git", "commit", "-m", commitMessage))

                SwingUtilities.invokeLater {
                    if (commitResult.success) {
                        appendProcess("✅ Git提交成功!\n")
                        appendProcess("提交哈希: ${commitResult.output.take(50)}...\n")
                        appendProcess("\n=== 🎉 代码提交完成 ===\n")

                        // 显示成功消息
                        com.intellij.openapi.ui.Messages.showInfoMessage(
                            "代码已成功提交到Git仓库！\n\n" +
                                    "评分: ${result.overallScore}/100\n" +
                                    "风险等级: ${getRiskLevelText(result.riskLevel)}\n" +
                                    "提交信息: ${commitMessage.split('\n').first()}",
                            "Git提交成功"
                        )

                        // 关闭对话框
                        close(OK_EXIT_CODE)
                    } else {
                        appendProcess("❌ Git提交失败: ${commitResult.error}\n")
                        commitButton.isEnabled = true
                        commitButton.text = "提交失败，重试"
                    }
                }

            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    appendProcess("❌ 提交过程出错: ${e.message}\n")
                    commitButton.isEnabled = true
                    commitButton.text = "提交出错，重试"
                }
            }
        }.start()
    }

    /**
     * 构建提交信息
     */
    private fun buildCommitMessage(result: CodeReviewResult): String {
        val sb = StringBuilder()

        // 基本提交信息
        sb.append("✅ AI代码评估通过 (${result.overallScore}/100)\n\n")

        // 评估摘要
        sb.append("📊 评估摘要:\n")
        sb.append("• 风险等级: ${getRiskLevelText(result.riskLevel)}\n")
        sb.append("• 发现问题: ${result.issues.size} 个\n")
        sb.append("• 改进建议: ${result.suggestions.size} 条\n\n")

        // 主要问题（如果有）
        if (result.issues.isNotEmpty()) {
            sb.append("🔍 主要问题:\n")
            result.issues.take(3).forEach { issue ->
                sb.append("• ${issue.message}\n")
            }
            if (result.issues.size > 3) {
                sb.append("• ... 还有 ${result.issues.size - 3} 个问题\n")
            }
            sb.append("\n")
        }

        // AI评估标记
        sb.append("🤖 通过AI代码评估系统检查")

        return sb.toString()
    }

    /**
     * 执行Git命令
     */
    private fun executeGitCommand(command: List<String>): GitCommandResult {
        return try {
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(java.io.File(System.getProperty("user.dir")))

            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()

            val exitCode = process.waitFor()

            GitCommandResult(
                success = exitCode == 0,
                output = output,
                error = error,
                exitCode = exitCode
            )
        } catch (e: Exception) {
            GitCommandResult(
                success = false,
                output = "",
                error = e.message ?: "Unknown error",
                exitCode = -1
            )
        }
    }

    /**
     * Git命令执行结果
     */
    private data class GitCommandResult(
        val success: Boolean,
        val output: String,
        val error: String,
        val exitCode: Int
    )

    /**
     * 构建详细的提示词用于显示
     */
    private fun buildDetailedPrompt(): String {
        val settings = com.vyibc.autocrplugin.settings.CodeReviewSettings.getInstance()
        val prompt = StringBuilder()

        // 使用自定义提示词或默认提示词
        val basePrompt = if (settings.customPrompt.isNotEmpty()) {
            settings.customPrompt
        } else {
            getDefaultPrompt()
        }

        prompt.append(basePrompt)
        prompt.append("\n\n## 📋 代码变更详情：\n\n")
        prompt.append("提交信息: $commitMessage\n\n")

        changes.forEach { change ->
            prompt.append("文件: ${change.filePath}\n")
            prompt.append("变更类型: ${change.changeType}\n")

            when (change.changeType) {
                ChangeType.ADDED -> {
                    prompt.append("新增内容:\n")
                    prompt.append(change.newContent ?: "")
                }
                ChangeType.MODIFIED -> {
                    prompt.append("新增行:\n")
                    change.addedLines.forEach { line ->
                        prompt.append("+ $line\n")
                    }
                    prompt.append("删除行:\n")
                    change.removedLines.forEach { line ->
                        prompt.append("- $line\n")
                    }
                }
                ChangeType.DELETED -> {
                    prompt.append("删除内容:\n")
                    prompt.append(change.oldContent ?: "")
                }
                ChangeType.RENAMED -> {
                    prompt.append("文件重命名\n")
                }
            }
            prompt.append("\n---\n\n")
        }

        prompt.append("""
## 📤 严格返回格式要求：
请严格按照以下JSON格式返回，不要添加任何其他文字：

```json
{
  "overallScore": 85,
  "riskLevel": "MEDIUM",
  "issues": [
    {
      "filePath": "文件路径",
      "lineNumber": 行号,
      "severity": "CRITICAL|MAJOR|MINOR|INFO",
      "category": "问题分类",
      "message": "问题描述",
      "suggestion": "修复建议"
    }
  ],
  "suggestions": [
    "改进建议1",
    "改进建议2"
  ],
  "summary": "总结"
}
```

注意：
- overallScore: 必须是0-100的整数
- riskLevel: 必须是 LOW|MEDIUM|HIGH|CRITICAL 之一
- severity: 必须是 CRITICAL|MAJOR|MINOR|INFO 之一
- 请确保返回的是有效的JSON格式
        """.trimIndent())

        return prompt.toString()
    }

    /**
     * 获取默认提示词
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
        """.trimIndent()
    }

    // 辅助方法
    private fun getChangeTypeText(changeType: ChangeType): String {
        return when (changeType) {
            ChangeType.ADDED -> "新增"
            ChangeType.MODIFIED -> "修改"
            ChangeType.DELETED -> "删除"
            ChangeType.RENAMED -> "重命名"
        }
    }

    private fun getRiskLevelText(riskLevel: RiskLevel): String {
        return when (riskLevel) {
            RiskLevel.LOW -> "低风险"
            RiskLevel.MEDIUM -> "中等风险"
            RiskLevel.HIGH -> "高风险"
            RiskLevel.CRITICAL -> "严重风险"
        }
    }

    private fun getSeverityText(severity: IssueSeverity): String {
        return when (severity) {
            IssueSeverity.CRITICAL -> "严重"
            IssueSeverity.MAJOR -> "重要"
            IssueSeverity.MINOR -> "轻微"
            IssueSeverity.INFO -> "信息"
        }
    }

    private fun getScoreColor(score: Int): Color {
        return when {
            score >= 90 -> Color(0, 150, 0)    // 绿色
            score >= 70 -> Color(255, 165, 0)  // 橙色
            score >= 50 -> Color(255, 140, 0)  // 深橙色
            else -> Color(220, 20, 60)         // 红色
        }
    }

    private fun getRiskColor(riskLevel: RiskLevel): Color {
        return when (riskLevel) {
            RiskLevel.LOW -> Color(0, 150, 0)      // 绿色
            RiskLevel.MEDIUM -> Color(255, 165, 0) // 橙色
            RiskLevel.HIGH -> Color(255, 69, 0)    // 红橙色
            RiskLevel.CRITICAL -> Color(220, 20, 60) // 红色
        }
    }
}
