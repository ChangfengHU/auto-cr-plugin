package com.vyibc.autocrplugin.ai

/**
 * AI Prompt模板系统 - V5.1版本
 * 快速筛选和深度分析的完整Prompt模板
 */
class AIPromptTemplateSystem {
    
    /**
     * 获取快速筛选Prompt
     */
    fun getQuickScreeningPrompt(
        context: CompressedContext,
        focusAreas: Set<FocusArea> = setOf(FocusArea.RISK_ASSESSMENT, FocusArea.CODE_QUALITY)
    ): String {
        val systemPrompt = buildString {
            appendLine("你是一个专业的代码评审助手，专注于快速识别代码变更中的关键风险和质量问题。")
            appendLine("请基于提供的代码变更信息，进行快速评估并给出结构化的反馈。")
            appendLine()
            appendLine("评估重点:")
            focusAreas.forEach { area ->
                appendLine("- ${area.description}")
            }
            appendLine()
            appendLine("输出格式要求:")
            appendLine("1. 风险等级: [高/中/低]")
            appendLine("2. 主要问题: [列举1-3个最重要的问题]")
            appendLine("3. 建议行动: [简要的改进建议]")
            appendLine("4. 需要深度分析: [是/否]")
        }
        
        val userPrompt = buildContextPrompt(context, PromptMode.QUICK)
        
        return buildCompletePrompt(systemPrompt, userPrompt)
    }
    
    /**
     * 获取深度分析Prompt
     */
    fun getDeepAnalysisPrompt(
        context: CompressedContext,
        analysisScope: AnalysisScope = AnalysisScope.COMPREHENSIVE
    ): String {
        val systemPrompt = buildString {
            appendLine("你是一个资深的代码评审专家，具备丰富的软件架构和代码质量经验。")
            appendLine("请对提供的代码变更进行全面深入的分析，识别潜在的架构风险、性能问题、安全隐患等。")
            appendLine()
            appendLine("分析范围: ${analysisScope.description}")
            appendLine()
            appendLine("分析维度:")
            appendLine("1. 架构影响分析")
            appendLine("   - 对现有架构的影响")
            appendLine("   - 耦合度变化")
            appendLine("   - 可维护性影响")
            appendLine()
            appendLine("2. 性能影响评估")
            appendLine("   - 时间复杂度分析")
            appendLine("   - 空间复杂度分析")
            appendLine("   - 潜在性能瓶颈")
            appendLine()
            appendLine("3. 安全风险识别")
            appendLine("   - 输入验证")
            appendLine("   - 权限控制")
            appendLine("   - 数据泄露风险")
            appendLine()
            appendLine("4. 代码质量评估")
            appendLine("   - 代码规范")
            appendLine("   - 可读性")
            appendLine("   - 测试覆盖度")
            appendLine()
            appendLine("5. 业务逻辑审查")
            appendLine("   - 业务规则正确性")
            appendLine("   - 边界条件处理")
            appendLine("   - 异常情况处理")
            appendLine()
            appendLine("请按以下JSON格式输出分析结果:")
            appendLine(getDeepAnalysisJsonSchema())
        }
        
        val userPrompt = buildContextPrompt(context, PromptMode.DEEP)
        
        return buildCompletePrompt(systemPrompt, userPrompt)
    }
    
    /**
     * 获取意图权重分析Prompt
     */
    fun getIntentAnalysisPrompt(context: CompressedContext, callPaths: List<CallPathInfo>): String {
        val systemPrompt = buildString {
            appendLine("你是一个代码意图分析专家，专门识别代码变更的业务意图和实现完整性。")
            appendLine("请分析提供的代码变更，评估其业务价值、实现质量和代码复杂度。")
            appendLine()
            appendLine("分析要点:")
            appendLine("1. 业务价值评估 (40%权重)")
            appendLine("   - 功能重要性: 核心业务功能 > 辅助功能 > 工具功能")
            appendLine("   - 用户影响: 直接用户交互 > 后端逻辑 > 配置变更")
            appendLine("   - 业务关联度: 主业务流程 > 分支业务 > 边缘功能")
            appendLine()
            appendLine("2. 实现完整性评估 (35%权重)")
            appendLine("   - 功能完整性: 是否完整实现预期功能")
            appendLine("   - 边界处理: 异常情况和边界条件处理")
            appendLine("   - 向后兼容: 是否影响现有功能")
            appendLine()
            appendLine("3. 代码质量评分 (25%权重)")
            appendLine("   - 代码规范: 命名、注释、结构")
            appendLine("   - 复杂度控制: 圈复杂度、认知复杂度")
            appendLine("   - 可维护性: 模块化、可扩展性")
            appendLine()
            appendLine("请为每个调用路径计算意图权重分数 (0-1分)，并提供详细分析依据。")
        }
        
        val userPrompt = buildIntentAnalysisPrompt(context, callPaths)
        
        return buildCompletePrompt(systemPrompt, userPrompt)
    }
    
