package com.vyibc.autocrplugin.analyzer

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.vyibc.autocrplugin.graph.model.BlockType
import com.vyibc.autocrplugin.service.CodeChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Git差异分析器 - V5.1增强版（简化实现）
 * 支持分支对比、文件变更分析、代码差异提取
 */
class GitDiffAnalyzer(private val project: Project) {
    
    private val changeListManager = ChangeListManager.getInstance(project)
    
    /**
     * 分析当前变更
     */
    suspend fun analyzeCurrentChanges(): BranchDiffAnalysis = withContext(Dispatchers.IO) {
        val changes = changeListManager.defaultChangeList.changes
        val fileChanges = changes.mapNotNull { change ->
            analyzeChange(change)
        }
        
        val businessKeywords = extractBusinessKeywords(fileChanges)
        val stats = calculateDiffStatistics(fileChanges)
        
        BranchDiffAnalysis(
            sourceBranch = "current",
            targetBranch = "HEAD",
            fileChanges = fileChanges,
            businessKeywords = businessKeywords,
            statistics = stats,
            analyzedAt = Instant.now()
        )
    }
    
    /**
     * 分析单个Change
     */
    private fun analyzeChange(change: Change): FileChangeAnalysis? {
        val filePath = change.afterRevision?.file?.path 
            ?: change.beforeRevision?.file?.path 
            ?: return null
            
        val fileType = change.afterRevision?.file?.fileType?.name
            ?: change.beforeRevision?.file?.fileType?.name
            ?: "Unknown"
        
        val oldContent = try {
            change.beforeRevision?.content
        } catch (e: Exception) {
            null
        }
        
        val newContent = try {
            change.afterRevision?.content
        } catch (e: Exception) {
            null
        }
        
        val lineDiffs = computeLineDiffs(oldContent, newContent)
        val methodChanges = identifyMethodChanges(filePath, lineDiffs)
        val blockType = detectBlockType(filePath, newContent)
        
        return FileChangeAnalysis(
            filePath = filePath,
            fileType = fileType,
            blockType = blockType,
            changeType = determineChangeType(oldContent != null, newContent != null),
            lineDiffs = lineDiffs,
            methodChanges = methodChanges,
            addedLines = lineDiffs.count { it.type == LineDiffType.ADDED },
            deletedLines = lineDiffs.count { it.type == LineDiffType.DELETED },
            modifiedLines = lineDiffs.count { it.type == LineDiffType.MODIFIED }
        )
    }
    
    /**
     * 计算行级差异
     */
    private fun computeLineDiffs(
        sourceContent: String?,
        targetContent: String?
    ): List<LineDiff> {
        if (sourceContent == null && targetContent == null) {
            return emptyList()
        }
        
        val sourceLines = sourceContent?.lines() ?: emptyList()
        val targetLines = targetContent?.lines() ?: emptyList()
        
        return computeDetailedDiff(sourceLines, targetLines)
    }
    
    /**
     * 识别方法级别的变更
     */
    private fun identifyMethodChanges(
        filePath: String,
        lineDiffs: List<LineDiff>
    ): List<MethodChange> {
        val methodChanges = mutableListOf<MethodChange>()
        
        // 简化实现：通过正则表达式识别方法签名
        val methodPattern = when {
            filePath.endsWith(".java") -> Regex("""(public|private|protected)?\s*\w+\s+(\w+)\s*\([^)]*\)""")
            filePath.endsWith(".kt") -> Regex("""fun\s+(\w+)\s*\([^)]*\)""")
            else -> null
        }
        
        if (methodPattern != null) {
            var currentMethod: String? = null
            var currentChanges = mutableListOf<LineDiff>()
            
            for (diff in lineDiffs) {
                val match = methodPattern.find(diff.content)
                if (match != null) {
                    // 发现新方法，保存之前的方法变更
                    if (currentMethod != null && currentChanges.isNotEmpty()) {
                        methodChanges.add(MethodChange(
                            methodName = currentMethod,
                            changeType = determineMethodChangeType(currentChanges),
                            affectedLines = currentChanges.size
                        ))
                    }
                    currentMethod = match.groupValues.lastOrNull() ?: "unknown"
                    currentChanges = mutableListOf()
                }
                
                currentChanges.add(diff)
            }
            
            // 保存最后一个方法的变更
            if (currentMethod != null && currentChanges.isNotEmpty()) {
                methodChanges.add(MethodChange(
                    methodName = currentMethod,
                    changeType = determineMethodChangeType(currentChanges),
                    affectedLines = currentChanges.size
                ))
            }
        }
        
        return methodChanges
    }
    
