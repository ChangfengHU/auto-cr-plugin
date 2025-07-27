package com.vyibc.autocrplugin.service

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ContentRevision

/**
 * Git变更分析器
 */
class GitChangeAnalyzer(private val project: Project) {

    /**
     * 获取当前待提交的变更
     */
    fun getCurrentChanges(): List<CodeChange> {
        val changeListManager = ChangeListManager.getInstance(project)
        val changes = changeListManager.defaultChangeList.changes
        
        return changes.mapNotNull { change ->
            convertToCodeChange(change)
        }
    }

    /**
     * 获取指定commit的变更
     */
    fun getCommitChanges(commitHash: String): List<CodeChange> {
        // 简化实现，返回当前变更
        // 在实际项目中，这里可以通过Git命令获取指定commit的变更
        return getCurrentChanges()
    }

    /**
     * 将IntelliJ的Change对象转换为CodeChange
     */
    private fun convertToCodeChange(change: Change): CodeChange? {
        val beforeRevision = change.beforeRevision
        val afterRevision = change.afterRevision
        
        val filePath = when {
            afterRevision != null -> afterRevision.file.path
            beforeRevision != null -> beforeRevision.file.path
            else -> return null
        }
        
        val changeType = when {
            beforeRevision == null && afterRevision != null -> ChangeType.ADDED
            beforeRevision != null && afterRevision == null -> ChangeType.DELETED
            beforeRevision != null && afterRevision != null -> {
                if (beforeRevision.file.path != afterRevision.file.path) {
                    ChangeType.RENAMED
                } else {
                    ChangeType.MODIFIED
                }
            }
            else -> return null
        }
        
        val oldContent = try {
            beforeRevision?.content
        } catch (e: Exception) {
            null
        }
        
        val newContent = try {
            afterRevision?.content
        } catch (e: Exception) {
            null
        }
        
        val (addedLines, removedLines, modifiedLines) = analyzeDiff(oldContent, newContent)
        
        return CodeChange(
            filePath = filePath,
            changeType = changeType,
            oldContent = oldContent,
            newContent = newContent,
            addedLines = addedLines,
            removedLines = removedLines,
            modifiedLines = modifiedLines
        )
    }

    /**
     * 分析文件差异
     */
    private fun analyzeDiff(
        oldContent: String?,
        newContent: String?
    ): Triple<List<String>, List<String>, List<Pair<String, String>>> {
        
        if (oldContent == null && newContent == null) {
            return Triple(emptyList(), emptyList(), emptyList())
        }
        
        if (oldContent == null) {
            // 新增文件
            val lines = newContent?.lines() ?: emptyList()
            return Triple(lines, emptyList(), emptyList())
        }
        
        if (newContent == null) {
            // 删除文件
            val lines = oldContent.lines()
            return Triple(emptyList(), lines, emptyList())
        }
        
        // 简单的行级别差异分析
        val oldLines = oldContent.lines()
        val newLines = newContent.lines()
        
        val addedLines = mutableListOf<String>()
        val removedLines = mutableListOf<String>()
        val modifiedLines = mutableListOf<Pair<String, String>>()
        
        val maxLines = maxOf(oldLines.size, newLines.size)
        
        for (i in 0 until maxLines) {
            val oldLine = oldLines.getOrNull(i)
            val newLine = newLines.getOrNull(i)
            
            when {
                oldLine == null && newLine != null -> addedLines.add(newLine)
                oldLine != null && newLine == null -> removedLines.add(oldLine)
                oldLine != null && newLine != null && oldLine != newLine -> {
                    modifiedLines.add(oldLine to newLine)
                }
            }
        }
        
        return Triple(addedLines, removedLines, modifiedLines)
    }

    /**
     * 获取当前分支名
     */
    fun getCurrentBranch(): String? {
        // 简化实现，返回默认分支名
        return "main"
    }

    /**
     * 获取最近的commit信息
     */
    fun getLastCommitInfo(): String? {
        // 简化实现，返回模拟的commit信息
        return "Latest commit info"
    }
}
