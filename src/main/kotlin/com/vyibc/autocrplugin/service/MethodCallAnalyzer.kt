package com.vyibc.autocrplugin.service

import com.intellij.openapi.project.Project
import java.io.File

/**
 * 方法调用分析器
 * 用于分析代码变更中涉及的方法调用，并获取被调用方法的实现
 */
class MethodCallAnalyzer(
    private val project: Project,
    private val maxCascadeDepth: Int = 2
) {
    
    private var inferredProjectPath: String? = null
    var debugCallback: ((String) -> Unit)? = null

    /**
     * 分析代码变更中的方法调用
     */
    fun analyzeMethodCalls(changes: List<CodeChange>): List<MethodCallInfo> {
        val methodCalls = mutableListOf<MethodCallInfo>()
        
        // 获取项目根路径（基于第一个文件的路径推断）
        if (changes.isNotEmpty()) {
            val firstFilePath = changes.first().filePath
            inferredProjectPath = inferProjectRootPath(firstFilePath)
            debugCallback?.invoke("🔍 推断的项目根路径: $inferredProjectPath")
        }
        
        changes.forEach { change ->
            if (change.changeType == ChangeType.MODIFIED || change.changeType == ChangeType.ADDED) {
                // 分析新增和修改的代码行
                val addedCalls = analyzeCodeLines(change.addedLines, change.filePath)
                val modifiedCalls = change.modifiedLines.flatMap { (_, newLine) ->
                    analyzeCodeLines(listOf(newLine), change.filePath)
                }
                
                methodCalls.addAll(addedCalls)
                methodCalls.addAll(modifiedCalls)
            }
        }
        
        return methodCalls.distinctBy { "${it.className}.${it.methodName}" }
    }
    
    /**
     * 根据文件路径推断项目根路径
     */
    private fun inferProjectRootPath(filePath: String): String {
        val file = File(filePath)
        var current = file.parentFile
        
        // 向上查找，直到找到包含src目录的路径
        while (current != null) {
            val srcDir = File(current, "src")
            if (srcDir.exists() && srcDir.isDirectory) {
                return current.absolutePath
            }
            current = current.parentFile
        }
        
        // 如果找不到src目录，返回文件的上级目录
        return file.parentFile?.absolutePath ?: filePath
    }
    
    /**
     * 分析代码行中的方法调用
     */
    private fun analyzeCodeLines(lines: List<String>, filePath: String): List<MethodCallInfo> {
        val methodCalls = mutableListOf<MethodCallInfo>()
        
        lines.forEach { line ->
            // 使用正则表达式匹配方法调用
            val methodCallPattern = Regex("""(\w+)\.(\w+)\s*\(""")
            val matches = methodCallPattern.findAll(line)
            
            matches.forEach { match ->
                val className = match.groupValues[1]
                val methodName = match.groupValues[2]
                
                debugCallback?.invoke("🔍 检测到方法调用: $className.$methodName() 在行: ${line.trim()}")
                
                // 查找方法实现
                val methodImpl = findMethodImplementation(className, methodName)
                if (methodImpl != null) {
                    debugCallback?.invoke("✅ 找到方法实现: ${methodImpl.filePath}")
                    methodCalls.add(
                        MethodCallInfo(
                            className = className,
                            methodName = methodName,
                            callerFile = filePath,
                            callerLine = line.trim(),
                            implementation = methodImpl
                        )
                    )
                } else {
                    debugCallback?.invoke("❌ 未找到方法实现: $className.$methodName")
                }
            }
        }
        
        return methodCalls
    }
    
    
    /**
     * 查找方法实现（包含级联分析）- 旧版本，保留作为备用
     */
    private fun findMethodImplementation(className: String, methodName: String, depth: Int = 0): MethodImplementation? {
        if (depth > maxCascadeDepth) return null // 防止无限递归
        
        try {
            
            // 简化实现：通过文件系统搜索
            val classFile = findClassFile(className)
            if (classFile != null && classFile.exists()) {
                val content = classFile.readText()
                val methodImpl = extractMethodImplementation(content, methodName)
                if (methodImpl != null) {
                    // 分析方法内的调用并获取级联方法
                    val cascadedMethods = findCascadedMethods(methodImpl, depth + 1)
                    
                    return MethodImplementation(
                        className = className,
                        methodName = methodName,
                        filePath = classFile.absolutePath,
                        sourceCode = methodImpl,
                        containsDangerousOperations = checkForDangerousOperations(methodImpl),
                        cascadedMethods = cascadedMethods
                    )
                }
            }
        } catch (e: Exception) {
            debugCallback?.invoke("❌ 文件分析出错: ${e.message}")
        }
        
        return null
    }
    
    /**
     * 查找方法内部的级联调用
     */
    private fun findCascadedMethods(methodCode: String, depth: Int): List<MethodImplementation> {
        val cascadedMethods = mutableListOf<MethodImplementation>()
        
        // 使用正则表达式匹配方法调用
        val methodCallPattern = Regex("""(\w+)\.(\w+)\s*\(""")
        val matches = methodCallPattern.findAll(methodCode)
        
        matches.forEach { match ->
            val cascadedClassName = match.groupValues[1]
            val cascadedMethodName = match.groupValues[2]
            
            // 递归查找级联方法的实现
            val cascadedImpl = findMethodImplementation(cascadedClassName, cascadedMethodName, depth)
            if (cascadedImpl != null) {
                cascadedMethods.add(cascadedImpl)
            }
        }
        
        return cascadedMethods.distinctBy { "${it.className}.${it.methodName}" }
    }
    
    /**
     * 查找类文件
     */
    private fun findClassFile(className: String): File? {
        val projectPath = inferredProjectPath ?: project.basePath ?: return null
        val projectDir = File(projectPath)
        
        debugCallback?.invoke("🔍 搜索类文件: $className 在项目路径: $projectPath")
        
        // 在src目录下搜索Java文件
        val result = findFileRecursively(projectDir, "${className}.java")
        if (result != null) {
            debugCallback?.invoke("✅ 找到类文件: ${result.absolutePath}")
        } else {
            debugCallback?.invoke("❌ 未找到类文件: ${className}.java")
        }
        
        return result
    }
    
    /**
     * 递归查找文件
     */
    private fun findFileRecursively(dir: File, fileName: String): File? {
        if (!dir.exists() || !dir.isDirectory) return null
        
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val found = findFileRecursively(file, fileName)
                if (found != null) return found
            } else if (file.name == fileName) {
                return file
            }
        }
        
        return null
    }
    
    /**
     * 提取方法实现
     */
    private fun extractMethodImplementation(classContent: String, methodName: String): String? {
        debugCallback?.invoke("🔍 开始提取方法: $methodName")
        
        // 针对您的getValue方法的特定模式: public static Set<String> getValue(String key) {
        val specificPattern = """public\s+static\s+Set<String>\s+$methodName\s*\([^)]*\)\s*\{"""
        
        // 更通用的模式
        val generalPatterns = listOf(
            // 精确匹配泛型返回类型
            Regex("""(public|private|protected)?\s*(static)?\s*Set<\w+>\s+$methodName\s*\([^)]*\)\s*\{""", RegexOption.MULTILINE),
            // 匹配任何泛型返回类型
            Regex("""(public|private|protected)?\s*(static)?\s*\w+<[^>]+>\s+$methodName\s*\([^)]*\)\s*\{""", RegexOption.MULTILINE),
            // 匹配简单返回类型
            Regex("""(public|private|protected)?\s*(static)?\s*\w+\s+$methodName\s*\([^)]*\)\s*\{""", RegexOption.MULTILINE),
            // 最宽松匹配
            Regex(""".*$methodName\s*\([^)]*\)\s*\{""", RegexOption.MULTILINE)
        )
        
        var match: MatchResult? = null
        var startIndex = -1
        
        // 先尝试特定模式
        debugCallback?.invoke("🔍 尝试特定模式: $specificPattern")
        match = Regex(specificPattern, RegexOption.MULTILINE).find(classContent)
        
        if (match != null) {
            startIndex = match.range.first
            debugCallback?.invoke("✅ 特定模式匹配成功: ${match.value}")
        } else {
            // 尝试通用模式
            for ((index, pattern) in generalPatterns.withIndex()) {
                debugCallback?.invoke("🔍 尝试通用模式 $index: ${pattern.pattern}")
                match = pattern.find(classContent)
                if (match != null) {
                    startIndex = match.range.first
                    debugCallback?.invoke("✅ 通用模式 $index 匹配成功: ${match.value}")
                    break
                }
            }
        }
        
        if (match == null || startIndex == -1) {
            debugCallback?.invoke("❌ 所有正则模式都无法匹配方法: $methodName")
            
            // 作为最后手段，尝试简单搜索并显示上下文
            val simpleSearch = classContent.indexOf("$methodName(")
            if (simpleSearch != -1) {
                debugCallback?.invoke("💡 找到方法名位置: $simpleSearch")
                val lineStart = classContent.lastIndexOf('\n', simpleSearch) + 1
                val lineEnd = classContent.indexOf('\n', simpleSearch).let { if (it == -1) classContent.length else it }
                val currentLine = classContent.substring(lineStart, lineEnd)
                debugCallback?.invoke("💡 当前行内容: '$currentLine'")
                
                // 显示更多上下文
                val contextStart = maxOf(0, simpleSearch - 200)
                val contextEnd = minOf(classContent.length, simpleSearch + 200)
                val context = classContent.substring(contextStart, contextEnd)
                debugCallback?.invoke("💡 上下文代码:\n$context")
            }
            return null
        }
        
        // 找到方法的结束位置（匹配大括号）
        var braceCount = 0
        var endIndex = startIndex
        var inMethod = false
        
        for (i in startIndex until classContent.length) {
            val char = classContent[i]
            if (char == '{') {
                braceCount++
                inMethod = true
            } else if (char == '}') {
                braceCount--
                if (inMethod && braceCount == 0) {
                    endIndex = i + 1
                    break
                }
            }
        }
        
        val result = if (endIndex > startIndex) {
            val methodCode = classContent.substring(startIndex, endIndex)
            debugCallback?.invoke("✅ 成功提取方法实现，长度: ${methodCode.length} 字符")
            debugCallback?.invoke("📝 方法代码预览: ${methodCode.take(100)}...")
            methodCode
        } else {
            debugCallback?.invoke("❌ 无法确定方法结束位置")
            null
        }
        
        return result
    }
    
    /**
     * 检查方法实现中是否包含危险操作
     */
    private fun checkForDangerousOperations(methodCode: String): List<String> {
        val dangerousOperations = mutableListOf<String>()
        
        // 检查Redis危险命令
        val redisDangerousPatterns = listOf(
            Regex("""(stringRedisTemplate|redisTemplate|jedis)\.keys\s*\(""", RegexOption.IGNORE_CASE) to "Redis keys() 模式匹配（可能导致性能问题）",
            Regex("""\.keys\s*\(""", RegexOption.IGNORE_CASE) to "Redis keys() 调用",
            Regex("""\.flushdb\s*\(""", RegexOption.IGNORE_CASE) to "Redis flushdb() 清空数据库",
            Regex("""\.flushall\s*\(""", RegexOption.IGNORE_CASE) to "Redis flushall() 清空所有数据库",
            Regex("""\.config\s*\(""", RegexOption.IGNORE_CASE) to "Redis config() 配置操作"
        )
        
        redisDangerousPatterns.forEach { (pattern, description) ->
            if (pattern.containsMatchIn(methodCode)) {
                dangerousOperations.add("Redis危险操作: $description")
            }
        }
        
        // 检查SQL危险操作
        val sqlDangerousPatterns = listOf(
            "select \\* from", "delete from.*where", "update.*set.*where"
        )
        
        sqlDangerousPatterns.forEach { pattern ->
            if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(methodCode)) {
                dangerousOperations.add("SQL危险操作: $pattern")
            }
        }
        
        return dangerousOperations
    }
}

/**
 * 方法调用信息
 */
data class MethodCallInfo(
    val className: String,
    val methodName: String,
    val callerFile: String,
    val callerLine: String,
    val implementation: MethodImplementation
)

/**
 * 方法实现信息
 */
data class MethodImplementation(
    val className: String,
    val methodName: String,
    val filePath: String,
    val sourceCode: String,
    val containsDangerousOperations: List<String>,
    val cascadedMethods: List<MethodImplementation> = emptyList()
)
