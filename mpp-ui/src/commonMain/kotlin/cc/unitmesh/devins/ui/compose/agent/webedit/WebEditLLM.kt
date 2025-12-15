package cc.unitmesh.devins.ui.compose.agent.webedit

import cc.unitmesh.viewer.web.webedit.AccessibilityNode
import cc.unitmesh.viewer.web.webedit.DOMElement

/**
 * Build a prompt for WebEdit page Q&A.
 *
 * This is used by the WebEdit UI and also by the JVM preview for quick verification.
 */
fun buildWebEditLLMPrompt(
    message: String,
    currentUrl: String,
    pageTitle: String,
    selectedElement: DOMElement?,
    elementTags: ElementTagCollection,
    actionableElements: List<AccessibilityNode> = emptyList()
): String {
    val contextBuilder = StringBuilder()
    contextBuilder.append("You are helping analyze a web page.\n\n")
    contextBuilder.append("Current page: $pageTitle ($currentUrl)\n\n")

    if (actionableElements.isNotEmpty()) {
        contextBuilder.append("Actionable elements (role/name/selector):\n")
        actionableElements.take(30).forEach { node ->
            val name = node.name?.take(80)?.replace("\n", " ")?.trim()
            contextBuilder.append("- ${node.role}${name?.let { " \"$it\"" } ?: ""} | selector: ${node.selector}\n")
        }
        contextBuilder.append("\n")
    }

    // Add element tags context if available
    if (elementTags.isNotEmpty()) {
        contextBuilder.append(elementTags.toLLMContext())
        contextBuilder.append("\n")
    } else if (selectedElement != null) {
        // Fallback to single selected element
        contextBuilder.append("Selected element:\n")
        contextBuilder.append("- Tag: ${selectedElement.tagName}\n")
        contextBuilder.append("- Selector: ${selectedElement.selector}\n")
        if (selectedElement.textContent?.isNotEmpty() == true) {
            contextBuilder.append("- Text: ${selectedElement.textContent}\n")
        }
        if (selectedElement.attributes.isNotEmpty()) {
            contextBuilder.append("- Attributes: ${selectedElement.attributes}\n")
        }
        contextBuilder.append("\n")
    }

    contextBuilder.append("User question: $message\n\n")
    contextBuilder.append("Please provide a helpful and concise answer.")
    return contextBuilder.toString()
}


