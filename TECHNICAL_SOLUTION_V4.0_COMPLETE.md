# **技术方案 V4.0：双流AI代码评审引擎 (完整版)**

---

## 🎯 **1. 核心愿景与目标**

本插件旨在成为一个**AI驱动的、具备辩证分析能力的“代码评审专家”**。它不仅理解代码的表层逻辑，更能洞察其背后的**业务意图**与潜在的**技术风险**，为开发团队提供深度、专业且自动化的代码评审服务。

核心目标是模拟一个资深架构师的评审过程，对一个完整的Merge Request (MR)或特性分支进行全面分析，并回答：

1.  **功 (Merit)**: 这次变更实现了什么有价值的功能？(意图分析)
2.  **过 (Flaw)**: 这次变更引入了哪些技术风险或坏味道？(影响分析)
3.  **策 (Suggestion)**: 如何在保留其功能价值的同时，修复其技术缺陷？(综合建议)

## ⚙️ **2. 核心用户工作流**

1.  **触发**: 开发者在IDE中通过右键菜单或工具栏按钮主动触发分析。插件提供多种灵活的分析模式：
    *   **分支对比模式**: 选择要对比的**源分支**和**目标分支**（标准MR/PR评审）。
    *   **Commit集合模式**: 在Git日志中，手动选择当前分支上的一系列连续或不连续的Commits进行分析。
    *   **(远期规划) URL模式**: 直接粘贴一个GitHub/GitLab的Merge/Pull Request链接进行分析。
2.  **预处理与加权**: 插件在后台启动**“双流分析引擎”**，对两个分支间的代码差异进行扫描，并从**“意图”**和**“风险”**两个维度，对所有相关的调用链路和代码变更进行加权评分。
3.  **两阶段AI分析**: 
    *   **阶段一 (快速筛选)**: 使用轻量级、高速度的AI模型，根据预处理阶段的权重，筛选出“黄金链路”和“高危链路”。
    *   **阶段二 (深度研判)**: 将筛选出的、带有明确上下文（意图/风险）的关键信息，提交给强大的主分析AI模型，进行深度、辩证的分析。
4.  **报告呈现**: 在IDE的专属工具窗口中，展示一份结构化、层次分明的**“AI代码评审报告”**，清晰地列出对意图、风险的分析以及最终的综合建议。

## 🏗️ **3. 系统架构设计 (V4.0)**

```
┌───────────────────────────────────────────────────────────────────────────┐
│                            IntelliJ IDEA Plugin                           │
├───────────────────────────────────────────────────────────────────────────┤
│  展现层 (UI Layer)                                                        │
│  ┌──────────────────────────┐   ┌───────────────────────────────────────┐ │
│  │   分支选择对话框         │   │        AI代码评审报告 (Tool Window)     │ │
│  │   - 源/目标分支          │   │        - Part 1: 意图分析             │ │
│  │   - 触发分析按钮         │   │        - Part 2: 风险分析             │ │
│  └──────────────────────────┘   │        - Part 3: 综合评审             │ │
│                                 └───────────────────────────────────────┘ │
├───────────────────────────────────────────────────────────────────────────┤
│  逻辑层 (Core Layer) - 双流智能分析引擎                                   │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │                     智能预处理器 (双权重系统)                         │ │
│  │  ┌─────────────────────────────┐  ┌────────────────────────────────┐ │
│  │  │   流A: 意图权重计算器       │  │   流B: 风险权重计算器          │ │
│  │  └─────────────────────────────┘  └────────────────────────────────┘ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │                     两阶段大模型调用管理器                            │ │
│  │  ┌─────────────────────────────┐  ┌────────────────────────────────┐ │
│  │  │   阶段一: 预分析 (小模型)   │  │   阶段二: 深度分析 (大模型)    │ │
│  │  └─────────────────────────────┘  └────────────────────────────────┘ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────┐   ┌─────────────────┐  ┌─────────────────┐  │
│  │   上下文聚合器           │   │   PSI解析引擎   │  │  嵌入式图引擎   │  │
│  │   - Git Diff/Log         │   │   - AST遍历     │  │   - JGraphT     │  │
│  │   - 测试用例关联         │   └─────────────────┘  │   - 内存图模型  │  │
│  └──────────────────────────┘                      └─────────────────┘  │
├───────────────────────────────────────────────────────────────────────────┤
│  数据层 (Data Layer) - 无外部依赖                                         │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────────────────┐  │
│  │  图数据磁盘缓存  │  │   索引状态       │  │    插件配置 (Settings)   │  │
│  │  - project.graph│  │   - isIndexed    │  │    - LLM Api Keys      │  │
│  └─────────────────┘  └─────────────────┘  └──────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────────┘
```

