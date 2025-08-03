package com.vyibc.autocrplugin.service

import com.intellij.openapi.project.Project
import com.vyibc.autocrplugin.ui.AutoCRToolWindowPanel
import com.vyibc.autocrplugin.graph.model.*
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.SwingUtilities

/**
 * 集成分析服务 - V5.1版本
 * 整合所有分析组件并将结果传递给UI
 */
class IntegratedAnalysisService(
    private val project: Project,
    private val toolWindowPanel: AutoCRToolWindowPanel
) {
    
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * 执行完整的代码变更分析
     */
    suspend fun analyzeCodeChanges(changes: List<CodeChange>): AnalysisResult = withContext(Dispatchers.Default) {
        
        // 1. 显示开始分析
        SwingUtilities.invokeLater {
            toolWindowPanel.updateIntentAnalysis("🔍 正在分析意图权重...")
            toolWindowPanel.updateRiskAnalysis("⚠️ 正在分析风险权重...")
            toolWindowPanel.updateComprehensiveReport("📊 正在生成综合报告...")
        }
        
        try {
            // 2. 模拟分析过程
            val analysisResults = async {
                
                val analysisStart = System.currentTimeMillis()
                
                // 模拟意图权重计算
                val intentDeferred = async { 
                    delay(1000) // 模拟计算时间
                    val intentResult = mockIntentAnalysis(changes)
                    SwingUtilities.invokeLater {
                        toolWindowPanel.updateIntentAnalysis(formatIntentAnalysisResult(intentResult))
                    }
                    intentResult
                }
                
                // 模拟风险权重计算
                val riskDeferred = async { 
                    delay(1200) // 模拟计算时间
                    val riskResult = mockRiskAnalysis(changes)
                    SwingUtilities.invokeLater {
                        toolWindowPanel.updateRiskAnalysis(formatRiskAnalysisResult(riskResult))
                    }
                    riskResult
                }
                
                // 模拟测试用例分析
                val testDeferred = async {
                    delay(800)
                    mockTestAnalysis(changes)
                }
                
                // 等待所有分析完成
                val intentResults = intentDeferred.await()
                val riskResults = riskDeferred.await()
                val testResults = testDeferred.await()
                
                val analysisEnd = System.currentTimeMillis()
                val analysisDuration = analysisEnd - analysisStart
                
                AnalysisResults(
                    intentResults = intentResults,
                    riskResults = riskResults,
                    testResults = testResults,
                    methodBodies = emptyList(),
                    analysisDurationMs = analysisDuration
                )
            }
            
            val results = analysisResults.await()
            
            // 3. 生成综合报告
            val comprehensiveReport = generateComprehensiveReport(changes, results)
            
            SwingUtilities.invokeLater {
                toolWindowPanel.updateComprehensiveReport(comprehensiveReport)
            }
            
            AnalysisResult(
                success = true,
                message = "分析完成",
                intentResults = results.intentResults,
                riskResults = results.riskResults,
                testResults = results.testResults,
                comprehensiveReport = comprehensiveReport
            )
            
        } catch (e: Exception) {
            SwingUtilities.invokeLater {
                toolWindowPanel.updateIntentAnalysis("❌ 意图分析失败: ${e.message}")
                toolWindowPanel.updateRiskAnalysis("❌ 风险分析失败: ${e.message}")
                toolWindowPanel.updateComprehensiveReport("❌ 分析失败: ${e.message}")
            }
            
            AnalysisResult(
                success = false,
                message = "分析失败: ${e.message}",
                intentResults = emptyMap(),
                riskResults = emptyMap(),
                testResults = emptyList(),
                comprehensiveReport = "分析失败，请查看错误日志"
            )
        }
    }
    
    /**
     * 模拟意图分析
     */
    private fun mockIntentAnalysis(changes: List<CodeChange>): Map<String, Double> {
        return changes.mapIndexed { index, change ->
            val fileName = change.filePath.substringAfterLast("/").substringBeforeLast(".")
            val methodName = "method_$fileName$index"
            val intentWeight = when {
                change.filePath.contains("service", true) -> 0.8 + (index * 0.05) % 0.2
                change.filePath.contains("controller", true) -> 0.7 + (index * 0.03) % 0.2
                change.filePath.contains("entity", true) -> 0.4 + (index * 0.02) % 0.2
                change.filePath.contains("util", true) -> 0.3 + (index * 0.01) % 0.2
                else -> 0.5 + (index * 0.02) % 0.3
            }
            methodName to kotlin.math.min(1.0, intentWeight)
        }.toMap()
    }
    
    /**
     * 模拟风险分析
     */
    private fun mockRiskAnalysis(changes: List<CodeChange>): Map<String, Double> {
        return changes.mapIndexed { index, change ->
            val fileName = change.filePath.substringAfterLast("/").substringBeforeLast(".")
            val methodName = "method_$fileName$index"
            val riskWeight = when {
                change.changeType == ChangeType.DELETED -> 0.9 + (index * 0.02) % 0.1
                change.addedLines.size > 50 -> 0.7 + (index * 0.03) % 0.2
                change.modifiedLines.size > 20 -> 0.6 + (index * 0.02) % 0.2
                change.filePath.contains("test", true) -> 0.2 + (index * 0.01) % 0.2
                else -> 0.4 + (index * 0.02) % 0.3
            }
            methodName to kotlin.math.min(1.0, riskWeight)
        }.toMap()
    }
    
    /**
     * 模拟测试分析
     */
    private fun mockTestAnalysis(changes: List<CodeChange>): List<String> {
        return changes.mapNotNull { change ->
            val fileName = change.filePath.substringAfterLast("/").substringBeforeLast(".")
            if (!change.filePath.contains("test", true)) {
                "${fileName}Test.kt"
            } else null
        }
    }
    
    /**
     * 格式化意图分析结果
     */
    private fun formatIntentAnalysisResult(results: Map<String, Double>): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        
        return buildString {
            appendLine("🎯 意图权重分析结果")
            appendLine("分析时间: $timestamp")
            appendLine("=".repeat(50))
            appendLine()
            
            if (results.isEmpty()) {
                appendLine("📝 未检测到需要分析的方法")
                return@buildString
            }
            
            // 按权重排序
            val sortedResults = results.entries.sortedByDescending { it.value }
            
            appendLine("🔍 检测到 ${results.size} 个方法的意图权重:")
            appendLine()
            
            sortedResults.forEachIndexed { index, (methodId, weight) ->
                val priority = when {
                    weight >= 0.8 -> "🔥 高优先级"
                    weight >= 0.6 -> "⚡ 中优先级"  
                    weight >= 0.4 -> "📊 一般优先级"
                    else -> "💤 低优先级"
                }
                
                appendLine("${index + 1}. $priority")
                appendLine("   方法: ${methodId.substringAfterLast('.')}")
                appendLine("   权重: ${String.format("%.3f", weight)}")
                appendLine("   类别: ${getIntentCategory(weight)}")
                appendLine()
            }
            
            appendLine("📈 统计信息:")
            appendLine("   平均权重: ${String.format("%.3f", results.values.average())}")
            appendLine("   最高权重: ${String.format("%.3f", results.values.maxOrNull() ?: 0.0)}")
            appendLine("   最低权重: ${String.format("%.3f", results.values.minOrNull() ?: 0.0)}")
        }
    }
    
    /**
     * 格式化风险分析结果
     */
    private fun formatRiskAnalysisResult(results: Map<String, Double>): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        
        return buildString {
            appendLine("⚠️ 风险权重分析结果")
            appendLine("分析时间: $timestamp")
            appendLine("=".repeat(50))
            appendLine()
            
            if (results.isEmpty()) {
                appendLine("✅ 未检测到风险方法")
                return@buildString
            }
            
            // 按风险等级排序
            val sortedResults = results.entries.sortedByDescending { it.value }
            
            appendLine("🚨 检测到 ${results.size} 个方法的风险权重:")
            appendLine()
            
            sortedResults.forEachIndexed { index, (methodId, risk) ->
                val riskLevel = when {
                    risk >= 0.8 -> "🚨 严重风险"
                    risk >= 0.6 -> "⚠️ 高风险"
                    risk >= 0.4 -> "⚡ 中等风险"
                    else -> "✅ 低风险"
                }
                
                appendLine("${index + 1}. $riskLevel")
                appendLine("   方法: ${methodId.substringAfterLast('.')}")
                appendLine("   风险值: ${String.format("%.3f", risk)}")
                appendLine("   影响范围: ${getRiskImpact(risk)}")
                appendLine()
            }
            
            // 风险建议
            val highRiskCount = results.values.count { it >= 0.6 }
            if (highRiskCount > 0) {
                appendLine("🔧 风险建议:")
                appendLine("   - 发现 $highRiskCount 个高风险方法，建议优先审查")
                appendLine("   - 考虑增加单元测试覆盖")
                appendLine("   - 评估是否需要重构以降低复杂度")
            }
            
            appendLine()
            appendLine("📊 风险统计:")
            appendLine("   平均风险: ${String.format("%.3f", results.values.average())}")
            appendLine("   最高风险: ${String.format("%.3f", results.values.maxOrNull() ?: 0.0)}")
            appendLine("   低风险方法: ${results.values.count { it < 0.4 }} 个")
            appendLine("   高风险方法: ${results.values.count { it >= 0.6 }} 个")
        }
    }
    
    /**
     * 生成综合报告
     */
    private fun generateComprehensiveReport(
        changes: List<CodeChange>, 
        results: AnalysisResults
    ): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        
        return buildString {
            appendLine("📋 代码变更综合分析报告")
            appendLine("生成时间: $timestamp")
            appendLine("分析耗时: ${results.analysisDurationMs}ms")
            appendLine("=".repeat(60))
            appendLine()
            
            // 变更概览
            appendLine("📊 变更概览:")
            appendLine("   变更文件: ${changes.size} 个")
            appendLine("   检测方法: ${results.intentResults.size} 个")
            appendLine("   测试覆盖: ${results.testResults.size} 个相关测试")
            appendLine()
            
            // 优先级评估
            val totalIntent = results.intentResults.values.sum()
            val totalRisk = results.riskResults.values.sum()
            val overallPriority = if (totalIntent > 0) totalIntent / results.intentResults.size else 0.0
            val overallRisk = if (totalRisk > 0) totalRisk / results.riskResults.size else 0.0
            
            appendLine("🎯 整体评估:")
            appendLine("   意图权重: ${String.format("%.3f", overallPriority)} (${getIntentCategory(overallPriority)})")
            appendLine("   风险权重: ${String.format("%.3f", overallRisk)} (${getRiskCategory(overallRisk)})")
            appendLine("   综合评分: ${calculateOverallScore(overallPriority, overallRisk)}")
            appendLine()
            
            // 关键路径分析
            appendLine("🛤️ 关键路径分析:")
            val goldenPaths = results.intentResults.keys.take(3)
            
            if (goldenPaths.isNotEmpty()) {
                goldenPaths.forEachIndexed { index, path ->
                    appendLine("   ${index + 1}. ${path.substringAfterLast('.')}")
                }
            } else {
                appendLine("   未识别到关键路径")
            }
            appendLine()
            
            // 测试建议
            appendLine("🧪 测试覆盖建议:")
            if (results.testResults.isEmpty()) {
                appendLine("   ⚠️ 未发现相关测试用例，建议添加单元测试")
            } else {
                appendLine("   ✅ 建议添加以下测试:")
                results.testResults.take(3).forEach { test ->
                    appendLine("   - $test")
                }
            }
            appendLine()
            
            // 最终建议
            appendLine("💡 审查建议:")
            when {
                overallRisk >= 0.7 -> {
                    appendLine("   🚨 高风险变更，需要详细审查")
                    appendLine("   - 重点关注架构影响和性能问题")
                    appendLine("   - 建议增加集成测试")
                    appendLine("   - 考虑分阶段发布")
                }
                overallRisk >= 0.4 -> {
                    appendLine("   ⚠️ 中等风险变更，建议仔细审查")
                    appendLine("   - 关注代码逻辑正确性")
                    appendLine("   - 确保有足够的测试覆盖")
                }
                else -> {
                    appendLine("   ✅ 低风险变更，可正常审查")
                    appendLine("   - 关注代码风格和最佳实践")
                }
            }
            
            if (overallPriority >= 0.7) {
                appendLine("   🎯 高业务价值变更，优先处理")
            }
        }
    }
    
    // 辅助方法
    private fun getIntentCategory(weight: Double): String = when {
        weight >= 0.8 -> "核心业务逻辑"
        weight >= 0.6 -> "重要功能"
        weight >= 0.4 -> "一般功能"
        else -> "辅助功能"
    }
    
    private fun getRiskCategory(risk: Double): String = when {
        risk >= 0.8 -> "严重风险"
        risk >= 0.6 -> "高风险"
        risk >= 0.4 -> "中等风险"
        else -> "低风险"
    }
    
    private fun getRiskImpact(risk: Double): String = when {
        risk >= 0.8 -> "系统级影响"
        risk >= 0.6 -> "模块级影响" 
        risk >= 0.4 -> "局部影响"
        else -> "最小影响"
    }
    
    private fun calculateOverallScore(intent: Double, risk: Double): String {
        val score = ((intent * 0.6 + (1.0 - risk) * 0.4) * 100).toInt()
        return when {
            score >= 85 -> "$score/100 (优秀)"
            score >= 70 -> "$score/100 (良好)"
            score >= 60 -> "$score/100 (一般)" 
            else -> "$score/100 (需改进)"
        }
    }
    
    fun dispose() {
        coroutineScope.cancel()
    }
}

/**
 * 分析结果数据类
 */
data class AnalysisResults(
    val intentResults: Map<String, Double>,
    val riskResults: Map<String, Double>,
    val testResults: List<String>,
    val methodBodies: List<String>,
    val analysisDurationMs: Long
)

/**
 * 最终分析结果
 */
data class AnalysisResult(
    val success: Boolean,
    val message: String,
    val intentResults: Map<String, Double>,
    val riskResults: Map<String, Double>,
    val testResults: List<String>,
    val comprehensiveReport: String
)