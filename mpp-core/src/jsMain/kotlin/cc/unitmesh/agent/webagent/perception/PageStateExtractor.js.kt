package cc.unitmesh.agent.webagent.perception

import cc.unitmesh.agent.webagent.model.*
import kotlin.js.Date

/**
 * JS implementation of PageStateExtractor.
 * 
 * Can use Playwright MCP server or direct DOM access in browser context.
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
 * JS stub implementation - Playwright MCP integration pending
 */
class JsPageStateExtractor(
    private val config: PageStateExtractorConfig
) : PageStateExtractor {
    
    override val isAvailable: Boolean = false
    
    override suspend fun extractPageState(): PageState {
        return PageState(
            url = "",
            title = "",
            viewport = Viewport(config.viewportWidth, config.viewportHeight),
            actionableElements = emptyList(),
            capturedAt = Date.now().toLong()
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
    return JsPageStateExtractor(config)
}
