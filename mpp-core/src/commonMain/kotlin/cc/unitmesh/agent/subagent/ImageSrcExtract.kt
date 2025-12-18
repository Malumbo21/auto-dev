package cc.unitmesh.agent.subagent


/**
 * Regex pattern to match Image components in NanoDSL code.
 * Matches multiline Image(src="...", ...) or Image(src=..., ...)
 * Uses [\s\S] instead of . to match any character including newlines (JS compatible).
 */
val imagePattern = Regex(
    """Image\s*\([\s\S]*?src\s*=\s*["']([^"']+)["']"""
)

/**
 * Check if a src value should be replaced with AI-generated image.
 * Since LLM often generates fake/hallucinated URLs, we replace all src values
 * except for data: URLs (which are actual embedded images).
 */
fun shouldGenerateImage(src: String): Boolean {
    val trimmed = src.trim()
    // Only skip data: URLs (actual embedded image data)
    // All other URLs (including http/https) should be replaced since they're usually fake
    return !trimmed.startsWith("data:")
}

/**
 * Extract a prompt from surrounding NanoDSL context.
 * Looks for nearby Text components that might describe the image.
 */
fun extractContextPrompt(context: String): String {
    if (context.isEmpty()) return ""

    // Look for Text components with meaningful content
    val textPattern = Regex("""Text\s*\(\s*["']([^"']+)["']""")
    val textMatches = textPattern.findAll(context).toList()

    // Filter out common UI labels and get meaningful descriptions
    val meaningfulTexts = textMatches
        .map { it.groupValues[1] }
        .filter { text ->
            text.length > 3 &&
                    !text.matches(
                        Regex(
                            "^(Click|Submit|Cancel|OK|Yes|No|Close|Open|Edit|Delete|Save|Back|Next|Previous)$",
                            RegexOption.IGNORE_CASE
                        )
                    )
        }

    return meaningfulTexts.firstOrNull() ?: ""
}

/**
 * Extract a meaningful prompt from the src value and surrounding context.
 * Works with URLs (including fake Unsplash links), paths, and placeholders.
 */
fun extractImagePrompt(src: String, surroundingContext: String = ""): String {
    // First, try to extract meaningful text from the surrounding context
    // Look for nearby Text components that might describe the image
    val contextPrompt = extractContextPrompt(surroundingContext)
    if (contextPrompt.isNotEmpty()) {
        return contextPrompt
    }

    // Handle URLs - try to extract meaningful parts
    val urlCleaned = if (src.startsWith("http://") || src.startsWith("https://")) {
        // For URLs, try to extract path segments that might be meaningful
        val pathPart = src
            .replace(Regex("^https?://[^/]+/"), "") // Remove domain
            .replace(Regex("\\?.*$"), "") // Remove query string
            .replace(Regex("photo-[0-9a-f-]+"), "") // Remove Unsplash photo IDs
            .replace(Regex("[0-9]+x[0-9]+"), "") // Remove dimensions
        pathPart
    } else {
        src
    }

    // Clean up the path/URL
    val cleaned = urlCleaned
        .replace(Regex("^[./]+"), "")
        .replace(Regex("\\.(jpg|jpeg|png|gif|webp|svg)$", RegexOption.IGNORE_CASE), "")
        .replace("/", " ")
        .replace("_", " ")
        .replace("-", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    // If it looks like a variable reference (e.g., item.image), extract the meaningful part
    if (cleaned.contains(".")) {
        val parts = cleaned.split(".")
        val extracted = parts.lastOrNull()?.replace(Regex("[^a-zA-Z0-9 ]"), " ")?.trim() ?: cleaned
        if (extracted.isNotEmpty()) return extracted
    }

    // If we got a meaningful string, use it
    if (cleaned.length > 3 && cleaned.any { it.isLetter() }) {
        return cleaned
    }

    // Fallback: return a generic prompt based on context or default
    return "high quality image"
}