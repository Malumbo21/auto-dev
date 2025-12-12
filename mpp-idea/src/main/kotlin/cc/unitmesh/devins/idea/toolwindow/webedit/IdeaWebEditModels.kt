package cc.unitmesh.devins.idea.toolwindow.webedit

/**
 * State for WebEdit Agent in IntelliJ IDEA
 */
data class IdeaWebEditState(
    val currentUrl: String = "",
    val pageTitle: String = "",
    val isLoading: Boolean = false,
    val loadProgress: Int = 0,
    val isSelectionMode: Boolean = false,
    val selectedElement: IdeaDOMElement? = null,
    val domTree: IdeaDOMElement? = null,
    val showDOMSidebar: Boolean = true,
    val isReady: Boolean = false,
    val error: String? = null
)

/**
 * Represents a DOM element from the web page
 */
data class IdeaDOMElement(
    val id: String,
    val tagName: String,
    val selector: String,
    val textContent: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val boundingBox: IdeaBoundingBox? = null,
    val children: List<IdeaDOMElement> = emptyList()
) {
    /**
     * Generate a display name for the DOM tree
     */
    fun getDisplayName(): String {
        val classAttr = attributes["class"]?.split(" ")?.firstOrNull()?.let { ".$it" } ?: ""
        val idAttr = attributes["id"]?.let { "#$it" } ?: ""
        val text = textContent?.take(30)?.let { " \"$it\"" } ?: ""
        return "$tagName$idAttr$classAttr$text"
    }
}

/**
 * Bounding box for element positioning
 */
data class IdeaBoundingBox(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double
)

/**
 * Message types from WebView to Kotlin
 */
sealed class IdeaWebEditMessage {
    data class DOMTreeUpdated(val root: IdeaDOMElement) : IdeaWebEditMessage()
    data class ElementSelected(val element: IdeaDOMElement) : IdeaWebEditMessage()
    data class PageLoaded(val url: String, val title: String) : IdeaWebEditMessage()
    data class Error(val message: String) : IdeaWebEditMessage()
    data class LoadProgress(val progress: Int) : IdeaWebEditMessage()
}
