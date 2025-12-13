package cc.unitmesh.viewer.web.webedit

import kotlinx.serialization.Serializable

/**
 * D2Snap (Downsampled DOM Snapshot) - A DOM compression algorithm designed for LLM Agents
 *
 * Based on: https://arxiv.org/html/2508.04412v2
 *
 * Key features:
 * 1. Structure Flattening: Removes container nodes without text, interactive attributes, or semantic attributes
 * 2. Attribute Filtering: Removes style attributes, event handlers, and long text. Keeps id, name, class, type, ARIA
 * 3. ID Truncation: Simplifies dynamic IDs (e.g., "input-12345-xyz" → "input-{dynamic}")
 */
object D2Snap {

    /**
     * Interactive HTML tags that should always be preserved
     */
    private val INTERACTIVE_TAGS = setOf(
        "a", "button", "input", "select", "textarea", "option", "optgroup",
        "label", "form", "details", "summary", "dialog", "menu", "menuitem"
    )

    /**
     * Semantic HTML5 tags that provide meaningful structure
     */
    private val SEMANTIC_TAGS = setOf(
        "header", "footer", "nav", "main", "article", "section", "aside",
        "h1", "h2", "h3", "h4", "h5", "h6", "p", "ul", "ol", "li", "dl", "dt", "dd",
        "table", "thead", "tbody", "tfoot", "tr", "th", "td", "caption",
        "figure", "figcaption", "blockquote", "pre", "code", "img", "video", "audio",
        "iframe", "embed", "object", "canvas", "svg"
    )

    /**
     * Attributes to preserve (key interactive and semantic attributes)
     */
    private val PRESERVED_ATTRIBUTES = setOf(
        "id", "name", "class", "type", "href", "src", "alt", "title", "value",
        "placeholder", "disabled", "readonly", "checked", "selected", "required",
        "role", "aria-label", "aria-labelledby", "aria-describedby", "aria-hidden",
        "aria-expanded", "aria-selected", "aria-checked", "aria-disabled",
        "aria-haspopup", "aria-controls", "aria-live", "aria-atomic",
        "data-testid", "data-id", "for", "tabindex", "contenteditable"
    )

    /**
     * Regex patterns to detect dynamic IDs
     */
    private val DYNAMIC_ID_PATTERNS = listOf(
        Regex("^(.+?)-[0-9a-f]{8,}$"),           // uuid-like suffix
        Regex("^(.+?)-\\d{4,}$"),                 // numeric suffix (4+ digits)
        Regex("^(.+?)_[0-9a-f]{8,}$"),            // underscore + uuid
        Regex("^(.+?)_\\d{4,}$"),                 // underscore + numeric
        Regex("^(.+?)\\.[0-9a-f]{8,}$"),          // dot + uuid
        Regex("^:r[0-9a-z]+:$"),                  // React-style IDs like :r1a:
        Regex("^(.+?)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$") // embedded UUID
    )

    /**
     * Compress a DOM tree using D2Snap algorithm
     *
     * @param root The root DOMElement to compress
     * @param maxDepth Maximum depth to traverse (default 10)
     * @param maxTextLength Maximum text content length to preserve (default 100)
     * @return Compressed D2SnapElement tree
     */
    fun compress(
        root: DOMElement,
        maxDepth: Int = 10,
        maxTextLength: Int = 100
    ): D2SnapElement? {
        return compressNode(root, 0, maxDepth, maxTextLength)
    }

    private fun compressNode(
        element: DOMElement,
        depth: Int,
        maxDepth: Int,
        maxTextLength: Int
    ): D2SnapElement? {
        if (depth > maxDepth) return null

        val tagName = element.tagName.lowercase()
        
        // Filter attributes
        val filteredAttrs = filterAttributes(element.attributes)
        
        // Process children recursively
        val compressedChildren = element.children
            .mapNotNull { compressNode(it, depth + 1, maxDepth, maxTextLength) }

        // Check if this node should be preserved
        val shouldPreserve = shouldPreserveNode(element, filteredAttrs, compressedChildren)

        if (!shouldPreserve && compressedChildren.size == 1) {
            // Flatten: return the single child instead of this wrapper node
            return compressedChildren.first()
        }

        if (!shouldPreserve && compressedChildren.isEmpty()) {
            // Remove empty non-semantic containers
            return null
        }

        // Truncate text content
        val truncatedText = element.textContent?.let { text ->
            val trimmed = text.trim()
            if (trimmed.length > maxTextLength) {
                trimmed.take(maxTextLength) + "..."
            } else {
                trimmed.ifEmpty { null }
            }
        }

        return D2SnapElement(
            id = element.id,
            tagName = tagName,
            selector = element.selector,
            text = truncatedText,
            attributes = filteredAttrs,
            children = compressedChildren,
            isShadowHost = element.isShadowHost,
            inShadowRoot = element.inShadowRoot
        )
    }

