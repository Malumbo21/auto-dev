package cc.unitmesh.agent.webagent.executor

import cc.unitmesh.agent.webagent.model.*

/**
 * JVM implementation of BrowserActionExecutor.
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
 * JVM implementation that wraps DriverBasedBrowserActionExecutor.
 *
 * Use [JvmBrowserActionExecutor.withDriver] to create an instance with a BrowserDriver.
 */
class JvmBrowserActionExecutor private constructor(
    private val delegate: DriverBasedBrowserActionExecutor
) : BrowserActionExecutor {

    override val isAvailable: Boolean get() = delegate.isAvailable

    /**
     * Update the current execution context (tag mapping, etc.)
     */
    fun setContext(context: ActionExecutionContext) {
        delegate.setContext(context)
    }

    override suspend fun execute(action: TestAction, context: ActionExecutionContext): ActionResult {
        return delegate.execute(action, context)
    }

    override suspend fun navigateTo(url: String): ActionResult {
        return delegate.navigateTo(url)
    }

    override suspend fun click(tagId: Int, options: ClickOptions): ActionResult {
        return delegate.click(tagId, options)
    }

    override suspend fun type(tagId: Int, text: String, options: TypeOptions): ActionResult {
        return delegate.type(tagId, text, options)
    }

    override suspend fun scroll(direction: ScrollDirection, amount: Int, tagId: Int?): ActionResult {
        return delegate.scroll(direction, amount, tagId)
    }

    override suspend fun waitFor(condition: WaitCondition): ActionResult {
        return delegate.waitFor(condition)
    }

    override suspend fun pressKey(key: String, modifiers: List<KeyModifier>): ActionResult {
        return delegate.pressKey(key, modifiers)
    }

    override suspend fun screenshot(name: String, fullPage: Boolean): ScreenshotResult {
        return delegate.screenshot(name, fullPage)
    }

    override fun close() {
        delegate.close()
    }

    companion object {
        /**
         * Create a JvmBrowserActionExecutor with a BrowserDriver.
         *
         * @param driver The BrowserDriver to use for browser operations
         * @param config Configuration for the executor
         */
        fun withDriver(driver: BrowserDriver, config: BrowserExecutorConfig = BrowserExecutorConfig()): JvmBrowserActionExecutor {
            return JvmBrowserActionExecutor(DriverBasedBrowserActionExecutor(driver, config))
        }
    }
}

/**
 * Factory function - returns null as no default driver is available.
 * Use [JvmBrowserActionExecutor.withDriver] to create with a specific driver.
 */
actual fun createBrowserActionExecutor(config: BrowserExecutorConfig): BrowserActionExecutor? {
    // No default driver available - use JvmBrowserActionExecutor.withDriver() instead
    return null
}

/**
 * JVM implementation of currentTimeMillis
 */
internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()
