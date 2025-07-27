package com.vyibc.autocrplugin.service.impl

import com.vyibc.autocrplugin.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * AI代码评估服务实现
 * 使用多种AI服务进行代码评估，支持故障转移
 */
class AICodeReviewService : CodeReviewService {

    private val gson = Gson()
    private val aiServiceManager = AIServiceManager()

    override suspend fun reviewCode(
        changes: List<CodeChange>,
        commitMessage: String
    ): CodeReviewResult = withContext(Dispatchers.IO) {

        try {
            val prompt = buildReviewPrompt(changes, commitMessage)
            val aiResponse = aiServiceManager.callAIWithFallback(prompt)
            parseAIResponse(aiResponse)
        } catch (e: Exception) {
            // 如果AI服务失败，返回基础的静态分析结果
            performBasicAnalysis(changes, commitMessage)
        }
    }

    /**
     * 构建AI评估提示词
     */
    private fun buildReviewPrompt(changes: List<CodeChange>, commitMessage: String): String {
        val prompt = StringBuilder()
        
        prompt.append("请对以下代码变更进行详细的代码评估(Code Review):\n\n")
        prompt.append("提交信息: $commitMessage\n\n")
        
        changes.forEach { change ->
            prompt.append("文件: ${change.filePath}\n")
            prompt.append("变更类型: ${change.changeType}\n")
            
            when (change.changeType) {
                ChangeType.ADDED -> {
                    prompt.append("新增内容:\n")
                    prompt.append(change.newContent ?: "")
                }
                ChangeType.MODIFIED -> {
                    prompt.append("新增行:\n")
                    change.addedLines.forEach { line ->
                        prompt.append("+ $line\n")
                    }
                    prompt.append("删除行:\n")
                    change.removedLines.forEach { line ->
                        prompt.append("- $line\n")
                    }
                    prompt.append("修改行:\n")
                    change.modifiedLines.forEach { (old, new) ->
                        prompt.append("- $old\n")
                        prompt.append("+ $new\n")
                    }
                }
                ChangeType.DELETED -> {
                    prompt.append("删除内容:\n")
                    prompt.append(change.oldContent ?: "")
                }
                ChangeType.RENAMED -> {
                    prompt.append("文件重命名\n")
                }
            }
            prompt.append("\n---\n\n")
        }
        
        prompt.append("""
            请从以下维度进行评估:
            1. 代码风格和规范
            2. 性能问题
            3. 安全风险
            4. 潜在Bug
            5. 可维护性
            6. 最佳实践
            
            请以JSON格式返回评估结果，包含:
            - overallScore: 总体评分(0-100)
            - riskLevel: 风险等级(LOW/MEDIUM/HIGH/CRITICAL)
            - issues: 问题列表，每个问题包含filePath, lineNumber, severity, category, message, suggestion
            - suggestions: 改进建议列表
            - summary: 总结
        """.trimIndent())
        
        return prompt.toString()
    }



    /**
     * 生成模拟AI响应（用于演示）
     */
    private fun generateMockAIResponse(prompt: String): String {
        return """
        {
            "overallScore": 85,
            "riskLevel": "LOW",
            "issues": [
                {
                    "filePath": "example.kt",
                    "lineNumber": 10,
                    "severity": "MINOR",
                    "category": "CODE_STYLE",
                    "message": "建议使用更具描述性的变量名",
                    "suggestion": "将变量名从 'data' 改为 'userData' 或 'responseData'"
                }
            ],
            "suggestions": [
                "代码整体结构良好",
                "建议添加更多的单元测试",
                "考虑添加错误处理机制"
            ],
            "summary": "代码质量良好，有一些小的改进空间。主要关注代码风格和错误处理。"
        }
        """.trimIndent()
    }

