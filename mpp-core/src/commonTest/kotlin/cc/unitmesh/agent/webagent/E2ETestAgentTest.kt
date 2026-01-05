package cc.unitmesh.agent.webagent

import cc.unitmesh.agent.webagent.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for E2E Testing Agent components.
 * 
 * @see <a href="https://github.com/phodal/auto-dev/issues/532">Issue #532</a>
 */
class E2ETestAgentTest {

    @Test
    fun testActionSerialization() {
        val clickAction = TestAction.Click(targetId = 5, button = MouseButton.LEFT)
        assertTrue(clickAction.targetId == 5)
        
        val typeAction = TestAction.Type(targetId = 10, text = "hello", pressEnter = true)
        assertEquals("hello", typeAction.text)
        assertTrue(typeAction.pressEnter)
    }

    @Test
    fun testElementFingerprint() {
        val fingerprint = ElementFingerprint(
            selector = "#login-btn",
            tagName = "button",
            id = "login-btn",
            classNames = listOf("btn", "btn-primary"),
            textContent = "Login",
            role = "button",
            testId = "login-button"
        )
        
        assertEquals("button", fingerprint.tagName)
        assertEquals("login-btn", fingerprint.id)
        assertEquals(2, fingerprint.classNames.size)
        assertEquals("login-button", fingerprint.testId)
    }

    @Test
    fun testTestStep() {
        val step = TestStep(
            id = "step-1",
            description = "Click login button",
            action = TestAction.Click(targetId = 5),
            expectedOutcome = "Login form should appear",
            retryCount = 2
        )
        
        assertEquals("step-1", step.id)
        assertEquals(2, step.retryCount)
    }

    @Test
    fun testTestScenario() {
        val scenario = TestScenario(
            id = "login-test",
            name = "User Login Test",
            description = "Verify user can login with valid credentials",
            startUrl = "https://example.com/login",
            steps = listOf(
                TestStep(
                    id = "step-1",
                    description = "Enter username",
                    action = TestAction.Type(targetId = 1, text = "testuser")
                ),
                TestStep(
                    id = "step-2",
                    description = "Enter password",
                    action = TestAction.Type(targetId = 2, text = "password123")
                ),
                TestStep(
                    id = "step-3",
                    description = "Click login",
                    action = TestAction.Click(targetId = 3)
                )
            ),
            tags = listOf("login", "auth"),
            priority = TestPriority.HIGH
        )
        
        assertEquals(3, scenario.steps.size)
        assertEquals(TestPriority.HIGH, scenario.priority)
    }

    @Test
    fun testTestMemory() {
        var memory = TestMemory.empty()
        
        memory = memory.withAction(
            ActionRecord(
                actionType = "Click",
                targetId = 5,
                timestamp = 1000L,
                success = true,
                description = "Click button"
            )
        )
        
        assertEquals(1, memory.recentActions.size)
        assertEquals("Click", memory.recentActions.first().actionType)
    }

    @Test
    fun testMemoryLoopDetection() {
        var memory = TestMemory.empty()
        
        // Add same action 3 times
        repeat(3) {
            memory = memory.withAction(
                ActionRecord(
                    actionType = "Click",
                    targetId = 5,
                    timestamp = (1000 + it).toLong(),
                    success = false,
                    description = "Click button"
                )
            )
        }
        
        assertTrue(memory.isInLoop())
    }

    @Test
    fun testE2ETestConfig() {
        val config = WebAgentConfig(
            defaultTimeoutMs = 10000,
            enableSelfHealing = true,
            healingThreshold = 0.85,
            viewportWidth = 1920,
            viewportHeight = 1080
        )
        
        assertEquals(10000, config.defaultTimeoutMs)
        assertTrue(config.enableSelfHealing)
        assertEquals(0.85, config.healingThreshold)
    }

    @Test
    fun testPageState() {
        val pageState = PageState(
            url = "https://example.com",
            title = "Example Page",
            viewport = Viewport(1280, 720),
            actionableElements = listOf(
                ActionableElement(
                    tagId = 1,
                    selector = "#btn",
                    tagName = "button",
                    role = "button",
                    name = "Submit",
                    isVisible = true,
                    isEnabled = true,
                    boundingBox = BoundingBox(100.0, 200.0, 80.0, 30.0),
                    fingerprint = ElementFingerprint(
                        selector = "#btn",
                        tagName = "button"
                    )
                )
            ),
            capturedAt = 1000L
        )
        
        assertEquals("https://example.com", pageState.url)
        assertEquals(1, pageState.actionableElements.size)
        assertEquals(1, pageState.actionableElements.first().tagId)
    }

    @Test
    fun testWaitConditions() {
        val visible = WaitCondition.ElementVisible(targetId = 5)
        val textPresent = WaitCondition.TextPresent(text = "Success")
        val urlContains = WaitCondition.UrlContains(substring = "/dashboard")
        val duration = WaitCondition.Duration(ms = 2000)
        
        assertTrue(visible is WaitCondition.ElementVisible)
        assertEquals("Success", (textPresent as WaitCondition.TextPresent).text)
        assertEquals("/dashboard", (urlContains as WaitCondition.UrlContains).substring)
        assertEquals(2000L, (duration as WaitCondition.Duration).ms)
    }

    @Test
    fun testAssertionTypes() {
        val visible = AssertionType.Visible
        val textEquals = AssertionType.TextEquals("Hello")
        val hasClass = AssertionType.HasClass("active")
        
        assertTrue(visible is AssertionType.Visible)
        assertEquals("Hello", (textEquals as AssertionType.TextEquals).text)
        assertEquals("active", (hasClass as AssertionType.HasClass).className)
    }
}
