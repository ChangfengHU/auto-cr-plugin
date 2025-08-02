### **技术方案 V3.0：智能代码意图分析与评审插件**

#### 🎯 **1. 核心愿景与目标**

我们的目标不是构建一个简单的调用链路查看器，而是打造一个**AI驱动的、能理解开发者意tu的“结对编程伙伴”**。

当开发者完成一系列修改后，插件应能主动分析并回答以下核心问题：
1.  **“你刚才想做什么？” (意图推断)**
2.  **“你是怎么做的？” (技术方案总结)**
3.  **“这样做可能会有什么问题？” (影响与风险评估)**
4.  **“有没有更好的方法？” (优化建议)**

#### ⚙️ **2. 核心用户工作流**

1.  **编码**: 开发者在IDE中正常进行代码编写和修改。
2.  **自动感知**: 插件在后台静默地监听文件变更。当检测到一系列相关的、有意义的修改时（例如，短时间内修改了多个文件，或一个文件的多处地方），分析流程被触发。
3.  **上下文聚合**: 插件自动从项目中抓取与变更相关的**多维度上下文信息**。
4.  **AI分析**: 将聚合后的上下文信息发送给大语言模型（LLM），进行深度分析。
5.  **报告呈现**: 在IDE的专属工具窗口中，以清晰、结构化的方式向用户展示一份“代码变更分析报告”。

#### 🏗️ **3. 架构设计 (V3.0 - 轻量级嵌入式)**

我们将采用一个完全无外部依赖的轻量级架构。

```
┌─────────────────────────────────────────────────────────────┐
│                    IntelliJ IDEA Plugin                     │
├─────────────────────────────────────────────────────────────┤
│  展现层 (UI Layer)                                           │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                 智能分析报告面板 (Tool Window)          │  │
│  │  - 推断意图、技术方案、风险评估、优化建议 (Markdown)    │  │
│  └───────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│  逻辑层 (Core Layer) - 核心引擎                            │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │   变更检测器     │  │  上下文聚合器    │  │  智能Prompt生成器│ │
│  │   - 文件监听     │  │  - 代码Diff提取  │  │  - 结构化Prompt │ │
│  │   - 触发分析     │  │  - 调用链路分析  │  │  - 对话模板     │ │
│  │                  │  │  - Git意图提取   │  └──────────────┘ │
│  └─────────────────┘  │  - 测试用例关联  │  ┌──────────────┐ │
│                       │  - 注释文档提取  │  │  LLM服务客户端 │ │
│  ┌─────────────────┐  └─────────────────┘  │  - API请求      │ │
│  │   PSI解析引擎   │                       │  - 结果解析     │ │
│  │   - AST遍历     │  ┌─────────────────┐  └──────────────┘ │
│  │   - 关系构建    │  │  嵌入式图引擎    │                     │
│  └─────────────────┘  │  - 内存图模型    │                     │
│                       │  - 增量更新      │                     │
│                       └─────────────────┘                     │
├─────────────────────────────────────────────────────────────┤
│  数据层 (Data Layer) - 无外部依赖                            │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │   图数据缓存     │  │   索引状态       │  │   插件配置    │ │
│  │  - 磁盘文件     │  │   - 是否完成     │  │  - API Key    │ │
│  │  - (e.g., .json)│  │   - 节点/关系数  │  │  - 用户偏好   │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

#### 💾 **4. 关键组件设计深潜**

##### **A. 嵌入式图引擎 (Embedded Graph Engine)**

这是替代 Neo4j 的核心。

*   **技术选型**: 使用成熟的Java/Kotlin图论库，例如 **JGraphT**。它轻量、高效，且完全在JVM内部运行。
*   **数据模型**:
    *   `Node`: 主要为 `MethodNode` 和 `ClassNode`。
    *   `Edge`: 主要为 `CALLS` (调用), `IMPLEMENTS` (实现)。
*   **工作流程**:
    1.  **首次索引**: 插件首次启动时，对项目进行全量扫描，使用PSI解析器构建一个完整的调用关系图，并将其保存在内存中的 JGraphT 对象里。
    2.  **持久化**: 为了避免每次启动都重新索引，将构建好的图模型序列化成一个文件（如 `project_graph.bin` 或 `.json`），存储在项目的 `.idea` 目录或根目录的 `.autocr` 文件夹下。
    3.  **增量更新**: 监听文件变更，当一个文件被修改时，只重新解析该文件，并更新内存图模型中与之相关的节点和边。这能保证极快的响应速度。

##### **B. 上下文聚合器 (Context Aggregator)**

这是实现“智能”的关键，它负责为大模型准备“食材”。

1.  **代码Diff收集器**:
    *   获取变更文件的 `diff`。更进一步，对于被修改的核心方法，直接提取**修改前**和**修改后**的完整方法体代码。

2.  **调用链路分析器**:
    *   利用内存中的**嵌入式图引擎**。
    *   输入：变更的多个方法节点。
    *   输出：连接这些节点的、最重要的几条调用路径。

3.  **Git意图提取器**:
    *   调用IDE的VCS API或直接执行 `git` 命令。
    *   获取与当前变更相关的**暂存区(Staged)的Commit Message**。如果暂存区为空，可以尝试获取最近一次的Commit Message作为参考。

4.  **测试影响分析器**:
    *   当 `src/main/java/com/xx/UserService.java` 发生变更时，智能地查找并分析 `src/test/java/com/xx/UserServiceTest.java` 的变更。
    *   提取测试用例的 `diff`，这直接反映了功能的预期行为变化。

5.  **语义文档收集器**:
    *   使用PSI，提取被修改方法及其所在类的 Javadoc 或 KDoc 注释。

##### **C. 智能Prompt生成器 (Smart Prompter)**

这是连接业务逻辑和AI能力的桥梁，它的质量直接决定了AI的输出质量。

*   **职责**: 将`上下文聚合器`收集到的所有信息，动态地、结构化地组合成一个高质量的Prompt。
*   **Prompt模板示例**:

```text
# Role: Senior Software Architect

