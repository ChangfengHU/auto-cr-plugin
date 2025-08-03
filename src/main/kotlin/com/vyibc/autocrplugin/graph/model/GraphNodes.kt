package com.vyibc.autocrplugin.graph.model

import java.time.Instant

/**
 * 方法节点数据模型 - V5.1版本
 * 包含完整的方法信息和性能指标
 */
data class MethodNode(
    val id: String,                    // 唯一标识符
    val methodName: String,            // 方法名
    val signature: String,             // 完整方法签名
    val returnType: String,            // 返回类型
    val paramTypes: List<String>,      // 参数类型列表
    val blockType: BlockType,          // 层级类型
    val isInterface: Boolean = false,  // 是否为接口方法
    val annotations: List<String> = emptyList(), // 注解列表
    val filePath: String,              // 文件绝对路径
    val lineNumber: Int,               // 方法定义行号
    val startLineNumber: Int,          // 起始行号
    val endLineNumber: Int,            // 结束行号
    
    // V5.1新增性能相关字段
    val cyclomaticComplexity: Int = 1,     // 圈复杂度
    val linesOfCode: Int = 0,              // 代码行数
    val inDegree: Int = 0,                 // 被调用次数
    val outDegree: Int = 0,                // 调用他人次数
    val riskScore: Double = 0.0,           // 预计算风险分数
    val hasTests: Boolean = false,         // 是否有测试覆盖
    val lastModified: Instant = Instant.now() // 最后修改时间
) {
    companion object {
        /**
         * 生成方法的唯一ID
         * 格式: {packageName}.{className}#{methodName}({paramTypes})
         */
        fun generateId(packageName: String, className: String, methodName: String, paramTypes: List<String>): String {
            val params = paramTypes.joinToString(",")
            return "$packageName.$className#$methodName($params)"
        }
    }
}

/**
 * 类节点数据模型 - V5.1版本
 * 包含完整的类信息和质量指标
 */
data class ClassNode(
    val id: String,                    // 唯一标识符
    val className: String,             // 类名
    val packageName: String,           // 包名
    val blockType: BlockType,          // 层级类型
    val isInterface: Boolean = false,  // 是否为接口
    val isAbstract: Boolean = false,   // 是否为抽象类
    val filePath: String,              // 文件路径
    val implementedInterfaces: List<String> = emptyList(), // 实现的接口
    val superClass: String? = null,    // 父类
    val annotations: List<String> = emptyList(), // 注解列表
    
    // V5.1新增字段
    val methodCount: Int = 0,          // 方法总数
    val fieldCount: Int = 0,           // 字段总数
    val cohesion: Double = 0.0,        // 内聚度
    val coupling: Double = 0.0,        // 耦合度
    val designPatterns: List<String> = emptyList() // 识别的设计模式
) {
    companion object {
        /**
         * 生成类的唯一ID
         * 格式: {packageName}.{className}
         */
        fun generateId(packageName: String, className: String): String {
            return "$packageName.$className"
        }
    }
}

/**
 * 层级类型枚举
 */
enum class BlockType {
    CONTROLLER,    // 控制器层
    SERVICE,       // 服务层
    REPOSITORY,    // 数据访问层
    MAPPER,        // MyBatis Mapper
    COMPONENT,     // 通用组件
    UTIL,          // 工具类
    CONFIG,        // 配置类
    ENTITY,        // 实体类
    DTO,           // 数据传输对象
    VO,            // 视图对象
    TEST,          // 测试类
    OTHER,         // 其他类型
    UNKNOWN        // 未知类型
}

/**
 * 设计模式枚举
 */
enum class DesignPattern {
    SINGLETON,
    FACTORY,
    BUILDER,
    OBSERVER,
    STRATEGY,
    TEMPLATE,
    ADAPTER,
    DECORATOR,
    PROXY,
    FACADE
}