package com.vyibc.autocrplugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * AutoCR工具窗口工厂
 */
class AutoCRToolWindowFactory : ToolWindowFactory {
    
    companion object {
        const val TOOL_WINDOW_ID = "AutoCR"
    }
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val panel = AutoCRToolWindowPanel(project)
        val content = contentFactory.createContent(panel, "AI Code Review", false)
        toolWindow.contentManager.addContent(content)
    }
}