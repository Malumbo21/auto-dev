package cc.unitmesh.agent.webagent.executor

import cc.unitmesh.agent.webagent.model.ScrollDirection

/**
 * Minimal browser driver interface for executing browser actions.
 * 
 * This interface abstracts the actual browser interaction layer, allowing
 * different implementations (WebEditBridge, Playwright, Selenium, etc.)
 * to be used interchangeably.
 * 
 * All element operations use CSS selectors for element identification.
 * The BrowserActionExecutor is responsible for converting SoM tag IDs
 * to selectors using the ActionExecutionContext.tagMapping.
 * 
 * @see <a href="https://github.com/phodal/auto-dev/issues/532">Issue #532</a>
 */
interface BrowserDriver {
    /**
     * Whether this driver is available and ready to use
     */
    val isAvailable: Boolean

    /**
     * Navigate to a URL
     * @param url The URL to navigate to
     */
    suspend fun navigateTo(url: String): BrowserDriverResult

    /**
     * Go back in browser history
     */
    suspend fun goBack(): BrowserDriverResult

    /**
     * Go forward in browser history
     */
    suspend fun goForward(): BrowserDriverResult

    /**
     * Reload the current page
     */
    suspend fun reload(): BrowserDriverResult

    /**
     * Click on an element identified by CSS selector
     * @param selector CSS selector for the element
     * @param clickCount Number of clicks (1 for single, 2 for double)
     */
    suspend fun click(selector: String, clickCount: Int = 1): BrowserDriverResult

    /**
     * Type text into an element identified by CSS selector
     * @param selector CSS selector for the element
     * @param text Text to type
     * @param clearFirst Whether to clear existing content first
     */
    suspend fun type(selector: String, text: String, clearFirst: Boolean = false): BrowserDriverResult

    /**
     * Hover over an element identified by CSS selector
     * @param selector CSS selector for the element
     */
    suspend fun hover(selector: String): BrowserDriverResult

    /**
     * Scroll the page or a specific element
     * @param direction Scroll direction
     * @param amount Scroll amount in pixels
     * @param selector Optional CSS selector for element to scroll within
     */
    suspend fun scroll(direction: ScrollDirection, amount: Int, selector: String? = null): BrowserDriverResult

    /**
     * Press a keyboard key
     * @param key Key to press (e.g., "Enter", "Tab", "Escape")
     * @param selector Optional CSS selector for target element
     */
    suspend fun pressKey(key: String, selector: String? = null): BrowserDriverResult

    /**
     * Select an option from a dropdown
     * @param selector CSS selector for the select element
     * @param value Option value to select
     */
    suspend fun selectOption(selector: String, value: String): BrowserDriverResult

    /**
     * Wait for an element to be visible
     * @param selector CSS selector for the element
     * @param timeoutMs Timeout in milliseconds
     */
    suspend fun waitForElement(selector: String, timeoutMs: Long = 5000): BrowserDriverResult

    /**
     * Wait for a specified duration
     * @param durationMs Duration in milliseconds
     */
    suspend fun waitForDuration(durationMs: Long): BrowserDriverResult

    /**
     * Wait for page to finish loading
     * @param timeoutMs Timeout in milliseconds
     */
    suspend fun waitForPageLoad(timeoutMs: Long = 10000): BrowserDriverResult

    /**
     * Check if an element is visible
     * @param selector CSS selector for the element
     */
    suspend fun isElementVisible(selector: String): Boolean

    /**
     * Get text content of an element
     * @param selector CSS selector for the element
     */
    suspend fun getElementText(selector: String): String?

    /**
     * Get attribute value of an element
     * @param selector CSS selector for the element
     * @param attribute Attribute name
     */
    suspend fun getElementAttribute(selector: String, attribute: String): String?

    /**
     * Capture a screenshot
     * @param fullPage Whether to capture the full page or just viewport
     * @return Base64 encoded image data, or null if failed
     */
    suspend fun captureScreenshot(fullPage: Boolean = false): BrowserDriverScreenshot?

    /**
     * Get current page URL
     */
    suspend fun getCurrentUrl(): String

    /**
     * Get current page title
     */
    suspend fun getPageTitle(): String

    /**
     * Close and cleanup resources
     */
    fun close()
}

/**
 * Result of a browser driver operation
 */
data class BrowserDriverResult(
    val success: Boolean,
    val error: String? = null,
    val durationMs: Long = 0
) {
    companion object {
        fun success(durationMs: Long = 0) = BrowserDriverResult(success = true, durationMs = durationMs)
        fun failure(error: String, durationMs: Long = 0) = BrowserDriverResult(success = false, error = error, durationMs = durationMs)
    }
}

/**
 * Screenshot result from browser driver
 */
data class BrowserDriverScreenshot(
    val base64: String,
    val width: Int,
    val height: Int,
    val format: String = "png"
)

