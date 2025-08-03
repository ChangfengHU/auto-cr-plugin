package com.vyibc.autocrplugin.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 上下文压缩器 - V5.1版本
 * 三种压缩策略：轻量/中等/重度压缩
 */
class ContextCompressor {
    
    /**
     * 压缩代码评审上下文
     */
    suspend fun compressCodeReviewContext(
        context: CodeReviewContext,
        strategy: CompressionStrategy = CompressionStrategy.MEDIUM
    ): CompressedContext = withContext(Dispatchers.Default) {
        
        when (strategy) {
            CompressionStrategy.LIGHT -> lightCompression(context)
            CompressionStrategy.MEDIUM -> mediumCompression(context)
            CompressionStrategy.HEAVY -> heavyCompression(context)
        }
    }
    
    /**
     * 轻量压缩 - 保留90%的信息
     * 主要去除重复信息和格式化
     */
    private suspend fun lightCompression(context: CodeReviewContext): CompressedContext {
        val compressedMethods = context.changedMethods.map { method ->
            CompressedMethodInfo(
                signature = method.signature,
                body = compressWhitespace(method.body),
                comments = method.comments?.let { compressComments(it, 0.1) },
                complexity = method.complexity,
                riskScore = method.riskScore,
                testCoverage = method.testCoverage
            )
        }
        
        val compressedDiffs = context.diffs.map { diff ->
            CompressedDiffInfo(
                filePath = diff.filePath,
                changeType = diff.changeType,
                before = compressCode(diff.before, 0.05),
                after = compressCode(diff.after, 0.05),
                contextLines = diff.contextLines.take(3) // 减少上下文行数
            )
        }
        
        return CompressedContext(
            projectInfo = context.projectInfo,
            branchInfo = context.branchInfo,
            commitInfo = compressCommitInfo(context.commitInfo, 0.1),
            methods = compressedMethods,
            diffs = compressedDiffs,
            callPaths = context.callPaths.take((context.callPaths.size * 0.9).toInt()),
            testResults = context.testResults,
            compressionRatio = 0.9,
            strategy = CompressionStrategy.LIGHT
        )
    }
    
    /**
     * 中等压缩 - 保留70%的信息
     * 智能选择关键信息，压缩非核心内容
     */
    private suspend fun mediumCompression(context: CodeReviewContext): CompressedContext {
        // 按重要性排序方法
        val sortedMethods = context.changedMethods.sortedByDescending { 
            it.riskScore * 0.4 + it.complexity * 0.3 + (1.0 - it.testCoverage) * 0.3 
        }
        
        val compressedMethods = sortedMethods.take((sortedMethods.size * 0.8).toInt()).map { method ->
            CompressedMethodInfo(
                signature = method.signature,
                body = compressCode(method.body, 0.3),
                comments = method.comments?.let { compressComments(it, 0.4) },
                complexity = method.complexity,
                riskScore = method.riskScore,
                testCoverage = method.testCoverage
            )
        }
        
        val compressedDiffs = context.diffs.filter { diff ->
            // 只保留核心变更
            diff.changeType in setOf("MODIFIED", "ADDED") && diff.after.lines().size > 5
        }.map { diff ->
            CompressedDiffInfo(
                filePath = diff.filePath,
                changeType = diff.changeType,
                before = compressCode(diff.before, 0.3),
                after = compressCode(diff.after, 0.3),
                contextLines = diff.contextLines.take(2)
            )
        }
        
        // 筛选关键调用路径
        val criticalPaths = context.callPaths.filter { path ->
            path.riskWeight > 0.6 || path.intentWeight > 0.7
        }.take(10)
        
        return CompressedContext(
            projectInfo = compressProjectInfo(context.projectInfo),
            branchInfo = context.branchInfo,
            commitInfo = compressCommitInfo(context.commitInfo, 0.4),
            methods = compressedMethods,
            diffs = compressedDiffs,
            callPaths = criticalPaths,
            testResults = compressTestResults(context.testResults),
            compressionRatio = 0.7,
            strategy = CompressionStrategy.MEDIUM
        )
    }
    
