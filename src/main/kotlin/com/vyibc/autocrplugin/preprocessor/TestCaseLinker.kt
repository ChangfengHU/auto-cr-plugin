package com.vyibc.autocrplugin.preprocessor

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.vyibc.autocrplugin.analyzer.FileAnalysisResult
import com.vyibc.autocrplugin.graph.model.CallPath
import com.vyibc.autocrplugin.graph.model.MethodNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 测试用例关联器 - V5.1版本
 * 测试用例关联器、覆盖率分析、缺失测试识别
 */
class TestCaseLinker(private val project: Project) {
    
    /**
     * 分析调用路径的测试覆盖情况
     */
    suspend fun analyzePathTestCoverage(
        path: CallPath,
        fileAnalysisResults: List<FileAnalysisResult>
    ): PathTestCoverageResult = withContext(Dispatchers.IO) {
        
        val methodCoverages = mutableListOf<MethodTestCoverage>()
        
        // 分析路径中每个方法的测试覆盖情况
        path.methods.forEach { method ->
            val coverage = analyzeMethodTestCoverage(method, fileAnalysisResults)
            methodCoverages.add(coverage)
        }
        
        // 计算路径整体覆盖率
        val overallCoverage = calculateOverallCoverage(methodCoverages)
        
        // 识别测试覆盖缺口
        val testGaps = identifyTestGaps(path, methodCoverages)
        
        // 生成测试建议
        val testRecommendations = generateTestRecommendations(path, methodCoverages)
        
        // 分析关键路径的测试需求
        val criticalTestNeeds = analyzeCriticalTestNeeds(path, methodCoverages)
        
        PathTestCoverageResult(
            path = path,
            overallCoverage = overallCoverage,
            methodCoverages = methodCoverages,
            testGaps = testGaps,
            recommendations = testRecommendations,
            criticalTestNeeds = criticalTestNeeds,
            riskLevel = determineTestRiskLevel(overallCoverage, testGaps.size)
        )
    }
    
    /**
     * 分析方法的测试覆盖情况
     */
    suspend fun analyzeMethodTestCoverage(
        method: MethodNode,
        fileAnalysisResults: List<FileAnalysisResult>
    ): MethodTestCoverage = withContext(Dispatchers.IO) {
        
        // 查找直接测试
        val directTests = findDirectTests(method)
        
        // 查找间接测试（通过调用链）
        val indirectTests = findIndirectTests(method)
        
        // 分析测试质量
        val testQuality = analyzeTestQuality(directTests + indirectTests)
        
        // 计算覆盖率分数
        val coverageScore = calculateCoverageScore(method, directTests, indirectTests)
        
        // 识别测试场景
        val testScenarios = identifyTestScenarios(method, directTests)
        
        // 识别缺失的测试场景
        val missingScenarios = identifyMissingTestScenarios(method, testScenarios)
        
        MethodTestCoverage(
            method = method,
            directTests = directTests,
            indirectTests = indirectTests,
            coverageScore = coverageScore,
            testQuality = testQuality,
            coveredScenarios = testScenarios,
            missingScenarios = missingScenarios,
            hasMockTests = hasMockTests(directTests),
            hasIntegrationTests = hasIntegrationTests(directTests),
            hasEdgeCaseTests = hasEdgeCaseTests(directTests)
        )
    }
    
    /**
     * 查找方法的直接测试
     */
    private suspend fun findDirectTests(method: MethodNode): List<TestMethod> = withContext(Dispatchers.IO) {
        val tests = mutableListOf<TestMethod>()
        
        // 构建可能的测试方法名模式
        val testNamePatterns = generateTestNamePatterns(method)
        
        // 搜索测试类
        val testClasses = findTestClasses(method)
        
        testClasses.forEach { testClass ->
            testClass.methods.forEach { psiMethod ->
                if (isTestMethod(psiMethod) && matchesTestPattern(psiMethod.name, testNamePatterns)) {
                    tests.add(createTestMethod(psiMethod, TestType.UNIT))
                }
            }
        }
        
        tests
    }
    
