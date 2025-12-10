package cc.unitmesh.agent.chatdb

/**
 * Fallback implementation for keyword extraction using simple regex-based tokenization.
 * This is used on platforms where NLP libraries are not available.
 */
object FallbackNlpTokenizer {
    /**
     * Extract keywords from natural language query using simple tokenization.
     * Supports both English and Chinese text.
     */
    fun extractKeywords(query: String, stopWords: Set<String>): List<String> {
        val keywords = mutableListOf<String>()

        // Extract English words
        val englishWords = query.lowercase()
            .replace(Regex("[^a-z0-9\\s_]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in stopWords }
        keywords.addAll(englishWords)

        // Extract Chinese characters/words (each Chinese character or common word)
        val chinesePattern = Regex("[\\u4e00-\\u9fa5]+")
        val chineseMatches = chinesePattern.findAll(query)
        for (match in chineseMatches) {
            val word = match.value
            keywords.add(word)
            // Also add individual characters for better matching
            if (word.length > 1) {
                word.forEach { char -> keywords.add(char.toString()) }
            }
        }

        return keywords.distinct()
    }
}