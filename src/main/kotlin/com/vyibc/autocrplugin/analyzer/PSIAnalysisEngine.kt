package com.vyibc.autocrplugin.analyzer

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.vyibc.autocrplugin.graph.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * PSI解析引擎 - V5.1版本
 * Java/Kotlin AST遍历、方法调用关系提取、注解识别
 */
class PSIAnalysisEngine(private val project: Project) {
    
    /**
     * 分析单个PSI文件
     */
    suspend fun analyzeFile(psiFile: PsiFile): FileAnalysisResult = withContext(Dispatchers.IO) {
        when (psiFile) {
            is PsiJavaFile -> analyzeJavaFile(psiFile)
            is PsiClassOwner -> analyzeKotlinFile(psiFile)
            else -> FileAnalysisResult(
                filePath = psiFile.virtualFile.path,
                classes = emptyList(),
                methods = emptyList(),
                callRelationships = emptyList()
            )
        }
    }
    
    /**
     * 分析Java文件
     */
    private fun analyzeJavaFile(javaFile: PsiJavaFile): FileAnalysisResult {
        val classes = mutableListOf<ClassAnalysis>()
        val methods = mutableListOf<MethodAnalysis>()
        val callRelationships = mutableListOf<CallRelationship>()
        
        // 遍历所有类
        javaFile.classes.forEach { psiClass ->
            val classAnalysis = analyzeClass(psiClass)
            classes.add(classAnalysis)
            
            // 分析类中的方法
            psiClass.methods.forEach { psiMethod ->
                val methodAnalysis = analyzeMethod(psiMethod, psiClass)
                methods.add(methodAnalysis)
                
                // 提取方法调用关系
                val calls = extractMethodCalls(psiMethod)
                callRelationships.addAll(calls)
            }
        }
        
        return FileAnalysisResult(
            filePath = javaFile.virtualFile.path,
            classes = classes,
            methods = methods,
            callRelationships = callRelationships
        )
    }
    
    /**
     * 分析Kotlin文件
     */
    private fun analyzeKotlinFile(kotlinFile: PsiClassOwner): FileAnalysisResult {
        val classes = mutableListOf<ClassAnalysis>()
        val methods = mutableListOf<MethodAnalysis>()
        val callRelationships = mutableListOf<CallRelationship>()
        
        // 遍历所有类
        kotlinFile.classes.forEach { psiClass ->
            val classAnalysis = analyzeClass(psiClass)
            classes.add(classAnalysis)
            
            // 分析类中的方法
            psiClass.methods.forEach { psiMethod ->
                val methodAnalysis = analyzeMethod(psiMethod, psiClass)
                methods.add(methodAnalysis)
                
                // 提取方法调用关系
                val calls = extractMethodCalls(psiMethod)
                callRelationships.addAll(calls)
            }
        }
        
        return FileAnalysisResult(
            filePath = kotlinFile.virtualFile.path,
            classes = classes,
            methods = methods,
            callRelationships = callRelationships
        )
    }
    
    /**
     * 分析类
     */
    private fun analyzeClass(psiClass: PsiClass): ClassAnalysis {
        val annotations = extractAnnotations(psiClass)
        val blockType = detectClassBlockType(psiClass, annotations)
        val interfaces = psiClass.interfaces.map { it.qualifiedName ?: it.name ?: "Unknown" }
        val superClass = psiClass.superClass?.qualifiedName
        
        // 计算质量指标
        val methods = psiClass.methods
        val fields = psiClass.fields
        val (cohesion, coupling) = calculateClassMetrics(psiClass)
        val designPatterns = detectDesignPatterns(psiClass)
        
        return ClassAnalysis(
            className = psiClass.name ?: "Anonymous",
            qualifiedName = psiClass.qualifiedName ?: "",
            packageName = (psiClass.containingFile as? PsiJavaFile)?.packageName ?: "",
            blockType = blockType,
            isInterface = psiClass.isInterface,
            isAbstract = psiClass.hasModifierProperty(PsiModifier.ABSTRACT),
            annotations = annotations,
            implementedInterfaces = interfaces,
            superClass = superClass,
            methodCount = methods.size,
            fieldCount = fields.size,
            cohesion = cohesion,
            coupling = coupling,
            designPatterns = designPatterns
        )
    }
    