    /**
     * 查找方法的间接测试
     */
    private suspend fun findIndirectTests(method: MethodNode): List<TestMethod> = withContext(Dispatchers.IO) {
        val tests = mutableListOf<TestMethod>()
        
        // 简化实现：查找集成测试和端到端测试
        val integrationTests = findIntegrationTests(method)
        val e2eTests = findEndToEndTests(method)
        
        tests.addAll(integrationTests)
        tests.addAll(e2eTests)
        
        tests
    }
    
    /**
     * 生成测试方法名模式
     */
    private fun generateTestNamePatterns(method: MethodNode): List<String> {
        val methodName = method.methodName
        val className = method.id.substringBefore("#").substringAfterLast(".")
        
        return listOf(
            "test$methodName",
            "test${methodName.capitalize()}",
            "${methodName}Test",
            "should.*${methodName.capitalize()}.*",
            "when.*${methodName.capitalize()}.*",
            "given.*${methodName.capitalize()}.*",
            "test${className}${methodName.capitalize()}",
            "${methodName}_.*",
            ".*_${methodName}_.*"
        )
    }
    
    /**
     * 查找测试类
     */
    private fun findTestClasses(method: MethodNode): List<PsiClass> {
        val className = method.id.substringBefore("#").substringAfterLast(".")
        val packageName = method.id.substringBefore("#").substringBeforeLast(".")
        
        val testClassNames = listOf(
            "${className}Test",
            "${className}Tests",
            "${className}UnitTest",
            "${className}IntegrationTest",
            "Test$className"
        )
        
        val testClasses = mutableListOf<PsiClass>()
        val psiFacade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        
        testClassNames.forEach { testClassName ->
            // 在相同包中查找
            psiFacade.findClass("$packageName.$testClassName", scope)?.let {
                testClasses.add(it)
            }
            
            // 在测试包中查找
            val testPackage = packageName.replace(".main.", ".test.").replace(".src.", ".test.")
            psiFacade.findClass("$testPackage.$testClassName", scope)?.let {
                testClasses.add(it)
            }
        }
        
        return testClasses
    }
    
    /**
     * 判断是否为测试方法
     */
    private fun isTestMethod(method: PsiMethod): Boolean {
        return method.annotations.any { annotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName == "org.junit.Test" ||
            qualifiedName == "org.junit.jupiter.api.Test" ||
            qualifiedName == "org.testng.annotations.Test" ||
            qualifiedName?.contains("Test") == true
        } || method.name.startsWith("test")
    }
    
    /**
     * 匹配测试模式
     */
    private fun matchesTestPattern(testMethodName: String, patterns: List<String>): Boolean {
        return patterns.any { pattern ->
            try {
                testMethodName.matches(Regex(pattern, RegexOption.IGNORE_CASE))
            } catch (e: Exception) {
                testMethodName.contains(pattern, ignoreCase = true)
            }
        }
    }
    
    /**
     * 创建测试方法对象
     */
    private fun createTestMethod(psiMethod: PsiMethod, type: TestType): TestMethod {
        val annotations = psiMethod.annotations.map { it.qualifiedName ?: "" }
        val testFramework = determineTestFramework(annotations)
        val complexity = analyzeTestComplexity(psiMethod)
        
        return TestMethod(
            name = psiMethod.name,
            type = type,
            framework = testFramework,
            complexity = complexity,
            hasAssertions = hasAssertions(psiMethod),
            hasMocks = hasMocks(psiMethod),
            coverageAreas = identifyCoverageAreas(psiMethod),
            filePath = psiMethod.containingFile.virtualFile.path,
            lineNumber = getLineNumber(psiMethod)
        )
    }
    