    /**
     * 检测文件所属的架构层级
     */
    private fun detectBlockType(filePath: String, content: String?): BlockType {
        val lowerPath = filePath.lowercase()
        val lowerContent = content?.lowercase() ?: ""
        
        return when {
            lowerPath.contains("controller") || lowerContent.contains("@controller") -> BlockType.CONTROLLER
            lowerPath.contains("service") || lowerContent.contains("@service") -> BlockType.SERVICE
            lowerPath.contains("repository") || lowerPath.contains("dao") || lowerContent.contains("@repository") -> BlockType.REPOSITORY
            lowerPath.contains("mapper") || lowerContent.contains("@mapper") -> BlockType.MAPPER
            lowerPath.contains("entity") || lowerPath.contains("model") || lowerContent.contains("@entity") -> BlockType.ENTITY
            lowerPath.contains("dto") -> BlockType.DTO
            lowerPath.contains("vo") || lowerPath.contains("view") -> BlockType.VO
            lowerPath.contains("util") || lowerPath.contains("helper") -> BlockType.UTIL
            lowerPath.contains("config") || lowerContent.contains("@configuration") -> BlockType.CONFIG
            lowerPath.contains("test") -> BlockType.TEST
            else -> BlockType.OTHER
        }
    }
    
    /**
     * 提取业务关键词
     */
    private fun extractBusinessKeywords(fileChanges: List<FileChangeAnalysis>): Set<String> {
        val keywords = mutableSetOf<String>()
        
        fileChanges.forEach { change ->
            // 从文件路径提取
            val pathParts = change.filePath.split("/", "\\", ".")
            pathParts.forEach { part ->
                if (part.length > 3 && !isCommonWord(part)) {
                    keywords.addAll(splitCamelCase(part))
                }
            }
            
            // 从方法名提取
            change.methodChanges.forEach { method ->
                keywords.addAll(splitCamelCase(method.methodName))
            }
        }
        
        return keywords.filter { it.length > 3 }.toSet()
    }
    
    /**
     * 拆分驼峰命名
     */
    private fun splitCamelCase(text: String): List<String> {
        return text.split("(?=[A-Z])".toRegex())
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
    }
    
    /**
     * 判断是否为常见词汇
     */
    private fun isCommonWord(word: String): Boolean {
        val commonWords = setOf(
            "com", "org", "java", "kotlin", "src", "main", "test",
            "get", "set", "add", "remove", "update", "delete"
        )
        return word.lowercase() in commonWords
    }
    
    /**
     * 计算差异统计信息
     */
    private fun calculateDiffStatistics(fileChanges: List<FileChangeAnalysis>): DiffStatistics {
        return DiffStatistics(
            totalFiles = fileChanges.size,
            addedFiles = fileChanges.count { it.changeType == FileChangeType.ADDED },
            modifiedFiles = fileChanges.count { it.changeType == FileChangeType.MODIFIED },
            deletedFiles = fileChanges.count { it.changeType == FileChangeType.DELETED },
            totalAddedLines = fileChanges.sumOf { it.addedLines },
            totalDeletedLines = fileChanges.sumOf { it.deletedLines },
            totalModifiedLines = fileChanges.sumOf { it.modifiedLines },
            affectedMethods = fileChanges.sumOf { it.methodChanges.size }
        )
    }
    