    /**
     * Filter and simplify attributes according to D2Snap rules
     */
    private fun filterAttributes(attributes: Map<String, String>): Map<String, String> {
        return attributes
            .filterKeys { key -> 
                key.lowercase() in PRESERVED_ATTRIBUTES || key.startsWith("aria-") || key.startsWith("data-")
            }
            .mapValues { (key, value) ->
                when (key.lowercase()) {
                    "id" -> truncateDynamicId(value)
                    "class" -> simplifyClass(value)
                    else -> value.take(200) // Truncate long attribute values
                }
            }
            .filterValues { it.isNotBlank() }
    }

    /**
     * Truncate dynamic IDs to prevent LLM overfitting
     * e.g., "input-12345-xyz" → "input-{dynamic}"
     */
    fun truncateDynamicId(id: String): String {
        for (pattern in DYNAMIC_ID_PATTERNS) {
            val match = pattern.find(id)
            if (match != null) {
                val prefix = match.groupValues.getOrNull(1) ?: ""
                return if (prefix.isNotEmpty()) "$prefix-{dynamic}" else "{dynamic}"
            }
        }
        return id
    }

    /**
     * Simplify class attribute - keep only first 3 meaningful classes
     */
    private fun simplifyClass(classValue: String): String {
        return classValue
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && !it.matches(Regex("^[a-z]{1,2}-[0-9]+$")) } // Filter utility classes like "p-4"
            .take(3)
            .joinToString(" ")
    }

    /**
     * Determine if a node should be preserved based on D2Snap rules
     */
    private fun shouldPreserveNode(
        element: DOMElement,
        filteredAttrs: Map<String, String>,
        children: List<D2SnapElement>
    ): Boolean {
        val tagName = element.tagName.lowercase()

        // Always preserve interactive elements
        if (tagName in INTERACTIVE_TAGS) return true

        // Always preserve semantic elements
        if (tagName in SEMANTIC_TAGS) return true

        // Preserve if has meaningful text content
        val hasText = !element.textContent.isNullOrBlank() && 
                      element.textContent.trim().length > 2

        // Preserve if has ARIA attributes (accessibility)
        val hasAriaAttrs = filteredAttrs.keys.any { it.startsWith("aria-") }

        // Preserve if has role attribute
        val hasRole = filteredAttrs.containsKey("role")

        // Preserve if has id or name (likely targetable)
        val hasIdentifier = filteredAttrs.containsKey("id") || filteredAttrs.containsKey("name")

        // Preserve shadow hosts
        if (element.isShadowHost) return true

        // Preserve if multiple children (structural node)
        if (children.size > 1) return true

        return hasText || hasAriaAttrs || hasRole || hasIdentifier
    }

    /**
     * Convert D2Snap tree to compact JSON string for LLM consumption
     */
    fun toCompactJson(element: D2SnapElement): String {
        return buildString {
            appendCompactJson(element, this, 0)
        }
    }

    private fun appendCompactJson(element: D2SnapElement, sb: StringBuilder, indent: Int) {
        val prefix = "  ".repeat(indent)
        sb.append(prefix)
        sb.append("<${element.tagName}")
        
        // Add key attributes inline
        element.attributes.forEach { (key, value) ->
            sb.append(" $key=\"$value\"")
        }
        
        if (element.children.isEmpty() && element.text.isNullOrBlank()) {
            sb.append("/>")
        } else {
            sb.append(">")
            element.text?.let { sb.append(it) }
            if (element.children.isNotEmpty()) {
                sb.append("\n")
                element.children.forEach { child ->
                    appendCompactJson(child, sb, indent + 1)
                    sb.append("\n")
                }
                sb.append(prefix)
            }
            sb.append("</${element.tagName}>")
        }
    }

    /**
     * Calculate compression ratio
     */
    fun calculateCompressionRatio(original: DOMElement, compressed: D2SnapElement?): Double {
        if (compressed == null) return 1.0
        val originalCount = countNodes(original)
        val compressedCount = countD2SnapNodes(compressed)
        return if (originalCount > 0) {
            1.0 - (compressedCount.toDouble() / originalCount.toDouble())
        } else 0.0
    }

    private fun countNodes(element: DOMElement): Int {
        return 1 + element.children.sumOf { countNodes(it) }
    }

    private fun countD2SnapNodes(element: D2SnapElement): Int {
        return 1 + element.children.sumOf { countD2SnapNodes(it) }
    }
}

/**
 * Compressed DOM element representation from D2Snap algorithm
 */
@Serializable
data class D2SnapElement(
    val id: String,
    val tagName: String,
    val selector: String,
    val text: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val children: List<D2SnapElement> = emptyList(),
    val isShadowHost: Boolean = false,
    val inShadowRoot: Boolean = false
) {
    /**
     * Get a display name for UI rendering
     */
    fun getDisplayName(): String {
        val idAttr = attributes["id"]?.let { "#$it" } ?: ""
        val classAttr = attributes["class"]?.split(" ")?.firstOrNull()?.let { ".$it" } ?: ""
        val roleAttr = attributes["role"]?.let { "[$it]" } ?: ""
        val textPreview = text?.take(20)?.let { " \"$it\"" } ?: ""
        return "$tagName$idAttr$classAttr$roleAttr$textPreview"
    }
}
