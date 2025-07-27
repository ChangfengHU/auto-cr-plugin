package com.vyibc.autocrplugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.vyibc.autocrplugin.service.GitChangeAnalyzer
import com.vyibc.autocrplugin.service.impl.AICodeReviewService
import com.vyibc.autocrplugin.ui.CodeReviewProcessDialog

/**
 * 右键菜单快速代码评估Action
 */
class QuickCodeReviewMenuAction : AnAction("Code Review") {

    private val codeReviewService = AICodeReviewService()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 检查是否有代码变更
        val changeListManager = ChangeListManager.getInstance(project)
        val defaultChangeList = changeListManager.defaultChangeList
        
        if (defaultChangeList.changes.isEmpty()) {
            // 如果没有暂存的变更，使用测试数据
            showTestReview(project)
            return
        }

        // 分析代码变更
        val gitAnalyzer = GitChangeAnalyzer(project)
        val changes = gitAnalyzer.getCurrentChanges()
        
        if (changes.isEmpty()) {
            showTestReview(project)
            return
        }
        
        val commitMessage = "🔍 右键菜单代码评估\n文件数量: ${changes.size}"
        
        // 显示代码评估过程对话框
        val processDialog = CodeReviewProcessDialog(project, changes, commitMessage)
        
        // 开始评估过程
        processDialog.startReview(codeReviewService) { canCommit, reviewResult ->
            processDialog.setCommitEnabled(canCommit)
            
            val dialogResult = processDialog.showAndGet()
            if (dialogResult && canCommit && reviewResult != null) {
                Messages.showInfoMessage(
                    "✅ 代码评估通过！\n\n" +
                            "评分: ${reviewResult.overallScore}/100\n" +
                            "风险等级: ${reviewResult.riskLevel}\n" +
                            "发现问题: ${reviewResult.issues.size} 个",
                    "代码评估结果"
                )
            } else if (!canCommit) {
                Messages.showWarningDialog(
                    "❌ 代码质量需要改进\n\n请根据评估结果修复问题。",
                    "代码评估未通过"
                )
            }
        }
    }

    /**
     * 显示测试评估（当没有实际代码变更时）
     */
    private fun showTestReview(project: Project) {
        val choice = Messages.showYesNoDialog(
            "当前没有代码变更。\n\n是否使用模拟数据进行代码评估测试？",
            "代码评估",
            "测试评估",
            "取消",
            Messages.getQuestionIcon()
        )
        
        if (choice == Messages.YES) {
            // 调用测试功能
            val testAction = com.vyibc.autocrplugin.action.TestCodeReviewAction()
            val mockEvent = object : AnActionEvent(
                null, 
                com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT,
                "",
                com.intellij.openapi.actionSystem.Presentation(),
                com.intellij.openapi.actionSystem.ActionManager.getInstance(),
                0
            ) {
                override fun getProject(): Project? = project
            }
            testAction.actionPerformed(mockEvent)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        // 总是显示，让用户可以随时进行代码评估
        e.presentation.isEnabledAndVisible = true
        e.presentation.text = "Code Review"
        e.presentation.description = "对当前项目进行代码评估"
        
        // 根据是否有变更来调整描述
        val changeListManager = ChangeListManager.getInstance(project)
        val hasChanges = changeListManager.defaultChangeList.changes.isNotEmpty()
        
        if (hasChanges) {
            val changeCount = changeListManager.defaultChangeList.changes.size
            e.presentation.description = "评估 $changeCount 个文件的变更"
        } else {
            e.presentation.description = "代码评估（使用测试数据）"
        }
    }
}