    /**
     * 获取风险权重分析Prompt
     */
    fun getRiskAnalysisPrompt(context: CompressedContext, callPaths: List<CallPathInfo>): String {
        val systemPrompt = buildString {
            appendLine("你是一个代码风险评估专家，专门识别代码变更可能带来的技术风险和业务风险。")
            appendLine("请分析提供的代码变更，评估其架构风险、影响范围和变更复杂度。")
            appendLine()
            appendLine("风险评估维度:")
            appendLine("1. 架构风险评分 (35%权重)")
            appendLine("   - 核心组件变更: 数据库、缓存、消息队列等")
            appendLine("   - 接口变更: 公共API、内部接口变更")
            appendLine("   - 依赖关系: 新增或修改关键依赖")
            appendLine()
            appendLine("2. 爆炸半径评分 (30%权重)")
            appendLine("   - 影响范围: 受变更影响的模块/组件数量")
            appendLine("   - 调用深度: 调用链的深度和广度")
            appendLine("   - 数据影响: 对数据结构和存储的影响")
            appendLine()
            appendLine("3. 变更复杂度评分 (20%权重)")
            appendLine("   - 代码行数: 变更的代码量大小")
            appendLine("   - 逻辑复杂度: 分支、循环等控制结构")
            appendLine("   - 业务复杂度: 涉及的业务规则复杂程度")
            appendLine()
            appendLine("4. 测试覆盖度风险 (15%权重)")
            appendLine("   - 测试完整性: 单元测试、集成测试覆盖")
            appendLine("   - 测试质量: 测试用例的有效性")
            appendLine("   - 验证方式: 自动化测试vs手工测试")
            appendLine()
            appendLine("请为每个调用路径计算风险权重分数 (0-1分)，并提供详细风险分析。")
        }
        
        val userPrompt = buildRiskAnalysisPrompt(context, callPaths)
        
        return buildCompletePrompt(systemPrompt, userPrompt)
    }
    
    /**
     * 获取测试建议Prompt
     */
    fun getTestRecommendationPrompt(context: CompressedContext): String {
        val systemPrompt = buildString {
            appendLine("你是一个测试策略专家，专门为代码变更提供测试建议和测试用例设计。")
            appendLine("请基于代码变更分析，提供全面的测试策略和具体的测试用例建议。")
            appendLine()
            appendLine("测试建议框架:")
            appendLine("1. 测试策略")
            appendLine("   - 测试类型: 单元测试、集成测试、端到端测试")
            appendLine("   - 测试优先级: 高/中/低")
            appendLine("   - 测试覆盖度目标")
            appendLine()
            appendLine("2. 测试用例设计")
            appendLine("   - 正常流程测试")
            appendLine("   - 边界条件测试")
            appendLine("   - 异常情况测试")
            appendLine("   - 性能测试")
            appendLine("   - 安全测试")
            appendLine()
            appendLine("3. 回归测试建议")
            appendLine("   - 需要回归测试的模块")
            appendLine("   - 自动化测试建议")
            appendLine("   - 手工测试要点")
            appendLine()
            appendLine("请提供结构化的测试建议，包括具体的测试用例和验证步骤。")
        }
        
        val userPrompt = buildTestRecommendationPrompt(context)
        
        return buildCompletePrompt(systemPrompt, userPrompt)
    }
    
