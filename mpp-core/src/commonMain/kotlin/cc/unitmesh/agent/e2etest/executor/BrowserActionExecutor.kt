package cc.unitmesh.agent.e2etest.executor

import cc.unitmesh.agent.e2etest.E2ETestContext
import cc.unitmesh.agent.e2etest.HealingLevel
import cc.unitmesh.agent.e2etest.model.*

/**
 * Executes browser actions on the page.
 * 
 * Platform-specific implementations handle actual browser interaction.
 * Supports self-healing when element locators fail.
 * 
 * @see <a href="https://github.com/phodal/auto-dev/issues/532">Issue #532</a>
 */
expect interface BrowserActionExecutor {
    /**
     * Check if executor is available on this platform
     */
    val isAvailable: Boolean

    /**
     * Execute a single action
     */
    suspend fun execute(action: TestAction, context: ActionExecutionContext): ActionResult

    /**
     * Navigate to a URL
     */
    suspend fun navigateTo(url: String): ActionResult

    /**
     * Click on element by SoM tag ID
     */
    suspend fun click(tagId: Int, options: ClickOptions = ClickOptions()): ActionResult

    /**
     * Type text into element
     */
    suspend fun type(tagId: Int, text: String, options: TypeOptions = TypeOptions()): ActionResult

    /**
     * Scroll the page or element
     */
    suspend fun scroll(direction: ScrollDirection, amount: Int, tagId: Int? = null): ActionResult

    /**
     * Wait for a condition
     */
    suspend fun waitFor(condition: WaitCondition): ActionResult

    /**
     * Press a keyboard key
     */
    suspend fun pressKey(key: String, modifiers: List<KeyModifier> = emptyList()): ActionResult

    /**
     * Take a screenshot
     */
    suspend fun screenshot(name: String, fullPage: Boolean = false): ScreenshotResult

    /**
     * Close and cleanup
     */
    fun close()
}

/**
 * Context for action execution
 */
data class ActionExecutionContext(
    /**
     * Mapping from SoM tag ID to element info
     */
    val tagMapping: Map<Int, ActionableElement>,

    /**
     * Self-healing locator for fallback
     */
    val selfHealingLocator: SelfHealingLocator? = null,

    /**
     * Timeout for this action
     */
    val timeoutMs: Long = 5000,

    /**
     * Retry count
     */
    val retryCount: Int = 2,

    /**
     * Slow motion delay (for debugging)
     */
    val slowMotionMs: Long = 0
)

/**
 * Result of executing an action
 */
data class ActionResult(
    /**
     * Whether action succeeded
     */
    val success: Boolean,

    /**
     * Error message if failed
     */
    val error: String? = null,

    /**
     * Duration in milliseconds
     */
    val durationMs: Long,

    /**
     * Whether self-healing was used
     */
    val selfHealed: Boolean = false,

    /**
     * New selector if self-healed
     */
    val healedSelector: String? = null,

    /**
     * Healing level used
     */
    val healingLevel: HealingLevel? = null
) {
    companion object {
        fun success(durationMs: Long) = ActionResult(
            success = true,
            durationMs = durationMs
        )

        fun failure(error: String, durationMs: Long) = ActionResult(
            success = false,
            error = error,
            durationMs = durationMs
        )

        fun healed(durationMs: Long, healedSelector: String, level: HealingLevel) = ActionResult(
            success = true,
            durationMs = durationMs,
            selfHealed = true,
            healedSelector = healedSelector,
            healingLevel = level
        )
    }
}

/**
 * Result of taking a screenshot
 */
data class ScreenshotResult(
    val success: Boolean,
    val path: String? = null,
    val base64: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val error: String? = null
)

/**
 * Options for click action
 */
data class ClickOptions(
    val button: MouseButton = MouseButton.LEFT,
    val clickCount: Int = 1,
    val delay: Long = 0
)

/**
 * Options for type action
 */
data class TypeOptions(
    val clearFirst: Boolean = false,
    val pressEnter: Boolean = false,
    val delay: Long = 0
)

/**
 * Factory function to create platform-specific executor
 */
expect fun createBrowserActionExecutor(config: BrowserExecutorConfig): BrowserActionExecutor?

/**
 * Configuration for browser executor
 */
data class BrowserExecutorConfig(
    val headless: Boolean = false,
    val viewportWidth: Int = 1280,
    val viewportHeight: Int = 720,
    val defaultTimeoutMs: Long = 5000,
    val slowMotionMs: Long = 0
)
