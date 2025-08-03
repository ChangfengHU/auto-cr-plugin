package com.vyibc.autocrplugin.analyzer

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Git日志提取器 - V5.1版本（简化实现）
 * 提交历史分析、业务关键词提取、意图演进分析
 */
class GitLogExtractorSimple(private val project: Project) {
    
    /**
     * 提取提交历史（简化版 - 基于当前变更）
     */
    suspend fun extractCommitHistory(
        maxCommits: Int = 100
    ): CommitHistoryAnalysis = withContext(Dispatchers.IO) {
        
        // 简化实现：分析当前的变更作为示例
        val changeListManager = ChangeListManager.getInstance(project)
        val currentChanges = changeListManager.defaultChangeList
        
        // 创建模拟的提交分析
        val commitAnalyses = listOf(
            createMockCommitAnalysis(
                "Latest changes",
                currentChanges.changes.size
            )
        )
        
        // 提取业务关键词
        val businessKeywords = extractBusinessKeywordsFromCommits(commitAnalyses)
        
        // 分析意图演进
        val intentEvolution = analyzeIntentEvolution(commitAnalyses)
        
        // 识别开发模式
        val developmentPatterns = identifyDevelopmentPatterns(commitAnalyses)
        
        CommitHistoryAnalysis(
            commits = commitAnalyses,
            businessKeywords = businessKeywords,
            intentEvolution = intentEvolution,
            developmentPatterns = developmentPatterns,
            timeRange = TimeRange(
                start = Instant.now().minusSeconds(86400), // 24 hours ago
                end = Instant.now()
            ),
            analyzedAt = Instant.now()
        )
    }
    
    /**
     * 创建模拟的提交分析
     */
    private fun createMockCommitAnalysis(message: String, changedFiles: Int): CommitAnalysis {
        val keywords = extractKeywordsFromMessage(message)
        val commitType = identifyCommitType(message)
        val intent = analyzeCommitIntent(message, "")
        
        return CommitAnalysis(
            hash = "mock_${System.currentTimeMillis()}",
            author = System.getProperty("user.name", "Unknown"),
            email = "${System.getProperty("user.name", "unknown")}@example.com",
            timestamp = Instant.now(),
            message = message,
            fullMessage = message,
            keywords = keywords,
            commitType = commitType,
            intent = intent,
            filesChanged = changedFiles,
            linesAdded = 0,
            linesDeleted = 0
        )
    }
    
    /**
     * 从提交信息中提取关键词
     */
    private fun extractKeywordsFromMessage(message: String): Set<String> {
        val keywords = mutableSetOf<String>()
        
        // 移除常见的提交类型前缀
        val cleanMessage = message
            .replace(Regex("^(feat|fix|docs|style|refactor|test|chore|build|ci|perf|revert)\\s*[:(]"), "")
            .replace(Regex("[^a-zA-Z0-9\\s\\u4e00-\\u9fa5]"), " ")
        
        // 分词
        val words = cleanMessage.split("\\s+".toRegex())
            .filter { it.length > 2 }
            .filter { !isStopWord(it) }
        
        // 提取英文单词
        words.forEach { word ->
            if (word.matches(Regex("[a-zA-Z]+"))) {
                keywords.add(word.lowercase())
                // 拆分驼峰命名
                keywords.addAll(splitCamelCase(word))
            }
        }
        
        // 提取中文词组
        val chinesePattern = Regex("[\\u4e00-\\u9fa5]+")
        chinesePattern.findAll(cleanMessage).forEach { match ->
            if (match.value.length >= 2) {
                keywords.add(match.value)
            }
        }
        
        return keywords
    }
    
