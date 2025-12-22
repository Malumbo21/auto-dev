package cc.unitmesh.nanodsl.language.parser

import cc.unitmesh.nanodsl.language.psi.NanoDSLFile
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * Parser tests for NanoDSL language.
 * Tests are organized into:
 * 1. Testcase files from xiuper-ui/testcases/expect/ (01-20)
 * 2. Additional feature-specific tests for new grammar features
 */
class NanoDSLParserTest : LightJavaCodeInsightFixtureTestCase() {

    private fun loadTestcaseFile(filename: String): String {
        val stream = javaClass.classLoader.getResourceAsStream("testcases/$filename")
            ?: error("Test resource not found: testcases/$filename")
        return stream.bufferedReader().use { it.readText() }
    }

    private fun assertNoParsingErrors(code: String, testName: String) {
        val file = myFixture.configureByText("test.nanodsl", code) as NanoDSLFile
        assertNotNull(file)
        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue(
            "[$testName] Should have no parsing errors, but found: ${errors.map { it.errorDescription }}",
            errors.isEmpty()
        )
    }

    // ========== Testcase Files (01-20) ==========

    fun test01SimpleCard() {
        val code = loadTestcaseFile("01-simple-card.nanodsl")
        assertNoParsingErrors(code, "01-simple-card")
    }

    fun test02ProductCard() {
        val code = loadTestcaseFile("02-product-card.nanodsl")
        assertNoParsingErrors(code, "02-product-card")
    }

    fun test03CounterCard() {
        val code = loadTestcaseFile("03-counter-card.nanodsl")
        assertNoParsingErrors(code, "03-counter-card")
    }

    fun test04LoginForm() {
        val code = loadTestcaseFile("04-login-form.nanodsl")
        assertNoParsingErrors(code, "04-login-form")
    }

    fun test05TaskList() {
        val code = loadTestcaseFile("05-task-list.nanodsl")
        assertNoParsingErrors(code, "05-task-list")
    }

    fun test06UserProfile() {
        val code = loadTestcaseFile("06-user-profile.nanodsl")
        assertNoParsingErrors(code, "06-user-profile")
    }

    fun test07HttpRequestForm() {
        val code = loadTestcaseFile("07-http-request-form.nanodsl")
        assertNoParsingErrors(code, "07-http-request-form")
    }

    fun test08NestedConditionals() {
        val code = loadTestcaseFile("08-nested-conditionals.nanodsl")
        assertNoParsingErrors(code, "08-nested-conditionals")
    }

    fun test09NestedLoops() {
        val code = loadTestcaseFile("09-nested-loops.nanodsl")
        assertNoParsingErrors(code, "09-nested-loops")
    }

    fun test10ComplexState() {
        val code = loadTestcaseFile("10-complex-state.nanodsl")
        assertNoParsingErrors(code, "10-complex-state")
    }

    fun test11MultiActionSequence() {
        val code = loadTestcaseFile("11-multi-action-sequence.nanodsl")
        assertNoParsingErrors(code, "11-multi-action-sequence")
    }

    fun test12DashboardLayout() {
        val code = loadTestcaseFile("12-dashboard-layout.nanodsl")
        assertNoParsingErrors(code, "12-dashboard-layout")
    }

    fun test13FormValidation() {
        val code = loadTestcaseFile("13-form-validation.nanodsl")
        assertNoParsingErrors(code, "13-form-validation")
    }

    fun test14PaginationList() {
        val code = loadTestcaseFile("14-pagination-list.nanodsl")
        assertNoParsingErrors(code, "14-pagination-list")
    }

    fun test15ShoppingCart() {
        val code = loadTestcaseFile("15-shopping-cart.nanodsl")
        assertNoParsingErrors(code, "15-shopping-cart")
    }

    fun test16MultiPageNavigation() {
        val code = loadTestcaseFile("16-multi-page-navigation.nanodsl")
        assertNoParsingErrors(code, "16-multi-page-navigation")
    }

    fun test17ParameterizedRoute() {
        val code = loadTestcaseFile("17-parameterized-route.nanodsl")
        assertNoParsingErrors(code, "17-parameterized-route")
    }

    fun test18SearchWithQuery() {
        val code = loadTestcaseFile("18-search-with-query.nanodsl")
        assertNoParsingErrors(code, "18-search-with-query")
    }

    fun test19ConditionalNavigation() {
        val code = loadTestcaseFile("19-conditional-navigation.nanodsl")
        assertNoParsingErrors(code, "19-conditional-navigation")
    }

    fun test20MarkdownText() {
        val code = loadTestcaseFile("20-markdown-text.nanodsl")
        assertNoParsingErrors(code, "20-markdown-text")
    }

    // ========== Additional Feature Tests ==========

