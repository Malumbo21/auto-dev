package cc.unitmesh.devins.ui.compose.agent.webedit

import cc.unitmesh.viewer.web.webedit.DOMElement
import kotlinx.serialization.Serializable

/**
 * Represents a selected DOM element tag for display in the chat input.
 * This is a lightweight representation optimized for UI display and LLM analysis.
 *
 * @param id Unique identifier for the tag
 * @param tagName HTML tag name (e.g., "div", "button")
 * @param selector CSS selector for the element
 * @param displayName Human-readable display name
 * @param textContent Optional text content preview
 * @param attributes Key attributes (class, id, etc.)
 * @param sourceHint Optional hint about potential source file location
 */
@Serializable
data class ElementTag(
    val id: String,
    val tagName: String,
    val selector: String,
    val displayName: String,
    val textContent: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val sourceHint: String? = null
) {
    companion object {
        /**
         * Create an ElementTag from a DOMElement
         */
        fun fromDOMElement(element: DOMElement): ElementTag {
            return ElementTag(
                id = element.id,
                tagName = element.tagName,
                selector = element.selector,
                displayName = element.getDisplayName(),
                textContent = element.textContent?.take(50),
                attributes = element.attributes,
                sourceHint = inferSourceHint(element)
            )
        }

        /**
         * Infer potential source file location based on element attributes.
         * This helps the LLM locate corresponding source code.
         */
        private fun inferSourceHint(element: DOMElement): String? {
            val hints = mutableListOf<String>()

            // Check for data attributes that might indicate component names
            element.attributes.forEach { (key, value) ->
                when {
                    key.startsWith("data-component") -> hints.add("Component: $value")
                    key.startsWith("data-testid") -> hints.add("TestID: $value")
                    key == "data-source" -> hints.add("Source: $value")
                    key == "class" && value.contains("__") -> {
                        // BEM naming convention might indicate component
                        val componentName = value.split("__").firstOrNull()
                        if (componentName != null) hints.add("BEM Component: $componentName")
                    }
                }
            }

            // Check for React/Vue/Angular patterns in class names
            element.attributes["class"]?.let { className ->
                when {
                    className.matches(Regex(".*[A-Z][a-z]+[A-Z].*")) -> {
                        // CamelCase suggests React component
                        hints.add("Likely React/Vue component")
                    }
                    className.contains("ng-") -> hints.add("Angular component")
                    className.contains("v-") -> hints.add("Vue directive")
                }
            }

            return hints.takeIf { it.isNotEmpty() }?.joinToString("; ")
        }
    }

    /**
     * Generate a short label for the tag chip
     */
    fun getShortLabel(): String {
        val idPart = attributes["id"]?.let { "#$it" } ?: ""
        val classPart = attributes["class"]?.split(" ")?.firstOrNull()?.let { ".$it" } ?: ""
        return "$tagName$idPart$classPart"
    }

    /**
     * Generate detailed context for LLM analysis
     */
    fun toLLMContext(): String {
        val sb = StringBuilder()
        sb.appendLine("Element: <$tagName>")
        sb.appendLine("Selector: $selector")

        if (attributes.isNotEmpty()) {
            sb.appendLine("Attributes:")
            attributes.forEach { (key, value) ->
                sb.appendLine("  - $key: $value")
            }
        }

        textContent?.let {
            sb.appendLine("Text Content: \"$it\"")
        }

        sourceHint?.let {
            sb.appendLine("Source Hints: $it")
        }

        return sb.toString()
    }

    /**
     * Generate a structured prompt section for source code mapping
     */
    fun toSourceMappingPrompt(): String {
        return """
            |## Selected DOM Element
            |
            |I need to find and potentially modify the source code for this DOM element:
            |
            |```
            |Tag: $tagName
            |Selector: $selector
            |${attributes.entries.joinToString("\n") { "Attribute ${it.key}: ${it.value}" }}
            |${textContent?.let { "Content: \"$it\"" } ?: ""}
            |```
            |
            |${sourceHint?.let { "**Source Hints:** $it" } ?: ""}
            |
            |Please:
            |1. Search for files that might contain this component/element
            |2. Identify the specific code location
            |3. Suggest modifications if requested
        """.trimMargin()
    }
}

/**
 * Collection of selected element tags for the chat input
 */
data class ElementTagCollection(
    val tags: List<ElementTag> = emptyList()
) {
    fun add(tag: ElementTag): ElementTagCollection {
        // Prevent duplicates based on selector
        if (tags.any { it.selector == tag.selector }) {
            return this
        }
        return copy(tags = tags + tag)
    }

    fun remove(tagId: String): ElementTagCollection {
        return copy(tags = tags.filter { it.id != tagId })
    }

    fun clear(): ElementTagCollection {
        return copy(tags = emptyList())
    }

    fun isEmpty(): Boolean = tags.isEmpty()

    fun isNotEmpty(): Boolean = tags.isNotEmpty()

    /**
     * Generate combined LLM context for all selected elements
     */
    fun toLLMContext(): String {
        if (tags.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("## Selected Elements (${tags.size})")
        sb.appendLine()

        tags.forEachIndexed { index, tag ->
            sb.appendLine("### Element ${index + 1}: ${tag.getShortLabel()}")
            sb.appendLine(tag.toLLMContext())
            sb.appendLine()
        }

        return sb.toString()
    }
}
