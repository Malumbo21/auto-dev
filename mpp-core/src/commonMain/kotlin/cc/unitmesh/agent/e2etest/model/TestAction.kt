package cc.unitmesh.agent.e2etest.model

import kotlinx.serialization.Serializable

/**
 * Action space for E2E testing, inspired by WebArena and Browser-use.
 * 
 * Each action represents an atomic operation that can be executed on a web page.
 * The targetId refers to a Set-of-Mark tag number when using visual grounding,
 * or can be mapped to a CSS selector.
 * 
 * @see <a href="https://github.com/phodal/auto-dev/issues/532">Issue #532</a>
 */
@Serializable
sealed class TestAction {
    /**
     * Click on an element identified by targetId (SoM tag number)
     */
    @Serializable
    data class Click(
        val targetId: Int,
        val button: MouseButton = MouseButton.LEFT,
        val clickCount: Int = 1
    ) : TestAction()

    /**
     * Type text into an element
     */
    @Serializable
    data class Type(
        val targetId: Int,
        val text: String,
        val clearFirst: Boolean = false,
        val pressEnter: Boolean = false
    ) : TestAction()

    /**
     * Hover over an element
     */
    @Serializable
    data class Hover(
        val targetId: Int
    ) : TestAction()

    /**
     * Scroll the page or a specific element
     */
    @Serializable
    data class Scroll(
        val direction: ScrollDirection,
        val amount: Int = 300,
        val targetId: Int? = null
    ) : TestAction()

    /**
     * Wait for a condition to be met
     */
    @Serializable
    data class Wait(
        val condition: WaitCondition,
        val timeoutMs: Long = 5000
    ) : TestAction()

    /**
     * Press a keyboard key
     */
    @Serializable
    data class PressKey(
        val key: String,
        val modifiers: List<KeyModifier> = emptyList()
    ) : TestAction()

    /**
     * Navigate to a URL
     */
    @Serializable
    data class Navigate(
        val url: String
    ) : TestAction()

    /**
     * Go back in browser history
     */
    @Serializable
    data object GoBack : TestAction()

    /**
     * Go forward in browser history
     */
    @Serializable
    data object GoForward : TestAction()

    /**
     * Refresh the current page
     */
    @Serializable
    data object Refresh : TestAction()

    /**
     * Assert a condition on an element
     */
    @Serializable
    data class Assert(
        val targetId: Int,
        val assertion: AssertionType,
        val expected: String? = null
    ) : TestAction()

    /**
     * Select an option from a dropdown
     */
    @Serializable
    data class Select(
        val targetId: Int,
        val value: String? = null,
        val label: String? = null,
        val index: Int? = null
    ) : TestAction()

    /**
     * Upload a file to a file input
     */
    @Serializable
    data class UploadFile(
        val targetId: Int,
        val filePath: String
    ) : TestAction()

    /**
     * Take a screenshot for verification
     */
    @Serializable
    data class Screenshot(
        val name: String,
        val fullPage: Boolean = false
    ) : TestAction()
}

@Serializable
enum class MouseButton {
    LEFT, RIGHT, MIDDLE
}

@Serializable
enum class ScrollDirection {
    UP, DOWN, LEFT, RIGHT
}

@Serializable
enum class KeyModifier {
    CTRL, ALT, SHIFT, META
}

@Serializable
sealed class WaitCondition {
    @Serializable
    data class ElementVisible(val targetId: Int) : WaitCondition()

    @Serializable
    data class ElementHidden(val targetId: Int) : WaitCondition()

    @Serializable
    data class ElementEnabled(val targetId: Int) : WaitCondition()

    @Serializable
    data class TextPresent(val text: String) : WaitCondition()

    @Serializable
    data class UrlContains(val substring: String) : WaitCondition()

    @Serializable
    data class PageLoaded(val timeoutMs: Long = 10000) : WaitCondition()

    @Serializable
    data class NetworkIdle(val timeoutMs: Long = 5000) : WaitCondition()

    @Serializable
    data class Duration(val ms: Long) : WaitCondition()
}

@Serializable
sealed class AssertionType {
    @Serializable
    data object Visible : AssertionType()

    @Serializable
    data object Hidden : AssertionType()

    @Serializable
    data object Enabled : AssertionType()

    @Serializable
    data object Disabled : AssertionType()

    @Serializable
    data object Checked : AssertionType()

    @Serializable
    data object Unchecked : AssertionType()

    @Serializable
    data class TextEquals(val text: String) : AssertionType()

    @Serializable
    data class TextContains(val text: String) : AssertionType()

    @Serializable
    data class AttributeEquals(val attribute: String, val value: String) : AssertionType()

    @Serializable
    data class HasClass(val className: String) : AssertionType()
}