## 💾 **4. 数据模型与图引擎设计**

我们将使用**嵌入式图引擎 (JGraphT)**，避免任何外部依赖，但其内部的数据结构将遵循以下经过深思熟虑的、清晰的规范。

### **4.1 节点ID规范**
- **方法节点ID**: `{packageName}.{className}#{methodName}({paramTypes})`
  - *示例*: `com.example.service.UserService#getUser(java.lang.String)`
- **类节点ID**: `{packageName}.{className}`
  - *示例*: `com.example.service.UserService`

### **4.2 核心数据模型 (概念性)**

#### **节点类型 (Node)**
```java
// 概念模型: 方法节点
MethodNode {
    String id; // 唯一标识
    String methodName;
    String signature; // 完整方法签名
    String returnType;
    List<String> paramTypes;
    String blockType; // CONTROLLER, SERVICE, MAPPER, etc.
    boolean isInterface;
    List<String> annotations;
    String filePath; // 绝对路径
    int lineNumber;
}

// 概念模型: 类节点
ClassNode {
    String id; // 唯一标识
    String className;
    String packageName;
    String blockType;
    boolean isInterface;
    String filePath;
}
```

#### **关系类型 (Edge)**
```java
// 概念模型: 调用关系
CallsEdge {
    MethodNode caller;
    MethodNode callee;
    String callType; // DIRECT, INTERFACE, REFLECTION
    int lineNumber; // 调用发生行号
}

// 概念模型: 实现关系
ImplementsEdge {
    MethodNode interfaceMethod;
    MethodNode implementationMethod;
}
```

### **4.3 嵌入式图引擎工作流**
1.  **首次索引**: 插件首次对项目进行全量扫描，使用PSI解析器构建一个完整的调用关系图，存储在内存的`JGraphT`对象中。
2.  **持久化**: 将构建好的图模型序列化成一个二进制文件（如 `project.graph`），存储在项目的`.idea`或`.autocr`目录下，避免每次重启IDE都重新全量索引。
3.  **增量更新**: （对于实时分析场景）监听文件变更，仅重新解析该文件并更新内存图模型。对于MR分析场景，此步骤可忽略。

## 🔧 **5. 核心逻辑与算法深潜**

### **5.1 PSI解析与范围限定**

为提高效率和准确性，我们只索引我们关心的代码。

#### **目标类型识别 (注解优先)**
我们将优先通过检查类是否包含关键注解（如`@RestController`, `@Service`, `@Repository`）来识别其`blockType`。当注解不存在时，才启用备用的、基于类名后缀的正则表达式匹配。

```kotlin
// 示例: 范围限定策略
object ClassTypeDetector {
    // 优先使用注解判断
    fun detectBlockType(psiClass: PsiClass): BlockType? {
        if (psiClass.hasAnnotation("org.springframework.stereotype.Service")) return BlockType.SERVICE
        if (psiClass.hasAnnotation("org.springframework.web.bind.annotation.RestController")) return BlockType.CONTROLLER
        // ... 其他注解
        
        // 注解不存在时，使用备用正则
        return detectByClassName(psiClass.name ?: "")
    }
    
    private fun detectByClassName(className: String): BlockType? {
        // ... V2中的正则表达式逻辑 ...
    }
}
```

