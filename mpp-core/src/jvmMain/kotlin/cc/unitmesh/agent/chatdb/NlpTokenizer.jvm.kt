package cc.unitmesh.agent.chatdb

import com.mayabot.nlp.segment.Lexers
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * JVM implementation of NlpTokenizer using MyNLP for Chinese tokenization.
 * 
 * MyNLP (https://github.com/jimichan/mynlp) provides high-quality Chinese word segmentation
 * which is essential for accurate keyword extraction from Chinese natural language queries.
 */
actual object NlpTokenizer {
    
    // Lazy initialization of the lexer to avoid startup overhead
    private val lexer by lazy {
        try {
            Lexers.core()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to initialize MyNLP lexer, falling back to simple tokenization" }
            null
        }
    }
    
    /**
     * Extract keywords from natural language query using MyNLP tokenization.
     * Supports both English and Chinese text.
     * 
     * For Chinese text, MyNLP provides proper word segmentation instead of
     * character-by-character splitting, which significantly improves matching accuracy.
     * 
     * @param query The natural language query to tokenize
     * @param stopWords Set of words to filter out from results
     * @return List of extracted keywords
     */
    actual fun extractKeywords(query: String, stopWords: Set<String>): List<String> {
        val currentLexer = lexer
        if (currentLexer == null) {
            // Fallback to simple tokenization if MyNLP initialization failed
            return FallbackNlpTokenizer.extractKeywords(query, stopWords)
        }
        
        return try {
            extractKeywordsWithMyNlp(query, stopWords, currentLexer)
        } catch (e: Exception) {
            logger.warn(e) { "MyNLP tokenization failed, falling back to simple tokenization" }
            FallbackNlpTokenizer.extractKeywords(query, stopWords)
        }
    }
    
    private fun extractKeywordsWithMyNlp(
        query: String, 
        stopWords: Set<String>,
        lexer: com.mayabot.nlp.segment.Lexer
    ): List<String> {
        val keywords = mutableListOf<String>()
        
        // Use MyNLP to tokenize the query
        val sentence = lexer.scan(query)
        
        for (term in sentence) {
            val word = term.word.lowercase()
            
            // Skip short words and stop words
            if (word.length <= 1) continue
            if (word in stopWords) continue
            
            // Skip pure punctuation
            if (word.all { !it.isLetterOrDigit() }) continue
            
            keywords.add(word)
        }
        
        // Also extract English words that might be missed by Chinese tokenizer
        val englishWords = query.lowercase()
            .replace(Regex("[^a-z0-9\\s_]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in stopWords && it !in keywords }
        keywords.addAll(englishWords)
        
        return keywords.distinct()
    }
}

