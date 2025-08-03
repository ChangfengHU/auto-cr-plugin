package com.vyibc.autocrplugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.vyibc.autocrplugin.ui.AutoCRToolWindowFactory

/**
 * AI代码评审Action - V5.1版本
 * 支持双流分析和多AI提供商
 */
class AutoCRAction : AnAction("AutoCR - AI Code Review") {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 检查项目是否有效
        if (project.isDisposed) {
            return
        }
        
        // 打开AutoCR工具窗口
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow(AutoCRToolWindowFactory.TOOL_WINDOW_ID)
        
        if (toolWindow != null) {
            toolWindow.show()
        } else {
            Messages.showInfoMessage(
                project,
                "AutoCR Tool Window is not available. Please restart the IDE.",
                "AutoCR - AI Code Review"
            )
        }
    }
    
    override fun update(e: AnActionEvent) {
        // 仅在有项目打开时启用
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null && !project.isDisposed
    }
}