### **5.2 上下文聚合器**
此模块负责为AI准备全面、高质量的“案发现场”信息。
1.  **Git差异分析器**: 执行 `git diff --name-status <target_branch>..<source_branch>` 获取所有变更文件列表。对每个文件执行`git diff`获取具体代码变更。
2.  **Git日志提取器**: 执行 `git log <target_branch>..<source_branch` 获取该分支上的所有Commit Message，了解开发者“声称的意图”演进过程。
3.  **测试用例关联器**: 根据变更的业务代码路径（如`src/main`），智能查找并分析对应测试路径（`src/test`）下的文件变更。
4.  **完整方法体提取器**: 对于发生变更的方法，使用PSI提取其**修改前**和**修改后**的完整方法体代码，提供更完整的逻辑上下文。

### **5.3 双流智能预处理器 (核心创新)**
此模块是分析质量的关键，它并行计算两种权重，以筛选信息。

#### **流 A: 意图权重 (Intent Weight)**
*   **目标**: 识别最能代表“开发者想做什么”的**黄金链路 (Golden Path)**。
*   **高权重信号**:
    *   **新端点链路**: 从新的`@PostMapping`或`@GetMapping`出发，贯穿`Service`，最终到达`Repository/Mapper`的完整链路。
    *   **业务名词匹配**: 链路中的类/方法名与Commit Message中的核心名词（如`User`, `Score`）高度相关。
    *   **DTO/VO变更**: 链路中涉及数据传输对象的字段变更。

#### **流 B: 风险权重 (Risk Weight)**
*   **目标**: 识别最可能“引入问题”的**高危链路 (High-Risk Path)**。
*   **高权重信号**:
    *   **架构违规**: 跨层调用 (`Controller` -> `DAO`)。
    *   **高爆炸半径**: 修改了一个被项目大量复用的公共方法或工具类。
    *   **敏感注解**: 链路中涉及 `@Transactional`, `@Async`, `@Scheduled`, `@Lock` 等。
    *   **缺乏测试**: 被修改的核心逻辑没有对应的单元测试变更。
    *   **“跨领域”修改**: 一次提交中修改了多个功能上不相关的模块。

### **5.4 两阶段大模型调用**
1.  **阶段一 (预分析 - 轻量级模型)**: 将所有链路和diff信息交给小模型，让其执行权重计算，输出`Top 1-2 Intent Paths`和`Top 3-5 Risk Paths`及其理由。
2.  **阶段二 (深度分析 - 主力模型)**: 将第一阶段筛选出的、带有明确上下文的高价值信息，提交给强大的主力模型，并使用下面的终极Prompt模板。