    /**
     * 重度压缩 - 保留40%的信息
     * 仅保留最核心的风险信息和变更摘要
     */
    private suspend fun heavyCompression(context: CodeReviewContext): CompressedContext {
        // 只保留高风险方法
        val highRiskMethods = context.changedMethods.filter { 
            it.riskScore > 0.7 || it.complexity > 15 || it.testCoverage < 0.3 
        }.take(5).map { method ->
            CompressedMethodInfo(
                signature = method.signature,
                body = extractMethodSummary(method.body),
                comments = extractKeyComments(method.comments),
                complexity = method.complexity,
                riskScore = method.riskScore,
                testCoverage = method.testCoverage
            )
        }
        
        // 只保留关键变更
        val criticalDiffs = context.diffs.filter { diff ->
            diff.changeType == "MODIFIED" && 
            (diff.after.contains("public") || diff.after.contains("private") || 
             diff.after.contains("@") || diff.after.lines().size > 10)
        }.take(3).map { diff ->
            CompressedDiffInfo(
                filePath = diff.filePath,
                changeType = diff.changeType,
                before = extractCodeSummary(diff.before),
                after = extractCodeSummary(diff.after),
                contextLines = emptyList()
            )
        }
        
        // 只保留最关键的路径
        val topRiskPaths = context.callPaths.filter { path ->
            path.riskWeight > 0.8
        }.take(3)
        
        return CompressedContext(
            projectInfo = ProjectInfo(
                name = context.projectInfo.name,
                language = context.projectInfo.language,
                framework = context.projectInfo.framework,
                description = ""
            ),
            branchInfo = context.branchInfo,
            commitInfo = CommitInfo(
                hash = context.commitInfo.hash,
                message = compressCommitMessage(context.commitInfo.message),
                author = context.commitInfo.author,
                timestamp = context.commitInfo.timestamp,
                files = context.commitInfo.files.take(5)
            ),
            methods = highRiskMethods,
            diffs = criticalDiffs,
            callPaths = topRiskPaths,
            testResults = null, // 重度压缩时移除测试结果
            compressionRatio = 0.4,
            strategy = CompressionStrategy.HEAVY
        )
    }
    
    /**
     * 压缩空白字符
     */
    private fun compressWhitespace(code: String): String {
        return code.lines()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }
    
    /**
     * 压缩代码内容
     */
    private fun compressCode(code: String, ratio: Double): String {
        val lines = code.lines().filter { it.trim().isNotEmpty() }
        val keepLines = (lines.size * (1.0 - ratio)).toInt()
        
        // 保留重要的行（包含关键字、方法声明等）
        val importantLines = lines.filter { line ->
            val trimmed = line.trim()
            trimmed.startsWith("public") || 
            trimmed.startsWith("private") || 
            trimmed.startsWith("protected") ||
            trimmed.startsWith("@") ||
            trimmed.startsWith("class") ||
            trimmed.startsWith("interface") ||
            trimmed.startsWith("fun") ||
            trimmed.startsWith("override") ||
            trimmed.contains("throw") ||
            trimmed.contains("return")
        }
        
        val otherLines = lines - importantLines
        val selectedOtherLines = otherLines.take(maxOf(0, keepLines - importantLines.size))
        
        return (importantLines + selectedOtherLines).joinToString("\n")
    }
    
    /**
     * 压缩注释
     */
    private fun compressComments(comments: String, ratio: Double): String {
        val lines = comments.lines().filter { it.trim().isNotEmpty() }
        val keepLines = (lines.size * (1.0 - ratio)).toInt()
        
        // 保留文档注释的关键部分
        val keyLines = lines.filter { line ->
            val trimmed = line.trim().lowercase()
            trimmed.contains("@param") ||
            trimmed.contains("@return") ||
            trimmed.contains("@throws") ||
            trimmed.contains("@deprecated") ||
            trimmed.contains("todo") ||
            trimmed.contains("fixme") ||
            trimmed.contains("bug") ||
            trimmed.contains("注意") ||
            trimmed.contains("警告")
        }
        
        val otherLines = lines - keyLines
        val selectedOtherLines = otherLines.take(maxOf(0, keepLines - keyLines.size))
        
        return (keyLines + selectedOtherLines).joinToString("\n")
    }
    
    /**
     * 提取方法摘要
     */
    private fun extractMethodSummary(methodBody: String): String {
        val lines = methodBody.lines()
        val signatureLine = lines.firstOrNull { it.contains("fun ") || it.contains("public ") || it.contains("private ") } ?: ""
        val returnLines = lines.filter { it.trim().startsWith("return") }
        val throwLines = lines.filter { it.contains("throw") }
        
        return (listOf(signatureLine) + returnLines + throwLines)
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }
    
    /**
     * 提取关键注释
     */
    private fun extractKeyComments(comments: String?): String? {
        if (comments == null) return null
        
        return comments.lines().filter { line ->
            val trimmed = line.trim().lowercase()
            trimmed.contains("@param") ||
            trimmed.contains("@return") ||
            trimmed.contains("@throws") ||
            trimmed.contains("todo") ||
            trimmed.contains("fixme") ||
            trimmed.contains("deprecated")
        }.joinToString("\n").takeIf { it.isNotEmpty() }
    }
    
