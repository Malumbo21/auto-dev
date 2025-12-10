package cc.unitmesh.agent.chatdb

/**
 * iOS implementation of NlpTokenizer.
 * Uses the fallback regex-based tokenization since MyNLP is JVM-only.
 * 
 * TODO: Consider using iOS NaturalLanguage framework for better Chinese tokenization.
 */
actual object NlpTokenizer {
    /**
     * Extract keywords from natural language query using simple tokenization.
     * Supports both English and Chinese text.
     * 
     * @param query The natural language query to tokenize
     * @param stopWords Set of words to filter out from results
     * @return List of extracted keywords
     */
    actual fun extractKeywords(query: String, stopWords: Set<String>): List<String> {
        return FallbackTokenizer.extractKeywords(query, stopWords)
    }
}

