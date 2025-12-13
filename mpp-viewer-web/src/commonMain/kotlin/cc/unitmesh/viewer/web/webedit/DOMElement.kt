package cc.unitmesh.viewer.web.webedit

import kotlinx.serialization.Serializable

/**
 * Represents a DOM element from the web page
 *
 * @param id Unique identifier for the element
 * @param tagName HTML tag name (e.g., "div", "span", "button")
 * @param selector CSS selector to locate this element
 * @param textContent Text content of the element (truncated if too long)
 * @param attributes Key attributes of the element (id, class, etc.)
 * @param boundingBox Bounding box of the element (x, y, width, height)
 * @param children Child elements (for tree structure)
 * @param isShadowHost Whether this element has a shadow root attached
 * @param inShadowRoot Whether this element is inside a shadow root
 */
@Serializable
data class DOMElement(
    val id: String,
    val tagName: String,
    val selector: String,
    val textContent: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val boundingBox: BoundingBox? = null,
    val children: List<DOMElement> = emptyList(),
    val isShadowHost: Boolean = false,
    val inShadowRoot: Boolean = false
) {
    /**
     * Generate a display name for the DOM tree
     */
    fun getDisplayName(): String {
        val classAttr = attributes["class"]?.split(" ")?.firstOrNull()?.let { ".$it" } ?: ""
        val idAttr = attributes["id"]?.let { "#$it" } ?: ""
        val shadowIndicator = if (isShadowHost) " 🔒" else if (inShadowRoot) " 👁" else ""
        val text = textContent?.take(30)?.let { " \"$it\"" } ?: ""
        return "$tagName$idAttr$classAttr$shadowIndicator$text"
    }
}

/**
 * Bounding box for element positioning
 */
@Serializable
data class BoundingBox(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double
)

/**
 * Message types from WebView to Kotlin
 */
@Serializable
sealed class WebEditMessage {
    /**
     * DOM tree updated message
     */
    @Serializable
    data class DOMTreeUpdated(val root: DOMElement) : WebEditMessage()
    
    /**
     * Element selected by user
     */
    @Serializable
    data class ElementSelected(val element: DOMElement) : WebEditMessage()
    
    /**
     * Page loaded event
     */
    @Serializable
    data class PageLoaded(val url: String, val title: String) : WebEditMessage()
    
    /**
     * Error message
     */
    @Serializable
    data class Error(val message: String) : WebEditMessage()
    
    /**
     * Page load progress
     */
    @Serializable
    data class LoadProgress(val progress: Int) : WebEditMessage()
}

/**
 * Commands from Kotlin to WebView
 */
@Serializable
sealed class WebEditCommand {
    /**
     * Enable/disable selection mode
     */
    @Serializable
    data class SetSelectionMode(val enabled: Boolean) : WebEditCommand()
    
    /**
     * Highlight a specific element
     */
    @Serializable
    data class HighlightElement(val selector: String) : WebEditCommand()
    
    /**
     * Clear all highlights
     */
    @Serializable
    object ClearHighlights : WebEditCommand()
    
    /**
     * Get DOM tree
     */
    @Serializable
    object GetDOMTree : WebEditCommand()
    
    /**
     * Scroll to element
     */
    @Serializable
    data class ScrollToElement(val selector: String) : WebEditCommand()
}

