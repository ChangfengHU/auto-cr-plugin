plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.vyibc"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin
dependencies {
    // 添加Gson依赖用于JSON解析
    implementation("com.google.code.gson:gson:2.10.1")

    // 添加Kotlin协程依赖
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
}

// Configure Gradle IntelliJ Plugin
intellij {
    version.set("2024.2.5")
    type.set("IC") // IntelliJ IDEA Community Edition

    plugins.set(listOf("vcs-git"))
}

tasks {
    patchPluginXml {
        sinceBuild.set("242")
        untilBuild.set("242.*")

        changeNotes.set("""
            Initial version with translation functionality
        """.trimIndent())
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }
}
