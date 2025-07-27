# Translation Plugin for IntelliJ IDEA

一个强大的IntelliJ IDEA翻译插件，允许你直接在编辑器中翻译选中的文本。

## 功能特性

- 🌍 **多语言支持**: 支持中文、英语、日语、韩语、法语、德语、西班牙语等多种语言
- 🚀 **快速翻译**: 选中文本后右键点击或使用快捷键即可翻译
- ⚙️ **可配置设置**: 可自定义默认目标语言、源语言检测等设置
- 💬 **美观界面**: 提供美观的翻译结果对话框
- 📋 **一键复制**: 可快速复制翻译结果到剪贴板

## 安装方法

### 从源码构建

1. 克隆项目到本地:
```bash
git clone <repository-url>
cd auto-cr-plugin
```

2. 构建插件:
```bash
./gradlew buildPlugin
```

3. 在IntelliJ IDEA中安装:
   - 打开 `File` -> `Settings` -> `Plugins`
   - 点击齿轮图标 -> `Install Plugin from Disk...`
   - 选择 `build/distributions/` 目录下的zip文件

## 使用方法

### 基本使用

1. 在编辑器中选中要翻译的文本
2. 右键点击选择 "Translate" 或使用快捷键 `Ctrl+Alt+T`
3. 查看翻译结果对话框

### 配置设置

1. 打开 `File` -> `Settings` -> `Tools` -> `Translation Settings`
2. 配置以下选项:
   - **默认目标语言**: 设置翻译的目标语言
   - **默认源语言**: 设置源语言（可选择自动检测）
   - **自动检测源语言**: 启用后会自动检测源语言
   - **显示翻译结果对话框**: 控制是否显示详细的翻译对话框

## 支持的语言

- 中文 (zh)
- 英语 (en)
- 日语 (ja)
- 韩语 (ko)
- 法语 (fr)
- 德语 (de)
- 西班牙语 (es)
- 俄语 (ru)
- 意大利语 (it)
- 葡萄牙语 (pt)
- 阿拉伯语 (ar)
- 印地语 (hi)
- 泰语 (th)
- 越南语 (vi)

## 技术实现

- **语言**: Kotlin
- **框架**: IntelliJ Platform SDK
- **翻译服务**: Google Translate API (免费版)
- **UI**: Swing + IntelliJ Platform UI组件
- **异步处理**: Kotlin Coroutines

## 项目结构

```
src/main/kotlin/com/vyibc/autocrplugin/
├── action/
│   └── TranslateAction.kt          # 翻译Action
├── service/
│   ├── TranslationService.kt       # 翻译服务接口
│   └── impl/
│       └── GoogleTranslationService.kt  # Google翻译实现
├── settings/
│   ├── TranslationSettings.kt      # 设置存储
│   └── TranslationConfigurable.kt  # 设置UI
├── ui/
│   └── TranslationDialog.kt        # 翻译结果对话框
└── util/
    └── TranslationBundle.kt        # 国际化工具类
```

## 开发说明

### 环境要求

- JDK 21+
- IntelliJ IDEA 2024.2+
- Gradle 8.0+

### 开发命令

```bash
# 运行插件开发环境
./gradlew runIde

# 构建插件
./gradlew buildPlugin

# 运行测试
./gradlew test
```

## 贡献指南

欢迎提交Issue和Pull Request来改进这个插件！

## 许可证

MIT License

## 更新日志

### v1.0.0
- 初始版本
- 基本翻译功能
- Google Translate集成
- 设置配置页面
- 翻译结果对话框