    /**
     * 提取代码摘要
     */
    private fun extractCodeSummary(code: String): String {
        return code.lines().filter { line ->
            val trimmed = line.trim()
            trimmed.startsWith("public") || 
            trimmed.startsWith("private") ||
            trimmed.startsWith("@") ||
            trimmed.contains("class") ||
            trimmed.contains("interface") ||
            trimmed.contains("fun") ||
            trimmed.contains("return") ||
            trimmed.contains("throw")
        }.take(5).joinToString("\n")
    }
    
    /**
     * 压缩提交信息
     */
    private fun compressCommitInfo(commitInfo: CommitInfo, ratio: Double): CommitInfo {
        val maxFiles = (commitInfo.files.size * (1.0 - ratio)).toInt()
        
        return commitInfo.copy(
            message = compressCommitMessage(commitInfo.message),
            files = commitInfo.files.take(maxOf(1, maxFiles))
        )
    }
    
    /**
     * 压缩提交消息
     */
    private fun compressCommitMessage(message: String): String {
        val lines = message.lines().filter { it.trim().isNotEmpty() }
        
        // 保留第一行（标题）和包含关键词的行
        val titleLine = lines.firstOrNull() ?: ""
        val keyLines = lines.drop(1).filter { line ->
            val lower = line.lowercase()
            lower.contains("fix") || lower.contains("add") || lower.contains("update") ||
            lower.contains("remove") || lower.contains("refactor") || lower.contains("优化") ||
            lower.contains("修复") || lower.contains("添加") || lower.contains("更新")
        }.take(2)
        
        return (listOf(titleLine) + keyLines).joinToString("\n")
    }
    
    /**
     * 压缩项目信息
     */
    private fun compressProjectInfo(projectInfo: ProjectInfo): ProjectInfo {
        return projectInfo.copy(
            description = projectInfo.description.take(100)
        )
    }
    
    /**
     * 压缩测试结果
     */
    private fun compressTestResults(testResults: TestResults?): TestResults? {
        if (testResults == null) return null
        
        return testResults.copy(
            failures = testResults.failures.take(5),
            details = testResults.details?.take(200)
        )
    }
}

/**
 * 压缩策略
 */
enum class CompressionStrategy {
    LIGHT,      // 轻量压缩 - 90%保留
    MEDIUM,     // 中等压缩 - 70%保留
    HEAVY       // 重度压缩 - 40%保留
}

/**
 * 代码评审上下文
 */
data class CodeReviewContext(
    val projectInfo: ProjectInfo,
    val branchInfo: BranchInfo,
    val commitInfo: CommitInfo,
    val changedMethods: List<MethodInfo>,
    val diffs: List<DiffInfo>,
    val callPaths: List<CallPathInfo>,
    val testResults: TestResults?
)

/**
 * 压缩后的上下文
 */
data class CompressedContext(
    val projectInfo: ProjectInfo,
    val branchInfo: BranchInfo,
    val commitInfo: CommitInfo,
    val methods: List<CompressedMethodInfo>,
    val diffs: List<CompressedDiffInfo>,
    val callPaths: List<CallPathInfo>,
    val testResults: TestResults?,
    val compressionRatio: Double,
    val strategy: CompressionStrategy
)

/**
 * 项目信息
 */
data class ProjectInfo(
    val name: String,
    val language: String,
    val framework: String,
    val description: String
)

/**
 * 分支信息
 */
data class BranchInfo(
    val currentBranch: String,
    val targetBranch: String,
    val ahead: Int,
    val behind: Int
)

/**
 * 提交信息
 */
data class CommitInfo(
    val hash: String,
    val message: String,
    val author: String,
    val timestamp: Long,
    val files: List<String>
)

/**
 * 方法信息
 */
data class MethodInfo(
    val signature: String,
    val body: String,
    val comments: String?,
    val complexity: Int,
    val riskScore: Double,
    val testCoverage: Double
)

/**
 * 压缩后的方法信息
 */
data class CompressedMethodInfo(
    val signature: String,
    val body: String,
    val comments: String?,
    val complexity: Int,
    val riskScore: Double,
    val testCoverage: Double
)

/**
 * 差异信息
 */
data class DiffInfo(
    val filePath: String,
    val changeType: String,
    val before: String,
    val after: String,
    val contextLines: List<String>
)

/**
 * 压缩后的差异信息
 */
data class CompressedDiffInfo(
    val filePath: String,
    val changeType: String,
    val before: String,
    val after: String,
    val contextLines: List<String>
)

/**
 * 调用路径信息
 */
data class CallPathInfo(
    val path: String,
    val riskWeight: Double,
    val intentWeight: Double,
    val methods: List<String>
)

/**
 * 测试结果
 */
data class TestResults(
    val passed: Int,
    val failed: Int,
    val skipped: Int,
    val coverage: Double,
    val failures: List<String>,
    val details: String?
)