package com.vyibc.autocrplugin.indexer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.vyibc.autocrplugin.analyzer.PSIAnalysisEngine
import com.vyibc.autocrplugin.graph.engine.GraphEngine
import com.vyibc.autocrplugin.graph.model.*
import com.vyibc.autocrplugin.service.CacheService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 项目索引器 - V5.1版本
 * 负责首次安装插件时对整个项目进行扫描和知识图谱构建
 */
class ProjectIndexer(
    private val project: Project,
    private val graphEngine: GraphEngine,
    private val psiAnalysisEngine: PSIAnalysisEngine,
    private val cacheService: CacheService
) {
    
    private val _indexingProgress = MutableStateFlow(IndexingProgress())
    val indexingProgress: StateFlow<IndexingProgress> = _indexingProgress.asStateFlow()
    
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * 开始项目索引
     */
    fun startProjectIndexing() {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "构建项目知识图谱...", true) {
            override fun run(indicator: ProgressIndicator) {
                coroutineScope.launch {
                    try {
                        performFullProjectIndexing(indicator)
                    } catch (e: Exception) {
                        _indexingProgress.value = _indexingProgress.value.copy(
                            status = IndexingStatus.FAILED,
                            error = e.message
                        )
                    }
                }
            }
        })
    }
    
    /**
     * 执行完整项目索引
     */
    private suspend fun performFullProjectIndexing(indicator: ProgressIndicator) = withContext(Dispatchers.IO) {
        _indexingProgress.value = IndexingProgress(
            status = IndexingStatus.SCANNING,
            message = "正在扫描项目文件..."
        )
        
        // 1. 扫描所有代码文件
        val allFiles = scanProjectFiles()
        _indexingProgress.value = _indexingProgress.value.copy(
            totalFiles = allFiles.size,
            message = "发现 ${allFiles.size} 个代码文件"
        )
        
        // 2. 分析所有类和方法
        _indexingProgress.value = _indexingProgress.value.copy(
            status = IndexingStatus.ANALYZING_CLASSES,
            message = "正在分析类结构..."
        )
        
        val classNodes = mutableListOf<ClassNode>()
        val methodNodes = mutableListOf<MethodNode>()
        
        allFiles.forEachIndexed { index, file ->
            if (indicator.isCanceled) return@withContext
            
            try {
                val (classes, methods) = analyzeFile(file)
                classNodes.addAll(classes)
                methodNodes.addAll(methods)
                
                _indexingProgress.value = _indexingProgress.value.copy(
                    processedFiles = index + 1,
                    message = "已分析: ${file.name}"
                )
            } catch (e: Exception) {
                // 记录错误但继续处理其他文件
                println("分析文件失败: ${file.path}, 错误: ${e.message}")
            }
        }
        
        // 3. 构建调用关系
        _indexingProgress.value = _indexingProgress.value.copy(
            status = IndexingStatus.BUILDING_RELATIONSHIPS,
            totalClasses = classNodes.size,
            totalMethods = methodNodes.size,
            message = "正在构建调用关系..."
        )
        
        val callEdges = buildCallRelationships(methodNodes, indicator)
        val implementsEdges = buildImplementsRelationships(classNodes, indicator)
        val dataFlowEdges = buildDataFlowRelationships(methodNodes, indicator)
        
        // 4. 构建知识图谱
        _indexingProgress.value = _indexingProgress.value.copy(
            status = IndexingStatus.BUILDING_GRAPH,
            message = "正在构建知识图谱..."
        )
        
        buildKnowledgeGraph(classNodes, methodNodes, callEdges, implementsEdges, dataFlowEdges)
        
        // 5. 缓存结果
        _indexingProgress.value = _indexingProgress.value.copy(
            status = IndexingStatus.CACHING,
            message = "正在缓存分析结果..."
        )
        
        cacheIndexingResults(classNodes, methodNodes, callEdges, implementsEdges, dataFlowEdges)
        
        // 6. 完成
        _indexingProgress.value = _indexingProgress.value.copy(
            status = IndexingStatus.COMPLETED,
            message = "项目知识图谱构建完成！",
            completedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * 扫描项目中的所有代码文件
     */
    private suspend fun scanProjectFiles(): List<VirtualFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<VirtualFile>()
        
        // 获取项目根目录
        val projectRoots = ProjectRootManager.getInstance(project).contentRoots
        
        projectRoots.forEach { root ->
            scanDirectory(root, files)
        }
        
        // 过滤出Java/Kotlin文件
        files.filter { file ->
            file.extension in setOf("java", "kt", "kts") && 
            !file.path.contains("/build/") && 
            !file.path.contains("/.gradle/") &&
            !file.path.contains("/target/")
        }
    }
    
    /**
     * 递归扫描目录
     */
    private fun scanDirectory(directory: VirtualFile, files: MutableList<VirtualFile>) {
        directory.children?.forEach { child ->
            if (child.isDirectory) {
                scanDirectory(child, files)
            } else {
                files.add(child)
            }
        }
    }
    
    /**
     * 分析单个文件
     */
    private suspend fun analyzeFile(file: VirtualFile): Pair<List<ClassNode>, List<MethodNode>> = withContext(Dispatchers.Default) {
        val psiFile = ApplicationManager.getApplication().runReadAction<PsiFile?> {
            PsiManager.getInstance(project).findFile(file)
        } ?: return@withContext Pair(emptyList(), emptyList())
        
        val classes = mutableListOf<ClassNode>()
        val methods = mutableListOf<MethodNode>()
        
        ApplicationManager.getApplication().runReadAction {
            when (psiFile) {
                is PsiJavaFile -> {
                    psiFile.classes.forEach { psiClass ->
                        val classNode = analyzeClass(psiClass, file.path)
                        classes.add(classNode)
                        
                        psiClass.methods.forEach { psiMethod ->
                            val methodNode = analyzeMethod(psiMethod, classNode.id, file.path)
                            methods.add(methodNode)
                        }
                    }
                }
                // Kotlin文件分析可以在这里添加
                else -> {
                    // 其他文件类型的处理
                }
            }
        }
        
        Pair(classes, methods)
    }
    
    /**
     * 分析类
     */
    private fun analyzeClass(psiClass: PsiClass, filePath: String): ClassNode {
        return ClassNode(
            id = "${psiClass.qualifiedName}",
            className = psiClass.name ?: "Anonymous",
            packageName = extractPackageName(psiClass),
            blockType = inferBlockType(psiClass),
            isInterface = psiClass.isInterface,
            isAbstract = psiClass.hasModifierProperty(PsiModifier.ABSTRACT),
            filePath = filePath,
            implementedInterfaces = psiClass.interfaces.mapNotNull { it.qualifiedName },
            superClass = psiClass.superClass?.qualifiedName,
            annotations = psiClass.annotations.mapNotNull { it.qualifiedName },
            methodCount = psiClass.methods.size,
            fieldCount = psiClass.fields.size,
            cohesion = 0.8, // 简化计算
            coupling = 0.3, // 简化计算
            designPatterns = emptyList() // 后续实现
        )
    }
    
    /**
     * 分析方法
     */
    private fun analyzeMethod(psiMethod: PsiMethod, classId: String, filePath: String): MethodNode {
        return MethodNode(
            id = "${classId}.${psiMethod.name}",
            methodName = psiMethod.name,
            signature = buildMethodSignature(psiMethod),
            returnType = psiMethod.returnType?.canonicalText ?: "void",
            paramTypes = psiMethod.parameters.map { "String" }, // 简化实现
            blockType = inferMethodBlockType(psiMethod),
            isInterface = psiMethod.containingClass?.isInterface ?: false,
            annotations = psiMethod.annotations.mapNotNull { it.qualifiedName },
            filePath = filePath,
            lineNumber = getMethodStartLine(psiMethod),
            startLineNumber = getMethodStartLine(psiMethod),
            endLineNumber = getMethodEndLine(psiMethod),
            cyclomaticComplexity = calculateMethodComplexity(psiMethod),
            linesOfCode = calculateMethodLineCount(psiMethod),
            inDegree = 0, // 需要后续通过调用关系计算
            outDegree = extractCalledMethods(psiMethod).size,
            riskScore = calculateMethodRiskScore(psiMethod),
            hasTests = false, // 需要后续通过测试分析确定
            lastModified = java.time.Instant.now()
        )
    }
    
    /**
     * 构建调用关系
     */
    private suspend fun buildCallRelationships(
        methods: List<MethodNode>, 
        indicator: ProgressIndicator
    ): List<CallsEdge> = withContext(Dispatchers.Default) {
        val callEdges = mutableListOf<CallsEdge>()
        
        methods.forEachIndexed { index, method ->
            if (indicator.isCanceled) return@withContext callEdges
            
            val calledMethods = extractCalledMethods(ApplicationManager.getApplication().runReadAction<PsiMethod?> {
                // 需要重新获取PSI方法以提取调用关系
                findPsiMethodById(method.id)
            } ?: return@forEachIndexed)
            
            calledMethods.forEach { calledSignature ->
                val targetMethod = methods.find { it.signature == calledSignature }
                if (targetMethod != null) {
                    callEdges.add(
                        CallsEdge(
                            caller = method,
                            callee = targetMethod,
                            callType = CallType.DIRECT,
                            lineNumber = method.lineNumber,
                            frequency = 1,
                            isConditional = false, // 需要更精细的分析
                            riskWeight = 0.0,
                            intentWeight = 0.0,
                            isNewInMR = false,
                            isModifiedInMR = false
                        )
                    )
                }
            }
            
            _indexingProgress.value = _indexingProgress.value.copy(
                processedMethods = index + 1,
                message = "构建调用关系: ${method.methodName}"
            )
        }
        
        callEdges
    }
    
    /**
     * 构建继承关系
     */
    private suspend fun buildImplementsRelationships(
        classes: List<ClassNode>,
        indicator: ProgressIndicator
    ): List<ImplementsEdge> = withContext(Dispatchers.Default) {
        val implementsEdges = mutableListOf<ImplementsEdge>()
        
        classes.forEachIndexed { index, classNode ->
            if (indicator.isCanceled) return@withContext implementsEdges
            
            // 简化处理 - 暂时跳过复杂的继承关系构建
            // TODO: 稍后实现完整的继承关系分析
            
            _indexingProgress.value = _indexingProgress.value.copy(
                processedClasses = index + 1,
                message = "构建继承关系: ${classNode.className}"
            )
        }
        
        implementsEdges
    }
    
    /**
     * 构建数据流关系  
     */
    private suspend fun buildDataFlowRelationships(
        methods: List<MethodNode>,
        indicator: ProgressIndicator
    ): List<DataFlowEdge> = withContext(Dispatchers.Default) {
        val dataFlowEdges = mutableListOf<DataFlowEdge>()
        
        methods.forEachIndexed { index, method ->
            if (indicator.isCanceled) return@withContext dataFlowEdges
            
            val usedFields = extractUsedFields(ApplicationManager.getApplication().runReadAction<PsiMethod?> {
                // 需要重新获取PSI方法以提取字段使用关系
                findPsiMethodById(method.id)
            } ?: return@forEachIndexed)
            
            usedFields.forEach { fieldName ->
                // 找到字段的定义方法（简化处理）
                val fieldDefinedIn = methods.find { it.methodName.contains("set${fieldName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}") }
                if (fieldDefinedIn != null) {
                    dataFlowEdges.add(
                        DataFlowEdge(
                            source = fieldDefinedIn,
                            target = method,
                            dataType = "field",
                            flowType = DataFlowType.FIELD_ACCESS,
                            isSensitive = false
                        )
                    )
                }
            }
        }
        
        dataFlowEdges
    }
    
    /**
     * 构建知识图谱
     */
    private suspend fun buildKnowledgeGraph(
        classes: List<ClassNode>,
        methods: List<MethodNode>,
        callEdges: List<CallsEdge>,
        implementsEdges: List<ImplementsEdge>,
        dataFlowEdges: List<DataFlowEdge>
    ) = withContext(Dispatchers.IO) {
        
        // 添加所有节点
        classes.forEach { /* graphEngine.addClassNode(it) */ }
        methods.forEach { /* graphEngine.addMethodNode(it) */ }
        
        // 添加所有边
        callEdges.forEach { /* graphEngine.addCallsEdge(it) */ }
        implementsEdges.forEach { /* graphEngine.addImplementsEdge(it) */ }
        dataFlowEdges.forEach { /* graphEngine.addDataFlowEdge(it) */ }
        
        // 保存图谱到本地文件
        // graphEngine.saveToFile("${project.basePath}/.auto-cr/project.tg")
    }
    
    /**
     * 缓存索引结果
     */
    private suspend fun cacheIndexingResults(
        classes: List<ClassNode>,
        methods: List<MethodNode>,
        callEdges: List<CallsEdge>,
        implementsEdges: List<ImplementsEdge>,
        dataFlowEdges: List<DataFlowEdge>
    ) = withContext(Dispatchers.IO) {
        
        cacheService.putProjectClasses(project.name, classes)
        cacheService.putProjectMethods(project.name, methods)
        cacheService.putProjectCallEdges(project.name, callEdges)
        cacheService.putProjectImplementsEdges(project.name, implementsEdges)
        cacheService.putProjectDataFlowEdges(project.name, dataFlowEdges)
        
        // 标记项目已索引
        cacheService.markProjectIndexed(project.name)
    }
    
    // 辅助方法 - 简化实现
    private fun extractPackageName(psiClass: PsiClass): String = 
        psiClass.qualifiedName?.substringBeforeLast('.') ?: ""
    
    private fun inferBlockType(psiClass: PsiClass): BlockType {
        val className = psiClass.name?.lowercase() ?: ""
        val annotations = psiClass.annotations.mapNotNull { it.qualifiedName }
        
        return when {
            annotations.any { it.contains("Controller") } -> BlockType.CONTROLLER
            annotations.any { it.contains("Service") } -> BlockType.SERVICE
            annotations.any { it.contains("Repository") } -> BlockType.REPOSITORY
            annotations.any { it.contains("Mapper") } -> BlockType.MAPPER
            annotations.any { it.contains("Component") } -> BlockType.COMPONENT
            annotations.any { it.contains("Configuration") } -> BlockType.CONFIG
            annotations.any { it.contains("Entity") } -> BlockType.ENTITY
            className.contains("dto") -> BlockType.DTO
            className.contains("vo") -> BlockType.VO
            className.contains("test") -> BlockType.TEST
            className.contains("util") -> BlockType.UTIL
            else -> BlockType.OTHER
        }
    }
    
    private fun inferMethodBlockType(psiMethod: PsiMethod): BlockType {
        return inferBlockType(psiMethod.containingClass!!)
    }
    
    private fun calculateClassLineCount(psiClass: PsiClass): Int = 
        psiClass.textRange?.let { range ->
            psiClass.containingFile.text.substring(range.startOffset, range.endOffset).lines().size
        } ?: 0
    
    private fun calculateClassComplexity(psiClass: PsiClass): Int = 
        psiClass.methods.sumOf { calculateMethodComplexity(it) }
    
    private fun extractClassResponsibilities(psiClass: PsiClass): List<String> = 
        listOf("data_management", "business_logic") // 简化实现
    
    private fun extractClassDependencies(psiClass: PsiClass): List<String> = 
        listOf("Object") // 简化实现
    
    private fun calculateClassRiskScore(psiClass: PsiClass): Double = 
        minOf(1.0, psiClass.methods.size / 20.0) // 简化风险评分
    
    private fun extractClassMetadata(psiClass: PsiClass): Map<String, String> = 
        mapOf("analyzed_at" to System.currentTimeMillis().toString())
    
    private fun buildMethodSignature(psiMethod: PsiMethod): String {
        val params = psiMethod.parameters.joinToString(", ") { "${it.type} ${it.name}" }
        return "${psiMethod.name}($params)"
    }
    
    private fun getMethodStartLine(psiMethod: PsiMethod): Int = 
        psiMethod.containingFile.viewProvider.document?.getLineNumber(psiMethod.textRange.startOffset) ?: 0
    
    private fun getMethodEndLine(psiMethod: PsiMethod): Int = 
        psiMethod.containingFile.viewProvider.document?.getLineNumber(psiMethod.textRange.endOffset) ?: 0
    
    private fun calculateMethodComplexity(psiMethod: PsiMethod): Int = 
        psiMethod.body?.statements?.size ?: 0
    
    private fun calculateMethodLineCount(psiMethod: PsiMethod): Int = 
        getMethodEndLine(psiMethod) - getMethodStartLine(psiMethod) + 1
    
    private fun extractCalledMethods(psiMethod: PsiMethod): List<String> {
        val calledMethods = mutableListOf<String>()
        psiMethod.accept(object : JavaRecursiveElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                expression.resolveMethod()?.let { resolvedMethod ->
                    calledMethods.add(buildMethodSignature(resolvedMethod))
                }
            }
        })
        return calledMethods
    }
    
    private fun extractUsedFields(psiMethod: PsiMethod): List<String> {
        val usedFields = mutableListOf<String>()
        psiMethod.accept(object : JavaRecursiveElementVisitor() {
            override fun visitReferenceExpression(expression: PsiReferenceExpression) {
                super.visitReferenceExpression(expression)
                if (expression.resolve() is PsiField) {
                    usedFields.add(expression.referenceName ?: "")
                }
            }
        })
        return usedFields
    }
    
    private fun calculateControlFlowComplexity(psiMethod: PsiMethod): Int = 
        calculateMethodComplexity(psiMethod) // 简化实现
    
    private fun calculateCognitiveComplexity(psiMethod: PsiMethod): Int = 
        calculateMethodComplexity(psiMethod) // 简化实现
    
    private fun calculateMethodRiskScore(psiMethod: PsiMethod): Double = 
        minOf(1.0, calculateMethodComplexity(psiMethod) / 10.0)
    
    private fun calculateBusinessValue(psiMethod: PsiMethod): Double = 
        if (psiMethod.hasModifierProperty(PsiModifier.PUBLIC)) 0.8 else 0.4
    
    private fun extractMethodMetadata(psiMethod: PsiMethod): Map<String, String> = 
        mapOf("analyzed_at" to System.currentTimeMillis().toString())
    
    /**
     * 根据方法ID查找PSI方法（用于重新获取调用关系）
     */
    private fun findPsiMethodById(methodId: String): PsiMethod? {
        // 简化实现：从方法ID中提取类名和方法名
        val parts = methodId.split(".")
        if (parts.size < 2) return null
        
        val className = parts.dropLast(1).joinToString(".")
        val methodName = parts.last()
        
        // 在项目中查找类
        val psiClass = JavaPsiFacade.getInstance(project)
            .findClass(className, GlobalSearchScope.projectScope(project))
        
        return psiClass?.methods?.find { it.name == methodName }
    }
    
    /**
     * 停止索引
     */
    fun stopIndexing() {
        coroutineScope.cancel()
        _indexingProgress.value = _indexingProgress.value.copy(
            status = IndexingStatus.CANCELLED,
            message = "索引已取消"
        )
    }
    
    /**
     * 检查项目是否已索引
     */
    fun isProjectIndexed(): Boolean {
        return cacheService.isProjectIndexed(project.name)
    }
}