### **5.5 终极Prompt模板 (V4.0)**
```text
# Role: Senior Software Architect

You are conducting a comprehensive code review for a Merge Request. Your task is to first analyze the developer's intended functionality and its implementation, and then separately analyze the potential risks and negative impacts. Finally, provide a balanced, holistic review.

# ==========================================
# Part 1: Intent Analysis (The "What")
# ==========================================

# Context for Intent Analysis:
The following "Golden Path" is believed to represent the core feature being implemented. The associated code changes and commit messages are also provided.

- **Golden Path**: ${top_intent_path}
- **Related Code Changes**: ${intent_related_diffs}
- **Developer's Stated Intent (Commit Msg)**: "${commit_messages}"

# Your Task for Part 1:
Based ONLY on the information for Intent Analysis, please answer:
1.  **Functionality Implemented**: Describe the business feature or technical functionality the developer has added or changed.
2.  **Implementation Summary**: Briefly explain how this functionality was technically realized through the provided code path.

# ==========================================
# Part 2: Impact & Risk Analysis (The "How")
# ==========================================

# Context for Risk Analysis:
The following "High-Risk Paths" have been identified by a pre-analysis tool due to potential issues.

<for each risk_path>
- **Risk Path**: ${risk_path}
- **Identified Risk**: ${reason_for_high_risk_weight}
- **Related Code Changes**: ${risk_related_diffs}
</for>

# Your Task for Part 2:
Based ONLY on the risk-related information, please answer:
1.  **Potential Bugs**: Identify any specific potential bugs or logical errors.
2.  **Architectural Concerns**: Point out any violations of software architecture principles.
3.  **Maintenance Issues**: Highlight any changes that might make the code harder to maintain.

# ==========================================
# Part 3: Holistic Review & Final Verdict
# ==========================================

# Your Task for Part 3:
Now, considering your analysis from both Part 1 and Part 2, provide a final, balanced code review.

### Overall Summary:
(Acknowledge the value of the implemented feature.)

### Actionable Recommendations:
(Provide a prioritized list of concrete suggestions, linking each back to a risk from Part 2.)
- **[High Priority]**: ...
- **[Medium Priority]**: ...
- **[Low Priority/Suggestion]**: ...

### Final Approval:
(Conclude with a final recommendation: "Approved with required changes," "Requires major rework," or "Looks good to merge.")
```

## 📈 **6. 成功指标**

### **性能指标**
- **分析速度**: 中型项目 (100k LoC, 50 files changed) 的完整MR分析 < 90秒。
- **内存占用**: 插件索引和分析期间的峰值内存占用 < 1GB。
- **准确率**: 关键链路（意图/风险）识别准确率 > 90%。

### **用户体验指标**
- **易用性**: 从触发到看到报告，操作步骤 < 3步。
- **报告质量**: AI生成的评审意见被开发者采纳率 > 70%。
- **错误率**: 插件运行时崩溃率 < 0.1%。

## 🗺️ **7. 实施路线图**

1.  **阶段一：基础引擎搭建**: 实现`嵌入式图引擎`和`上下文聚合器`。
2.  **阶段二：双流预处理器**: 开发`智能预处理器`，实现双权重计算算法。
3.  **阶段三：AI集成与呈现**: 实现`两阶段大模型调用管理器`和UI界面。
4.  **阶段四：高级功能与优化**: 集成CI/CD流程，支持GitHub/GitLab API，提供配置界面允许用户微调权重规则。



以下是在原方案基础上，融合了各项建议后形成的一份完整的、可直接用于指导下一步迭代的优化方案。我们将建议按优先级（高、中、低）进行了划分。

🔴 高优先级优化建议
1. 核心引擎与AI集成：明确化、可控化
   当前方案对AI的依赖是核心，但也是最大的不确定性来源。必须将其明确化和可控化。

建议1.1: 增加AI模型支持与选型说明

问题: 未明确支持哪些AI模型，也未区分轻重模型的选型，这对于成本和性能评估是至关重要的。

优化:

模型兼容性: 方案应明确支持主流的大语言模型，如 OpenAI GPT系列 (GPT-4o/GPT-3.5-Turbo), Anthropic Claude系列 (Opus/Sonnet/Haiku), Google Gemini系列 (Pro/Flash)，并考虑支持通过LoRA等技术微调的本地模型，以适应不同企业的安全和成本需求。

推荐选型:

阶段一 (快速筛选): 推荐使用高性价比、低延迟的模型，如 Claude 3 Haiku 或 Gemini 1.5 Flash。目标是在秒级完成链路的初步筛选和权重计算。

阶段二 (深度研判): 推荐使用能力最强的模型，如 GPT-4o 或 Claude 3 Opus，以保证分析的深度和准确性。

建议1.2: 建立API配额与成本保护机制

问题: 频繁的AI调用可能导致API费用失控。

优化: 在“两阶段大模型调用管理器”中，必须内置API配额保护器。可以设置日/月调用次数或费用上限，当接近阈值时，自动降级服务（如仅使用轻量模型）或通知管理员，避免预算超支。