    /**
     * 解析AI响应
     */
    private fun parseAIResponse(response: String): CodeReviewResult {
        try {
            val jsonObject = gson.fromJson(response, JsonObject::class.java)
            
            val overallScore = jsonObject.get("overallScore")?.asInt ?: 70
            val riskLevel = RiskLevel.valueOf(jsonObject.get("riskLevel")?.asString ?: "MEDIUM")
            val summary = jsonObject.get("summary")?.asString ?: "代码评估完成"
            
            val issues = mutableListOf<CodeIssue>()
            val issuesArray = jsonObject.getAsJsonArray("issues")
            issuesArray?.forEach { issueElement ->
                val issueObj = issueElement.asJsonObject
                issues.add(
                    CodeIssue(
                        filePath = issueObj.get("filePath")?.asString ?: "",
                        lineNumber = issueObj.get("lineNumber")?.asInt,
                        severity = IssueSeverity.valueOf(issueObj.get("severity")?.asString ?: "INFO"),
                        category = IssueCategory.valueOf(issueObj.get("category")?.asString ?: "CODE_STYLE"),
                        message = issueObj.get("message")?.asString ?: "",
                        suggestion = issueObj.get("suggestion")?.asString
                    )
                )
            }
            
            val suggestions = mutableListOf<String>()
            val suggestionsArray = jsonObject.getAsJsonArray("suggestions")
            suggestionsArray?.forEach { suggestion ->
                suggestions.add(suggestion.asString)
            }
            
            return CodeReviewResult(
                overallScore = overallScore,
                issues = issues,
                suggestions = suggestions,
                riskLevel = riskLevel,
                summary = summary
            )
            
        } catch (e: Exception) {
            // 解析失败时返回默认结果
            return CodeReviewResult(
                overallScore = 70,
                issues = emptyList(),
                suggestions = listOf("AI服务响应解析失败，请检查网络连接"),
                riskLevel = RiskLevel.MEDIUM,
                summary = "无法获取详细的代码评估结果"
            )
        }
    }

    /**
     * 执行基础静态分析（当AI服务不可用时）
     */
    private fun performBasicAnalysis(changes: List<CodeChange>, commitMessage: String): CodeReviewResult {
        val issues = mutableListOf<CodeIssue>()
        val suggestions = mutableListOf<String>()
        var totalScore = 100
        var maxRisk = RiskLevel.LOW

        changes.forEach { change ->
            // 基础的静态分析规则
            analyzeBasicIssues(change, issues)
        }

        // 根据问题数量调整评分
        issues.forEach { issue ->
            when (issue.severity) {
                IssueSeverity.CRITICAL -> {
                    totalScore -= 20
                    maxRisk = RiskLevel.CRITICAL
                }
                IssueSeverity.MAJOR -> {
                    totalScore -= 10
                    if (maxRisk.ordinal < RiskLevel.HIGH.ordinal) maxRisk = RiskLevel.HIGH
                }
                IssueSeverity.MINOR -> {
                    totalScore -= 5
                    if (maxRisk.ordinal < RiskLevel.MEDIUM.ordinal) maxRisk = RiskLevel.MEDIUM
                }
                IssueSeverity.INFO -> totalScore -= 1
            }
        }

        totalScore = maxOf(0, totalScore)

        if (issues.isEmpty()) {
            suggestions.add("代码变更看起来不错！")
        } else {
            suggestions.add("发现了一些可以改进的地方，请查看详细问题列表")
        }

        return CodeReviewResult(
            overallScore = totalScore,
            issues = issues,
            suggestions = suggestions,
            riskLevel = maxRisk,
            summary = "基础静态分析完成，发现 ${issues.size} 个问题"
        )
    }

    /**
     * 分析基础问题
     */
    private fun analyzeBasicIssues(change: CodeChange, issues: MutableList<CodeIssue>) {
        val content = change.newContent ?: return
        val lines = content.lines()

        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1
            
            // 检查长行
            if (line.length > 120) {
                issues.add(
                    CodeIssue(
                        filePath = change.filePath,
                        lineNumber = lineNumber,
                        severity = IssueSeverity.MINOR,
                        category = IssueCategory.CODE_STYLE,
                        message = "行长度超过120字符",
                        suggestion = "考虑将长行拆分为多行"
                    )
                )
            }
            
            // 检查TODO注释
            if (line.contains("TODO", ignoreCase = true)) {
                issues.add(
                    CodeIssue(
                        filePath = change.filePath,
                        lineNumber = lineNumber,
                        severity = IssueSeverity.INFO,
                        category = IssueCategory.DOCUMENTATION,
                        message = "发现TODO注释",
                        suggestion = "考虑完成TODO项目或创建相应的任务"
                    )
                )
            }
            
            // 检查潜在的空指针风险（简单检查）
            if (line.contains("!!") && change.filePath.endsWith(".kt")) {
                issues.add(
                    CodeIssue(
                        filePath = change.filePath,
                        lineNumber = lineNumber,
                        severity = IssueSeverity.MAJOR,
                        category = IssueCategory.BUG_RISK,
                        message = "使用了非空断言操作符(!!)",
                        suggestion = "考虑使用安全调用(?.)或适当的空值检查"
                    )
                )
            }
        }
    }

    override fun getServiceName(): String {
        val primaryService = aiServiceManager.getPrimaryService()
        return "AI Code Review (${primaryService?.getServiceName() ?: "No Service"})"
    }
}