    /**
     * Test TravelPlan DSL with:
     * - str type alias
     * - dict type for state
     * - dict literals
     * - Icon component
     * - standalone Divider
     * - arithmetic operators (+)
     * - color property
     */
    fun testTravelPlanBudget() {
        val code = """
            component TravelPlan:
                state:
                    transport: str = "train"
                    days: int = 3
                    budget: dict = {"transport": 800, "hotel": 1200, "food": 600, "tickets": 400}
                    checklist: dict = {"id": true, "clothes": false, "medicine": false, "camera": false}
                    notes: str = ""

                VStack(spacing="lg"):
                    Card(padding="md", shadow="sm"):
                        Text("预算估算", style="h3")
                        VStack(spacing="sm"):
                            HStack(justify="between"):
                                HStack:
                                    Icon("train", size="sm")
                                    Text("交通", style="body")
                                Text("¥800", style="body")

                            HStack(justify="between"):
                                HStack:
                                    Icon("hotel", size="sm")
                                    Text("住宿", style="body")
                                Text("¥1200", style="body")

                            HStack(justify="between"):
                                HStack:
                                    Icon("restaurant", size="sm")
                                    Text("餐饮", style="body")
                                Text("¥600", style="body")

                            HStack(justify="between"):
                                HStack:
                                    Icon("confirmation-number", size="sm")
                                    Text("门票", style="body")
                                Text("¥400", style="body")

                            Divider

                            HStack(justify="between"):
                                Text("总计", style="h3")
                                Text("¥3000", style="h3", color="danger")
        """.trimIndent()

        val file = myFixture.configureByText("test.nanodsl", code) as NanoDSLFile
        assertNotNull(file)

        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue("Should have no parsing errors, but found: ${errors.map { it.errorDescription }}", errors.isEmpty())
    }

    /**
     * Test dict literal in state initialization
     */
    fun testDictLiteralInState() {
        val code = """
            component TestDict:
                state:
                    config: dict = {"key1": "value1", "key2": 123, "key3": true}

                Text("Test")
        """.trimIndent()

        val file = myFixture.configureByText("test.nanodsl", code) as NanoDSLFile
        assertNotNull(file)

        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue("Should have no parsing errors, but found: ${errors.map { it.errorDescription }}", errors.isEmpty())
    }

    /**
     * Test list literal in state initialization
     */
    fun testListLiteralInState() {
        val code = """
            component TestList:
                state:
                    items: List = ["item1", "item2", "item3"]
                    numbers: List = [1, 2, 3, 4, 5]

                Text("Test")
        """.trimIndent()

        val file = myFixture.configureByText("test.nanodsl", code) as NanoDSLFile
        assertNotNull(file)

        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue("Should have no parsing errors, but found: ${errors.map { it.errorDescription }}", errors.isEmpty())
    }

    /**
     * Test arithmetic expressions
     */
    fun testArithmeticExpressions() {
        val code = """
            component Calculator:
                state:
                    a: int = 10
                    b: int = 20

                VStack:
                    Text("Sum")
                    if state.a + state.b > 25:
                        Text("Large sum")
                    if state.a * state.b >= 200:
                        Text("Large product")
        """.trimIndent()

        val file = myFixture.configureByText("test.nanodsl", code) as NanoDSLFile
        assertNotNull(file)

        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue("Should have no parsing errors, but found: ${errors.map { it.errorDescription }}", errors.isEmpty())
    }

    /**
     * Test Icon component
     */
    fun testIconComponent() {
        val code = """
            component IconTest:
                HStack:
                    Icon("home", size="lg", color="primary")
                    Icon("settings", size="md")
                    Icon("user")
        """.trimIndent()

        val file = myFixture.configureByText("test.nanodsl", code) as NanoDSLFile
        assertNotNull(file)

        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue("Should have no parsing errors, but found: ${errors.map { it.errorDescription }}", errors.isEmpty())
    }

    /**
     * Test standalone Divider (without parentheses)
     */
    fun testStandaloneDivider() {
        val code = """
            component DividerTest:
                VStack:
                    Text("Section 1")
                    Divider
                    Text("Section 2")
                    Divider
                    Text("Section 3")
        """.trimIndent()

        val file = myFixture.configureByText("test.nanodsl", code) as NanoDSLFile
        assertNotNull(file)

        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue("Should have no parsing errors, but found: ${errors.map { it.errorDescription }}", errors.isEmpty())
    }

    /**
     * Test str type alias
     */
    fun testStrTypeAlias() {
        val code = """
            component StrTest:
                state:
                    name: str = "John"
                    message: str = ""

                Input(value := state.name, placeholder="Enter name")
        """.trimIndent()

        val file = myFixture.configureByText("test.nanodsl", code) as NanoDSLFile
        assertNotNull(file)

        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue("Should have no parsing errors, but found: ${errors.map { it.errorDescription }}", errors.isEmpty())
    }

    /**
     * Test comparison operators
     */
    fun testComparisonOperators() {
        val code = """
            component ComparisonTest:
                state:
                    count: int = 5
                    limit: int = 10

                VStack:
                    if state.count < state.limit:
                        Text("Under limit")
                    if state.count <= state.limit:
                        Text("At or under limit")
                    if state.count == 5:
                        Text("Exactly 5")
                    if state.count != 0:
                        Text("Not zero")
                    if state.count >= 1:
                        Text("At least 1")
                    if state.count > 0:
                        Text("Positive")
        """.trimIndent()

        val file = myFixture.configureByText("test.nanodsl", code) as NanoDSLFile
        assertNotNull(file)

        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue("Should have no parsing errors, but found: ${errors.map { it.errorDescription }}", errors.isEmpty())
    }
}