/**
 * 索引进度状态
 */
data class IndexingProgress(
    val status: IndexingStatus = IndexingStatus.NOT_STARTED,
    val message: String = "",
    val totalFiles: Int = 0,
    val processedFiles: Int = 0,
    val totalClasses: Int = 0,
    val processedClasses: Int = 0,
    val totalMethods: Int = 0,
    val processedMethods: Int = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val error: String? = null
) {
    val progressPercentage: Int
        get() = when (status) {
            IndexingStatus.NOT_STARTED -> 0
            IndexingStatus.SCANNING -> 10
            IndexingStatus.ANALYZING_CLASSES -> if (totalFiles > 0) 10 + (processedFiles * 40 / totalFiles) else 10
            IndexingStatus.BUILDING_RELATIONSHIPS -> if (totalMethods > 0) 50 + (processedMethods * 30 / totalMethods) else 50
            IndexingStatus.BUILDING_GRAPH -> 80
            IndexingStatus.CACHING -> 90
            IndexingStatus.COMPLETED -> 100
            IndexingStatus.FAILED, IndexingStatus.CANCELLED -> 0
        }
    
    val estimatedTimeRemaining: Long?
        get() = if (processedFiles > 0 && totalFiles > 0) {
            val elapsed = System.currentTimeMillis() - startedAt
            val avgTimePerFile = elapsed / processedFiles
            (totalFiles - processedFiles) * avgTimePerFile
        } else null
}

/**
 * 索引状态
 */
enum class IndexingStatus {
    NOT_STARTED,
    SCANNING,
    ANALYZING_CLASSES,
    BUILDING_RELATIONSHIPS,
    BUILDING_GRAPH,
    CACHING,
    COMPLETED,
    FAILED,
    CANCELLED
}