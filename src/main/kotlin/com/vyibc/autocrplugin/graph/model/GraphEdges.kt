package com.vyibc.autocrplugin.graph.model

/**
 * 调用关系边 - 增强版
 */
data class CallsEdge(
    val caller: MethodNode,        // 调用者
    val callee: MethodNode,        // 被调用者
    val callType: CallType,        // 调用类型
    val lineNumber: Int,           // 调用发生行号
    val frequency: Int = 1,        // 调用频率（静态分析估算）
    val isConditional: Boolean = false, // 是否条件调用
    val context: String? = null,   // 调用上下文 (try/catch/if/loop)
    
    // V5.1新增
    val riskWeight: Double = 0.0,  // 该调用的风险权重
    val intentWeight: Double = 0.0, // 该调用的意图权重
    val isNewInMR: Boolean = false, // 是否为MR新增调用
    val isModifiedInMR: Boolean = false // 是否为MR修改调用
) {
    fun getEdgeId(): String = "${caller.id} -> ${callee.id}"
}

/**
 * 实现关系边 - 增强版
 */
data class ImplementsEdge(
    val interfaceMethod: MethodNode,      // 接口方法
    val implementationMethod: MethodNode,  // 实现方法
    val isOverride: Boolean = false,      // 是否为重写
    
    // V5.1新增
    val implementationQuality: Double = 1.0, // 实现质量评分
    val followsContract: Boolean = true      // 是否遵循接口契约
) {
    fun getEdgeId(): String = "${interfaceMethod.id} <- ${implementationMethod.id}"
}

/**
 * 数据流关系边 - V5.1新增
 */
data class DataFlowEdge(
    val source: MethodNode,        // 数据源
    val target: MethodNode,        // 数据目标
    val dataType: String,          // 传递的数据类型
    val flowType: DataFlowType,   // 数据流类型
    val isSensitive: Boolean = false // 是否为敏感数据
) {
    fun getEdgeId(): String = "${source.id} ~> ${target.id}"
}

/**
 * 调用类型枚举
 */
enum class CallType {
    DIRECT,      // 直接调用
    INTERFACE,   // 接口调用
    REFLECTION,  // 反射调用
    LAMBDA,      // Lambda表达式调用
    METHOD_REF   // 方法引用
}

/**
 * 数据流类型枚举
 */
enum class DataFlowType {
    PARAMETER,      // 参数传递
    RETURN_VALUE,   // 返回值
    FIELD_ACCESS,   // 字段访问
    SHARED_STATE    // 共享状态
}

/**
 * 调用路径
 */
data class CallPath(
    val id: String,
    val methods: List<MethodNode>,
    val edges: List<CallsEdge>,
    val pathType: PathType,
    val totalWeight: Double = 0.0
) {
    fun hasNewEndpoint(): Boolean {
        return methods.any { it.blockType == BlockType.CONTROLLER && it.annotations.any { ann -> 
            ann.contains("Mapping") || ann.contains("RequestMapping")
        }}
    }
    
    fun isRESTfulEndpoint(): Boolean {
        return methods.any { it.annotations.any { ann ->
            ann.contains("RestController") || ann.contains("ResponseBody")
        }}
    }
    
    fun hasDataModelChanges(): Boolean {
        return methods.any { 
            it.blockType in listOf(BlockType.ENTITY, BlockType.DTO, BlockType.VO)
        }
    }
    
    fun isCoreBusinessEntity(): Boolean {
        return methods.any { 
            it.blockType == BlockType.ENTITY && 
            (it.id.contains("User") || it.id.contains("Order") || 
             it.id.contains("Product") || it.id.contains("Payment"))
        }
    }
    
    fun hasDatabaseOperations(): Boolean {
        return methods.any { 
            it.blockType in listOf(BlockType.REPOSITORY, BlockType.MAPPER)
        }
    }
    
    fun hasTransactionalOperations(): Boolean {
        return methods.any { 
            it.annotations.any { ann -> ann.contains("Transactional") }
        }
    }
    
    fun hasExternalApiCalls(): Boolean {
        return methods.any { method ->
            method.annotations.any { ann -> 
                ann.contains("FeignClient") || ann.contains("RestTemplate")
            }
        }
    }
    
    fun hasLayerViolation(): Boolean {
        for (i in 0 until edges.size) {
            val edge = edges[i]
            val callerType = edge.caller.blockType
            val calleeType = edge.callee.blockType
            
            // 检查层级违规
            if (callerType == BlockType.CONTROLLER && calleeType == BlockType.REPOSITORY) return true
            if (callerType == BlockType.SERVICE && calleeType == BlockType.CONTROLLER) return true
            if (callerType == BlockType.REPOSITORY && calleeType == BlockType.SERVICE) return true
        }
        return false
    }
    
    fun getLayerViolationType(): LayerViolationType? {
        for (edge in edges) {
            val callerType = edge.caller.blockType
            val calleeType = edge.callee.blockType
            
            when {
                callerType == BlockType.CONTROLLER && calleeType == BlockType.REPOSITORY -> 
                    return LayerViolationType.CONTROLLER_TO_DAO
                callerType == BlockType.SERVICE && calleeType == BlockType.CONTROLLER -> 
                    return LayerViolationType.SERVICE_TO_CONTROLLER
                callerType == BlockType.UTIL && calleeType == BlockType.SERVICE -> 
                    return LayerViolationType.UTIL_TO_SERVICE
            }
        }
        return null
    }
    
    fun getSensitiveAnnotations(): List<String> {
        return methods.flatMap { it.annotations }.filter { ann ->
            ann in listOf("@Transactional", "@Async", "@Scheduled", 
                         "@Cacheable", "@PreAuthorize", "@PostAuthorize", "@Lock")
        }.distinct()
    }
    
    fun hasCircularDependency(): Boolean {
        val visited = mutableSetOf<String>()
        for (method in methods) {
            if (visited.contains(method.id)) return true
            visited.add(method.id)
        }
        return false
    }
    
    fun getAverageComplexity(): Double {
        if (methods.isEmpty()) return 0.0
        return methods.map { it.cyclomaticComplexity }.average()
    }
    
    fun getAverageMethodLength(): Double {
        if (methods.isEmpty()) return 0.0
        return methods.map { it.linesOfCode }.average()
    }
    
    fun getBusinessTerms(): List<String> {
        return methods.flatMap { method ->
            val terms = mutableListOf<String>()
            // 从方法名提取业务术语
            terms.addAll(extractBusinessTermsFromName(method.methodName))
            // 从类名提取业务术语 - 从ID中提取类名
            val className = method.id.substringAfterLast('.').substringBefore('#')
            terms.addAll(extractBusinessTermsFromName(className))
            terms
        }.distinct()
    }
    
    private fun extractBusinessTermsFromName(name: String): List<String> {
        // 简单的驼峰命名拆分
        return name.split("(?=[A-Z])".toRegex())
            .filter { it.length > 2 }
            .map { it.lowercase() }
    }
}

/**
 * 路径类型枚举
 */
enum class PathType {
    GOLDEN_PATH,   // 黄金路径（意图路径）
    RISK_PATH,     // 风险路径
    CRITICAL_PATH, // 关键路径
    NEUTRAL_PATH   // 中性路径
}

/**
 * 层级违规类型枚举
 */
enum class LayerViolationType {
    CONTROLLER_TO_DAO,      // 控制器直接访问DAO
    SERVICE_TO_CONTROLLER,  // 服务层访问控制器
    UTIL_TO_SERVICE,        // 工具类访问服务层
    DAO_TO_SERVICE          // DAO访问服务层
}