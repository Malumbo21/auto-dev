package cc.unitmesh.agent.e2etest.perception

import cc.unitmesh.agent.e2etest.model.*

@JsFun("() => Date.now()")
private external fun dateNow(): Double

/**
 * WASM JS implementation of PageStateExtractor.
 *
 * Uses browser APIs or Playwright MCP server.
 *
 * @see <a href="https://github.com/phodal/auto-dev/issues/532">Issue #532</a>
 */
actual interface PageStateExtractor {
    actual val isAvailable: Boolean
    actual suspend fun extractPageState(): PageState
    actual suspend fun extractAccessibilityTree(): AccessibilityNode?
    actual suspend fun extractCleanDOM(): String
    actual suspend fun captureScreenshotWithSoM(): SoMScreenshot?
    actual suspend fun getActionableElements(): List<ActionableElement>
    actual suspend fun getElementByTagId(tagId: Int): ActionableElement?
    actual suspend fun getElementFingerprint(selector: String): ElementFingerprint?
    actual suspend fun isPageLoaded(): Boolean
    actual suspend fun getCurrentUrl(): String
    actual suspend fun getPageTitle(): String
    actual fun close()
}

/**
 * WASM stub implementation
 */
class WasmJsPageStateExtractor(
    private val config: PageStateExtractorConfig
) : PageStateExtractor {

    override val isAvailable: Boolean = false

    override suspend fun extractPageState(): PageState {
        return PageState(
            url = "",
            title = "",
            viewport = Viewport(config.viewportWidth, config.viewportHeight),
            actionableElements = emptyList(),
            capturedAt = dateNow().toLong()
        )
    }
    
    override suspend fun extractAccessibilityTree(): AccessibilityNode? = null
    override suspend fun extractCleanDOM(): String = ""
    override suspend fun captureScreenshotWithSoM(): SoMScreenshot? = null
    override suspend fun getActionableElements(): List<ActionableElement> = emptyList()
    override suspend fun getElementByTagId(tagId: Int): ActionableElement? = null
    override suspend fun getElementFingerprint(selector: String): ElementFingerprint? = null
    override suspend fun isPageLoaded(): Boolean = false
    override suspend fun getCurrentUrl(): String = ""
    override suspend fun getPageTitle(): String = ""
    override fun close() {}
}

actual fun createPageStateExtractor(config: PageStateExtractorConfig): PageStateExtractor? {
    return WasmJsPageStateExtractor(config)
}
