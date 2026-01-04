package cc.unitmesh.agent.webagent.model

import kotlinx.serialization.Serializable

/**
 * Action space for E2E testing, inspired by WebArena and Browser-use.
 *
 * Each action represents an atomic operation that can be executed on a web page.
 *
 * Element identification supports two modes:
 * 1. **Set-of-Mark (SoM)**: Use `targetId` (Int) when visual grounding is available
 * 2. **CSS Selector**: Use `selector` (String) for direct selector-based targeting
 *
 * When both are provided, `selector` takes precedence for execution.
 *
 * @see <a href="https://github.com/phodal/auto-dev/issues/532">Issue #532</a>
 */
@Serializable
sealed class TestAction {
    /**
     * Click on an element
     *
     * @param targetId Set-of-Mark tag number (for visual grounding)
     * @param selector CSS selector (for direct targeting)
     * @param button Mouse button to use
     * @param clickCount Number of clicks (1 for single, 2 for double)
     */
    @Serializable
    data class Click(
        val targetId: Int = 0,
        val selector: String? = null,
        val button: MouseButton = MouseButton.LEFT,
        val clickCount: Int = 1
    ) : TestAction()

    /**
     * Type text into an element
     *
     * @param targetId Set-of-Mark tag number (for visual grounding)
     * @param selector CSS selector (for direct targeting)
     * @param text Text to type
     * @param clearFirst Whether to clear existing content first
     * @param pressEnter Whether to press Enter after typing
     */
    @Serializable
    data class Type(
        val targetId: Int = 0,
        val selector: String? = null,
        val text: String,
        val clearFirst: Boolean = false,
        val pressEnter: Boolean = false
    ) : TestAction()

    /**
     * Hover over an element
     *
     * @param targetId Set-of-Mark tag number (for visual grounding)
     * @param selector CSS selector (for direct targeting)
     */
    @Serializable
    data class Hover(
        val targetId: Int = 0,
        val selector: String? = null
    ) : TestAction()

    /**
     * Scroll the page or a specific element
     *
     * @param direction Scroll direction
     * @param amount Scroll amount in pixels
     * @param targetId Set-of-Mark tag number of element to scroll (optional)
     * @param selector CSS selector of element to scroll (optional)
     */
    @Serializable
    data class Scroll(
        val direction: ScrollDirection,
        val amount: Int = 300,
        val targetId: Int? = null,
        val selector: String? = null
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
     *
     * @param targetId Set-of-Mark tag number (for visual grounding)
     * @param selector CSS selector (for direct targeting)
     * @param assertion Type of assertion to perform
     * @param expected Expected value (for text/attribute assertions)
     */
    @Serializable
    data class Assert(
        val targetId: Int = 0,
        val selector: String? = null,
        val assertion: AssertionType,
        val expected: String? = null
    ) : TestAction()

    /**
     * Select an option from a dropdown
     *
     * @param targetId Set-of-Mark tag number (for visual grounding)
     * @param selector CSS selector (for direct targeting)
     * @param value Option value to select
     * @param label Option label to select
     * @param index Option index to select
     */
    @Serializable
    data class Select(
        val targetId: Int = 0,
        val selector: String? = null,
        val value: String? = null,
        val label: String? = null,
        val index: Int? = null
    ) : TestAction()

    /**
     * Upload a file to a file input
     *
     * @param targetId Set-of-Mark tag number (for visual grounding)
     * @param selector CSS selector (for direct targeting)
     * @param filePath Path to the file to upload
     */
    @Serializable
    data class UploadFile(
        val targetId: Int = 0,
        val selector: String? = null,
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
    data class ElementVisible(val targetId: Int = 0, val selector: String? = null) : WaitCondition()

    @Serializable
    data class ElementHidden(val targetId: Int = 0, val selector: String? = null) : WaitCondition()

    @Serializable
    data class ElementEnabled(val targetId: Int = 0, val selector: String? = null) : WaitCondition()

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