    /**
     * 识别提交类型
     */
    private fun identifyCommitType(subject: String): CommitType {
        val lowerSubject = subject.lowercase()
        
        return when {
            lowerSubject.startsWith("feat") || lowerSubject.contains("feature") || lowerSubject.contains("add") -> CommitType.FEATURE
            lowerSubject.startsWith("fix") || lowerSubject.contains("bug") || lowerSubject.contains("修复") -> CommitType.BUGFIX
            lowerSubject.startsWith("refactor") || lowerSubject.contains("重构") -> CommitType.REFACTOR
            lowerSubject.startsWith("perf") || lowerSubject.contains("performance") || lowerSubject.contains("优化") -> CommitType.PERFORMANCE
            lowerSubject.startsWith("test") || lowerSubject.contains("测试") -> CommitType.TEST
            lowerSubject.startsWith("docs") || lowerSubject.contains("文档") -> CommitType.DOCS
            lowerSubject.startsWith("style") || lowerSubject.contains("格式") -> CommitType.STYLE
            lowerSubject.startsWith("build") || lowerSubject.contains("构建") -> CommitType.BUILD
            lowerSubject.startsWith("ci") -> CommitType.CI
            lowerSubject.startsWith("chore") -> CommitType.CHORE
            else -> CommitType.OTHER
        }
    }
    
    /**
     * 分析提交意图
     */
    private fun analyzeCommitIntent(subject: String, body: String): CommitIntent {
        val fullText = "$subject $body".lowercase()
        
        val businessValue = when {
            fullText.contains(Regex("(new feature|新功能|新特性|需求)")) -> 0.8
            fullText.contains(Regex("(enhance|improve|优化|改进)")) -> 0.6
            fullText.contains(Regex("(fix|修复|解决)")) -> 0.5
            else -> 0.3
        }
        
        val riskLevel = when {
            fullText.contains(Regex("(break|破坏|不兼容|重大)")) -> 0.8
            fullText.contains(Regex("(refactor|重构|架构)")) -> 0.6
            fullText.contains(Regex("(change|修改|调整)")) -> 0.4
            else -> 0.2
        }
        
        val urgency = when {
            fullText.contains(Regex("(urgent|critical|紧急|严重)")) -> 0.9
            fullText.contains(Regex("(important|重要|必须)")) -> 0.7
            fullText.contains(Regex("(hotfix|热修复)")) -> 0.8
            else -> 0.4
        }
        
        return CommitIntent(
            businessValue = businessValue,
            riskLevel = riskLevel,
            urgency = urgency
        )
    }
    
    /**
     * 从提交历史中提取业务关键词
     */
    private fun extractBusinessKeywordsFromCommits(commits: List<CommitAnalysis>): Map<String, Int> {
        val keywordFreq = mutableMapOf<String, Int>()
        
        commits.forEach { commit ->
            commit.keywords.forEach { keyword ->
                keywordFreq[keyword] = keywordFreq.getOrDefault(keyword, 0) + 1
            }
        }
        
        // 按频率排序，返回前50个关键词
        return keywordFreq.entries
            .sortedByDescending { it.value }
            .take(50)
            .associate { it.key to it.value }
    }
    
    /**
     * 分析意图演进
     */
    private fun analyzeIntentEvolution(commits: List<CommitAnalysis>): IntentEvolution {
        if (commits.isEmpty()) {
            return IntentEvolution(emptyList(), emptyMap())
        }
        
        // 简化实现：创建单一时间点
        val timelineData = listOf(
            IntentTimelinePoint(
                period = "current",
                averageBusinessValue = commits.map { it.intent.businessValue }.average(),
                averageRiskLevel = commits.map { it.intent.riskLevel }.average(),
                commitCount = commits.size,
                dominantType = commits.groupingBy { it.commitType }.eachCount()
                    .maxByOrNull { it.value }?.key ?: CommitType.OTHER
            )
        )
        
        return IntentEvolution(
            timeline = timelineData,
            trends = mapOf(
                "businessValue" to Trend.STABLE,
                "riskLevel" to Trend.STABLE,
                "commitFrequency" to Trend.STABLE
            )
        )
    }
    