    /**
     * 分析方法
     */
    private fun analyzeMethod(psiMethod: PsiMethod, containingClass: PsiClass): MethodAnalysis {
        val annotations = extractAnnotations(psiMethod)
        val parameters = psiMethod.parameterList.parameters.map { param ->
            param.type.canonicalText
        }
        
        // 计算方法复杂度
        val complexity = calculateCyclomaticComplexity(psiMethod)
        val linesOfCode = calculateLinesOfCode(psiMethod)
        
        // 构建方法ID
        val methodId = buildMethodId(containingClass, psiMethod)
        
        // 检测方法类型
        val blockType = detectMethodBlockType(psiMethod, containingClass, annotations)
        
        return MethodAnalysis(
            methodId = methodId,
            methodName = psiMethod.name,
            signature = buildMethodSignature(psiMethod),
            returnType = psiMethod.returnType?.canonicalText ?: "void",
            paramTypes = parameters,
            blockType = blockType,
            isInterface = psiMethod.containingClass?.isInterface ?: false,
            annotations = annotations,
            filePath = psiMethod.containingFile.virtualFile.path,
            lineNumber = getLineNumber(psiMethod),
            startLine = getStartLineNumber(psiMethod),
            endLine = getEndLineNumber(psiMethod),
            cyclomaticComplexity = complexity,
            linesOfCode = linesOfCode,
            hasTests = hasAssociatedTests(psiMethod)
        )
    }
    
