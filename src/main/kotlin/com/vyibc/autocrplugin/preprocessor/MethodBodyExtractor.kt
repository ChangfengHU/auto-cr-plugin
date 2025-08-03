package com.vyibc.autocrplugin.preprocessor

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import com.vyibc.autocrplugin.analyzer.FileAnalysisResult
import com.vyibc.autocrplugin.graph.model.MethodNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 方法体提取器 - V5.1版本
 * 完整方法体提取、变更前后对比、依赖分析
 */
class MethodBodyExtractor(private val project: Project) {
    
    /**
     * 提取方法的完整内容和依赖信息
     */
    suspend fun extractMethodBody(
        method: MethodNode,
        includeCallGraph: Boolean = true,
        includeDependencies: Boolean = true
    ): MethodBodyExtraction = withContext(Dispatchers.IO) {
        
        val psiMethod = findPsiMethod(method) 
            ?: return@withContext createEmptyExtraction(method)
        
        // 提取方法基本信息
        val basicInfo = extractBasicInfo(psiMethod)
        
        // 提取方法体代码
        val methodBody = extractMethodBodyCode(psiMethod)
        
        // 分析局部变量
        val localVariables = if (includeDependencies) {
            analyzeLocalVariables(psiMethod)
        } else emptyList()
        
        // 分析方法调用
        val methodCalls = if (includeCallGraph) {
            analyzeMethodCalls(psiMethod)
        } else emptyList()
        
        // 分析字段访问
        val fieldAccesses = if (includeDependencies) {
            analyzeFieldAccesses(psiMethod)
        } else emptyList()
        
        // 分析异常处理
        val exceptionHandling = analyzeExceptionHandling(psiMethod)
        
        // 分析控制流
        val controlFlow = analyzeControlFlow(psiMethod)
        
        // 提取注释和文档
        val documentation = extractDocumentation(psiMethod)
        
        // 分析数据流
        val dataFlow = analyzeDataFlow(psiMethod)
        
        MethodBodyExtraction(
            method = method,
            basicInfo = basicInfo,
            methodBody = methodBody,
            localVariables = localVariables,
            methodCalls = methodCalls,
            fieldAccesses = fieldAccesses,
            exceptionHandling = exceptionHandling,
            controlFlow = controlFlow,
            documentation = documentation,
            dataFlow = dataFlow,
            extractionTimestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * 比较方法的变更前后版本
     */
    suspend fun compareMethodVersions(
        oldMethod: MethodNode,
        newMethod: MethodNode
    ): MethodChangeAnalysis = withContext(Dispatchers.Default) {
        
        val oldExtraction = extractMethodBody(oldMethod, true, true)
        val newExtraction = extractMethodBody(newMethod, true, true)
        
        // 比较基本信息变更
        val basicInfoChanges = compareBasicInfo(oldExtraction.basicInfo, newExtraction.basicInfo)
        
        // 比较方法体变更
        val bodyChanges = compareMethodBody(oldExtraction.methodBody, newExtraction.methodBody)
        
        // 比较依赖变更
        val dependencyChanges = compareDependencies(oldExtraction, newExtraction)
        
        // 比较控制流变更
        val controlFlowChanges = compareControlFlow(oldExtraction.controlFlow, newExtraction.controlFlow)
        
        // 分析变更影响
        val impactAnalysis = analyzeChangeImpact(bodyChanges, dependencyChanges, controlFlowChanges)
        
        MethodChangeAnalysis(
            oldMethod = oldMethod,
            newMethod = newMethod,
            basicInfoChanges = basicInfoChanges,
            bodyChanges = bodyChanges,
            dependencyChanges = dependencyChanges,
            controlFlowChanges = controlFlowChanges,
            impactAnalysis = impactAnalysis,
            changeComplexity = calculateChangeComplexity(bodyChanges, dependencyChanges),
            riskLevel = assessChangeRisk(impactAnalysis, changeComplexity = 0.0)
        )
    }
    
    /**
     * 查找PSI方法
     */
    private fun findPsiMethod(method: MethodNode): PsiMethod? {
        // 简化实现：通过类名和方法名查找
        val className = method.id.substringBefore("#")
        val methodName = method.methodName
        
        val psiFacade = JavaPsiFacade.getInstance(project)
        val scope = com.intellij.psi.search.GlobalSearchScope.projectScope(project)
        
        val psiClass = psiFacade.findClass(className, scope) ?: return null
        
        return psiClass.methods.find { psiMethod ->
            psiMethod.name == methodName &&
            psiMethod.parameterList.parameters.size == method.paramTypes.size
        }
    }
    
    /**
     * 提取基本信息
     */
    private fun extractBasicInfo(psiMethod: PsiMethod): MethodBasicInfo {
        val modifiers = psiMethod.modifierList.children
            .filterIsInstance<PsiKeyword>()
            .map { it.text }
        
        val annotations = psiMethod.annotations.map { annotation ->
            MethodAnnotation(
                name = annotation.qualifiedName ?: "",
                parameters = extractAnnotationParameters(annotation)
            )
        }
        
        val parameters = psiMethod.parameterList.parameters.map { param ->
            MethodParameter(
                name = param.name ?: "",
                type = param.type.canonicalText,
                annotations = param.annotations.map { it.qualifiedName ?: "" }
            )
        }
        
        return MethodBasicInfo(
            name = psiMethod.name,
            returnType = psiMethod.returnType?.canonicalText ?: "void",
            modifiers = modifiers,
            annotations = annotations,
            parameters = parameters,
            throwsTypes = psiMethod.throwsList.referencedTypes.map { it.canonicalText }
        )
    }
    
    /**
     * 提取方法体代码
     */
    private fun extractMethodBodyCode(psiMethod: PsiMethod): MethodBodyCode {
        val body = psiMethod.body
        val codeText = body?.text ?: ""
        
        // 分析代码结构
        val statements = if (body != null) {
            PsiTreeUtil.findChildrenOfType(body, PsiStatement::class.java).map { stmt ->
                CodeStatement(
                    type = stmt.javaClass.simpleName,
                    text = stmt.text.take(200), // 限制长度
                    lineNumber = getLineNumber(stmt),
                    startOffset = stmt.textRange.startOffset,
                    endOffset = stmt.textRange.endOffset
                )
            }
        } else emptyList()
        
        // 提取代码块
        val codeBlocks = if (body != null) {
            extractCodeBlocks(body)
        } else emptyList()
        
        return MethodBodyCode(
            fullText = codeText,
            statements = statements,
            codeBlocks = codeBlocks,
            linesOfCode = codeText.lines().size,
            complexity = calculateMethodComplexity(psiMethod)
        )
    }
    
    /**
     * 分析局部变量
     */
    private fun analyzeLocalVariables(psiMethod: PsiMethod): List<LocalVariable> {
        val variables = mutableListOf<LocalVariable>()
        val body = psiMethod.body ?: return variables
        
        PsiTreeUtil.findChildrenOfType(body, PsiDeclarationStatement::class.java).forEach { stmt ->
            stmt.declaredElements.filterIsInstance<PsiLocalVariable>().forEach { variable ->
                variables.add(LocalVariable(
                    name = variable.name,
                    type = variable.type.canonicalText,
                    initializer = variable.initializer?.text,
                    isFinal = variable.hasModifierProperty(PsiModifier.FINAL),
                    declarationLine = getLineNumber(variable)
                ))
            }
        }
        
        return variables
    }
    
    /**
     * 分析方法调用
     */
    private fun analyzeMethodCalls(psiMethod: PsiMethod): List<MethodCall> {
        val calls = mutableListOf<MethodCall>()
        val body = psiMethod.body ?: return calls
        
        PsiTreeUtil.findChildrenOfType(body, PsiMethodCallExpression::class.java).forEach { callExpr ->
            val resolvedMethod = callExpr.resolveMethod()
            calls.add(MethodCall(
                methodName = callExpr.methodExpression.referenceName ?: "",
                className = resolvedMethod?.containingClass?.qualifiedName,
                arguments = callExpr.argumentList.expressions.map { it.text },
                isStatic = resolvedMethod?.hasModifierProperty(PsiModifier.STATIC) ?: false,
                lineNumber = getLineNumber(callExpr),
                callType = determineCallType(callExpr)
            ))
        }
        
        return calls
    }
    
    /**
     * 分析字段访问
     */
    private fun analyzeFieldAccesses(psiMethod: PsiMethod): List<FieldAccess> {
        val accesses = mutableListOf<FieldAccess>()
        val body = psiMethod.body ?: return accesses
        
        PsiTreeUtil.findChildrenOfType(body, PsiReferenceExpression::class.java).forEach { ref ->
            val resolved = ref.resolve()
            if (resolved is PsiField) {
                accesses.add(FieldAccess(
                    fieldName = resolved.name,
                    className = resolved.containingClass?.qualifiedName,
                    accessType = determineAccessType(ref),
                    isStatic = resolved.hasModifierProperty(PsiModifier.STATIC),
                    lineNumber = getLineNumber(ref)
                ))
            }
        }
        
        return accesses
    }
    
    /**
     * 分析异常处理
     */
    private fun analyzeExceptionHandling(psiMethod: PsiMethod): ExceptionHandling {
        val body = psiMethod.body ?: return ExceptionHandling()
        
        val tryStatements = PsiTreeUtil.findChildrenOfType(body, PsiTryStatement::class.java)
        val throwStatements = PsiTreeUtil.findChildrenOfType(body, PsiThrowStatement::class.java)
        
        val tryBlocks = tryStatements.map { tryStmt ->
            TryBlock(
                tryBody = tryStmt.tryBlock?.text ?: "",
                catchBlocks = tryStmt.catchSections.map { catchSection ->
                    CatchBlock(
                        exceptionType = catchSection.parameter?.type?.canonicalText ?: "",
                        catchBody = catchSection.catchBlock?.text ?: "",
                        lineNumber = getLineNumber(catchSection)
                    )
                },
                finallyBlock = tryStmt.finallyBlock?.text,
                lineNumber = getLineNumber(tryStmt)
            )
        }
        
        val thrownExceptions = throwStatements.map { throwStmt ->
            ThrownException(
                exceptionType = throwStmt.exception?.type?.canonicalText ?: "",
                message = throwStmt.exception?.text ?: "",
                lineNumber = getLineNumber(throwStmt)
            )
        }
        
        return ExceptionHandling(
            tryBlocks = tryBlocks,
            thrownExceptions = thrownExceptions,
            declaredExceptions = psiMethod.throwsList.referencedTypes.map { it.canonicalText }
        )
    }
    
    /**
     * 分析控制流
     */
    private fun analyzeControlFlow(psiMethod: PsiMethod): ControlFlow {
        val body = psiMethod.body ?: return ControlFlow()
        
        val branches = mutableListOf<ControlFlowBranch>()
        val loops = mutableListOf<ControlFlowLoop>()
        
        // 分析分支语句
        PsiTreeUtil.findChildrenOfType(body, PsiIfStatement::class.java).forEach { ifStmt ->
            branches.add(ControlFlowBranch(
                type = "if",
                condition = ifStmt.condition?.text ?: "",
                lineNumber = getLineNumber(ifStmt)
            ))
        }
        
        PsiTreeUtil.findChildrenOfType(body, PsiSwitchStatement::class.java).forEach { switchStmt ->
            branches.add(ControlFlowBranch(
                type = "switch",
                condition = switchStmt.expression?.text ?: "",
                lineNumber = getLineNumber(switchStmt)
            ))
        }
        
        // 分析循环语句
        PsiTreeUtil.findChildrenOfType(body, PsiForStatement::class.java).forEach { forStmt ->
            loops.add(ControlFlowLoop(
                type = "for",
                condition = forStmt.condition?.text ?: "",
                lineNumber = getLineNumber(forStmt)
            ))
        }
        
        PsiTreeUtil.findChildrenOfType(body, PsiWhileStatement::class.java).forEach { whileStmt ->
            loops.add(ControlFlowLoop(
                type = "while",
                condition = whileStmt.condition?.text ?: "",
                lineNumber = getLineNumber(whileStmt)
            ))
        }
        
        return ControlFlow(
            branches = branches,
            loops = loops,
            cyclomaticComplexity = calculateCyclomaticComplexity(body)
        )
    }
    
    /**
     * 提取文档
     */
    private fun extractDocumentation(psiMethod: PsiMethod): MethodDocumentation {
        val docComment = psiMethod.docComment
        
        return MethodDocumentation(
            javadoc = docComment?.text ?: "",
            description = extractDescription(docComment),
            parameters = extractParameterDocs(docComment),
            returnDoc = extractReturnDoc(docComment),
            throwsDoc = extractThrowsDocs(docComment),
            seeAlso = extractSeeAlso(docComment)
        )
    }
    
    /**
     * 分析数据流
     */
    private fun analyzeDataFlow(psiMethod: PsiMethod): DataFlowAnalysis {
        val body = psiMethod.body ?: return DataFlowAnalysis()
        
        // 简化的数据流分析
        val dataInputs = mutableListOf<DataInput>()
        val dataOutputs = mutableListOf<DataOutput>()
        
        // 分析参数作为输入
        psiMethod.parameterList.parameters.forEach { param ->
            dataInputs.add(DataInput(
                name = param.name ?: "",
                type = param.type.canonicalText,
                source = "parameter"
            ))
        }
        
        // 分析返回语句作为输出
        PsiTreeUtil.findChildrenOfType(body, PsiReturnStatement::class.java).forEach { returnStmt ->
            val returnValue = returnStmt.returnValue
            if (returnValue != null) {
                dataOutputs.add(DataOutput(
                    name = "return",
                    type = returnValue.type?.canonicalText ?: "",
                    lineNumber = getLineNumber(returnStmt)
                ))
            }
        }
        
        return DataFlowAnalysis(
            inputs = dataInputs,
            outputs = dataOutputs,
            sideEffects = analyzeSideEffects(body)
        )
    }
    
    // 辅助方法
    
    private fun createEmptyExtraction(method: MethodNode): MethodBodyExtraction {
        return MethodBodyExtraction(
            method = method,
            basicInfo = MethodBasicInfo("", "", emptyList(), emptyList(), emptyList(), emptyList()),
            methodBody = MethodBodyCode("", emptyList(), emptyList(), 0, 0),
            localVariables = emptyList(),
            methodCalls = emptyList(),
            fieldAccesses = emptyList(),
            exceptionHandling = ExceptionHandling(),
            controlFlow = ControlFlow(),
            documentation = MethodDocumentation("", "", emptyList(), "", emptyList(), emptyList()),
            dataFlow = DataFlowAnalysis(),
            extractionTimestamp = System.currentTimeMillis()
        )
    }
    
    private fun extractAnnotationParameters(annotation: PsiAnnotation): Map<String, String> {
        // 简化实现
        return emptyMap()
    }
    
    private fun extractCodeBlocks(body: PsiCodeBlock): List<CodeBlock> {
        // 简化实现
        return emptyList()
    }
    
    private fun calculateMethodComplexity(psiMethod: PsiMethod): Int {
        val body = psiMethod.body ?: return 1
        return calculateCyclomaticComplexity(body)
    }
    
    private fun calculateCyclomaticComplexity(codeBlock: PsiCodeBlock): Int {
        var complexity = 1
        
        // 简化的圈复杂度计算
        complexity += PsiTreeUtil.findChildrenOfType(codeBlock, PsiIfStatement::class.java).size
        complexity += PsiTreeUtil.findChildrenOfType(codeBlock, PsiWhileStatement::class.java).size
        complexity += PsiTreeUtil.findChildrenOfType(codeBlock, PsiForStatement::class.java).size
        complexity += PsiTreeUtil.findChildrenOfType(codeBlock, PsiSwitchStatement::class.java).size
        complexity += PsiTreeUtil.findChildrenOfType(codeBlock, PsiCatchSection::class.java).size
        
        return complexity
    }
    
    private fun getLineNumber(element: PsiElement): Int {
        val document = element.containingFile.viewProvider.document ?: return -1
        return document.getLineNumber(element.textOffset) + 1
    }
    
    private fun determineCallType(callExpr: PsiMethodCallExpression): String {
        return when {
            callExpr.methodExpression.qualifier == null -> "this"
            callExpr.methodExpression.qualifier is PsiSuperExpression -> "super"
            else -> "external"
        }
    }
    
    private fun determineAccessType(ref: PsiReferenceExpression): String {
        val parent = ref.parent
        return when {
            parent is PsiAssignmentExpression && parent.lExpression == ref -> "write"
            else -> "read"
        }
    }
    
    private fun extractDescription(docComment: PsiDocComment?): String {
        // 简化实现
        return docComment?.descriptionElements?.joinToString(" ") { it.text } ?: ""
    }
    
    private fun extractParameterDocs(docComment: PsiDocComment?): List<String> {
        // 简化实现
        return emptyList()
    }
    
    private fun extractReturnDoc(docComment: PsiDocComment?): String {
        // 简化实现
        return ""
    }
    
    private fun extractThrowsDocs(docComment: PsiDocComment?): List<String> {
        // 简化实现
        return emptyList()
    }
    
    private fun extractSeeAlso(docComment: PsiDocComment?): List<String> {
        // 简化实现
        return emptyList()
    }
    
    private fun analyzeSideEffects(body: PsiCodeBlock): List<String> {
        // 简化实现：检查常见的副作用
        val sideEffects = mutableListOf<String>()
        
        if (PsiTreeUtil.findChildOfType(body, PsiMethodCallExpression::class.java) != null) {
            sideEffects.add("method_calls")
        }
        
        return sideEffects
    }
    
    // 比较方法
    
    private fun compareBasicInfo(old: MethodBasicInfo, new: MethodBasicInfo): List<String> {
        val changes = mutableListOf<String>()
        
        if (old.returnType != new.returnType) {
            changes.add("return_type_changed")
        }
        
        if (old.modifiers != new.modifiers) {
            changes.add("modifiers_changed")
        }
        
        if (old.parameters.size != new.parameters.size) {
            changes.add("parameter_count_changed")
        }
        
        return changes
    }
    
    private fun compareMethodBody(old: MethodBodyCode, new: MethodBodyCode): BodyChanges {
        return BodyChanges(
            linesChanged = (new.linesOfCode - old.linesOfCode),
            statementsChanged = (new.statements.size - old.statements.size),
            complexityChanged = (new.complexity - old.complexity),
            textSimilarity = calculateTextSimilarity(old.fullText, new.fullText)
        )
    }
    
    private fun compareDependencies(old: MethodBodyExtraction, new: MethodBodyExtraction): DependencyChanges {
        return DependencyChanges(
            addedCalls = new.methodCalls.size - old.methodCalls.size,
            removedCalls = maxOf(0, old.methodCalls.size - new.methodCalls.size),
            addedFieldAccesses = new.fieldAccesses.size - old.fieldAccesses.size,
            removedFieldAccesses = maxOf(0, old.fieldAccesses.size - new.fieldAccesses.size)
        )
    }
    
    private fun compareControlFlow(old: ControlFlow, new: ControlFlow): ControlFlowChanges {
        return ControlFlowChanges(
            branchesChanged = new.branches.size - old.branches.size,
            loopsChanged = new.loops.size - old.loops.size,
            complexityChanged = new.cyclomaticComplexity - old.cyclomaticComplexity
        )
    }
    
    private fun analyzeChangeImpact(
        bodyChanges: BodyChanges,
        dependencyChanges: DependencyChanges,
        controlFlowChanges: ControlFlowChanges
    ): ChangeImpactAnalysis {
        val impactLevel = when {
            controlFlowChanges.complexityChanged > 5 -> "HIGH"
            bodyChanges.linesChanged > 50 -> "MEDIUM"
            dependencyChanges.addedCalls > 3 -> "MEDIUM"
            else -> "LOW"
        }
        
        return ChangeImpactAnalysis(
            impactLevel = impactLevel,
            affectedAreas = listOf("logic", "dependencies", "control_flow"),
            riskFactors = calculateRiskFactors(bodyChanges, dependencyChanges, controlFlowChanges)
        )
    }
    
    private fun calculateChangeComplexity(
        bodyChanges: BodyChanges,
        dependencyChanges: DependencyChanges
    ): Double {
        val bodyComplexity = kotlin.math.abs(bodyChanges.linesChanged) * 0.1
        val dependencyComplexity = (dependencyChanges.addedCalls + dependencyChanges.removedCalls) * 0.2
        
        return bodyComplexity + dependencyComplexity
    }
    
    private fun assessChangeRisk(
        impactAnalysis: ChangeImpactAnalysis,
        changeComplexity: Double
    ): String {
        return when {
            impactAnalysis.impactLevel == "HIGH" || changeComplexity > 10 -> "HIGH"
            impactAnalysis.impactLevel == "MEDIUM" || changeComplexity > 5 -> "MEDIUM"
            else -> "LOW"
        }
    }
    
    private fun calculateTextSimilarity(text1: String, text2: String): Double {
        // 简化的文本相似度计算
        val words1 = text1.split("\\s+".toRegex()).toSet()
        val words2 = text2.split("\\s+".toRegex()).toSet()
        
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        
        return if (union > 0) intersection.toDouble() / union else 0.0
    }
    
    private fun calculateRiskFactors(
        bodyChanges: BodyChanges,
        dependencyChanges: DependencyChanges,
        controlFlowChanges: ControlFlowChanges
    ): List<String> {
        val factors = mutableListOf<String>()
        
        if (controlFlowChanges.complexityChanged > 0) {
            factors.add("increased_complexity")
        }
        
        if (dependencyChanges.addedCalls > 0) {
            factors.add("new_dependencies")
        }
        
        if (bodyChanges.linesChanged > 20) {
            factors.add("significant_code_change")
        }
        
        return factors
    }
}

// 数据类定义

/**
 * 方法体提取结果
 */
data class MethodBodyExtraction(
    val method: MethodNode,
    val basicInfo: MethodBasicInfo,
    val methodBody: MethodBodyCode,
    val localVariables: List<LocalVariable>,
    val methodCalls: List<MethodCall>,
    val fieldAccesses: List<FieldAccess>,
    val exceptionHandling: ExceptionHandling,
    val controlFlow: ControlFlow,
    val documentation: MethodDocumentation,
    val dataFlow: DataFlowAnalysis,
    val extractionTimestamp: Long
)

/**
 * 方法基本信息
 */
data class MethodBasicInfo(
    val name: String,
    val returnType: String,
    val modifiers: List<String>,
    val annotations: List<MethodAnnotation>,
    val parameters: List<MethodParameter>,
    val throwsTypes: List<String>
)

/**
 * 方法注解
 */
data class MethodAnnotation(
    val name: String,
    val parameters: Map<String, String>
)

/**
 * 方法参数
 */
data class MethodParameter(
    val name: String,
    val type: String,
    val annotations: List<String>
)

/**
 * 方法体代码
 */
data class MethodBodyCode(
    val fullText: String,
    val statements: List<CodeStatement>,
    val codeBlocks: List<CodeBlock>,
    val linesOfCode: Int,
    val complexity: Int
)

/**
 * 代码语句
 */
data class CodeStatement(
    val type: String,
    val text: String,
    val lineNumber: Int,
    val startOffset: Int,
    val endOffset: Int
)

/**
 * 代码块
 */
data class CodeBlock(
    val type: String,
    val content: String,
    val startLine: Int,
    val endLine: Int
)

/**
 * 局部变量
 */
data class LocalVariable(
    val name: String,
    val type: String,
    val initializer: String?,
    val isFinal: Boolean,
    val declarationLine: Int
)

/**
 * 方法调用
 */
data class MethodCall(
    val methodName: String,
    val className: String?,
    val arguments: List<String>,
    val isStatic: Boolean,
    val lineNumber: Int,
    val callType: String
)

/**
 * 字段访问
 */
data class FieldAccess(
    val fieldName: String,
    val className: String?,
    val accessType: String,
    val isStatic: Boolean,
    val lineNumber: Int
)

/**
 * 异常处理
 */
data class ExceptionHandling(
    val tryBlocks: List<TryBlock> = emptyList(),
    val thrownExceptions: List<ThrownException> = emptyList(),
    val declaredExceptions: List<String> = emptyList()
)

/**
 * Try块
 */
data class TryBlock(
    val tryBody: String,
    val catchBlocks: List<CatchBlock>,
    val finallyBlock: String?,
    val lineNumber: Int
)

/**
 * Catch块
 */
data class CatchBlock(
    val exceptionType: String,
    val catchBody: String,
    val lineNumber: Int
)

/**
 * 抛出异常
 */
data class ThrownException(
    val exceptionType: String,
    val message: String,
    val lineNumber: Int
)

/**
 * 控制流
 */
data class ControlFlow(
    val branches: List<ControlFlowBranch> = emptyList(),
    val loops: List<ControlFlowLoop> = emptyList(),
    val cyclomaticComplexity: Int = 1
)

/**
 * 控制流分支
 */
data class ControlFlowBranch(
    val type: String,
    val condition: String,
    val lineNumber: Int
)

/**
 * 控制流循环
 */
data class ControlFlowLoop(
    val type: String,
    val condition: String,
    val lineNumber: Int
)

/**
 * 方法文档
 */
data class MethodDocumentation(
    val javadoc: String,
    val description: String,
    val parameters: List<String>,
    val returnDoc: String,
    val throwsDoc: List<String>,
    val seeAlso: List<String>
)

/**
 * 数据流分析
 */
data class DataFlowAnalysis(
    val inputs: List<DataInput> = emptyList(),
    val outputs: List<DataOutput> = emptyList(),
    val sideEffects: List<String> = emptyList()
)

/**
 * 数据输入
 */
data class DataInput(
    val name: String,
    val type: String,
    val source: String
)

/**
 * 数据输出
 */
data class DataOutput(
    val name: String,
    val type: String,
    val lineNumber: Int
)

/**
 * 方法变更分析
 */
data class MethodChangeAnalysis(
    val oldMethod: MethodNode,
    val newMethod: MethodNode,
    val basicInfoChanges: List<String>,
    val bodyChanges: BodyChanges,
    val dependencyChanges: DependencyChanges,
    val controlFlowChanges: ControlFlowChanges,
    val impactAnalysis: ChangeImpactAnalysis,
    val changeComplexity: Double,
    val riskLevel: String
)

/**
 * 方法体变更
 */
data class BodyChanges(
    val linesChanged: Int,
    val statementsChanged: Int,
    val complexityChanged: Int,
    val textSimilarity: Double
)

/**
 * 依赖变更
 */
data class DependencyChanges(
    val addedCalls: Int,
    val removedCalls: Int,
    val addedFieldAccesses: Int,
    val removedFieldAccesses: Int
)

/**
 * 控制流变更
 */
data class ControlFlowChanges(
    val branchesChanged: Int,
    val loopsChanged: Int,
    val complexityChanged: Int
)

/**
 * 变更影响分析
 */
data class ChangeImpactAnalysis(
    val impactLevel: String,
    val affectedAreas: List<String>,
    val riskFactors: List<String>
)