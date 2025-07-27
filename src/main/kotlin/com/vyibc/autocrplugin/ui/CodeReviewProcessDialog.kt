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
    private lateinit var commitButton: JButton
    private lateinit var cancelButton: JButton
    
    private var reviewResult: CodeReviewResult? = null
    private var canCommit = false

    init {
        title = "代码评估过程"
        setSize(900, 700)
        init()
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
        commitButton = JButton("提交代码")
        commitButton.isEnabled = false
        
        cancelButton = JButton("取消")
        
        val commitAction = object : AbstractAction("提交代码") {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                if (canCommit) {
                    close(OK_EXIT_CODE)
                }
            }
        }
        
        val cancelAction = object : AbstractAction("取消") {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                close(CANCEL_EXIT_CODE)
            }
        }
        
        return arrayOf(commitAction, cancelAction)
    }

    /**
     * 启用/禁用提交按钮
     */
    fun setCommitEnabled(enabled: Boolean) {
        SwingUtilities.invokeLater {
            commitButton.isEnabled = enabled
        }
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