    /**
     * 确定测试框架
     */
    private fun determineTestFramework(annotations: List<String>): TestFramework {
        return when {
            annotations.any { it.contains("junit.jupiter") } -> TestFramework.JUNIT5
            annotations.any { it.contains("junit") } -> TestFramework.JUNIT4
            annotations.any { it.contains("testng") } -> TestFramework.TESTNG
            annotations.any { it.contains("spek") } -> TestFramework.SPEK
            else -> TestFramework.UNKNOWN
        }
    }
    
    /**
     * 分析测试复杂度
     */
    private fun analyzeTestComplexity(psiMethod: PsiMethod): TestComplexity {
        val statements = PsiTreeUtil.findChildrenOfType(psiMethod, PsiStatement::class.java)
        val assertCount = countAssertions(psiMethod)
        val mockCount = countMocks(psiMethod)
        
        return when {
            statements.size > 20 || assertCount > 10 || mockCount > 5 -> TestComplexity.HIGH
            statements.size > 10 || assertCount > 5 || mockCount > 2 -> TestComplexity.MEDIUM
            else -> TestComplexity.LOW
        }
    }
    
    /**
     * 检查是否有断言
     */
    private fun hasAssertions(psiMethod: PsiMethod): Boolean {
        val methodText = psiMethod.text
        val assertionKeywords = listOf(
            "assert", "assertEquals", "assertTrue", "assertFalse", "assertNull",
            "assertNotNull", "assertThat", "expect", "should", "verify"
        )
        
        return assertionKeywords.any { keyword ->
            methodText.contains(keyword, ignoreCase = true)
        }
    }
    
    /**
     * 检查是否有Mock
     */
    private fun hasMocks(psiMethod: PsiMethod): Boolean {
        val methodText = psiMethod.text
        val mockKeywords = listOf(
            "mock", "spy", "stub", "when", "given", "doReturn", "thenReturn"
        )
        
        return mockKeywords.any { keyword ->
            methodText.contains(keyword, ignoreCase = true)
        }
    }
    
    /**
     * 识别覆盖区域
     */
    private fun identifyCoverageAreas(psiMethod: PsiMethod): List<String> {
        val areas = mutableListOf<String>()
        val methodText = psiMethod.text.lowercase()
        
        if (methodText.contains("exception") || methodText.contains("error")) {
            areas.add("异常处理")
        }
        if (methodText.contains("null")) {
            areas.add("空值处理")
        }
        if (methodText.contains("edge") || methodText.contains("boundary")) {
            areas.add("边界条件")
        }
        if (methodText.contains("valid") || methodText.contains("invalid")) {
            areas.add("输入验证")
        }
        
        return areas
    }
    
    /**
     * 计算覆盖率分数
     */
    private fun calculateCoverageScore(
        method: MethodNode,
        directTests: List<TestMethod>,
        indirectTests: List<TestMethod>
    ): Double {
        var score = 0.0
        
        // 基础覆盖率
        if (directTests.isNotEmpty()) score += 0.6
        if (indirectTests.isNotEmpty()) score += 0.2
        
        // 测试质量加分
        val hasQualityTests = directTests.any { it.hasAssertions && it.hasMocks }
        if (hasQualityTests) score += 0.2
        
        // 覆盖场景加分
        val totalCoverageAreas = (directTests + indirectTests).flatMap { it.coverageAreas }.toSet()
        score += totalCoverageAreas.size * 0.05
        
        return minOf(1.0, score)
    }
    
    /**
     * 分析测试质量
     */
    private fun analyzeTestQuality(tests: List<TestMethod>): TestQuality {
        if (tests.isEmpty()) return TestQuality.NONE
        
        val hasAssertions = tests.any { it.hasAssertions }
        val hasMocks = tests.any { it.hasMocks }
        val hasComplexTests = tests.any { it.complexity == TestComplexity.HIGH }
        val coverageAreas = tests.flatMap { it.coverageAreas }.toSet()
        
        val qualityScore = listOf(
            if (hasAssertions) 0.3 else 0.0,
            if (hasMocks) 0.2 else 0.0,
            if (hasComplexTests) 0.2 else 0.0,
            coverageAreas.size * 0.1
        ).sum()
        
        return when {
            qualityScore >= 0.8 -> TestQuality.HIGH
            qualityScore >= 0.5 -> TestQuality.MEDIUM
            qualityScore >= 0.2 -> TestQuality.LOW
            else -> TestQuality.POOR
        }
    }
    
