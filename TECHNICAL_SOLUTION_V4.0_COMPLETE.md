# **技术方案 V4.0：双流AI代码评审引擎 (完整版)**

---

## 🎯 **1. 核心愿景与目标**

本插件旨在成为一个**AI驱动的、具备辩证分析能力的“代码评审专家”**。它不仅理解代码的表层逻辑，更能洞察其背后的**业务意图**与潜在的**技术风险**，为开发团队提供深度、专业且自动化的代码评审服务。

核心目标是模拟一个资深架构师的评审过程，对一个完整的Merge Request (MR)或特性分支进行全面分析，并回答：

1.  **功 (Merit)**: 这次变更实现了什么有价值的功能？(意图分析)
2.  **过 (Flaw)**: 这次变更引入了哪些技术风险或坏味道？(影响分析)
3.  **策 (Suggestion)**: 如何在保留其功能价值的同时，修复其技术缺陷？(综合建议)

## ⚙️ **2. 核心用户工作流**

1.  **触发**: 开发者在IDE中通过右键菜单或工具栏按钮主动触发分析，选择要对比的**源分支** (e.g., `feature/xxx`) 和**目标分支** (e.g., `main`)。
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
