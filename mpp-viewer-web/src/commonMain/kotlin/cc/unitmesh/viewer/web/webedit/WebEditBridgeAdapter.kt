package cc.unitmesh.viewer.web.webedit

import cc.unitmesh.agent.e2etest.executor.BrowserDriver
import cc.unitmesh.agent.e2etest.executor.BrowserDriverResult
import cc.unitmesh.agent.e2etest.executor.BrowserDriverScreenshot
import cc.unitmesh.agent.e2etest.model.ScrollDirection
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/**
 * Adapter that wraps WebEditBridge to implement BrowserDriver interface.
 *
 * This allows BrowserActionExecutor to use WebEditBridge for browser operations,
 * enabling reuse of the existing WebView integration across platforms.
 *
 * @param bridge The WebEditBridge instance to wrap
 */
class WebEditBridgeAdapter(
    private val bridge: WebEditBridge
) : BrowserDriver {

    private val timeSource = TimeSource.Monotonic

    override val isAvailable: Boolean
        get() = bridge.isReady.value

    override suspend fun navigateTo(url: String): BrowserDriverResult {
        val startMark = timeSource.markNow()
        return try {
            bridge.navigateTo(url)
            // Wait for page to start loading
            delay(100)
            BrowserDriverResult.success(startMark.elapsedNow().inWholeMilliseconds)
        } catch (e: Exception) {
            BrowserDriverResult.failure(e.message ?: "Navigation failed", startMark.elapsedNow().inWholeMilliseconds)
        }
    }

    override suspend fun goBack(): BrowserDriverResult {
        val startMark = timeSource.markNow()
        return try {
            bridge.goBack()
            BrowserDriverResult.success(startMark.elapsedNow().inWholeMilliseconds)
        } catch (e: Exception) {
            BrowserDriverResult.failure(e.message ?: "Go back failed", startMark.elapsedNow().inWholeMilliseconds)
        }
    }

    override suspend fun goForward(): BrowserDriverResult {
        val startMark = timeSource.markNow()
        return try {
            bridge.goForward()
            BrowserDriverResult.success(startMark.elapsedNow().inWholeMilliseconds)
        } catch (e: Exception) {
            BrowserDriverResult.failure(e.message ?: "Go forward failed", startMark.elapsedNow().inWholeMilliseconds)
        }
    }

    override suspend fun reload(): BrowserDriverResult {
        val startMark = timeSource.markNow()
        return try {
            bridge.reload()
            BrowserDriverResult.success(startMark.elapsedNow().inWholeMilliseconds)
        } catch (e: Exception) {
            BrowserDriverResult.failure(e.message ?: "Reload failed", startMark.elapsedNow().inWholeMilliseconds)
        }
    }

    override suspend fun click(selector: String, clickCount: Int): BrowserDriverResult {
        val startMark = timeSource.markNow()
        return try {
            bridge.click(selector)
            // For double-click, click again
            if (clickCount > 1) {
                repeat(clickCount - 1) {
                    delay(50)
                    bridge.click(selector)
                }
            }
            BrowserDriverResult.success(startMark.elapsedNow().inWholeMilliseconds)
        } catch (e: Exception) {
            BrowserDriverResult.failure(e.message ?: "Click failed", startMark.elapsedNow().inWholeMilliseconds)
        }
    }

    override suspend fun type(selector: String, text: String, clearFirst: Boolean): BrowserDriverResult {
        val startMark = timeSource.markNow()
        return try {
            bridge.typeText(selector, text, clearFirst)
            BrowserDriverResult.success(startMark.elapsedNow().inWholeMilliseconds)
        } catch (e: Exception) {
            BrowserDriverResult.failure(e.message ?: "Type failed", startMark.elapsedNow().inWholeMilliseconds)
        }
    }

    override suspend fun hover(selector: String): BrowserDriverResult {
        val startMark = timeSource.markNow()
        return try {
            // WebEditBridge doesn't have a direct hover method, use highlight as approximation
            bridge.highlightElement(selector)
            BrowserDriverResult.success(startMark.elapsedNow().inWholeMilliseconds)
        } catch (e: Exception) {
            BrowserDriverResult.failure(e.message ?: "Hover failed", startMark.elapsedNow().inWholeMilliseconds)
        }
    }

    override suspend fun scroll(direction: ScrollDirection, amount: Int, selector: String?): BrowserDriverResult {
        val startMark = timeSource.markNow()
        return try {
            // Use performAction with scroll action
            val scrollAction = WebEditAction(
                action = "scroll",
                selector = selector,
                value = when (direction) {
                    ScrollDirection.UP -> "up:$amount"
                    ScrollDirection.DOWN -> "down:$amount"
                    ScrollDirection.LEFT -> "left:$amount"
                    ScrollDirection.RIGHT -> "right:$amount"
                }
            )
            bridge.performAction(scrollAction)
            BrowserDriverResult.success(startMark.elapsedNow().inWholeMilliseconds)
        } catch (e: Exception) {
            BrowserDriverResult.failure(e.message ?: "Scroll failed", startMark.elapsedNow().inWholeMilliseconds)
        }
    }

    override suspend fun pressKey(key: String, selector: String?): BrowserDriverResult {
        val startMark = timeSource.markNow()
        return try {
            bridge.pressKey(key, selector)
            BrowserDriverResult.success(startMark.elapsedNow().inWholeMilliseconds)
        } catch (e: Exception) {
            BrowserDriverResult.failure(e.message ?: "Press key failed", startMark.elapsedNow().inWholeMilliseconds)
        }
    }

    override suspend fun selectOption(selector: String, value: String): BrowserDriverResult {
        val startMark = timeSource.markNow()
        return try {
            bridge.selectOption(selector, value)
            BrowserDriverResult.success(startMark.elapsedNow().inWholeMilliseconds)
        } catch (e: Exception) {
            BrowserDriverResult.failure(e.message ?: "Select option failed", startMark.elapsedNow().inWholeMilliseconds)
        }
    }

    override suspend fun waitForElement(selector: String, timeoutMs: Long): BrowserDriverResult {
        val startMark = timeSource.markNow()

        while (startMark.elapsedNow().inWholeMilliseconds < timeoutMs) {
            if (isElementVisible(selector)) {
                return BrowserDriverResult.success(startMark.elapsedNow().inWholeMilliseconds)
            }
            delay(100)
        }

        return BrowserDriverResult.failure("Timeout waiting for element: $selector", timeoutMs)
    }

    override suspend fun waitForDuration(durationMs: Long): BrowserDriverResult {
        val startMark = timeSource.markNow()
        delay(durationMs)
        return BrowserDriverResult.success(startMark.elapsedNow().inWholeMilliseconds)
    }

    override suspend fun waitForPageLoad(timeoutMs: Long): BrowserDriverResult {
        val startMark = timeSource.markNow()

        while (startMark.elapsedNow().inWholeMilliseconds < timeoutMs) {
            if (!bridge.isLoading.value) {
                return BrowserDriverResult.success(startMark.elapsedNow().inWholeMilliseconds)
            }
            delay(100)
        }

        return BrowserDriverResult.failure("Timeout waiting for page load", timeoutMs)
    }

    override suspend fun isElementVisible(selector: String): Boolean {
        // Check if element exists in actionable elements
        val elements = bridge.actionableElements.value
        return elements.any { it.selector == selector }
    }

    override suspend fun getElementText(selector: String): String? {
        // Get text from actionable elements if available
        val elements = bridge.actionableElements.value
        return elements.find { it.selector == selector }?.name
    }

    override suspend fun getElementAttribute(selector: String, attribute: String): String? {
        // Limited support - would need JavaScript execution for full support
        // For now, return null as WebEditBridge doesn't expose attribute access directly
        return null
    }

    override suspend fun captureScreenshot(fullPage: Boolean): BrowserDriverScreenshot? {
        return try {
            bridge.captureScreenshot(maxWidth = 1280, quality = 0.8)
            // Wait for screenshot to be captured
            val result = withTimeoutOrNull(5000L) {
                var screenshot = bridge.lastScreenshot.value
                while (screenshot == null || screenshot.base64 == null) {
                    delay(100)
                    screenshot = bridge.lastScreenshot.value
                }
                screenshot
            }

            if (result != null && result.base64 != null && result.error == null) {
                BrowserDriverScreenshot(
                    base64 = result.base64!!,
                    width = result.width ?: 0,
                    height = result.height ?: 0,
                    format = result.mimeType?.substringAfter("/") ?: "jpeg"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getCurrentUrl(): String {
        return bridge.currentUrl.value
    }

    override suspend fun getPageTitle(): String {
        return bridge.pageTitle.value
    }

    override fun close() {
        // WebEditBridge doesn't have a close method - it's managed by the UI
    }
}

/**
 * Extension function to create a BrowserDriver from a WebEditBridge.
 */
fun WebEditBridge.asBrowserDriver(): BrowserDriver = WebEditBridgeAdapter(this)

