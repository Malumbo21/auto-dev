package cc.unitmesh.agent.webagent.executor

import cc.unitmesh.agent.webagent.model.*
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * iOS implementation of BrowserActionExecutor.
 *
 * Delegates to DriverBasedBrowserActionExecutor with a BrowserDriver.
 *
 * @see <a href="https://github.com/phodal/auto-dev/issues/532">Issue #532</a>
 */
actual interface BrowserActionExecutor {
    actual val isAvailable: Boolean
    actual suspend fun execute(action: TestAction, context: ActionExecutionContext): ActionResult
    actual suspend fun navigateTo(url: String): ActionResult
    actual suspend fun click(tagId: Int, options: ClickOptions): ActionResult
    actual suspend fun type(tagId: Int, text: String, options: TypeOptions): ActionResult
    actual suspend fun scroll(direction: ScrollDirection, amount: Int, tagId: Int?): ActionResult
    actual suspend fun waitFor(condition: WaitCondition): ActionResult
    actual suspend fun pressKey(key: String, modifiers: List<KeyModifier>): ActionResult
    actual suspend fun screenshot(name: String, fullPage: Boolean): ScreenshotResult
    actual fun close()
}

/**
 * iOS implementation that wraps DriverBasedBrowserActionExecutor.
 *
 * Use [IosBrowserActionExecutor.withDriver] to create an instance with a BrowserDriver.
 */
class IosBrowserActionExecutor private constructor(
    private val delegate: DriverBasedBrowserActionExecutor
) : BrowserActionExecutor {

    override val isAvailable: Boolean get() = delegate.isAvailable

    fun setContext(context: ActionExecutionContext) {
        delegate.setContext(context)
    }

    override suspend fun execute(action: TestAction, context: ActionExecutionContext): ActionResult {
        return delegate.execute(action, context)
    }

    override suspend fun navigateTo(url: String): ActionResult = delegate.navigateTo(url)
    override suspend fun click(tagId: Int, options: ClickOptions): ActionResult = delegate.click(tagId, options)
    override suspend fun type(tagId: Int, text: String, options: TypeOptions): ActionResult = delegate.type(tagId, text, options)
    override suspend fun scroll(direction: ScrollDirection, amount: Int, tagId: Int?): ActionResult = delegate.scroll(direction, amount, tagId)
    override suspend fun waitFor(condition: WaitCondition): ActionResult = delegate.waitFor(condition)
    override suspend fun pressKey(key: String, modifiers: List<KeyModifier>): ActionResult = delegate.pressKey(key, modifiers)
    override suspend fun screenshot(name: String, fullPage: Boolean): ScreenshotResult = delegate.screenshot(name, fullPage)
    override fun close() = delegate.close()

    companion object {
        fun withDriver(driver: BrowserDriver, config: BrowserExecutorConfig = BrowserExecutorConfig()): IosBrowserActionExecutor {
            return IosBrowserActionExecutor(DriverBasedBrowserActionExecutor(driver, config))
        }
    }
}

actual fun createBrowserActionExecutor(config: BrowserExecutorConfig): BrowserActionExecutor? = null

internal actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
