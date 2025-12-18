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

private fun extractPromptFromSrc(src: String): String? {
    if (src.isBlank()) return null

    // data: URLs are already real embedded images; they usually don't contain useful prompts.
    val trimmed = src.trim()
    if (trimmed.startsWith("data:")) return null

    // Handle URLs - try to extract meaningful parts
    val urlCleaned = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        // For URLs, try to extract path segments that might be meaningful
        trimmed
            .replace(Regex("^https?://[^/]+/"), "") // Remove domain
            .replace(Regex("\\?.*$"), "") // Remove query string
            .replace(Regex("photo-[0-9a-f-]+"), "") // Remove Unsplash photo IDs
            .replace(Regex("[0-9]+x[0-9]+"), "") // Remove dimensions
    } else {
        trimmed
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
    val candidate = if (cleaned.contains(".")) {
        cleaned.split(".")
            .lastOrNull()
            ?.replace(Regex("[^a-zA-Z0-9 ]"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() } ?: cleaned
    } else {
        cleaned
    }

    if (candidate.length > 3 && candidate.any { it.isLetter() }) return candidate
    return null
}

/**
 * Extract a meaningful prompt from the src value and surrounding context.
 * Works with URLs (including fake Unsplash links), paths, and placeholders.
 */
fun extractImagePrompt(src: String, surroundingContext: String = ""): String {
    // Prefer deriving prompt from src when it contains meaningful information.
    // This avoids picking unrelated nearby Text like titles (e.g., "Singapore Trip Planner").
    val srcPrompt = extractPromptFromSrc(src)
    if (srcPrompt != null) return srcPrompt

    // Otherwise, try to extract meaningful text from the surrounding NanoDSL context.
    val contextPrompt = extractContextPrompt(surroundingContext)
    if (contextPrompt.isNotEmpty()) return contextPrompt

    // Fallback: return a generic prompt based on context or default
    return "high quality image"
}