    /**
     * 提取方法调用关系
     */
    private fun extractMethodCalls(psiMethod: PsiMethod): List<CallRelationship> {
        val callRelationships = mutableListOf<CallRelationship>()
        val callerId = buildMethodId(psiMethod.containingClass!!, psiMethod)
        
        // 遍历方法体查找方法调用
        psiMethod.accept(object : JavaRecursiveElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                
                val resolvedMethod = expression.resolveMethod()
                if (resolvedMethod != null && resolvedMethod.containingClass != null) {
                    val calleeId = buildMethodId(resolvedMethod.containingClass!!, resolvedMethod)
                    val callType = determineCallType(expression)
                    val lineNumber = getLineNumber(expression)
                    val isConditional = isInConditionalContext(expression)
                    val context = getCallContext(expression)
                    
                    callRelationships.add(CallRelationship(
                        callerId = callerId,
                        calleeId = calleeId,
                        callType = callType,
                        lineNumber = lineNumber,
                        isConditional = isConditional,
                        context = context
                    ))
                }
            }
        })
        
        return callRelationships
    }
    
    /**
     * 提取注解
     */
    private fun extractAnnotations(element: PsiModifierListOwner): List<String> {
        return element.modifierList?.annotations?.map { annotation ->
            annotation.qualifiedName ?: annotation.nameReferenceElement?.text ?: ""
        } ?: emptyList()
    }
    
    /**
     * 检测类的架构层级
     */
    private fun detectClassBlockType(psiClass: PsiClass, annotations: List<String>): BlockType {
        val className = psiClass.name?.lowercase() ?: ""
        val annotationTexts = annotations.map { it.lowercase() }
        
        return when {
            annotationTexts.any { it.contains("controller") || it.contains("restcontroller") } -> BlockType.CONTROLLER
            annotationTexts.any { it.contains("service") } -> BlockType.SERVICE
            annotationTexts.any { it.contains("repository") } -> BlockType.REPOSITORY
            annotationTexts.any { it.contains("mapper") } -> BlockType.MAPPER
            annotationTexts.any { it.contains("entity") } -> BlockType.ENTITY
            className.contains("controller") -> BlockType.CONTROLLER
            className.contains("service") -> BlockType.SERVICE
            className.contains("repository") || className.contains("dao") -> BlockType.REPOSITORY
            className.contains("mapper") -> BlockType.MAPPER
            className.contains("entity") || className.contains("model") -> BlockType.ENTITY
            className.contains("dto") -> BlockType.DTO
            className.contains("vo") -> BlockType.VO
            className.contains("util") || className.contains("helper") -> BlockType.UTIL
            className.contains("config") -> BlockType.CONFIG
            className.contains("test") -> BlockType.TEST
            else -> BlockType.OTHER
        }
    }
    
    /**
     * 检测方法的架构层级
     */
    private fun detectMethodBlockType(
        psiMethod: PsiMethod, 
        containingClass: PsiClass,
        annotations: List<String>
    ): BlockType {
        // 优先使用类的层级
        val classBlockType = detectClassBlockType(containingClass, extractAnnotations(containingClass))
        if (classBlockType != BlockType.OTHER) {
            return classBlockType
        }
        
        // 基于方法注解判断
        val annotationTexts = annotations.map { it.lowercase() }
        return when {
            annotationTexts.any { it.contains("mapping") } -> BlockType.CONTROLLER
            annotationTexts.any { it.contains("transactional") } -> BlockType.SERVICE
            else -> BlockType.OTHER
        }
    }
    
    /**
     * 计算圈复杂度
     */
    private fun calculateCyclomaticComplexity(psiMethod: PsiMethod): Int {
        var complexity = 1 // 基础复杂度
        
        psiMethod.accept(object : JavaRecursiveElementVisitor() {
            override fun visitIfStatement(statement: PsiIfStatement) {
                super.visitIfStatement(statement)
                complexity++
            }
            
            override fun visitForStatement(statement: PsiForStatement) {
                super.visitForStatement(statement)
                complexity++
            }
            
            override fun visitWhileStatement(statement: PsiWhileStatement) {
                super.visitWhileStatement(statement)
                complexity++
            }
            
            override fun visitDoWhileStatement(statement: PsiDoWhileStatement) {
                super.visitDoWhileStatement(statement)
                complexity++
            }
            
            override fun visitSwitchStatement(statement: PsiSwitchStatement) {
                super.visitSwitchStatement(statement)
                complexity += statement.body?.statements?.filterIsInstance<PsiSwitchLabelStatement>()?.size ?: 0
            }
            
            override fun visitConditionalExpression(expression: PsiConditionalExpression) {
                super.visitConditionalExpression(expression)
                complexity++
            }
            
            override fun visitCatchSection(section: PsiCatchSection) {
                super.visitCatchSection(section)
                complexity++
            }
        })
        
        return complexity
    }
    
    /**
     * 计算代码行数
     */
    private fun calculateLinesOfCode(psiMethod: PsiMethod): Int {
        val startLine = getStartLineNumber(psiMethod)
        val endLine = getEndLineNumber(psiMethod)
        return if (startLine > 0 && endLine > 0) endLine - startLine + 1 else 0
    }
    
    /**
     * 计算类的内聚性和耦合度
     */
    private fun calculateClassMetrics(psiClass: PsiClass): Pair<Double, Double> {
        val methods = psiClass.methods.filter { !it.isConstructor }
        val fields = psiClass.fields
        
        if (methods.isEmpty() || fields.isEmpty()) {
            return Pair(1.0, 0.0)
        }
        
        // 计算内聚性 (LCOM - Lack of Cohesion of Methods 的反向)
        var sharedFieldAccess = 0
        var totalPairs = 0
        
        for (i in methods.indices) {
            for (j in i + 1 until methods.size) {
                totalPairs++
                if (shareFields(methods[i], methods[j], fields)) {
                    sharedFieldAccess++
                }
            }
        }
        
        val cohesion = if (totalPairs > 0) sharedFieldAccess.toDouble() / totalPairs else 1.0
        
        // 计算耦合度 (基于外部依赖)
        val externalDependencies = countExternalDependencies(psiClass)
        val coupling = minOf(externalDependencies / 10.0, 1.0)
        
        return Pair(cohesion, coupling)
    }
    
    /**
     * 检查两个方法是否共享字段访问
     */
    private fun shareFields(method1: PsiMethod, method2: PsiMethod, classFields: Array<PsiField>): Boolean {
        val fields1 = getAccessedFields(method1, classFields)
        val fields2 = getAccessedFields(method2, classFields)
        return fields1.intersect(fields2).isNotEmpty()
    }
    
    /**
     * 获取方法访问的字段
     */
    private fun getAccessedFields(method: PsiMethod, classFields: Array<PsiField>): Set<PsiField> {
        val accessedFields = mutableSetOf<PsiField>()
        
        method.accept(object : JavaRecursiveElementVisitor() {
            override fun visitReferenceExpression(expression: PsiReferenceExpression) {
                super.visitReferenceExpression(expression)
                val resolved = expression.resolve()
                if (resolved is PsiField && resolved in classFields) {
                    accessedFields.add(resolved)
                }
            }
        })
        
        return accessedFields
    }
    
    /**
     * 计算外部依赖数
     */
    private fun countExternalDependencies(psiClass: PsiClass): Int {
        val dependencies = mutableSetOf<String>()
        val currentPackage = (psiClass.containingFile as? PsiJavaFile)?.packageName ?: ""
        
        psiClass.accept(object : JavaRecursiveElementVisitor() {
            override fun visitTypeElement(type: PsiTypeElement) {
                super.visitTypeElement(type)
                val qualifiedName = type.type.canonicalText
                if (!qualifiedName.startsWith("java.") && 
                    !qualifiedName.startsWith(currentPackage) &&
                    !isPrimitiveType(qualifiedName)) {
                    dependencies.add(qualifiedName)
                }
            }
        })
        
        return dependencies.size
    }
    
    /**
     * 检测设计模式
     */
    private fun detectDesignPatterns(psiClass: PsiClass): List<String> {
        val patterns = mutableListOf<String>()
        
        // Singleton检测
        if (hasSingletonPattern(psiClass)) {
            patterns.add("Singleton")
        }
        
        // Factory检测
        if (hasFactoryPattern(psiClass)) {
            patterns.add("Factory")
        }
        
        // Observer检测
        if (hasObserverPattern(psiClass)) {
            patterns.add("Observer")
        }
        
        // Strategy检测
        if (hasStrategyPattern(psiClass)) {
            patterns.add("Strategy")
        }
        
        return patterns
    }
    
    /**
     * 检测是否有关联的测试
     */
    private fun hasAssociatedTests(psiMethod: PsiMethod): Boolean {
        val methodName = psiMethod.name
        val className = psiMethod.containingClass?.name ?: return false
        
        // 搜索测试类
        val testClassName = "${className}Test"
        val scope = GlobalSearchScope.projectScope(project)
        val psiManager = PsiManager.getInstance(project)
        
        // 简化实现：检查是否存在对应的测试类
        return JavaPsiFacade.getInstance(project).findClass(testClassName, scope) != null
    }
    
    /**
     * 辅助方法
     */
    private fun buildMethodId(containingClass: PsiClass, method: PsiMethod): String {
        val className = containingClass.qualifiedName ?: containingClass.name ?: "Unknown"
        val params = method.parameterList.parameters.joinToString(",") { it.type.canonicalText }
        return "$className#${method.name}($params)"
    }
    
    private fun buildMethodSignature(method: PsiMethod): String {
        val params = method.parameterList.parameters.joinToString(", ") { 
            "${it.type.presentableText} ${it.name}"
        }
        return "${method.name}($params)"
    }
    
    private fun getLineNumber(element: PsiElement): Int {
        val file = element.containingFile.viewProvider.document ?: return -1
        return file.getLineNumber(element.textOffset) + 1
    }
    
    private fun getStartLineNumber(element: PsiElement): Int {
        val file = element.containingFile.viewProvider.document ?: return -1
        return file.getLineNumber(element.textRange.startOffset) + 1
    }
    
    private fun getEndLineNumber(element: PsiElement): Int {
        val file = element.containingFile.viewProvider.document ?: return -1
        return file.getLineNumber(element.textRange.endOffset) + 1
    }
    
    private fun determineCallType(expression: PsiMethodCallExpression): CallType {
        return when {
            expression.methodExpression.qualifierExpression is PsiSuperExpression -> CallType.DIRECT
            expression.methodExpression.qualifierExpression is PsiThisExpression -> CallType.DIRECT
            isLambdaCall(expression) -> CallType.LAMBDA
            isMethodReference(expression) -> CallType.METHOD_REF
            else -> CallType.DIRECT
        }
    }
    
    private fun isInConditionalContext(expression: PsiElement): Boolean {
        return PsiTreeUtil.getParentOfType(expression, 
            PsiIfStatement::class.java,
            PsiConditionalExpression::class.java,
            PsiSwitchStatement::class.java
        ) != null
    }
    
    private fun getCallContext(expression: PsiElement): String? {
        return when {
            PsiTreeUtil.getParentOfType(expression, PsiTryStatement::class.java) != null -> "try-catch"
            PsiTreeUtil.getParentOfType(expression, PsiIfStatement::class.java) != null -> "if"
            PsiTreeUtil.getParentOfType(expression, PsiLoopStatement::class.java) != null -> "loop"
            else -> null
        }
    }
    
    private fun isPrimitiveType(typeName: String): Boolean {
        return typeName in setOf("int", "long", "double", "float", "boolean", "char", "byte", "short", "void")
    }
    
    private fun isLambdaCall(expression: PsiMethodCallExpression): Boolean {
        return expression.parent is PsiLambdaExpression
    }
    
    private fun isMethodReference(expression: PsiMethodCallExpression): Boolean {
        return expression.parent is PsiMethodReferenceExpression
    }
    
    private fun hasSingletonPattern(psiClass: PsiClass): Boolean {
        val hasPrivateConstructor = psiClass.constructors.any { 
            it.hasModifierProperty(PsiModifier.PRIVATE) 
        }
        val hasStaticInstance = psiClass.fields.any { 
            it.hasModifierProperty(PsiModifier.STATIC) && 
            it.type.canonicalText == psiClass.qualifiedName 
        }
        return hasPrivateConstructor && hasStaticInstance
    }
    
    private fun hasFactoryPattern(psiClass: PsiClass): Boolean {
        return psiClass.methods.any { method ->
            method.hasModifierProperty(PsiModifier.STATIC) &&
            method.returnType != null &&
            method.name.lowercase().contains("create")
        }
    }
    
    private fun hasObserverPattern(psiClass: PsiClass): Boolean {
        val hasListenerList = psiClass.fields.any { field ->
            field.type.canonicalText.contains("List") &&
            field.name.lowercase().contains("listener")
        }
        val hasNotifyMethod = psiClass.methods.any { method ->
            method.name.lowercase().contains("notify")
        }
        return hasListenerList && hasNotifyMethod
    }
    
    private fun hasStrategyPattern(psiClass: PsiClass): Boolean {
        return psiClass.isInterface && 
               psiClass.methods.size == 1 &&
               !psiClass.methods[0].hasModifierProperty(PsiModifier.DEFAULT)
    }
}