    /**
     * 识别测试场景
     */
    private fun identifyTestScenarios(method: MethodNode, tests: List<TestMethod>): List<TestScenario> {
        val scenarios = mutableListOf<TestScenario>()
        
        tests.forEach { test ->
            val testName = test.name.lowercase()
            val scenario = when {
                testName.contains("success") || testName.contains("valid") -> 
                    TestScenario.HAPPY_PATH
                testName.contains("error") || testName.contains("exception") || testName.contains("fail") ->
                    TestScenario.ERROR_HANDLING
                testName.contains("null") || testName.contains("empty") ->
                    TestScenario.NULL_EMPTY_INPUT
                testName.contains("edge") || testName.contains("boundary") || testName.contains("limit") ->
                    TestScenario.EDGE_CASES
                testName.contains("invalid") || testName.contains("bad") ->
                    TestScenario.INVALID_INPUT
                else -> TestScenario.GENERAL
            }
            
            if (!scenarios.contains(scenario)) {
                scenarios.add(scenario)
            }
        }
        
        return scenarios
    }
    
    /**
     * 识别缺失的测试场景
     */
    private fun identifyMissingTestScenarios(
        method: MethodNode,
        coveredScenarios: List<TestScenario>
    ): List<TestScenario> {
        val allScenarios = when (method.blockType) {
            com.vyibc.autocrplugin.graph.model.BlockType.CONTROLLER -> 
                listOf(TestScenario.HAPPY_PATH, TestScenario.ERROR_HANDLING, TestScenario.INVALID_INPUT)
            com.vyibc.autocrplugin.graph.model.BlockType.SERVICE ->
                listOf(TestScenario.HAPPY_PATH, TestScenario.ERROR_HANDLING, TestScenario.NULL_EMPTY_INPUT, TestScenario.EDGE_CASES)
            com.vyibc.autocrplugin.graph.model.BlockType.REPOSITORY ->
                listOf(TestScenario.HAPPY_PATH, TestScenario.ERROR_HANDLING, TestScenario.NULL_EMPTY_INPUT)
            else ->
                listOf(TestScenario.HAPPY_PATH, TestScenario.ERROR_HANDLING)
        }
        
        return allScenarios.filter { it !in coveredScenarios }
    }
    
    /**
     * 计算整体覆盖率
     */
    private fun calculateOverallCoverage(methodCoverages: List<MethodTestCoverage>): Double {
        if (methodCoverages.isEmpty()) return 0.0
        
        return methodCoverages.map { it.coverageScore }.average()
    }
    
    /**
     * 识别测试缺口
     */
    private fun identifyTestGaps(path: CallPath, methodCoverages: List<MethodTestCoverage>): List<TestGap> {
        val gaps = mutableListOf<TestGap>()
        
        methodCoverages.forEach { coverage ->
            if (coverage.coverageScore < 0.3) {
                gaps.add(TestGap(
                    type = TestGapType.NO_TESTS,
                    method = coverage.method,
                    severity = TestGapSeverity.HIGH,
                    description = "方法缺少测试覆盖"
                ))
            } else if (coverage.missingScenarios.isNotEmpty()) {
                gaps.add(TestGap(
                    type = TestGapType.MISSING_SCENARIOS,
                    method = coverage.method,
                    severity = TestGapSeverity.MEDIUM,
                    description = "缺少测试场景: ${coverage.missingScenarios.joinToString(", ")}"
                ))
            }
            
            if (!coverage.hasEdgeCaseTests && coverage.method.cyclomaticComplexity > 10) {
                gaps.add(TestGap(
                    type = TestGapType.MISSING_EDGE_CASES,
                    method = coverage.method,
                    severity = TestGapSeverity.MEDIUM,
                    description = "高复杂度方法缺少边界测试"
                ))
            }
        }
        
        return gaps
    }
    
