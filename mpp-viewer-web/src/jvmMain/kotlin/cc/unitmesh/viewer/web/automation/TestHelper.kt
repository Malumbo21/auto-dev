package cc.unitmesh.viewer.web.automation

import cc.unitmesh.viewer.web.webedit.DOMElement

/**
 * Helper functions for DOM tree operations and test assertions
 */
object TestHelper {
    
    /**
     * Count all elements in DOM tree recursively
     */
    fun countAllElements(element: DOMElement): Int {
        var count = 1
        element.children.forEach { count += countAllElements(it) }
        return count
    }

    /**
     * Collect all shadow host elements from DOM tree
     */
    fun collectShadowHosts(element: DOMElement): List<DOMElement> {
        val hosts = mutableListOf<DOMElement>()
        if (element.isShadowHost) {
            hosts.add(element)
        }
        element.children.forEach { child ->
            hosts.addAll(collectShadowHosts(child))
        }
        return hosts
    }

    /**
     * Count elements that are inside shadow DOM
     */
    fun countShadowElements(element: DOMElement): Int {
        var count = if (element.inShadowRoot) 1 else 0
        element.children.forEach { count += countShadowElements(it) }
        return count
    }

    /**
     * Print DOM tree structure for debugging
     */
    fun printDOMTreeSummary(element: DOMElement, depth: Int = 0, maxDepth: Int = 3) {
        if (depth > maxDepth) return

        val indent = "  ".repeat(depth)
        val shadowMarker = if (element.inShadowRoot) "🔒" else ""
        val hostMarker = if (element.isShadowHost) "⚡" else ""
        println("$indent$shadowMarker$hostMarker${element.tagName}#${element.attributes["id"] ?: ""}")

        element.children.forEach { child ->
            printDOMTreeSummary(child, depth + 1, maxDepth)
        }
    }

    /**
     * Find element by selector in DOM tree
     */
    fun findElement(root: DOMElement, selector: String): DOMElement? {
        if (root.selector == selector) return root
        root.children.forEach { child ->
            findElement(child, selector)?.let { return it }
        }
        return null
    }

    /**
     * Get all elements matching a tag name
     */
    fun getElementsByTag(root: DOMElement, tagName: String): List<DOMElement> {
        val elements = mutableListOf<DOMElement>()
        if (root.tagName.equals(tagName, ignoreCase = true)) {
            elements.add(root)
        }
        root.children.forEach { child ->
            elements.addAll(getElementsByTag(child, tagName))
        }
        return elements
    }

    /**
     * Validate DOM tree structure
     */
    fun validateDOMTree(root: DOMElement): Boolean {
        if (root.tagName.isEmpty()) return false
        if (root.selector.isEmpty()) return false
        
        // Validate all children recursively
        root.children.forEach { child ->
            if (!validateDOMTree(child)) return false
        }
        
        return true
    }
}
