package cc.unitmesh.agent.chatdb

/**
 * Platform-specific NLP tokenizer for keyword extraction.
 * 
 * On JVM, this uses MyNLP (https://github.com/jimichan/mynlp) for Chinese tokenization.
 * On other platforms (JS, WASM, iOS, Android), this falls back to simple regex-based tokenization.
 */
expect object NlpTokenizer {
    /**
     * Extract keywords from natural language query using NLP tokenization.
     * Supports both English and Chinese text.
     * 
     * @param query The natural language query to tokenize
     * @param stopWords Set of words to filter out from results
     * @return List of extracted keywords
     */
    fun extractKeywords(query: String, stopWords: Set<String>): List<String>
}
