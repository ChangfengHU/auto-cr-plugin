package com.vyibc.autocrplugin.service.impl

import com.vyibc.autocrplugin.service.*
import com.vyibc.autocrplugin.settings.CodeReviewSettings
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
        val settings = CodeReviewSettings.getInstance()
        val prompt = StringBuilder()

        // 使用自定义提示词或默认提示词
        val basePrompt = if (settings.customPrompt.isNotEmpty()) {
            settings.customPrompt
        } else {
            getDefaultPrompt()
        }

        prompt.append(basePrompt)
        prompt.append("\n\n## 📋 代码变更详情：\n\n")
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

## 📤 严格返回格式要求：
请严格按照以下JSON格式返回，不要添加任何其他文字：

```json
{
  "overallScore": 85,
  "riskLevel": "MEDIUM",
  "issues": [
    {
      "filePath": "文件路径",
      "lineNumber": 行号,
      "severity": "CRITICAL|MAJOR|MINOR|INFO",
      "category": "问题分类",
      "message": "问题描述",
      "suggestion": "修复建议"
    }
  ],
  "suggestions": [
    "改进建议1",
    "改进建议2"
  ],
  "summary": "总结"
}
```

注意：
- overallScore: 必须是0-100的整数
- riskLevel: 必须是 LOW|MEDIUM|HIGH|CRITICAL 之一
- severity: 必须是 CRITICAL|MAJOR|MINOR|INFO 之一
- 请确保返回的是有效的JSON格式，不要包含markdown代码块标记
        """.trimIndent())
        
        return prompt.toString()
    }

    /**
     * 获取默认的AI分析提示词
     */
    private fun getDefaultPrompt(): String {
        return """
请对以下代码变更进行专业的代码评估(Code Review)，重点关注生产环境安全性和最佳实践：

## 🔍 重点检查项目：

### 🚨 生产环境危险操作
- Redis危险命令：keys、flushdb、flushall、config等
- 数据库全表扫描：select * without where、count(*)等
- 阻塞操作：同步IO、长时间循环等
- 资源泄漏：未关闭连接、内存泄漏等

### 🔒 安全问题
- SQL注入风险
- XSS攻击风险
- 敏感信息泄露（密码、token等）
- 权限控制缺失
- 输入验证不足

### 📊 性能问题
- N+1查询问题
- 不必要的数据库查询
- 低效的算法实现
- 内存使用不当
- 缓存使用不当

### 🏗️ 代码质量
- 代码重复
- 方法过长或过于复杂
- 命名不规范
- 异常处理不当
- 日志记录不足

### 🧪 测试覆盖
- 缺少单元测试
- 边界条件未测试
- 异常情况未覆盖

## 📋 评估要求：
1. 给出0-100的综合评分
2. 标注风险等级：LOW/MEDIUM/HIGH/CRITICAL
3. 列出具体问题和改进建议
4. 特别标注生产环境风险项
        """.trimIndent()
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
        println("=== 🔍 AI响应解析过程 ===")
        println("原始响应长度: ${response.length} 字符")
        println("原始响应内容:")
        println(response)
        println("========================")

        try {
            // 清理响应内容，移除可能的markdown代码块标记
            val cleanedResponse = response
                .replace("```json", "")
                .replace("```", "")
                .trim()

            println("清理后的响应:")
            println(cleanedResponse)
            println("========================")

            val jsonObject = gson.fromJson(cleanedResponse, JsonObject::class.java)
            println("JSON解析成功")

            val overallScore = jsonObject.get("overallScore")?.asInt ?: 70
            println("解析评分: $overallScore")

            val riskLevelStr = jsonObject.get("riskLevel")?.asString ?: "MEDIUM"
            val riskLevel = try {
                RiskLevel.valueOf(riskLevelStr)
            } catch (e: Exception) {
                println("风险等级解析失败: $riskLevelStr，使用默认值 MEDIUM")
                RiskLevel.MEDIUM
            }
            println("解析风险等级: $riskLevel")

            val summary = jsonObject.get("summary")?.asString ?: "代码评估完成"
            println("解析总结: $summary")
            
            val issues = mutableListOf<CodeIssue>()
            val issuesArray = jsonObject.getAsJsonArray("issues")
            println("解析问题列表，数量: ${issuesArray?.size() ?: 0}")

            issuesArray?.forEach { issueElement ->
                try {
                    val issueObj = issueElement.asJsonObject
                    val severityStr = issueObj.get("severity")?.asString ?: "INFO"
                    val categoryStr = issueObj.get("category")?.asString ?: "CODE_STYLE"

                    val severity = try {
                        IssueSeverity.valueOf(severityStr)
                    } catch (e: Exception) {
                        println("严重程度解析失败: $severityStr，使用默认值 INFO")
                        IssueSeverity.INFO
                    }

                    val category = try {
                        IssueCategory.valueOf(categoryStr)
                    } catch (e: Exception) {
                        println("问题分类解析失败: $categoryStr，使用默认值 CODE_STYLE")
                        IssueCategory.CODE_STYLE
                    }

                    val issue = CodeIssue(
                        filePath = issueObj.get("filePath")?.asString ?: "",
                        lineNumber = issueObj.get("lineNumber")?.asInt,
                        severity = severity,
                        category = category,
                        message = issueObj.get("message")?.asString ?: "",
                        suggestion = issueObj.get("suggestion")?.asString
                    )
                    issues.add(issue)
                    println("解析问题: ${issue.message}")
                } catch (e: Exception) {
                    println("解析单个问题失败: ${e.message}")
                }
            }
            
            val suggestions = mutableListOf<String>()
            val suggestionsArray = jsonObject.getAsJsonArray("suggestions")
            println("解析建议列表，数量: ${suggestionsArray?.size() ?: 0}")

            suggestionsArray?.forEach { suggestion ->
                try {
                    val suggestionText = suggestion.asString
                    suggestions.add(suggestionText)
                    println("解析建议: $suggestionText")
                } catch (e: Exception) {
                    println("解析单个建议失败: ${e.message}")
                }
            }

            val result = CodeReviewResult(
                overallScore = overallScore,
                issues = issues,
                suggestions = suggestions,
                riskLevel = riskLevel,
                summary = summary
            )

            println("=== ✅ AI响应解析完成 ===")
            println("最终结果: 评分=$overallScore, 风险=${riskLevel}, 问题=${issues.size}个, 建议=${suggestions.size}条")

            return result

        } catch (e: Exception) {
            println("=== ❌ AI响应解析失败 ===")
            println("错误信息: ${e.message}")
            println("错误堆栈: ${e.stackTraceToString()}")

            // 解析失败时返回默认结果
            return CodeReviewResult(
                overallScore = 70,
                issues = emptyList(),
                suggestions = listOf(
                    "AI服务响应解析失败: ${e.message}",
                    "原始响应长度: ${response.length} 字符",
                    "请检查AI服务配置和网络连接"
                ),
                riskLevel = RiskLevel.MEDIUM,
                summary = "AI响应解析失败，无法获取详细的代码评估结果"
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
