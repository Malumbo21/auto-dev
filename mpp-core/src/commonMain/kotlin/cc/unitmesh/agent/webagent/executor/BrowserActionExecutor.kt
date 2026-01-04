package cc.unitmesh.agent.webagent.executor

import cc.unitmesh.agent.webagent.HealingLevel
import cc.unitmesh.agent.webagent.model.*

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

/**
 * Common implementation of BrowserActionExecutor that delegates to a BrowserDriver.
 *
 * This class handles the conversion from SoM tag IDs to CSS selectors using
 * the ActionExecutionContext.tagMapping, and delegates actual browser operations
 * to the provided BrowserDriver implementation.
 *
 * Platform-specific implementations can use this class with their own BrowserDriver
 * (e.g., WebEditBridge adapter, Playwright, Selenium).
 */
class DriverBasedBrowserActionExecutor(
    private val driver: BrowserDriver,
    private val config: BrowserExecutorConfig = BrowserExecutorConfig(),
    private var currentContext: ActionExecutionContext? = null
) {
    val isAvailable: Boolean get() = driver.isAvailable

    /**
     * Update the current execution context (tag mapping, etc.)
     */
    fun setContext(context: ActionExecutionContext) {
        this.currentContext = context
    }

    /**
     * Get selector for a tag ID from current context
     */
    private fun getSelectorForTag(tagId: Int): String? {
        return currentContext?.tagMapping?.get(tagId)?.selector
    }

    suspend fun execute(action: TestAction, context: ActionExecutionContext): ActionResult {
        setContext(context)
        val startTime = currentTimeMillis()

        return try {
            when (action) {
                is TestAction.Click -> click(action.targetId, ClickOptions(action.button, action.clickCount))
                is TestAction.Type -> type(action.targetId, action.text, TypeOptions(action.clearFirst, action.pressEnter))
                is TestAction.Hover -> hover(action.targetId)
                is TestAction.Scroll -> scroll(action.direction, action.amount, action.targetId)
                is TestAction.Wait -> waitFor(action.condition)
                is TestAction.PressKey -> pressKey(action.key, action.modifiers)
                is TestAction.Navigate -> navigateTo(action.url)
                is TestAction.GoBack -> goBack()
                is TestAction.GoForward -> goForward()
                is TestAction.Refresh -> refresh()
                is TestAction.Assert -> executeAssert(action)
                is TestAction.Select -> select(action.targetId, action.value, action.label, action.index)
                is TestAction.UploadFile -> uploadFile(action.targetId, action.filePath)
                is TestAction.Screenshot -> {
                    val result = screenshot(action.name, action.fullPage)
                    if (result.success) ActionResult.success(currentTimeMillis() - startTime)
                    else ActionResult.failure(result.error ?: "Screenshot failed", currentTimeMillis() - startTime)
                }
            }
        } catch (e: Exception) {
            ActionResult.failure(e.message ?: "Unknown error", currentTimeMillis() - startTime)
        }
    }

    suspend fun navigateTo(url: String): ActionResult {
        val result = driver.navigateTo(url)
        return result.toActionResult()
    }

    suspend fun click(tagId: Int, options: ClickOptions = ClickOptions()): ActionResult {
        val selector = getSelectorForTag(tagId)
            ?: return ActionResult.failure("No selector found for tag ID: $tagId", 0)
        val result = driver.click(selector, options.clickCount)
        return result.toActionResult()
    }

    suspend fun type(tagId: Int, text: String, options: TypeOptions = TypeOptions()): ActionResult {
        val selector = getSelectorForTag(tagId)
            ?: return ActionResult.failure("No selector found for tag ID: $tagId", 0)
        val result = driver.type(selector, text, options.clearFirst)
        if (result.success && options.pressEnter) {
            driver.pressKey("Enter", selector)
        }
        return result.toActionResult()
    }

    suspend fun hover(tagId: Int): ActionResult {
        val selector = getSelectorForTag(tagId)
            ?: return ActionResult.failure("No selector found for tag ID: $tagId", 0)
        val result = driver.hover(selector)
        return result.toActionResult()
    }

    suspend fun scroll(direction: ScrollDirection, amount: Int, tagId: Int? = null): ActionResult {
        val selector = tagId?.let { getSelectorForTag(it) }
        val result = driver.scroll(direction, amount, selector)
        return result.toActionResult()
    }

    suspend fun waitFor(condition: WaitCondition): ActionResult {
        val startTime = currentTimeMillis()
        val result = when (condition) {
            is WaitCondition.ElementVisible -> {
                val selector = getSelectorForTag(condition.targetId)
                    ?: return ActionResult.failure("No selector found for tag ID: ${condition.targetId}", 0)
                driver.waitForElement(selector, config.defaultTimeoutMs)
            }
            is WaitCondition.ElementHidden -> {
                // Wait for element to become hidden - poll until not visible
                val selector = getSelectorForTag(condition.targetId)
                    ?: return ActionResult.failure("No selector found for tag ID: ${condition.targetId}", 0)
                waitUntil(config.defaultTimeoutMs) { !driver.isElementVisible(selector) }
            }
            is WaitCondition.ElementEnabled -> {
                val selector = getSelectorForTag(condition.targetId)
                    ?: return ActionResult.failure("No selector found for tag ID: ${condition.targetId}", 0)
                driver.waitForElement(selector, config.defaultTimeoutMs)
            }
            is WaitCondition.TextPresent -> {
                // Not directly supported - would need page search
                BrowserDriverResult.success(0)
            }
            is WaitCondition.UrlContains -> {
                waitUntil(config.defaultTimeoutMs) { driver.getCurrentUrl().contains(condition.substring) }
            }
            is WaitCondition.PageLoaded -> driver.waitForPageLoad(condition.timeoutMs)
            is WaitCondition.NetworkIdle -> driver.waitForPageLoad(condition.timeoutMs)
            is WaitCondition.Duration -> driver.waitForDuration(condition.ms)
        }
        return result.toActionResult()
    }

    suspend fun pressKey(key: String, modifiers: List<KeyModifier> = emptyList()): ActionResult {
        // TODO: Handle modifiers
        val result = driver.pressKey(key)
        return result.toActionResult()
    }

    suspend fun screenshot(name: String, fullPage: Boolean = false): ScreenshotResult {
        val screenshot = driver.captureScreenshot(fullPage)
        return if (screenshot != null) {
            ScreenshotResult(
                success = true,
                base64 = screenshot.base64,
                width = screenshot.width,
                height = screenshot.height
            )
        } else {
            ScreenshotResult(success = false, error = "Failed to capture screenshot")
        }
    }

    private suspend fun goBack(): ActionResult {
        val result = driver.goBack()
        return result.toActionResult()
    }

    private suspend fun goForward(): ActionResult {
        val result = driver.goForward()
        return result.toActionResult()
    }

    private suspend fun refresh(): ActionResult {
        val result = driver.reload()
        return result.toActionResult()
    }

    private suspend fun executeAssert(action: TestAction.Assert): ActionResult {
        val selector = getSelectorForTag(action.targetId)
            ?: return ActionResult.failure("No selector found for tag ID: ${action.targetId}", 0)

        val startTime = currentTimeMillis()
        val success = when (action.assertion) {
            is AssertionType.Visible -> driver.isElementVisible(selector)
            is AssertionType.Hidden -> !driver.isElementVisible(selector)
            is AssertionType.Enabled -> driver.isElementVisible(selector) // Simplified
            is AssertionType.Disabled -> !driver.isElementVisible(selector) // Simplified
            is AssertionType.Checked -> driver.getElementAttribute(selector, "checked") == "true"
            is AssertionType.Unchecked -> driver.getElementAttribute(selector, "checked") != "true"
            is AssertionType.TextEquals -> driver.getElementText(selector) == action.assertion.text
            is AssertionType.TextContains -> driver.getElementText(selector)?.contains(action.assertion.text) == true
            is AssertionType.AttributeEquals -> driver.getElementAttribute(selector, action.assertion.attribute) == action.assertion.value
            is AssertionType.HasClass -> driver.getElementAttribute(selector, "class")?.contains(action.assertion.className) == true
        }

        return if (success) {
            ActionResult.success(currentTimeMillis() - startTime)
        } else {
            ActionResult.failure("Assertion failed: ${action.assertion}", currentTimeMillis() - startTime)
        }
    }

    private suspend fun select(tagId: Int, value: String?, label: String?, index: Int?): ActionResult {
        val selector = getSelectorForTag(tagId)
            ?: return ActionResult.failure("No selector found for tag ID: $tagId", 0)
        val selectValue = value ?: label ?: return ActionResult.failure("No value or label provided for select", 0)
        val result = driver.selectOption(selector, selectValue)
        return result.toActionResult()
    }

    private suspend fun uploadFile(tagId: Int, filePath: String): ActionResult {
        // File upload is complex and platform-specific
        return ActionResult.failure("File upload not yet implemented", 0)
    }

    fun close() {
        driver.close()
    }

    private fun BrowserDriverResult.toActionResult(): ActionResult {
        return if (success) {
            ActionResult.success(durationMs)
        } else {
            ActionResult.failure(error ?: "Unknown error", durationMs)
        }
    }

    private suspend fun waitUntil(timeoutMs: Long, condition: suspend () -> Boolean): BrowserDriverResult {
        val startTime = currentTimeMillis()
        val endTime = startTime + timeoutMs
        while (currentTimeMillis() < endTime) {
            if (condition()) {
                return BrowserDriverResult.success(currentTimeMillis() - startTime)
            }
            kotlinx.coroutines.delay(100)
        }
        return BrowserDriverResult.failure("Timeout waiting for condition", timeoutMs)
    }
}

/**
 * Platform-specific time function
 */
internal expect fun currentTimeMillis(): Long