/**
 * 文件分析结果
 */
data class FileAnalysisResult(
    val filePath: String,
    val classes: List<ClassAnalysis>,
    val methods: List<MethodAnalysis>,
    val callRelationships: List<CallRelationship>
)

/**
 * 类分析结果
 */
data class ClassAnalysis(
    val className: String,
    val qualifiedName: String,
    val packageName: String,
    val blockType: BlockType,
    val isInterface: Boolean,
    val isAbstract: Boolean,
    val annotations: List<String>,
    val implementedInterfaces: List<String>,
    val superClass: String?,
    val methodCount: Int,
    val fieldCount: Int,
    val cohesion: Double,
    val coupling: Double,
    val designPatterns: List<String>
)

/**
 * 方法分析结果
 */
data class MethodAnalysis(
    val methodId: String,
    val methodName: String,
    val signature: String,
    val returnType: String,
    val paramTypes: List<String>,
    val blockType: BlockType,
    val isInterface: Boolean,
    val annotations: List<String>,
    val filePath: String,
    val lineNumber: Int,
    val startLine: Int,
    val endLine: Int,
    val cyclomaticComplexity: Int,
    val linesOfCode: Int,
    val hasTests: Boolean
)

/**
 * 调用关系
 */
data class CallRelationship(
    val callerId: String,
    val calleeId: String,
    val callType: CallType,
    val lineNumber: Int,
    val isConditional: Boolean,
    val context: String?
)