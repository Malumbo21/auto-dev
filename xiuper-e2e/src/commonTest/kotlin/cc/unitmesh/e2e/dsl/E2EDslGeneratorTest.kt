package cc.unitmesh.e2e.dsl

import cc.unitmesh.agent.e2etest.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class E2EDslGeneratorTest {

    private val generator = E2EDslGenerator()
    private val parser = E2EDslParser()

    @Test
    fun testGenerateSimpleScenario() {
        val scenario = TestScenario(
            id = "test-1",
            name = "Login Test",
            description = "Test user login",
            startUrl = "https://example.com/login",
            steps = listOf(
                TestStep(
                    id = "step-1",
                    description = "Enter username",
                    action = TestAction.Type(1, "testuser")
                ),
                TestStep(
                    id = "step-2",
                    description = "Click login",
                    action = TestAction.Click(2)
                )
            ),
            priority = TestPriority.HIGH
        )

        val dsl = generator.generate(scenario)

        assertTrue(dsl.contains("scenario \"Login Test\""))
        assertTrue(dsl.contains("description \"Test user login\""))
        assertTrue(dsl.contains("url \"https://example.com/login\""))
        assertTrue(dsl.contains("priority high"))
        assertTrue(dsl.contains("step \"Enter username\""))
        assertTrue(dsl.contains("type #1 \"testuser\""))
        assertTrue(dsl.contains("step \"Click login\""))
        assertTrue(dsl.contains("click #2"))
    }

    @Test
    fun testRoundTripParsing() {
        val originalScenario = TestScenario(
            id = "test-1",
            name = "Round Trip Test",
            description = "Test round trip parsing",
            startUrl = "https://example.com",
            steps = listOf(
                TestStep(
                    id = "step-1",
                    description = "Click button",
                    action = TestAction.Click(1, MouseButton.LEFT, 1)
                ),
                TestStep(
                    id = "step-2",
                    description = "Type text",
                    action = TestAction.Type(2, "hello", clearFirst = true, pressEnter = true)
                ),
                TestStep(
                    id = "step-3",
                    description = "Scroll down",
                    action = TestAction.Scroll(ScrollDirection.DOWN, 500)
                ),
                TestStep(
                    id = "step-4",
                    description = "Assert visible",
                    action = TestAction.Assert(3, AssertionType.Visible)
                )
            ),
            tags = listOf("test", "roundtrip"),
            priority = TestPriority.MEDIUM
        )

        // Generate DSL
        val dsl = generator.generate(originalScenario)

        // Parse it back
        val parseResult = parser.parse(dsl)

        assertTrue(parseResult.success, "Parse should succeed: ${parseResult.errors}")
        val parsedScenario = parseResult.scenario
        assertEquals(originalScenario.name, parsedScenario?.name)
        assertEquals(originalScenario.description, parsedScenario?.description)
        assertEquals(originalScenario.startUrl, parsedScenario?.startUrl)
        assertEquals(originalScenario.steps.size, parsedScenario?.steps?.size)
        assertEquals(originalScenario.priority, parsedScenario?.priority)
    }

    @Test
    fun testGenerateAllActionTypes() {
        val scenario = TestScenario(
            id = "test-all",
            name = "All Actions Test",
            description = "Test all action types",
            startUrl = "https://example.com",
            steps = listOf(
                TestStep("s1", "Click", TestAction.Click(1)),
                TestStep("s2", "Type", TestAction.Type(2, "text")),
                TestStep("s3", "Hover", TestAction.Hover(3)),
                TestStep("s4", "Scroll", TestAction.Scroll(ScrollDirection.DOWN)),
                TestStep("s5", "Wait", TestAction.Wait(WaitCondition.Duration(1000))),
                TestStep("s6", "Press Key", TestAction.PressKey("Enter")),
                TestStep("s7", "Navigate", TestAction.Navigate("https://example.com/page")),
                TestStep("s8", "Go Back", TestAction.GoBack),
                TestStep("s9", "Go Forward", TestAction.GoForward),
                TestStep("s10", "Refresh", TestAction.Refresh),
                TestStep("s11", "Assert", TestAction.Assert(4, AssertionType.Visible)),
                TestStep("s12", "Select", TestAction.Select(5, value = "opt1")),
                TestStep("s13", "Upload", TestAction.UploadFile(6, "/path/to/file")),
                TestStep("s14", "Screenshot", TestAction.Screenshot("test", fullPage = true))
            )
        )

        val dsl = generator.generate(scenario)

        assertTrue(dsl.contains("click #1"))
        assertTrue(dsl.contains("type #2 \"text\""))
        assertTrue(dsl.contains("hover #3"))
        assertTrue(dsl.contains("scroll down"))
        assertTrue(dsl.contains("wait duration 1000"))
        assertTrue(dsl.contains("pressKey \"Enter\""))
        assertTrue(dsl.contains("navigate \"https://example.com/page\""))
        assertTrue(dsl.contains("goBack"))
        assertTrue(dsl.contains("goForward"))
        assertTrue(dsl.contains("refresh"))
        assertTrue(dsl.contains("assert #4 visible"))
        assertTrue(dsl.contains("select #5 value \"opt1\""))
        assertTrue(dsl.contains("uploadFile #6 \"/path/to/file\""))
        assertTrue(dsl.contains("screenshot \"test\" fullPage"))
    }

    @Test
    fun testGenerateWithStepOptions() {
        val scenario = TestScenario(
            id = "test-opts",
            name = "Step Options Test",
            description = "Test step options",
            startUrl = "https://example.com",
            steps = listOf(
                TestStep(
                    id = "s1",
                    description = "Click with options",
                    action = TestAction.Click(1),
                    expectedOutcome = "Button clicked",
                    timeoutMs = 10000,
                    retryCount = 3,
                    continueOnFailure = true
                )
            )
        )

        val dsl = generator.generate(scenario)

        assertTrue(dsl.contains("expect \"Button clicked\""))
        assertTrue(dsl.contains("timeout 10000"))
        assertTrue(dsl.contains("retry 3"))
        assertTrue(dsl.contains("continueOnFailure"))
    }
}
