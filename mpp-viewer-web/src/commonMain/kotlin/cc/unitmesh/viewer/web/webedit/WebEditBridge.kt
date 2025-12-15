package cc.unitmesh.viewer.web.webedit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Abstract bridge for communication between Kotlin and WebView
 * 
 * This interface defines the contract for bidirectional communication
 * between the Kotlin/Compose layer and the embedded WebView for web editing.
 */
interface WebEditBridge {
    /**
     * Current URL being viewed
     */
    val currentUrl: StateFlow<String>
    
    /**
     * Page title
     */
    val pageTitle: StateFlow<String>
    
    /**
     * Whether the page is loading
     */
    val isLoading: StateFlow<Boolean>
    
    /**
     * Load progress (0-100)
     */
    val loadProgress: StateFlow<Int>
    
    /**
     * Current DOM tree
     */
    val domTree: StateFlow<DOMElement?>
    
    /**
     * Currently selected element
     */
    val selectedElement: StateFlow<DOMElement?>

    /**
     * Last received D2Snap tree (compressed DOM snapshot for LLM consumption)
     */
    val d2SnapTree: StateFlow<D2SnapElement?>

    /**
     * Last received accessibility tree (semantic snapshot for LLM consumption)
     */
    val accessibilityTree: StateFlow<AccessibilityNode?>

    /**
     * Last received actionable elements list (from accessibility tree extraction)
     */
    val actionableElements: StateFlow<List<AccessibilityNode>>

    /**
     * Last action execution result from the browser
     */
    val lastActionResult: StateFlow<WebEditMessage.ActionResult?>

    /**
     * Last captured screenshot from the WebView (for Vision LLM fallback)
     */
    val lastScreenshot: StateFlow<WebEditMessage.ScreenshotCaptured?>

    /**
     * Perform a structured browser action. This is the preferred API for LLM-driven automation
     * because callers can attach a unique [WebEditAction.id] and reliably correlate results.
     */
    suspend fun performAction(action: WebEditAction)
    
    /**
     * Whether selection mode is enabled
     */
    val isSelectionMode: StateFlow<Boolean>
    
    /**
     * Whether the bridge is ready
     */
    val isReady: StateFlow<Boolean>
    
    /**
     * Last error message, if any
     */
    val errorMessage: StateFlow<String?>

    /**
     * Navigate to a URL
     */
    suspend fun navigateTo(url: String)
    
    /**
     * Reload the current page
     */
    suspend fun reload()
    
    /**
     * Go back in history
     */
    suspend fun goBack()
    
    /**
     * Go forward in history
     */
    suspend fun goForward()
    
    /**
     * Enable or disable selection mode
     */
    suspend fun setSelectionMode(enabled: Boolean)

    /**
     * Enable inspect mode (with Shadow DOM support)
     */
    suspend fun enableInspectMode()

    /**
     * Disable inspect mode
     */
    suspend fun disableInspectMode()

    /**
     * Highlight a specific element
     */
    suspend fun highlightElement(selector: String)

    /**
     * Clear all highlights
     */
    suspend fun clearHighlights()

    /**
     * Scroll to an element
     */
    suspend fun scrollToElement(selector: String)

    /**
     * Refresh the DOM tree
     */
    suspend fun refreshDOMTree()

    /**
     * Get D2Snap compressed DOM tree (optimized for LLM consumption)
     * Based on: https://arxiv.org/html/2508.04412v2
     */
    suspend fun refreshD2SnapTree()

    /**
     * Get Accessibility Tree (semantic tree for screen readers/LLM)
     * Based on: https://arxiv.org/html/2508.04412v2
     */
    suspend fun refreshAccessibilityTree()

    /**
     * Get only actionable elements from the page
     */
    suspend fun refreshActionableElements()

    /**
     * Get element at specific coordinates
     */
    suspend fun getElementAtPoint(x: Int, y: Int): DOMElement?

    /**
     * Get the HTML content of the selected element
     */
    suspend fun getSelectedElementHtml(): String?

    /**
     * Click an element by selector (shadow DOM aware in injected script).
     */
    suspend fun click(selector: String)

    /**
     * Type text into an element by selector (input/textarea/contenteditable).
     */
    suspend fun typeText(selector: String, text: String, clearFirst: Boolean = true)

    /**
     * Select an option for a <select> element by selector.
     * The value may be a visible text or option value depending on page.
     */
    suspend fun selectOption(selector: String, value: String)

    /**
     * Press a key on the currently focused element (or optional target selector).
     */
    suspend fun pressKey(key: String, selector: String? = null)

    /**
     * Capture a screenshot of the current WebView viewport.
     * The result will be available in [lastScreenshot] StateFlow.
     * 
     * @param maxWidth Maximum width of the screenshot (default 1280)
     * @param quality JPEG quality 0.0-1.0 (default 0.8)
     */
    suspend fun captureScreenshot(maxWidth: Int = 1280, quality: Double = 0.8)
    
    /**
     * Mark bridge as ready
     */
    fun markReady()
    
    /**
     * Handle message from WebView
     */
    fun handleMessage(message: WebEditMessage)
}

/**
 * State holder for WebEdit
 */
data class WebEditState(
    val currentUrl: String = "",
    val pageTitle: String = "",
    val isLoading: Boolean = false,
    val loadProgress: Int = 0,
    val domTree: DOMElement? = null,
    val selectedElement: DOMElement? = null,
    val d2SnapTree: D2SnapElement? = null,
    val accessibilityTree: AccessibilityNode? = null,
    val actionableElements: List<AccessibilityNode> = emptyList(),
    val lastActionResult: WebEditMessage.ActionResult? = null,
    val lastScreenshot: WebEditMessage.ScreenshotCaptured? = null,
    val isSelectionMode: Boolean = false,
    val isReady: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Factory function for creating WebEditBridge instances
 */
expect fun createWebEditBridge(): WebEditBridge

