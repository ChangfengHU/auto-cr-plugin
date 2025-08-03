package com.vyibc.autocrplugin.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * AutoCR工具窗口面板 - V5.1版本
 * 三部分结构化展示：意图分析、风险分析、综合报告
 */
class AutoCRToolWindowPanel(private val project: Project) : JBPanel<JBPanel<*>>(BorderLayout()) {
    
    private val tabbedPane = JBTabbedPane()
    
    // 三个主要的分析面板
    private val intentAnalysisPanel = createTextPanel("意图分析结果将在这里显示...")
    private val riskAnalysisPanel = createTextPanel("风险分析结果将在这里显示...")
    private val comprehensiveReportPanel = createTextPanel("综合评审报告将在这里显示...")
    
    init {
        setupUI()
    }
    
    private fun setupUI() {
        // 添加三个标签页
        tabbedPane.addTab("意图分析", createScrollablePanel(intentAnalysisPanel))
        tabbedPane.addTab("风险分析", createScrollablePanel(riskAnalysisPanel))
        tabbedPane.addTab("综合报告", createScrollablePanel(comprehensiveReportPanel))
        
        add(tabbedPane, BorderLayout.CENTER)
    }
    
    private fun createTextPanel(defaultText: String): JTextArea {
        return JTextArea(defaultText).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
        }
    }
    
    private fun createScrollablePanel(component: JTextArea): JBScrollPane {
        return JBScrollPane(component)
    }
    
    fun updateIntentAnalysis(content: String) {
        intentAnalysisPanel.text = content
    }
    
    fun updateRiskAnalysis(content: String) {
        riskAnalysisPanel.text = content
    }
    
    fun updateComprehensiveReport(content: String) {
        comprehensiveReportPanel.text = content
    }
}