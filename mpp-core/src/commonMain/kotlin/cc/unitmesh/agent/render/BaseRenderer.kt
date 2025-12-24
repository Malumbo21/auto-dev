package cc.unitmesh.agent.render

/**
 * Base abstract renderer providing common functionality
 * All specific renderer implementations should extend this class
 */
abstract class BaseRenderer : CodingAgentRenderer {
    protected val reasoningBuffer = StringBuilder()
    protected var isInDevinBlock = false
    protected var isInThinkBlock = false
    protected var lastIterationReasoning = ""
    protected var consecutiveRepeats = 0
    protected val thinkingBuffer = StringBuilder()

    /**
     * Common devin block filtering logic
     * Note: This does NOT filter <think> blocks - those are handled separately
     */
    protected fun filterDevinBlocks(content: String): String {
        var filtered = content

        // Remove complete devin blocks
        filtered = filtered.replace(Regex("<devin[^>]*>[\\s\\S]*?</devin>"), "")

        // Handle incomplete devin blocks at the end
        val openDevinIndex = filtered.lastIndexOf("<devin")
        if (openDevinIndex != -1) {
            val closeDevinIndex = filtered.indexOf("</devin>", openDevinIndex)
            if (closeDevinIndex == -1) {
                // Incomplete devin block, remove it
                filtered = filtered.substring(0, openDevinIndex)
            }
        }

        // Remove partial devin tags
        // IMPORTANT:
        // We only want to strip partial "<devin" tags (e.g. "<d", "<de", "<dev", "<devi", "<devin"),
        // not any dangling "<" which is extremely common in streaming HTML/XML and would break streaming UX.
        filtered = filtered.replace(Regex("<d(?:e(?:v(?:i(?:n)?)?)?)?$"), "")

        return filtered
    }

    /**
     * Check for incomplete devin blocks
     */
    protected fun hasIncompleteDevinBlock(content: String): Boolean {
        val lastOpenDevin = content.lastIndexOf("<devin")
        val lastCloseDevin = content.lastIndexOf("</devin>")

        // Check for partial opening tags
        // Only treat partial "<devin" as incomplete devin block.
        // Do NOT match a dangling "<" because HTML/XML streams often end mid-tag.
        val partialDevinPattern = Regex("<d(?:e(?:v(?:i(?:n)?)?)?)?$")
        val hasPartialTag = partialDevinPattern.containsMatchIn(content)

        return lastOpenDevin > lastCloseDevin || hasPartialTag
    }

    /**
     * Process content and extract thinking blocks.
     * Returns a pair of (contentWithoutThinking, thinkingContent)
     *
     * Handles both complete and streaming <think>...</think> blocks.
     */
    protected fun extractThinkingContent(content: String): ThinkingExtractionResult {
        val result = ThinkingExtractionResult()
        var remaining = content

        // Handle complete <think>...</think> blocks
        val completeThinkPattern = Regex("<think>([\\s\\S]*?)</think>")
        val matches = completeThinkPattern.findAll(remaining)
        for (match in matches) {
            result.thinkingContent.append(match.groupValues[1])
            result.hasCompleteThinkBlock = true
        }
        remaining = remaining.replace(completeThinkPattern, "")

        // Check for incomplete <think> block at the end
        val openThinkIndex = remaining.lastIndexOf("<think>")
        if (openThinkIndex != -1) {
            val closeThinkIndex = remaining.indexOf("</think>", openThinkIndex)
            if (closeThinkIndex == -1) {
                // Incomplete think block - extract content after <think>
                val thinkContent = remaining.substring(openThinkIndex + 7)
                result.thinkingContent.append(thinkContent)
                result.hasIncompleteThinkBlock = true
                remaining = remaining.substring(0, openThinkIndex)
            }
        }

        // Check for partial <think or </think tags
        val partialThinkPattern = Regex("<(?:t(?:h(?:i(?:n(?:k)?)?)?)?)?$|</(?:t(?:h(?:i(?:n(?:k)?)?)?)?)?$")
        if (partialThinkPattern.containsMatchIn(remaining)) {
            result.hasPartialTag = true
            remaining = remaining.replace(partialThinkPattern, "")
        }

        result.contentWithoutThinking = remaining
        return result
    }

    /**
     * Result of thinking content extraction
     */
    protected class ThinkingExtractionResult {
        var contentWithoutThinking: String = ""
        val thinkingContent = StringBuilder()
        var hasCompleteThinkBlock = false
        var hasIncompleteThinkBlock = false
        var hasPartialTag = false

        val hasThinking: Boolean
            get() = thinkingContent.isNotEmpty()
    }
    
    /**
     * Calculate similarity between two strings for repeat detection
     */
    protected fun calculateSimilarity(str1: String, str2: String): Double {
        if (str1.isEmpty() || str2.isEmpty()) return 0.0
        
        val words1 = str1.lowercase().split(Regex("\\s+"))
        val words2 = str2.lowercase().split(Regex("\\s+"))
        
        val commonWords = words1.intersect(words2.toSet())
        val totalWords = maxOf(words1.size, words2.size)
        
        return if (totalWords > 0) commonWords.size.toDouble() / totalWords else 0.0
    }
    
    /**
     * Clean up excessive newlines in content
     */
    protected fun cleanNewlines(content: String): String {
        return content.replace(Regex("\n{3,}"), "\n\n")
    }
    
    override fun renderLLMResponseStart() {
        reasoningBuffer.clear()
        isInDevinBlock = false
    }
    
    /**
     * Default implementation for LLM response end with similarity checking
     */
    override fun renderLLMResponseEnd() {
        val currentReasoning = reasoningBuffer.toString().trim()
        val similarity = calculateSimilarity(currentReasoning, lastIterationReasoning)
        
        if (similarity > 0.95 && lastIterationReasoning.isNotEmpty()) {
            consecutiveRepeats++
            if (consecutiveRepeats >= 2) {
                renderRepeatAnalysisWarning()
            }
        } else {
            consecutiveRepeats = 0
        }
        
        lastIterationReasoning = currentReasoning
    }
    
    /**
     * Render warning for repetitive analysis - can be overridden by subclasses
     */
    protected open fun renderRepeatAnalysisWarning() {
        renderError("Agent appears to be repeating similar analysis...")
    }
}