# Context:
You are analyzing a series of code changes in a Java project. Your goal is to understand the developer's intent, evaluate their technical solution, and provide constructive feedback.

# Provided Information:

## 1. Developer's Stated Intent (from Git Commit Message):
"${git_commit_message}"

## 2. Core Code Changes:
<for each changed_file>
### File: ${file_path}
#### Before:
'''java
${method_code_before}
'''
#### After:
'''java
${method_code_after}
'''
</for>

## 3. Affected Core Call Chain:
- ${call_chain_1}
- ${call_chain_2}

## 4. Associated Test Case Changes:
### File: ${test_file_path}
'''diff
${test_file_diff}
'''

# Your Task:
Based on all the information provided above, please provide a concise analysis in the following structure:

### 1. Inferred Intent:
(Summarize what the developer was trying to achieve in one or two sentences.)

### 2. Technical Solution:
(Describe the technical approach the developer took to implement the changes.)

### 3. Evaluation & Potential Risks:
(Assess the solution's quality, considering readability, efficiency, and potential side effects or risks.)

### 4. Actionable Suggestions:
(Provide 1-2 specific, constructive suggestions for improvement. If the code is already excellent, acknowledge it.)
```

#### 🎨 **5. UI/UX 设想**

*   在IDE右侧或底部创建一个专用的工具窗口，名为“**AI Code Review**”。
*   当分析完成后，自动弹出该窗口并展示Markdown格式的报告。
*   报告应包含清晰的标题（意图、方案、评估、建议），并使用代码块、列表等元素，使其易于阅读。
*   可以加入“重新生成”、“分析更多”等交互按钮。

#### 🗺️ **6. 实施路线图 (Roadmap)**

1.  **阶段一：奠定基础 (Foundation)**
    *   ✅ **目标**: 替换Neo4j，实现基于JGraphT的嵌入式图分析引擎。
    *   **任务**:
        *   集成JGraphT库。
        *   实现全量项目扫描和图构建逻辑。
        *   实现图的磁盘序列化与加载。
        *   实现基于文件变更的增量更新。

2.  **阶段二：丰富上下文 (Context Enrichment)**
    *   ✅ **目标**: 构建完整的`上下文聚合器`。
    *   **任务**:
        *   开发Git意图提取器。
        *   开发测试用例关联与分析模块。
        *   开发注释文档提取器。

3.  **阶段三：智能实现 (Intelligence)**
    *   ✅ **目标**: 对接大模型并呈现结果。
    *   **任务**:
        *   实现`智能Prompt生成器`。
        *   实现LLM API的调用客户端。
        *   开发用于展示分析报告的UI工具窗口。

---

这份方案为您勾勒了一个宏大但可行的蓝图。它不仅解决了您最初的技术痛点，更将您的产品提升到了一个全新的、极具竞争力的战略高度。这不再是一个简单的工具，而是一个能够与开发者“对话”和“思考”的智能伙伴。
