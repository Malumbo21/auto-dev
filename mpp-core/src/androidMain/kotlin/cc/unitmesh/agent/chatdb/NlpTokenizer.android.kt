package cc.unitmesh.agent.chatdb

/**
 * Android implementation of NlpTokenizer.
 * Uses the fallback regex-based tokenization since MyNLP is JVM-only
 * and may have compatibility issues on Android.
 * 
 * TODO: Consider using Android's BreakIterator or a lightweight NLP library for better tokenization.
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

