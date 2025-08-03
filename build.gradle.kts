plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.17.4"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.25"
}

group = "com.vyibc"
version = "1.0.1"

repositories {
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin
dependencies {
    // 添加Gson依赖用于JSON解析
    implementation("com.google.code.gson:gson:2.10.1")

    // 添加JGraphT核心库用于图分析
    implementation("org.jgrapht:jgrapht-core:1.5.2")
    
    // Apache TinkerGraph - 嵌入式图数据库
    implementation("org.apache.tinkerpop:tinkergraph-gremlin:3.6.2")
    
    // Caffeine - 高性能缓存库
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    
    // Neo4j驱动 (可选，用于可视化)
    implementation("org.neo4j.driver:neo4j-java-driver:5.13.0")
    
    // OkHttp - 用于AI服务HTTP调用
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Jackson - 用于高级JSON/YAML处理
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
    
    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // 注意：Kotlin协程已经包含在IntelliJ Platform中，不需要显式添加
}

// Configure Gradle IntelliJ Plugin
intellij {
    version.set("2024.2.5") // 使用稳定版本
    type.set("IC") // IntelliJ IDEA Community Edition

    plugins.set(listOf("vcs-git", "java", "Git4Idea"))
}

tasks {
    patchPluginXml {
        sinceBuild.set("242")
        untilBuild.set("252.*") // 支持更广泛的版本范围

        changeNotes.set("""
            <h3>Version 1.0.1 - 兼容性更新</h3>
            <ul>
                <li><strong>🔧 兼容性修复</strong>: 支持IntelliJ IDEA 2024.3+ (build 251.*)</li>
                <li><strong>📦 构建优化</strong>: 修复Java版本兼容性问题</li>
            </ul>

            <h3>Version 1.0.0 - AI代码评估插件</h3>
            <ul>
                <li><strong>🤖 AI代码评估</strong>: 支持DeepSeek、通义千问、Google Gemini多种AI服务</li>
                <li><strong>🔍 智能分析</strong>: 专门检测生产环境危险操作(Redis keys命令等)</li>
                <li><strong>📊 评分系统</strong>: 0-100分评分，支持自定义最低分数阈值</li>
                <li><strong>🚨 风险评估</strong>: LOW/MEDIUM/HIGH/CRITICAL四级风险等级</li>
                <li><strong>💡 AI提示词配置</strong>: 支持自定义AI分析提示词</li>
                <li><strong>🚀 Git集成</strong>: 自动执行git commit，使用AI生成的提交信息</li>
                <li><strong>📋 多种评估方式</strong>: 支持已暂存变更、已提交变更、右键菜单评估</li>
                <li><strong>🔧 详细过程展示</strong>: 完整显示AI交互过程和Git操作过程</li>
                <li><strong>🌐 翻译功能</strong>: 支持选中文本翻译</li>
                <li><strong>⚙️ 灵活配置</strong>: 丰富的设置选项，支持团队定制</li>
            </ul>
        """.trimIndent())
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
}
