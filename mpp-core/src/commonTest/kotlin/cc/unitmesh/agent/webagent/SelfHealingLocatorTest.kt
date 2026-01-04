package cc.unitmesh.agent.webagent

import cc.unitmesh.agent.webagent.executor.SelfHealingLocator
import cc.unitmesh.agent.webagent.executor.SelfHealingConfig
import cc.unitmesh.agent.webagent.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for SelfHealingLocator.
 * 
 * @see <a href="https://github.com/phodal/auto-dev/issues/532">Issue #532</a>
 */
class SelfHealingLocatorTest {

    private val locator = SelfHealingLocator(config = SelfHealingConfig(threshold = 0.7))

    @Test
    fun testExactMatch() {
        val original = ElementFingerprint(
            selector = "#login-btn",
            tagName = "button",
            id = "login-btn",
            textContent = "Login",
            role = "button",
            testId = "login-button"
        )

        val candidates = listOf(
            createActionableElement(
                tagId = 1,
                selector = "#login-btn",
                tagName = "button",
                id = "login-btn",
                textContent = "Login",
                role = "button",
                testId = "login-button"
            )
        )

        val result = locator.healWithAlgorithm("#old-selector", original, candidates)

        assertNotNull(result)
        assertEquals("#login-btn", result.healedSelector)
        assertTrue(result.confidence > 0.9)
    }

    @Test
    fun testPartialMatch_TextContent() {
        val original = ElementFingerprint(
            selector = "#submit",
            tagName = "button",
            textContent = "Submit Form",
            role = "button"
        )

        val candidates = listOf(
            createActionableElement(
                tagId = 1,
                selector = "#new-submit",
                tagName = "button",
                textContent = "Submit Form",
                role = "button"
            ),
            createActionableElement(
                tagId = 2,
                selector = "#cancel",
                tagName = "button",
                textContent = "Cancel",
                role = "button"
            )
        )

        val result = locator.healWithAlgorithm("#submit", original, candidates)

        assertNotNull(result)
        assertEquals("#new-submit", result.healedSelector)
    }

    @Test
    fun testPartialMatch_TestId() {
        val original = ElementFingerprint(
            selector = ".old-class",
            tagName = "button",
            testId = "submit-btn"
        )

        val candidates = listOf(
            createActionableElement(
                tagId = 1,
                selector = ".new-class",
                tagName = "button",
                testId = "submit-btn"
            )
        )

        val result = locator.healWithAlgorithm(".old-class", original, candidates)

        assertNotNull(result)
        assertEquals(".new-class", result.healedSelector)
        assertTrue(result.confidence > 0.8) // testId has high weight
    }

    @Test
    fun testNoMatch_BelowThreshold() {
        val original = ElementFingerprint(
            selector = "#login",
            tagName = "button",
            id = "login",
            textContent = "Login",
            role = "button"
        )

        val candidates = listOf(
            createActionableElement(
                tagId = 1,
                selector = "#logout",
                tagName = "a",
                textContent = "Logout",
                role = "link"
            )
        )

        val result = locator.healWithAlgorithm("#login", original, candidates)

        assertNull(result) // Should not match due to low similarity
    }

    @Test
    fun testClassNameSimilarity() {
        val original = ElementFingerprint(
            selector = ".btn.btn-primary.submit",
            tagName = "button",
            classNames = listOf("btn", "btn-primary", "submit"),
            role = "button"  // Add role for better matching
        )

        val candidates = listOf(
            createActionableElement(
                tagId = 1,
                selector = ".btn.btn-primary.submit-form",
                tagName = "button",
                classNames = listOf("btn", "btn-primary", "submit-form"),
                role = "button"  // Add role for better matching
            ),
            createActionableElement(
                tagId = 2,
                selector = ".link",
                tagName = "a",
                classNames = listOf("link"),
                role = "link"
            )
        )

        val result = locator.healWithAlgorithm(".btn.btn-primary.submit", original, candidates)

        assertNotNull(result)
        assertEquals(1, candidates.find { it.selector == result.healedSelector }?.tagId)
    }

    @Test
    fun testEmptyCandidates() {
        val original = ElementFingerprint(
            selector = "#btn",
            tagName = "button"
        )

        val result = locator.healWithAlgorithm("#btn", original, emptyList())

        assertNull(result)
    }

    @Test
    fun testHealingLevel() {
        val original = ElementFingerprint(
            selector = "#btn",
            tagName = "button",
            textContent = "Click Me"
        )

        val candidates = listOf(
            createActionableElement(
                tagId = 1,
                selector = "#new-btn",
                tagName = "button",
                textContent = "Click Me"
            )
        )

        val result = locator.healWithAlgorithm("#btn", original, candidates)

        assertNotNull(result)
        assertEquals(HealingLevel.L1_ALGORITHM, result.level)
    }

    private fun createActionableElement(
        tagId: Int,
        selector: String,
        tagName: String,
        id: String? = null,
        textContent: String? = null,
        role: String = "generic",
        testId: String? = null,
        classNames: List<String> = emptyList()
    ): ActionableElement {
        return ActionableElement(
            tagId = tagId,
            selector = selector,
            tagName = tagName,
            role = role,
            name = textContent ?: "",
            isVisible = true,
            isEnabled = true,
            boundingBox = BoundingBox(0.0, 0.0, 100.0, 30.0),
            fingerprint = ElementFingerprint(
                selector = selector,
                tagName = tagName,
                id = id,
                textContent = textContent,
                role = role,
                testId = testId,
                classNames = classNames
            )
        )
    }
}