    /**
     * 使用改进的算法计算详细差异
     */
    private fun computeDetailedDiff(oldLines: List<String>, newLines: List<String>): List<LineDiff> {
        val diffs = mutableListOf<LineDiff>()
        val lcs = computeLCS(oldLines, newLines)
        
        var oldIndex = 0
        var newIndex = 0
        var lcsIndex = 0
        
        while (oldIndex < oldLines.size || newIndex < newLines.size) {
            when {
                lcsIndex < lcs.size && 
                oldIndex < oldLines.size && 
                newIndex < newLines.size &&
                oldLines[oldIndex] == lcs[lcsIndex] && 
                newLines[newIndex] == lcs[lcsIndex] -> {
                    // 未变更的行
                    oldIndex++
                    newIndex++
                    lcsIndex++
                }
                oldIndex < oldLines.size && 
                (lcsIndex >= lcs.size || oldLines[oldIndex] != lcs[lcsIndex]) -> {
                    // 删除的行
                    diffs.add(LineDiff(
                        type = LineDiffType.DELETED,
                        content = oldLines[oldIndex],
                        lineNumber = oldIndex + 1,
                        oldLineNumber = oldIndex + 1,
                        newLineNumber = null
                    ))
                    oldIndex++
                }
                newIndex < newLines.size -> {
                    // 新增的行
                    diffs.add(LineDiff(
                        type = LineDiffType.ADDED,
                        content = newLines[newIndex],
                        lineNumber = newIndex + 1,
                        oldLineNumber = null,
                        newLineNumber = newIndex + 1
                    ))
                    newIndex++
                }
            }
        }
        
        return diffs
    }
    
    /**
     * 计算最长公共子序列
     */
    private fun computeLCS(oldLines: List<String>, newLines: List<String>): List<String> {
        val m = oldLines.size
        val n = newLines.size
        val dp = Array(m + 1) { IntArray(n + 1) }
        
        for (i in 1..m) {
            for (j in 1..n) {
                if (oldLines[i - 1] == newLines[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                } else {
                    dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }
        
        val lcs = mutableListOf<String>()
        var i = m
        var j = n
        
        while (i > 0 && j > 0) {
            when {
                oldLines[i - 1] == newLines[j - 1] -> {
                    lcs.add(0, oldLines[i - 1])
                    i--
                    j--
                }
                dp[i - 1][j] > dp[i][j - 1] -> i--
                else -> j--
            }
        }
        
        return lcs
    }
    
    private fun determineChangeType(hasOld: Boolean, hasNew: Boolean): FileChangeType {
        return when {
            !hasOld && hasNew -> FileChangeType.ADDED
            hasOld && !hasNew -> FileChangeType.DELETED
            else -> FileChangeType.MODIFIED
        }
    }
    
    private fun determineMethodChangeType(changes: List<LineDiff>): MethodChangeType {
        val hasAdded = changes.any { it.type == LineDiffType.ADDED }
        val hasDeleted = changes.any { it.type == LineDiffType.DELETED }
        
        return when {
            hasAdded && !hasDeleted -> MethodChangeType.ADDED
            !hasAdded && hasDeleted -> MethodChangeType.DELETED
            else -> MethodChangeType.MODIFIED
        }
    }
}

/**
 * 分支差异分析结果
 */
data class BranchDiffAnalysis(
    val sourceBranch: String,
    val targetBranch: String,
    val fileChanges: List<FileChangeAnalysis>,
    val businessKeywords: Set<String>,
    val statistics: DiffStatistics,
    val analyzedAt: Instant
)

/**
 * 文件变更分析结果
 */
data class FileChangeAnalysis(
    val filePath: String,
    val fileType: String,
    val blockType: BlockType,
    val changeType: FileChangeType,
    val lineDiffs: List<LineDiff>,
    val methodChanges: List<MethodChange>,
    val addedLines: Int,
    val deletedLines: Int,
    val modifiedLines: Int
)

/**
 * 行级差异
 */
data class LineDiff(
    val type: LineDiffType,
    val content: String,
    val lineNumber: Int,
    val oldLineNumber: Int?,
    val newLineNumber: Int?
)

/**
 * 方法变更信息
 */
data class MethodChange(
    val methodName: String,
    val changeType: MethodChangeType,
    val affectedLines: Int
)

/**
 * 差异统计信息
 */
data class DiffStatistics(
    val totalFiles: Int,
    val addedFiles: Int,
    val modifiedFiles: Int,
    val deletedFiles: Int,
    val totalAddedLines: Int,
    val totalDeletedLines: Int,
    val totalModifiedLines: Int,
    val affectedMethods: Int
)

/**
 * 文件变更类型
 */
enum class FileChangeType {
    ADDED, MODIFIED, DELETED, RENAMED
}

/**
 * 行差异类型
 */
enum class LineDiffType {
    ADDED, DELETED, MODIFIED
}

/**
 * 方法变更类型
 */
enum class MethodChangeType {
    ADDED, MODIFIED, DELETED
}