    /**
     * 构建上下文Prompt
     */
    private fun buildContextPrompt(context: CompressedContext, mode: PromptMode): String {
        return buildString {
            appendLine("## 项目信息")
            appendLine("项目名称: ${context.projectInfo.name}")
            appendLine("编程语言: ${context.projectInfo.language}")
            appendLine("框架: ${context.projectInfo.framework}")
            if (context.projectInfo.description.isNotEmpty()) {
                appendLine("项目描述: ${context.projectInfo.description}")
            }
            appendLine()
            
            appendLine("## 分支信息")
            appendLine("当前分支: ${context.branchInfo.currentBranch}")
            appendLine("目标分支: ${context.branchInfo.targetBranch}")
            appendLine("领先提交: ${context.branchInfo.ahead}")
            appendLine("落后提交: ${context.branchInfo.behind}")
            appendLine()
            
            appendLine("## 提交信息")
            appendLine("提交哈希: ${context.commitInfo.hash}")
            appendLine("提交消息: ${context.commitInfo.message}")
            appendLine("提交作者: ${context.commitInfo.author}")
            appendLine("变更文件: ${context.commitInfo.files.joinToString(", ")}")
            appendLine()
            
            if (context.methods.isNotEmpty()) {
                appendLine("## 变更方法")
                context.methods.forEachIndexed { index, method ->
                    appendLine("### 方法 ${index + 1}")
                    appendLine("签名: ${method.signature}")
                    appendLine("复杂度: ${method.complexity}")
                    appendLine("风险分数: ${"%.2f".format(method.riskScore)}")
                    appendLine("测试覆盖率: ${"%.1f".format(method.testCoverage * 100)}%")
                    
                    if (mode == PromptMode.DEEP) {
                        appendLine("代码内容:")
                        appendLine("```")
                        appendLine(method.body)
                        appendLine("```")
                        
                        if (method.comments != null) {
                            appendLine("注释:")
                            appendLine(method.comments)
                        }
                    }
                    appendLine()
                }
            }
            
            if (context.diffs.isNotEmpty()) {
                appendLine("## 代码变更")
                context.diffs.forEachIndexed { index, diff ->
                    appendLine("### 变更 ${index + 1}")
                    appendLine("文件: ${diff.filePath}")
                    appendLine("类型: ${diff.changeType}")
                    
                    if (mode == PromptMode.DEEP) {
                        if (diff.before.isNotEmpty()) {
                            appendLine("变更前:")
                            appendLine("```")
                            appendLine(diff.before)
                            appendLine("```")
                        }
                        
                        appendLine("变更后:")
                        appendLine("```")
                        appendLine(diff.after)
                        appendLine("```")
                    }
                    appendLine()
                }
            }
            
            if (context.callPaths.isNotEmpty()) {
                appendLine("## 关键调用路径")
                context.callPaths.forEachIndexed { index, path ->
                    appendLine("### 路径 ${index + 1}")
                    appendLine("路径: ${path.path}")
                    appendLine("风险权重: ${"%.2f".format(path.riskWeight)}")
                    appendLine("意图权重: ${"%.2f".format(path.intentWeight)}")
                    appendLine("涉及方法: ${path.methods.joinToString(" -> ")}")
                    appendLine()
                }
            }
            
            context.testResults?.let { testResults ->
                appendLine("## 测试结果")
                appendLine("通过: ${testResults.passed}")
                appendLine("失败: ${testResults.failed}")
                appendLine("跳过: ${testResults.skipped}")
                appendLine("覆盖率: ${"%.1f".format(testResults.coverage * 100)}%")
                
                if (testResults.failures.isNotEmpty()) {
                    appendLine("失败测试:")
                    testResults.failures.forEach { failure ->
                        appendLine("- $failure")
                    }
                }
                appendLine()
            }
            
            appendLine("## 压缩信息")
            appendLine("压缩策略: ${context.strategy}")
            appendLine("压缩比例: ${"%.1f".format(context.compressionRatio * 100)}%")
        }
    }
    
    /**
     * 构建意图分析Prompt
     */
    private fun buildIntentAnalysisPrompt(context: CompressedContext, callPaths: List<CallPathInfo>): String {
        return buildString {
            append(buildContextPrompt(context, PromptMode.QUICK))
            appendLine()
            appendLine("## 需要分析的调用路径")
            callPaths.forEachIndexed { index, path ->
                appendLine("### 路径 ${index + 1}")
                appendLine("路径描述: ${path.path}")
                appendLine("当前风险权重: ${"%.3f".format(path.riskWeight)}")
                appendLine("当前意图权重: ${"%.3f".format(path.intentWeight)}")
                appendLine("调用链: ${path.methods.joinToString(" -> ")}")
                appendLine()
            }
            appendLine("请为每个路径重新计算意图权重分数，并说明计算依据。")
        }
    }
    
