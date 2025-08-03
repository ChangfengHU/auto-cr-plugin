package com.vyibc.autocrplugin.graph.engine

import com.vyibc.autocrplugin.graph.model.*
import kotlinx.coroutines.flow.Flow

/**
 * 图引擎接口 - 定义图数据的基本操作
 */
interface GraphEngine {
    
    /**
     * 添加方法节点
     */
    suspend fun addMethodNode(node: MethodNode): Boolean
    
    /**
     * 添加类节点
     */
    suspend fun addClassNode(node: ClassNode): Boolean
    
    /**
     * 添加调用边
     */
    suspend fun addCallEdge(edge: CallsEdge): Boolean
    
    /**
     * 添加实现边
     */
    suspend fun addImplementsEdge(edge: ImplementsEdge): Boolean
    
    /**
     * 添加数据流边
     */
    suspend fun addDataFlowEdge(edge: DataFlowEdge): Boolean
    
    /**
     * 获取方法节点
     */
    suspend fun getMethodNode(id: String): MethodNode?
    
    /**
     * 获取类节点
     */
    suspend fun getClassNode(id: String): ClassNode?
    
    /**
     * 获取方法的所有调用者
     */
    suspend fun getCallers(methodId: String): List<MethodNode>
    
    /**
     * 获取方法调用的所有方法
     */
    suspend fun getCallees(methodId: String): List<MethodNode>
    
    /**
     * 查找两个方法之间的所有路径
     */
    suspend fun findPaths(sourceId: String, targetId: String, maxDepth: Int = 5): List<CallPath>
    
    /**
     * 获取方法的影响范围（被影响的所有方法）
     */
    suspend fun getImpactRadius(methodId: String, maxDepth: Int = 3): Set<MethodNode>
    
    /**
     * 获取所有方法节点数量
     */
    suspend fun getMethodCount(): Int
    
    /**
     * 获取所有类节点数量
     */
    suspend fun getClassCount(): Int
    
    /**
     * 清空图数据
     */
    suspend fun clear()
    
    /**
     * 批量操作事务支持
     */
    suspend fun <T> transaction(block: suspend GraphEngine.() -> T): T
}

/**
 * 本地图引擎接口 - 扩展基础图引擎，提供本地特有功能
 */
interface LocalGraphEngine : GraphEngine {
    
    /**
     * 保存图数据到文件
     */
    suspend fun saveToFile(filePath: String): Boolean
    
    /**
     * 从文件加载图数据
     */
    suspend fun loadFromFile(filePath: String): Boolean
    
    /**
     * 获取热点路径（最常被访问的路径）
     */
    suspend fun getHotPaths(limit: Int = 10): List<CallPath>
    
    /**
     * 更新节点的风险分数
     */
    suspend fun updateRiskScore(methodId: String, score: Double): Boolean
    
    /**
     * 增量更新支持
     */
    suspend fun incrementalUpdate(changes: List<FileChange>): UpdateResult
    
    /**
     * 删除文件相关的所有节点
     */
    suspend fun removeFileNodes(filePath: String)
    
    /**
     * 重命名文件相关的所有节点
     */
    suspend fun renameFileNodes(oldPath: String, newPath: String)
    
    /**
     * 从分析结果更新图结构
     */
    suspend fun updateFromAnalysis(analysisResult: com.vyibc.autocrplugin.analyzer.FileAnalysisResult)
    
    /**
     * 获取图统计信息
     */
    suspend fun getStatistics(): GraphStatistics
    
    /**
     * 查找调用路径
     */
    suspend fun findCallPaths(
        startMethodId: String, 
        endMethodId: String, 
        maxDepth: Int = 10
    ): List<CallPath>
    
    /**
     * 获取方法的直接调用者
     */
    suspend fun getDirectCallers(methodId: String): List<MethodNode>
    
    /**
     * 获取方法直接调用的方法
     */
    suspend fun getDirectCallees(methodId: String): List<MethodNode>
    
    /**
     * 计算方法的风险传播范围
     */
    suspend fun calculateRiskPropagation(
        methodId: String, 
        depth: Int = 3
    ): Map<String, Double>
    
    /**
     * 添加调用关系
     */
    suspend fun addCallsEdge(edge: CallsEdge): Boolean
}

