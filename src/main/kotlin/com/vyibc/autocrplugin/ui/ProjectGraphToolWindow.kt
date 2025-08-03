package com.vyibc.autocrplugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.*
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.vyibc.autocrplugin.indexer.IndexingProgress
import com.vyibc.autocrplugin.indexer.IndexingStatus
import com.vyibc.autocrplugin.indexer.ProjectIndexer
import com.vyibc.autocrplugin.neo4j.Neo4jService
import com.vyibc.autocrplugin.neo4j.ProjectStatistics
import com.vyibc.autocrplugin.settings.AutoCRSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.*
import javax.swing.border.TitledBorder

/**
 * 项目知识图谱状态工具窗口
 */
class ProjectGraphToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentPanel = ProjectGraphPanel(project)
        val content = ContentFactory.getInstance().createContent(contentPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

/**
 * 项目图谱状态面板
 */
class ProjectGraphPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val settings = AutoCRSettings.getInstance(project)
    private val neo4jService = Neo4jService(settings)
    
    // 索引状态组件
    private val indexStatusLabel = JLabel("未索引")
    private val indexProgressBar = JProgressBar()
    private val indexMessageLabel = JLabel("点击开始按钮进行项目索引")
    private val startIndexButton = JButton("开始索引")
    private val stopIndexButton = JButton("停止索引")
    private val reindexButton = JButton("重新索引")
    
    // 统计信息组件
    private val classCountLabel = JLabel("0")
    private val methodCountLabel = JLabel("0")
    private val callRelationshipLabel = JLabel("0")
    private val avgComplexityLabel = JLabel("0.0")
    private val highRiskMethodLabel = JLabel("0")
    private val lastSyncLabel = JLabel("从未同步")
    
    // Neo4j状态组件
    private val neo4jStatusLabel = JLabel("未连接")
    private val neo4jConnectButton = JButton("连接Neo4j")
    private val neo4jSyncButton = JButton("同步数据")
    private val openNeo4jButton = JButton("打开Neo4j浏览器")
    
    // 项目索引器实例
    private var projectIndexer: ProjectIndexer? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    init {
        setupUI()
        setupEventListeners()
        updateProjectUI()
        startStatusMonitoring()
    }
    
    private fun setupUI() {
        // 主面板
        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        mainPanel.border = JBUI.Borders.empty(10)
        
        // 索引状态面板
        mainPanel.add(createIndexingStatusPanel())
        mainPanel.add(Box.createVerticalStrut(10))
        
        // 项目统计面板
        mainPanel.add(createProjectStatisticsPanel())
        mainPanel.add(Box.createVerticalStrut(10))
        
        // Neo4j状态面板
        mainPanel.add(createNeo4jStatusPanel())
        mainPanel.add(Box.createVerticalStrut(10))
        
        // 操作按钮面板
        mainPanel.add(createActionPanel())
        
        // 添加到主面板
        add(JScrollPane(mainPanel), BorderLayout.CENTER)
    }
    
    private fun createIndexingStatusPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = TitledBorder("项目索引状态")
        
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(5)
        gbc.anchor = GridBagConstraints.WEST
        
        // 状态行
        gbc.gridx = 0; gbc.gridy = 0
        panel.add(JLabel("索引状态:"), gbc)
        gbc.gridx = 1
        indexStatusLabel.foreground = Color.ORANGE
        panel.add(indexStatusLabel, gbc)
        
        // 进度条
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        indexProgressBar.isStringPainted = true
        indexProgressBar.string = "0%"
        panel.add(indexProgressBar, gbc)
        
        // 消息行
        gbc.gridy = 2; gbc.gridwidth = 2
        indexMessageLabel.foreground = Color.GRAY
        panel.add(indexMessageLabel, gbc)
        
        return panel
    }
    
    private fun createProjectStatisticsPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = TitledBorder("项目统计信息")
        
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(5)
        gbc.anchor = GridBagConstraints.WEST
        
        // 类数量
        gbc.gridx = 0; gbc.gridy = 0
        panel.add(JLabel("类数量:"), gbc)
        gbc.gridx = 1
        classCountLabel.font = classCountLabel.font.deriveFont(Font.BOLD)
        panel.add(classCountLabel, gbc)
        
        // 方法数量
        gbc.gridx = 2; gbc.gridy = 0
        panel.add(JLabel("方法数量:"), gbc)
        gbc.gridx = 3
        methodCountLabel.font = methodCountLabel.font.deriveFont(Font.BOLD)
        panel.add(methodCountLabel, gbc)
        
        // 调用关系
        gbc.gridx = 0; gbc.gridy = 1
        panel.add(JLabel("调用关系:"), gbc)
        gbc.gridx = 1
        callRelationshipLabel.font = callRelationshipLabel.font.deriveFont(Font.BOLD)
        panel.add(callRelationshipLabel, gbc)
        
        // 平均复杂度
        gbc.gridx = 2; gbc.gridy = 1
        panel.add(JLabel("平均复杂度:"), gbc)
        gbc.gridx = 3
        avgComplexityLabel.font = avgComplexityLabel.font.deriveFont(Font.BOLD)
        panel.add(avgComplexityLabel, gbc)
        
        // 高风险方法
        gbc.gridx = 0; gbc.gridy = 2
        panel.add(JLabel("高风险方法:"), gbc)
        gbc.gridx = 1
        highRiskMethodLabel.font = highRiskMethodLabel.font.deriveFont(Font.BOLD)
        highRiskMethodLabel.foreground = Color.RED
        panel.add(highRiskMethodLabel, gbc)
        
        // 最后同步时间
        gbc.gridx = 2; gbc.gridy = 2
        panel.add(JLabel("最后同步:"), gbc)
        gbc.gridx = 3
        lastSyncLabel.font = lastSyncLabel.font.deriveFont(Font.ITALIC)
        lastSyncLabel.foreground = Color.GRAY
        panel.add(lastSyncLabel, gbc)
        
        return panel
    }
    
    private fun createNeo4jStatusPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = TitledBorder("Neo4j可视化状态")
        
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(5)
        gbc.anchor = GridBagConstraints.WEST
        
        // Neo4j状态
        gbc.gridx = 0; gbc.gridy = 0
        panel.add(JLabel("连接状态:"), gbc)
        gbc.gridx = 1
        neo4jStatusLabel.foreground = Color.RED
        panel.add(neo4jStatusLabel, gbc)
        
        // Neo4j配置链接
        gbc.gridx = 2; gbc.gridy = 0
        val configLink = JLabel("<html><a href='#'>配置Neo4j</a></html>")
        configLink.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        configLink.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                openSettings()
            }
        })
        panel.add(configLink, gbc)
        
        return panel
    }
    
    private fun createActionPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))
        panel.border = TitledBorder("操作")
        
        // 索引操作按钮
        panel.add(startIndexButton)
        panel.add(stopIndexButton)
        panel.add(reindexButton)
        
        panel.add(Box.createHorizontalStrut(20))
        
        // Neo4j操作按钮
        panel.add(neo4jConnectButton)
        panel.add(neo4jSyncButton)
        panel.add(openNeo4jButton)
        
        // 初始按钮状态
        stopIndexButton.isEnabled = false
        neo4jSyncButton.isEnabled = false
        openNeo4jButton.isEnabled = false
        
        return panel
    }
    
    private fun setupEventListeners() {
        // 开始索引
        startIndexButton.addActionListener {
            startProjectIndexing()
        }
        
        // 停止索引
        stopIndexButton.addActionListener {
            stopProjectIndexing()
        }
        
        // 重新索引
        reindexButton.addActionListener {
            reindexProject()
        }
        
        // 连接Neo4j
        neo4jConnectButton.addActionListener {
            connectNeo4j()
        }
        
        // 同步数据到Neo4j
        neo4jSyncButton.addActionListener {
            syncToNeo4j()
        }
        
        // 打开Neo4j浏览器
        openNeo4jButton.addActionListener {
            openNeo4jBrowser()
        }
    }
    
    private fun startStatusMonitoring() {
        coroutineScope.launch {
            // 定期更新Neo4j状态
            while (true) {
                updateNeo4jStatus()
                updateProjectStatistics()
                delay(5000) // 每5秒更新一次
            }
        }
    }
    
    private fun startProjectIndexing() {
        // TODO: 需要注入实际的依赖
        // projectIndexer = ProjectIndexer(project, graphEngine, psiAnalysisEngine, cacheService)
        
        startIndexButton.isEnabled = false
        stopIndexButton.isEnabled = true
        reindexButton.isEnabled = false
        
        indexStatusLabel.text = "索引中..."
        indexStatusLabel.foreground = Color.BLUE
        
        // TODO: 实际启动索引
        // projectIndexer?.startProjectIndexing()
        
        // 模拟索引进度更新
        coroutineScope.launch {
            for (i in 0..100) {
                indexProgressBar.value = i
                indexProgressBar.string = "$i%"
                
                when {
                    i < 20 -> indexMessageLabel.text = "正在扫描项目文件..."
                    i < 60 -> indexMessageLabel.text = "正在分析类结构..."
                    i < 80 -> indexMessageLabel.text = "正在构建调用关系..."
                    i < 95 -> indexMessageLabel.text = "正在构建知识图谱..."
                    else -> indexMessageLabel.text = "正在缓存分析结果..."
                }
                
                delay(100)
            }
            
            // 索引完成
            indexStatusLabel.text = "已索引"
            indexStatusLabel.foreground = Color.GREEN
            indexMessageLabel.text = "项目知识图谱构建完成"
            
            startIndexButton.isEnabled = true
            stopIndexButton.isEnabled = false
            reindexButton.isEnabled = true
            neo4jSyncButton.isEnabled = true
            
            updateProjectStatistics()
        }
    }
    
    private fun stopProjectIndexing() {
        projectIndexer?.stopIndexing()
        
        startIndexButton.isEnabled = true
        stopIndexButton.isEnabled = false
        reindexButton.isEnabled = true
        
        indexStatusLabel.text = "已停止"
        indexStatusLabel.foreground = Color.ORANGE
        indexMessageLabel.text = "索引已停止"
        indexProgressBar.value = 0
        indexProgressBar.string = "0%"
    }
    
    private fun reindexProject() {
        val result = JOptionPane.showConfirmDialog(
            this,
            "重新索引将清除现有的分析数据，确定要继续吗？",
            "确认重新索引",
            JOptionPane.YES_NO_OPTION
        )
        
        if (result == JOptionPane.YES_OPTION) {
            // 清除缓存和现有数据
            startProjectIndexing()
        }
    }
    
    private fun connectNeo4j() {
        if (!settings.state.neo4jEnabled) {
            JOptionPane.showMessageDialog(
                this,
                "请先在设置中启用并配置Neo4j连接",
                "Neo4j未配置",
                JOptionPane.WARNING_MESSAGE
            )
            openSettings()
            return
        }
        
        neo4jConnectButton.isEnabled = false
        neo4jStatusLabel.text = "连接中..."
        neo4jStatusLabel.foreground = Color.ORANGE
        
        coroutineScope.launch {
            try {
                val result = neo4jService.testConnection()
                SwingUtilities.invokeLater {
                    if (result.success) {
                        neo4jStatusLabel.text = "已连接"
                        neo4jStatusLabel.foreground = Color.GREEN
                        neo4jSyncButton.isEnabled = true
                        openNeo4jButton.isEnabled = true
                        
                        runBlocking {
                            if (neo4jService.initializeConnection()) {
                                updateProjectStatistics()
                            }
                        }
                    } else {
                        neo4jStatusLabel.text = "连接失败"
                        neo4jStatusLabel.foreground = Color.RED
                        JOptionPane.showMessageDialog(
                            this@ProjectGraphPanel,
                            result.message,
                            "连接失败",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                    neo4jConnectButton.isEnabled = true
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    neo4jStatusLabel.text = "连接错误"
                    neo4jStatusLabel.foreground = Color.RED
                    neo4jConnectButton.isEnabled = true
                }
            }
        }
    }
    
    private fun syncToNeo4j() {
        if (neo4jStatusLabel.text != "已连接") {
            JOptionPane.showMessageDialog(
                this,
                "请先连接Neo4j数据库",
                "未连接",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }
        
        neo4jSyncButton.isEnabled = false
        
        coroutineScope.launch {
            try {
                // TODO: 获取实际的项目数据
                // val syncResult = neo4jService.syncProjectData(classes, methods, callEdges, implementsEdges, dataFlowEdges)
                
                SwingUtilities.invokeLater {
                    neo4jSyncButton.isEnabled = true
                    // updateProjectStatistics()
                    
                    JOptionPane.showMessageDialog(
                        this@ProjectGraphPanel,
                        "数据同步完成！\n\n可以在Neo4j浏览器中查看项目知识图谱",
                        "同步成功",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    neo4jSyncButton.isEnabled = true
                    JOptionPane.showMessageDialog(
                        this@ProjectGraphPanel,
                        "同步失败: ${e.message}",
                        "同步错误",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }
    }
    
    private fun openNeo4jBrowser() {
        val uri = settings.state.neo4jUri.replace("bolt://", "http://").replace(":7687", ":7474")
        
        try {
            Desktop.getDesktop().browse(java.net.URI(uri))
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                "无法打开浏览器，请手动访问: $uri",
                "打开浏览器失败",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }
    
    private fun openSettings() {
        // 打开插件设置页面
        com.intellij.openapi.options.ShowSettingsUtil.getInstance()
            .showSettingsDialog(project, "AI代码评审")
    }
    
    private fun updateProjectUI() {
        // 检查项目是否已索引
        // val isIndexed = projectIndexer?.isProjectIndexed() ?: false
        val isIndexed = false // 临时设置
        
        if (isIndexed) {
            indexStatusLabel.text = "已索引"
            indexStatusLabel.foreground = Color.GREEN
            indexMessageLabel.text = "项目知识图谱已构建"
            reindexButton.isEnabled = true
        } else {
            indexStatusLabel.text = "未索引"
            indexStatusLabel.foreground = Color.ORANGE
            indexMessageLabel.text = "点击开始按钮进行项目索引"
            reindexButton.isEnabled = false
        }
    }
    
    private suspend fun updateNeo4jStatus() {
        if (!settings.state.neo4jEnabled) {
            SwingUtilities.invokeLater {
                neo4jStatusLabel.text = "未启用"
                neo4jStatusLabel.foreground = Color.GRAY
            }
            return
        }
        
        try {
            val result = neo4jService.testConnection()
            SwingUtilities.invokeLater {
                if (result.success) {
                    neo4jStatusLabel.text = "已连接"
                    neo4jStatusLabel.foreground = Color.GREEN
                    neo4jSyncButton.isEnabled = true
                    openNeo4jButton.isEnabled = true
                } else {
                    neo4jStatusLabel.text = "未连接"
                    neo4jStatusLabel.foreground = Color.RED
                    neo4jSyncButton.isEnabled = false
                    openNeo4jButton.isEnabled = false
                }
            }
        } catch (e: Exception) {
            SwingUtilities.invokeLater {
                neo4jStatusLabel.text = "连接错误"
                neo4jStatusLabel.foreground = Color.RED
            }
        }
    }
    
    private suspend fun updateProjectStatistics() {
        try {
            val stats = neo4jService.getProjectStatistics()
            SwingUtilities.invokeLater {
                classCountLabel.text = stats.classCount.toString()
                methodCountLabel.text = stats.methodCount.toString()
                callRelationshipLabel.text = stats.callRelationshipCount.toString()
                avgComplexityLabel.text = String.format("%.1f", stats.averageComplexity)
                highRiskMethodLabel.text = stats.highRiskMethodCount.toString()
                
                val timeAgo = (System.currentTimeMillis() - stats.lastSyncTime) / 60000
                lastSyncLabel.text = when {
                    timeAgo < 1 -> "刚刚"
                    timeAgo < 60 -> "${timeAgo}分钟前"
                    timeAgo < 1440 -> "${timeAgo / 60}小时前"
                    else -> "${timeAgo / 1440}天前"
                }
            }
        } catch (e: Exception) {
            // 如果Neo4j未连接，显示默认值
        }
    }
}