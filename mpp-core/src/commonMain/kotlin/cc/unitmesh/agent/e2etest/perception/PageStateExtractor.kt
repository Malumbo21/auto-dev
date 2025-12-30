package cc.unitmesh.agent.e2etest.perception

import cc.unitmesh.agent.e2etest.model.*

/**
 * Extracts page state for agent perception.
 * 
 * Platform-specific implementations handle browser interaction:
 * - JVM: Uses KCEF (Chromium Embedded Framework)
 * - Android: Uses Android WebView
 * - iOS: Uses WKWebView via Swift bridge
 * - JS/WASM: Uses Playwright MCP or iframe
 * 
 * @see <a href="https://github.com/phodal/auto-dev/issues/532">Issue #532</a>
 */
expect interface PageStateExtractor {
    /**
     * Check if the extractor is available on this platform
     */
    val isAvailable: Boolean

    /**
     * Extract the current page state including actionable elements
     */
    suspend fun extractPageState(): PageState

    /**
     * Extract accessibility tree (high signal-to-noise ratio)
     */
    suspend fun extractAccessibilityTree(): AccessibilityNode?

    /**
     * Extract and clean DOM, removing non-essential elements
     */
    suspend fun extractCleanDOM(): String

    /**
     * Capture screenshot with Set-of-Mark annotations
     */
    suspend fun captureScreenshotWithSoM(): SoMScreenshot?

    /**
     * Get all actionable elements with SoM tags
     */
    suspend fun getActionableElements(): List<ActionableElement>

    /**
     * Get element by SoM tag ID
     */
    suspend fun getElementByTagId(tagId: Int): ActionableElement?

    /**
     * Get element fingerprint for self-healing
     */
    suspend fun getElementFingerprint(selector: String): ElementFingerprint?

    /**
     * Check if page is fully loaded
     */
    suspend fun isPageLoaded(): Boolean

    /**
     * Get current URL
     */
    suspend fun getCurrentUrl(): String

    /**
     * Get page title
     */
    suspend fun getPageTitle(): String

    /**
     * Close and cleanup resources
     */
    fun close()
}

/**
 * Factory function to create platform-specific PageStateExtractor
 */
expect fun createPageStateExtractor(config: PageStateExtractorConfig): PageStateExtractor?

/**
 * Configuration for PageStateExtractor
 */
data class PageStateExtractorConfig(
    /**
     * Viewport width
     */
    val viewportWidth: Int = 1280,

    /**
     * Viewport height
     */
    val viewportHeight: Int = 720,

    /**
     * Maximum elements to tag with SoM
     */
    val maxSoMTags: Int = 100,

    /**
     * Include elements in shadow DOM
     */
    val includeShadowDOM: Boolean = true,

    /**
     * Include elements in iframes
     */
    val includeIframes: Boolean = true,

    /**
     * Minimum element size to be considered actionable (pixels)
     */
    val minElementSize: Int = 10,

    /**
     * Screenshot quality (0.0 to 1.0)
     */
    val screenshotQuality: Double = 0.8,

    /**
     * DOM cleaning aggressiveness (0.0 to 1.0)
     */
    val domCleaningLevel: Double = 0.7
)