    /**
     * 生成测试建议
     */
    private fun generateTestRecommendations(
        path: CallPath,
        methodCoverages: List<MethodTestCoverage>
    ): List<TestRecommendation> {
        val recommendations = mutableListOf<TestRecommendation>()
        
        val lowCoverageMethods = methodCoverages.filter { it.coverageScore < 0.5 }
        if (lowCoverageMethods.isNotEmpty()) {
            recommendations.add(TestRecommendation(
                type = TestRecommendationType.ADD_UNIT_TESTS,
                priority = TestPriority.HIGH,
                description = "为 ${lowCoverageMethods.size} 个方法添加单元测试",
                affectedMethods = lowCoverageMethods.map { it.method.id }
            ))
        }
        
        val noMockMethods = methodCoverages.filter { !it.hasMockTests && it.method.outDegree > 0 }
        if (noMockMethods.isNotEmpty()) {
            recommendations.add(TestRecommendation(
                type = TestRecommendationType.ADD_MOCK_TESTS,
                priority = TestPriority.MEDIUM,
                description = "为有外部依赖的方法添加Mock测试",
                affectedMethods = noMockMethods.map { it.method.id }
            ))
        }
        
        return recommendations
    }
    
    /**
     * 分析关键测试需求
     */
    private fun analyzeCriticalTestNeeds(
        path: CallPath,
        methodCoverages: List<MethodTestCoverage>
    ): List<CriticalTestNeed> {
        val criticalNeeds = mutableListOf<CriticalTestNeed>()
        
        // 关键路径测试
        if (path.pathType == com.vyibc.autocrplugin.graph.model.PathType.CRITICAL_PATH) {
            val pathCoverage = methodCoverages.map { it.coverageScore }.average()
            if (pathCoverage < 0.8) {
                criticalNeeds.add(CriticalTestNeed(
                    type = CriticalTestType.END_TO_END,
                    urgency = TestUrgency.HIGH,
                    description = "关键路径需要端到端测试覆盖",
                    pathId = path.id
                ))
            }
        }
        
        // 入口点测试
        val entryMethods = path.methods.filter { 
            it.blockType == com.vyibc.autocrplugin.graph.model.BlockType.CONTROLLER 
        }
        entryMethods.forEach { method ->
            val coverage = methodCoverages.find { it.method.id == method.id }
            if (coverage?.hasIntegrationTests != true) {
                criticalNeeds.add(CriticalTestNeed(
                    type = CriticalTestType.INTEGRATION,
                    urgency = TestUrgency.HIGH,
                    description = "入口方法需要集成测试",
                    pathId = path.id
                ))
            }
        }
        
        return criticalNeeds
    }
    
    /**
     * 确定测试风险级别
     */
    private fun determineTestRiskLevel(overallCoverage: Double, gapCount: Int): TestRiskLevel {
        return when {
            overallCoverage < 0.3 || gapCount > 5 -> TestRiskLevel.HIGH
            overallCoverage < 0.6 || gapCount > 2 -> TestRiskLevel.MEDIUM
            else -> TestRiskLevel.LOW
        }
    }
    
    // 辅助方法
    private fun findIntegrationTests(method: MethodNode): List<TestMethod> {
        // 简化实现
        return emptyList()
    }
    
    private fun findEndToEndTests(method: MethodNode): List<TestMethod> {
        // 简化实现
        return emptyList()
    }
    
    private fun countAssertions(psiMethod: PsiMethod): Int {
        // 简化实现
        return if (hasAssertions(psiMethod)) 1 else 0
    }
    