/**
 * 可视化图引擎接口 - Neo4j异步同步
 */
interface VisualizationGraphEngine : GraphEngine {
    
    /**
     * 同步本地图数据到Neo4j
     */
    suspend fun syncFromLocal(localEngine: LocalGraphEngine): SyncResult
    
    /**
     * 生成项目架构可视化数据
     */
    suspend fun generateArchitectureView(): ArchitectureView
    
    /**
     * 生成风险热力图数据
     */
    suspend fun generateRiskHeatmap(): RiskHeatmap
    
    /**
     * 生成代码演进时间线
     */
    suspend fun generateEvolutionTimeline(startTime: Long, endTime: Long): EvolutionTimeline
    
    /**
     * 分析团队协作模式
     */
    suspend fun analyzeCollaborationPatterns(): CollaborationAnalysis
}

/**
 * 文件变更信息
 */
data class FileChange(
    val filePath: String,
    val changeType: ChangeType,
    val modifiedMethods: List<String>,
    val addedMethods: List<String>,
    val deletedMethods: List<String>
)

/**
 * 变更类型
 */
enum class ChangeType {
    ADDED,
    MODIFIED, 
    DELETED
}

/**
 * 更新结果
 */
data class UpdateResult(
    val success: Boolean,
    val affectedNodes: Int,
    val affectedEdges: Int,
    val errors: List<String> = emptyList()
)

/**
 * 同步结果
 */
data class SyncResult(
    val success: Boolean,
    val syncedNodes: Int,
    val syncedEdges: Int,
    val duration: Long,
    val errors: List<String> = emptyList()
)

/**
 * 架构视图数据
 */
data class ArchitectureView(
    val layers: Map<BlockType, List<ClassNode>>,
    val dependencies: List<LayerDependency>,
    val violations: List<ArchitectureViolation>
)

/**
 * 层级依赖
 */
data class LayerDependency(
    val fromLayer: BlockType,
    val toLayer: BlockType,
    val callCount: Int
)

/**
 * 架构违规
 */
data class ArchitectureViolation(
    val type: LayerViolationType,
    val fromMethod: MethodNode,
    val toMethod: MethodNode,
    val severity: ViolationSeverity
)

/**
 * 违规严重程度
 */
enum class ViolationSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

/**
 * 风险热力图
 */
data class RiskHeatmap(
    val riskZones: Map<String, RiskZone>,
    val overallRiskLevel: RiskLevel
)

/**
 * 风险区域
 */
data class RiskZone(
    val packageName: String,
    val riskScore: Double,
    val riskLevel: RiskLevel,
    val topRisks: List<RiskItem>
)

/**
 * 风险项
 */
data class RiskItem(
    val method: MethodNode,
    val riskType: String,
    val severity: Double
)

/**
 * 风险等级
 */
enum class RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}

/**
 * 演进时间线
 */
data class EvolutionTimeline(
    val periods: List<EvolutionPeriod>,
    val trends: QualityTrends
)

/**
 * 演进时期
 */
data class EvolutionPeriod(
    val startTime: Long,
    val endTime: Long,
    val changes: Int,
    val qualityScore: Double
)

/**
 * 质量趋势
 */
data class QualityTrends(
    val complexityTrend: Trend,
    val cohesionTrend: Trend,
    val couplingTrend: Trend
)

/**
 * 趋势方向
 */
enum class Trend {
    IMPROVING, STABLE, DEGRADING
}

/**
 * 协作分析
 */
data class CollaborationAnalysis(
    val topContributors: List<ContributorInfo>,
    val collaborationPatterns: List<CollaborationPattern>
)

/**
 * 贡献者信息
 */
data class ContributorInfo(
    val name: String,
    val commits: Int,
    val linesChanged: Int,
    val focusAreas: List<String>
)

/**
 * 协作模式
 */
data class CollaborationPattern(
    val type: String,
    val participants: List<String>,
    val frequency: Int
)

/**
 * 图统计信息
 */
data class GraphStatistics(
    val nodeCount: Int,
    val edgeCount: Int,
    val methodCount: Int,
    val classCount: Int,
    val averageComplexity: Double,
    val hotspotMethods: List<String>,
    val riskDistribution: Map<RiskLevel, Int>
)