建议1.3: 明确化“权重计算”这一核心算法

问题: “双流智能预处理器”是本方案的灵魂，但其权重计算的具体算法仍是一个“黑盒”。

优化: 应将权重计算的逻辑明确化。初期可以是一个可配置的加权求和模型 (RiskScore = w1 * is_arch_violation + w2 * blast_radius_score + ...)。这样做的好处是：1) 使分析过程透明可控；2) 为后续基于反馈的“自我学习”提供了调优的基础。

🟡 中优先级优化建议
2. 报告、工作流与DevOps集成：标准化、自动化
   要让工具真正融入团队，就必须打破IDE的壁垒，适应多样化的DevOps工作流。

建议2.1: 标准化评审报告结构与输出

问题: UI中的报告结构定义粗略，不利于机器读取和自动化流程集成。

优化:

定义JSON Schema: 为AI评审报告设计一套严格的 JSON Schema。这份Schema应包含元数据（分支、提交者）、功/过/策的详细条目、风险等级、代码位置、建议代码片段等。

支持多种输出格式: 提供将报告导出为 结构化JSON (用于CI/CD集成)、Markdown (用于GitLab/GitHub MR评论) 和 HTML (用于生成静态报告页面) 的功能。

报告持久化: 支持将每次的评审报告（如mr-123.json）保存到项目的特定目录（如.autocr/reports/）或输出到日志，便于归档和追溯。

建议2.2: 建立评估回流与自我学习机制

问题: 方案是单向的“AI输出”，缺乏反馈闭环，无法持续进化。

优化:

开发者反馈接口: 在UI报告的每条建议旁，增加简单的交互按钮，如 “👍 采纳” / “👎 不准确”。

数据驱动调优: 收集这些反馈数据，用于量化评估评审命中率/误报率。定期利用这些数据，反向微调“意图权重”和“风险权重”计算器中的权重系数，实现模型的自我进化。

建议2.3: 丰富上下文数据的深度与广度

问题: 当前数据模型主要关注调用关系，对代码内在质量和配置风险的洞察不足。

优化:

增强节点信息: 为MethodNode增加关键的静态分析指标，如圈复杂度、代码行数、入度/出度，这些本身就是强烈的风险信号。

引入数据流分析: 尝试分析关键变量（尤其是DTO）在调用链路中的传递和使用情况，发现“定义但未使用”、“异常传递”等问题。

扫描配置文件变更: 将*.properties, *.yml, pom.xml等配置文件的变更纳入高风险分析范畴。

🟢 低优先级优化建议
3. 用户体验与生态扩展：可视化、平台化
   这些建议旨在提升工具的易用性和适用范围，使其更具吸引力。

建议3.1: 增强分析过程的可视化

问题: 用户不理解AI是如何得出结论的，信任感不足。

优化:

提供UI Mockup: 在方案中增加报告界面的UI展示草图 (Mockup)，清晰展示Golden Path和High-Risk Path是如何呈现给用户的。

链路权重可视化: 设计一个简易的调用图可视化组件，用颜色深浅或线条粗细来表示不同链路的“意图权重”或“风险权重”，让AI的“思考过程”更直观。

建议3.2: 提升对多样化工作流的适应性

问题: 插件目前强绑定于IDE手动触发，流程刚性。

优化: 将核心引擎设计成一个可独立调用的服务，从而支持：

CI/CD集成: 作为GitHub Action或GitLab CI Job运行，自动为MR/PR创建评审评论。

Git Hooks: 通过配置注册为本地的pre-commit或pre-push钩子，在提交前进行检查。

命令行接口 (CLI): 提供一个CLI工具，允许开发者在终端中对两个分支进行分析。

多IDE生态: 核心逻辑与IDE解耦，未来可以更方便地移植到VSCode等其他平台。