    /**
     * 识别开发模式
     */
    private fun identifyDevelopmentPatterns(commits: List<CommitAnalysis>): List<DevelopmentPattern> {
        val patterns = mutableListOf<DevelopmentPattern>()
        
        // 分析提交类型分布
        val typeDistribution = commits.groupingBy { it.commitType }.eachCount()
        val dominantType = typeDistribution.maxByOrNull { it.value }?.key
        
        if (dominantType != null) {
            val percentage = (typeDistribution[dominantType]!! * 100.0 / commits.size)
            patterns.add(DevelopmentPattern(
                type = PatternType.COMMIT_STYLE,
                description = "$dominantType commits dominate (${percentage.toInt()}%)",
                confidence = percentage / 100.0
            ))
        }
        
        return patterns
    }
    
    /**
     * 辅助方法
     */
    private fun isStopWord(word: String): Boolean {
        val stopWords = setOf(
            "the", "is", "at", "which", "on", "and", "a", "an", "as", "are",
            "been", "be", "have", "has", "had", "do", "does", "did", "will",
            "would", "should", "could", "may", "might", "must", "shall", "can",
            "this", "that", "these", "those", "i", "you", "he", "she", "it",
            "we", "they", "them", "their", "what", "which", "who", "when",
            "where", "why", "how", "all", "each", "every", "some", "any",
            "的", "了", "和", "是", "在", "有", "个", "到", "为", "与"
        )
        return word.lowercase() in stopWords
    }
    
    private fun splitCamelCase(text: String): List<String> {
        return text.split("(?=[A-Z])".toRegex())
            .filter { it.isNotBlank() && it.length > 2 }
            .map { it.lowercase() }
    }
}

/**
 * 提交历史分析结果
 */
data class CommitHistoryAnalysis(
    val commits: List<CommitAnalysis>,
    val businessKeywords: Map<String, Int>,
    val intentEvolution: IntentEvolution,
    val developmentPatterns: List<DevelopmentPattern>,
    val timeRange: TimeRange,
    val analyzedAt: Instant
)

/**
 * 单个提交的分析结果
 */
data class CommitAnalysis(
    val hash: String,
    val author: String,
    val email: String,
    val timestamp: Instant,
    val message: String,
    val fullMessage: String,
    val keywords: Set<String>,
    val commitType: CommitType,
    val intent: CommitIntent,
    val filesChanged: Int,
    val linesAdded: Int,
    val linesDeleted: Int
)

/**
 * 提交意图
 */
data class CommitIntent(
    val businessValue: Double,  // 0.0-1.0
    val riskLevel: Double,      // 0.0-1.0
    val urgency: Double         // 0.0-1.0
)

/**
 * 意图演进分析
 */
data class IntentEvolution(
    val timeline: List<IntentTimelinePoint>,
    val trends: Map<String, Trend>
)

/**
 * 意图时间线点
 */
data class IntentTimelinePoint(
    val period: String,
    val averageBusinessValue: Double,
    val averageRiskLevel: Double,
    val commitCount: Int,
    val dominantType: CommitType
)

/**
 * 开发模式
 */
data class DevelopmentPattern(
    val type: PatternType,
    val description: String,
    val confidence: Double
)

/**
 * 时间范围
 */
data class TimeRange(
    val start: Instant,
    val end: Instant
)

/**
 * 提交类型
 */
enum class CommitType {
    FEATURE,
    BUGFIX,
    REFACTOR,
    PERFORMANCE,
    TEST,
    DOCS,
    STYLE,
    BUILD,
    CI,
    CHORE,
    OTHER
}

/**
 * 模式类型
 */
enum class PatternType {
    WORK_HOURS,
    COMMIT_STYLE,
    COLLABORATION,
    RELEASE_CYCLE,
    CODE_QUALITY
}

/**
 * 趋势
 */
enum class Trend {
    INCREASING,
    STABLE,
    DECREASING
}