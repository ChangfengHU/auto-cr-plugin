package com.vyibc.autocrplugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.wm.ToolWindowManager
import com.vyibc.autocrplugin.service.GitChangeAnalyzer
import com.vyibc.autocrplugin.service.IntegratedAnalysisService
import com.vyibc.autocrplugin.ui.AutoCRToolWindowFactory
import com.vyibc.autocrplugin.ui.AutoCRToolWindowPanel
import kotlinx.coroutines.*

/**
 * 启动V5.1集成分析Action
 * 触发意图分析、风险分析并更新UI
 */
class StartIntegratedAnalysisAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 检查是否有待提交的变更
        val changeListManager = ChangeListManager.getInstance(project)
        val defaultChangeList = changeListManager.defaultChangeList
        
        if (defaultChangeList.changes.isEmpty()) {
            Messages.showInfoMessage(
                "没有待提交的变更，无法进行分析",
                "集成分析"
            )
            return
        }
        
        // 获取或打开ToolWindow
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow(AutoCRToolWindowFactory.TOOL_WINDOW_ID)
        
        if (toolWindow == null) {
            Messages.showErrorDialog(
                "无法找到AutoCR工具窗口",
                "集成分析错误"
            )
            return
        }
        
        // 激活ToolWindow
        toolWindow.activate(null)
        
        // 获取ToolWindow面板
        val content = toolWindow.contentManager.getContent(0)
        val toolWindowPanel = content?.component as? AutoCRToolWindowPanel
        
        if (toolWindowPanel == null) {
            Messages.showErrorDialog(
                "无法获取工具窗口面板",
                "集成分析错误"
            )
            return
        }
        
        // 分析代码变更
        val gitAnalyzer = GitChangeAnalyzer(project)
        val changes = gitAnalyzer.getCurrentChanges()
        
        if (changes.isEmpty()) {
            Messages.showInfoMessage(
                "没有检测到代码变更",
                "集成分析"
            )
            return
        }
        
        // 启动集成分析
        val analysisService = IntegratedAnalysisService(project, toolWindowPanel)
        
        // 在后台协程中执行分析
        GlobalScope.launch(Dispatchers.Main) {
            try {
                val result = analysisService.analyzeCodeChanges(changes)
                
                if (result.success) {
                    Messages.showInfoMessage(
                        "分析完成！检查工具窗口查看详细结果。\\n" +
                        "检测到 ${result.intentResults.size} 个方法的意图权重\\n" +
                        "检测到 ${result.riskResults.size} 个方法的风险权重",
                        "集成分析完成"
                    )
                } else {
                    Messages.showWarningDialog(
                        "分析过程中遇到问题：${result.message}",
                        "集成分析警告"
                    )
                }
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    "分析失败：${e.message}",
                    "集成分析错误"
                )
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        val changeListManager = ChangeListManager.getInstance(project)
        val hasChanges = changeListManager.defaultChangeList.changes.isNotEmpty()
        
        e.presentation.isEnabledAndVisible = hasChanges
        e.presentation.text = "开始集成分析"
        e.presentation.description = "分析当前变更的意图权重和风险权重"
    }
}