    private fun countMocks(psiMethod: PsiMethod): Int {
        // 简化实现
        return if (hasMocks(psiMethod)) 1 else 0
    }
    
    private fun hasMockTests(tests: List<TestMethod>): Boolean {
        return tests.any { it.hasMocks }
    }
    
    private fun hasIntegrationTests(tests: List<TestMethod>): Boolean {
        return tests.any { it.type == TestType.INTEGRATION }
    }
    
    private fun hasEdgeCaseTests(tests: List<TestMethod>): Boolean {
        return tests.any { test ->
            test.coverageAreas.any { area ->
                area.contains("边界") || area.contains("异常")
            }
        }
    }
    
    private fun getLineNumber(element: PsiElement): Int {
        val document = element.containingFile.viewProvider.document ?: return -1
        return document.getLineNumber(element.textOffset) + 1
    }
}

// 数据类定义

/**
 * 路径测试覆盖结果
 */
data class PathTestCoverageResult(
    val path: CallPath,
    val overallCoverage: Double,
    val methodCoverages: List<MethodTestCoverage>,
    val testGaps: List<TestGap>,
    val recommendations: List<TestRecommendation>,
    val criticalTestNeeds: List<CriticalTestNeed>,
    val riskLevel: TestRiskLevel
)

/**
 * 方法测试覆盖
 */
data class MethodTestCoverage(
    val method: MethodNode,
    val directTests: List<TestMethod>,
    val indirectTests: List<TestMethod>,
    val coverageScore: Double,
    val testQuality: TestQuality,
    val coveredScenarios: List<TestScenario>,
    val missingScenarios: List<TestScenario>,
    val hasMockTests: Boolean,
    val hasIntegrationTests: Boolean,
    val hasEdgeCaseTests: Boolean
)

/**
 * 测试方法
 */
data class TestMethod(
    val name: String,
    val type: TestType,
    val framework: TestFramework,
    val complexity: TestComplexity,
    val hasAssertions: Boolean,
    val hasMocks: Boolean,
    val coverageAreas: List<String>,
    val filePath: String,
    val lineNumber: Int
)

/**
 * 测试缺口
 */
data class TestGap(
    val type: TestGapType,
    val method: MethodNode,
    val severity: TestGapSeverity,
    val description: String
)

/**
 * 测试建议
 */
data class TestRecommendation(
    val type: TestRecommendationType,
    val priority: TestPriority,
    val description: String,
    val affectedMethods: List<String>
)

/**
 * 关键测试需求
 */
data class CriticalTestNeed(
    val type: CriticalTestType,
    val urgency: TestUrgency,
    val description: String,
    val pathId: String
)

// 枚举定义

enum class TestType {
    UNIT, INTEGRATION, END_TO_END, PERFORMANCE, SECURITY
}

enum class TestFramework {
    JUNIT4, JUNIT5, TESTNG, SPEK, UNKNOWN
}

enum class TestComplexity {
    LOW, MEDIUM, HIGH
}

enum class TestQuality {
    NONE, POOR, LOW, MEDIUM, HIGH
}

enum class TestScenario {
    HAPPY_PATH, ERROR_HANDLING, NULL_EMPTY_INPUT, EDGE_CASES, INVALID_INPUT, GENERAL
}

enum class TestGapType {
    NO_TESTS, MISSING_SCENARIOS, MISSING_EDGE_CASES, POOR_QUALITY
}

enum class TestGapSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

enum class TestRecommendationType {
    ADD_UNIT_TESTS, ADD_INTEGRATION_TESTS, ADD_MOCK_TESTS, IMPROVE_TEST_QUALITY
}

enum class TestPriority {
    LOW, MEDIUM, HIGH, CRITICAL
}

enum class CriticalTestType {
    INTEGRATION, END_TO_END, PERFORMANCE, SECURITY
}

enum class TestUrgency {
    LOW, MEDIUM, HIGH, CRITICAL
}

enum class TestRiskLevel {
    LOW, MEDIUM, HIGH
}