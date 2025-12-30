package cc.unitmesh.agent.e2etest.perception

import cc.unitmesh.agent.e2etest.model.*

/**
 * Android implementation of PageStateExtractor.
 * 
 * Uses Android WebView for browser control.
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
 * Android stub implementation - WebView integration pending
 */
class AndroidPageStateExtractor(
    private val config: PageStateExtractorConfig
) : PageStateExtractor {
    
    override val isAvailable: Boolean = false
    
    override suspend fun extractPageState(): PageState {
        return PageState(
            url = "",
            title = "",
            viewport = Viewport(config.viewportWidth, config.viewportHeight),
            actionableElements = emptyList(),
            capturedAt = System.currentTimeMillis()
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
    return AndroidPageStateExtractor(config)
}