    /**
     * 构建风险分析Prompt
     */
    private fun buildRiskAnalysisPrompt(context: CompressedContext, callPaths: List<CallPathInfo>): String {
        return buildString {
            append(buildContextPrompt(context, PromptMode.QUICK))
            appendLine()
            appendLine("## 需要分析的调用路径")
            callPaths.forEachIndexed { index, path ->
                appendLine("### 路径 ${index + 1}")
                appendLine("路径描述: ${path.path}")
                appendLine("当前风险权重: ${"%.3f".format(path.riskWeight)}")
                appendLine("当前意图权重: ${"%.3f".format(path.intentWeight)}")
                appendLine("调用链: ${path.methods.joinToString(" -> ")}")
                appendLine()
            }
            appendLine("请为每个路径重新计算风险权重分数，并提供详细的风险分析报告。")
        }
    }
    
    /**
     * 构建测试建议Prompt
     */
    private fun buildTestRecommendationPrompt(context: CompressedContext): String {
        return buildString {
            append(buildContextPrompt(context, PromptMode.DEEP))
            appendLine()
            appendLine("## 测试建议要求")
            appendLine("基于上述代码变更，请提供:")
            appendLine("1. 针对每个变更方法的测试用例建议")
            appendLine("2. 整体测试策略和优先级")
            appendLine("3. 回归测试范围建议")
            appendLine("4. 性能测试和安全测试建议")
            appendLine("5. 测试自动化建议")
        }
    }
    
    /**
     * 构建完整Prompt
     */
    private fun buildCompletePrompt(systemPrompt: String, userPrompt: String): String {
        return buildString {
            appendLine("## SYSTEM")
            appendLine(systemPrompt)
            appendLine()
            appendLine("## USER")
            appendLine(userPrompt)
        }
    }
    
    /**
     * 获取深度分析JSON Schema
     */
    private fun getDeepAnalysisJsonSchema(): String {
        return """
{
  "analysis": {
    "overall_risk": "string", // 高/中/低
    "confidence": "number", // 0-1
    "architecture": {
      "impact_level": "string", // 高/中/低
      "affected_components": ["string"],
      "coupling_changes": "string",
      "maintainability_impact": "string"
    },
    "performance": {
      "time_complexity": "string",
      "space_complexity": "string",
      "potential_bottlenecks": ["string"],
      "optimization_suggestions": ["string"]
    },
    "security": {
      "risk_level": "string", // 高/中/低
      "vulnerabilities": ["string"],
      "recommendations": ["string"]
    },
    "code_quality": {
      "score": "number", // 0-10
      "issues": ["string"],
      "best_practices": ["string"]
    },
    "business_logic": {
      "correctness": "string", // 正确/可疑/错误
      "edge_cases": ["string"],
      "error_handling": "string"
    },
    "recommendations": {
      "immediate_actions": ["string"],
      "long_term_improvements": ["string"],
      "testing_suggestions": ["string"]
    }
  }
}
        """.trimIndent()
    }
}

/**
 * 焦点领域
 */
enum class FocusArea(val description: String) {
    RISK_ASSESSMENT("风险评估 - 识别潜在的技术和业务风险"),
    CODE_QUALITY("代码质量 - 评估代码规范、可读性、维护性"),
    PERFORMANCE("性能影响 - 分析对系统性能的影响"),
    SECURITY("安全审查 - 识别安全漏洞和风险点"),
    ARCHITECTURE("架构影响 - 评估对系统架构的影响"),
    BUSINESS_LOGIC("业务逻辑 - 验证业务规则的正确性"),
    TESTING("测试完整性 - 评估测试覆盖度和质量")
}

/**
 * 分析范围
 */
enum class AnalysisScope(val description: String) {
    COMPREHENSIVE("全面分析 - 涵盖所有维度的深度分析"),
    FOCUSED("重点分析 - 针对高风险区域的深度分析"),
    QUICK("快速分析 - 基础的风险和质量检查")
}

/**
 * Prompt模式
 */
enum class PromptMode {
    QUICK,  // 快速模式 - 简化上下文
    DEEP    // 深度模式 - 完整上下文
}