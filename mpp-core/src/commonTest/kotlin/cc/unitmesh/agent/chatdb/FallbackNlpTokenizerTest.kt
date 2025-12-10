package cc.unitmesh.agent.chatdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Comprehensive tests for FallbackNlpTokenizer.
 * Tests cover:
 * 1. Porter Stemmer for English morphological normalization
 * 2. Bi-directional Maximum Matching (BiMM) for Chinese segmentation
 * 3. RAKE keyword extraction algorithm
 * 4. SemVer and CamelCase handling
 */
class FallbackNlpTokenizerTest {

    // ==================== Porter Stemmer Tests ====================

    @Test
    fun `Porter Stemmer should reduce processing to process`() {
        val tokens = FallbackNlpTokenizer.tokenize("processing")
        assertEquals(1, tokens.size)
        assertEquals("process", tokens[0].text)
    }

    @Test
    fun `Porter Stemmer should reduce processed to process`() {
        val tokens = FallbackNlpTokenizer.tokenize("processed")
        assertEquals(1, tokens.size)
        assertEquals("process", tokens[0].text)
    }

    @Test
    fun `Porter Stemmer should handle various English word forms`() {
        // Test various word forms that should stem to similar roots
        val testCases = mapOf(
            "running" to "run",
            "runs" to "run",
            "runner" to "runner", // noun, different handling
            "cats" to "cat",
            "playing" to "plai", // Porter stemmer result
            "played" to "plai",
            "happily" to "happili",
            "happiness" to "happi"
        )

        testCases.forEach { (input, expectedStem) ->
            val tokens = FallbackNlpTokenizer.tokenize(input)
            assertEquals(1, tokens.size, "Expected 1 token for '$input'")
            assertEquals(expectedStem, tokens[0].text, "Expected '$input' to stem to '$expectedStem'")
        }
    }

    @Test
    fun `Porter Stemmer should unify related words for better recall`() {
        // Words like "processing", "processed", "process" should all map to "process"
        val words = listOf("processing", "processed", "process")
        val stems = words.map { FallbackNlpTokenizer.tokenize(it)[0].text }

        // All should be "process"
        assertTrue(stems.all { it == "process" }, "All variations should stem to 'process', got: $stems")
    }

    @Test
    fun `Porter Stemmer should preserve short words`() {
        // Words shorter than 3 characters should be preserved
        val tokens = FallbackNlpTokenizer.tokenize("go do be")
        assertEquals(3, tokens.size)
        assertEquals("go", tokens[0].text)
        assertEquals("do", tokens[1].text)
        assertEquals("be", tokens[2].text)
    }

    // ==================== Chinese BiMM Segmentation Tests ====================

    @Test
    fun `BiMM should segment Chinese text with dictionary words`() {
        val tokens = FallbackNlpTokenizer.tokenize("人工智能")
        // Should recognize "人工智能" as a single word
        val chineseTokens = tokens.filter { it.type == FallbackNlpTokenizer.TokenType.CHINESE }
        assertTrue(chineseTokens.any { it.text == "人工智能" }, "Should recognize '人工智能' as a word")
    }

    @Test
    fun `BiMM should segment common Chinese words`() {
        val tokens = FallbackNlpTokenizer.tokenize("数据库系统")
        val texts = tokens.map { it.text }

        // Should segment as "数据库" + "系统" (both in dictionary)
        assertTrue(texts.contains("数据库") || texts.contains("系统"),
            "Should recognize common tech terms, got: $texts")
    }

    @Test
    fun `BiMM should handle ambiguous segmentation with forward and reverse matching`() {
        // "南京市长江大桥" is a classic ambiguity example
        // FMM might give: 南京市/长江/大桥
        // RMM might give: 南京/市长/江大桥 (incorrect)
        // BiMM chooses based on fewer tokens (longer matches)
        val tokens = FallbackNlpTokenizer.tokenize("南京市长江大桥")
        val texts = tokens.map { it.text }

        // Should prefer segmentation with "南京市", "长江", "大桥" (all in dictionary)
        assertTrue(
            texts.contains("南京市") || texts.contains("长江") || texts.contains("大桥"),
            "Should handle ambiguous segmentation correctly, got: $texts"
        )
    }

    @Test
    fun `BiMM should fall back to character-by-character for unknown words`() {
        // Text with characters not in dictionary
        val tokens = FallbackNlpTokenizer.tokenize("啊吧唧")
        assertEquals(3, tokens.size, "Should segment unknown characters individually")
    }

    // ==================== Mixed Language Tests ====================

    @Test
    fun `should handle mixed English and Chinese text`() {
        val tokens = FallbackNlpTokenizer.tokenize("Hello世界Test测试")
        val englishTokens = tokens.filter { it.type == FallbackNlpTokenizer.TokenType.ENGLISH }
        val chineseTokens = tokens.filter { it.type == FallbackNlpTokenizer.TokenType.CHINESE }

        assertTrue(englishTokens.isNotEmpty(), "Should have English tokens")
        assertTrue(chineseTokens.isNotEmpty(), "Should have Chinese tokens")
    }

    // ==================== CamelCase Handling Tests ====================

    @Test
    fun `should split CamelCase words`() {
        val tokens = FallbackNlpTokenizer.tokenize("UserDao")
        val texts = tokens.map { it.text }

        assertTrue(texts.contains("user"), "Should extract 'user' from 'UserDao'")
        assertTrue(texts.contains("dao"), "Should extract 'dao' from 'UserDao'")
    }

    @Test
    fun `should split complex CamelCase with multiple words`() {
        val tokens = FallbackNlpTokenizer.tokenize("getUserNameById")
        val texts = tokens.map { it.text }

        assertTrue(texts.contains("get"), "Should extract 'get'")
        assertTrue(texts.contains("user"), "Should extract 'user'")
        assertTrue(texts.contains("name"), "Should extract 'name'")
        // "by" is short, "id" is short - they should still be present (as "by", "id")
        assertTrue(texts.contains("by"), "Should extract 'by'")
        assertTrue(texts.contains("id"), "Should extract 'id'")
    }

    @Test
    fun `should handle HTML style uppercase sequences`() {
        val tokens = FallbackNlpTokenizer.tokenize("HTMLParser")
        val texts = tokens.map { it.text }

        // Should split as "HTML" + "Parser" -> "html", "parser"
        assertTrue(texts.contains("html") || texts.any { it.contains("html") },
            "Should handle HTMLParser correctly, got: $texts")
    }

    @Test
    fun `should handle snake_case`() {
        val tokens = FallbackNlpTokenizer.tokenize("user_name_service")
        val texts = tokens.map { it.text }

        assertTrue(texts.contains("user"), "Should extract 'user' from snake_case")
        assertTrue(texts.contains("name"), "Should extract 'name' from snake_case")
        assertTrue(texts.contains("servic"), "Should extract 'servic' (stemmed) from snake_case")
    }

    // ==================== SemVer Handling Tests ====================

    @Test
    fun `should preserve semantic version numbers`() {
        val tokens = FallbackNlpTokenizer.tokenize("v1.2.3")
        assertEquals(1, tokens.size, "SemVer should be kept as single token")
        assertEquals("v1.2.3", tokens[0].text)
        assertEquals(FallbackNlpTokenizer.TokenType.CODE, tokens[0].type)
    }

    @Test
    fun `should preserve complex SemVer with prerelease`() {
        val tokens = FallbackNlpTokenizer.tokenize("1.0.0-alpha")
        assertEquals(1, tokens.size, "SemVer with prerelease should be single token")
        assertEquals("1.0.0-alpha", tokens[0].text)
    }

    @Test
    fun `should preserve SemVer with build metadata`() {
        val tokens = FallbackNlpTokenizer.tokenize("2.1.0+build.123")
        assertEquals(1, tokens.size, "SemVer with build metadata should be single token")
        assertEquals("2.1.0+build.123", tokens[0].text)
    }

    // ==================== RAKE Keyword Extraction Tests ====================

    @Test
    fun `RAKE should extract meaningful keywords from text`() {
        val keywords = FallbackNlpTokenizer.extractKeywords(
            "The quick brown fox jumps over the lazy dog",
            maxKeywords = 5
        )

        assertTrue(keywords.isNotEmpty(), "Should extract keywords")
        assertTrue(keywords.none { it in FallbackNlpTokenizer.StopWords.ENGLISH },
            "Keywords should not contain stop words")
    }

    @Test
    fun `RAKE should filter stop words`() {
        val keywords = FallbackNlpTokenizer.extractKeywords(
            "The is at which on and in to of for",
            maxKeywords = 10
        )

        assertTrue(keywords.isEmpty(), "Stop words only text should return empty keywords")
    }

    @Test
    fun `RAKE should rank keywords by co-occurrence`() {
        val keywords = FallbackNlpTokenizer.extractKeywords(
            "data processing pipeline data analysis data visualization",
            maxKeywords = 5
        )

        // "data" appears most frequently and co-occurs with many words
        assertTrue(keywords.contains("data"), "Frequent co-occurring word 'data' should be top keyword")
    }

    @Test
    fun `RAKE should handle Chinese text`() {
        val keywords = FallbackNlpTokenizer.extractKeywords(
            "人工智能技术在数据分析领域的应用",
            maxKeywords = 5
        )

        assertTrue(keywords.isNotEmpty(), "Should extract Chinese keywords")
        assertTrue(keywords.none { it in FallbackNlpTokenizer.StopWords.CHINESE },
            "Chinese keywords should not contain stop words")
    }

    @Test
    fun `RAKE should respect maxKeywords limit`() {
        val keywords = FallbackNlpTokenizer.extractKeywords(
            "apple banana cherry date elderberry fig grape honeydew kiwi lemon mango nectarine",
            maxKeywords = 3
        )

        assertTrue(keywords.size <= 3, "Should respect maxKeywords limit")
    }

    // ==================== Legacy Method Tests ====================

    @Test
    fun `legacy extractKeywords should work with custom stop words`() {
        val customStopWords = setOf("quick", "lazy")
        val keywords = FallbackNlpTokenizer.extractKeywords(
            "The quick brown fox jumps over the lazy dog",
            customStopWords
        )

        assertTrue(keywords.none { it == "quick" }, "Should filter custom stop word 'quick'")
        assertTrue(keywords.none { it == "lazy" }, "Should filter custom stop word 'lazy'")
    }

    @Test
    fun `legacy extractKeywords should return distinct tokens`() {
        val keywords = FallbackNlpTokenizer.extractKeywords(
            "test test test data data",
            emptySet()
        )

        assertEquals(keywords.distinct().size, keywords.size, "Keywords should be distinct")
    }

    // ==================== Edge Cases ====================

    @Test
    fun `should handle empty string`() {
        val tokens = FallbackNlpTokenizer.tokenize("")
        assertTrue(tokens.isEmpty(), "Empty string should return empty tokens")

        val keywords = FallbackNlpTokenizer.extractKeywords("", maxKeywords = 5)
        assertTrue(keywords.isEmpty(), "Empty string should return empty keywords")
    }

    @Test
    fun `should handle whitespace only`() {
        val tokens = FallbackNlpTokenizer.tokenize("   \t\n  ")
        assertTrue(tokens.isEmpty(), "Whitespace only should return empty tokens")
    }

    @Test
    fun `should handle special characters`() {
        val tokens = FallbackNlpTokenizer.tokenize("hello! @world# \$test%")
        assertTrue(tokens.isNotEmpty(), "Should extract tokens from text with special chars")
    }

    @Test
    fun `should handle numbers mixed with text`() {
        val tokens = FallbackNlpTokenizer.tokenize("user123 test456")
        val texts = tokens.map { it.text }

        // Numbers should be separated from text
        assertTrue(texts.any { it.contains("user") || it == "user" },
            "Should handle alphanumeric text")
    }

    // ==================== Integration Tests ====================

    @Test
    fun `should handle realistic code query`() {
        val keywords = FallbackNlpTokenizer.extractKeywords(
            "How to implement UserService with database connection pooling?",
            maxKeywords = 10
        )

        assertTrue(keywords.isNotEmpty(), "Should extract keywords from code query")
        // Should contain stemmed versions of key terms
        assertTrue(
            keywords.any { it.contains("user") || it.contains("servic") || it.contains("databas") },
            "Should extract relevant programming terms, got: $keywords"
        )
    }

    @Test
    fun `should handle realistic Chinese tech query`() {
        val keywords = FallbackNlpTokenizer.extractKeywords(
            "如何使用数据库连接池优化系统性能？",
            maxKeywords = 10
        )

        assertTrue(keywords.isNotEmpty(), "Should extract keywords from Chinese query")
    }

    @Test
    fun `should handle mixed language tech query`() {
        val keywords = FallbackNlpTokenizer.extractKeywords(
            "如何实现UserService的数据库connection pooling?",
            maxKeywords = 10
        )

        assertTrue(keywords.isNotEmpty(), "Should extract keywords from mixed language query")
    }

    // ==================== Token Type Tests ====================

    @Test
    fun `should correctly identify token types`() {
        val tokens = FallbackNlpTokenizer.tokenize("Hello世界v1.2.3")

        val englishTokens = tokens.filter { it.type == FallbackNlpTokenizer.TokenType.ENGLISH }
        val chineseTokens = tokens.filter { it.type == FallbackNlpTokenizer.TokenType.CHINESE }
        val codeTokens = tokens.filter { it.type == FallbackNlpTokenizer.TokenType.CODE }

        assertTrue(englishTokens.isNotEmpty(), "Should have English tokens")
        assertTrue(chineseTokens.isNotEmpty(), "Should have Chinese tokens")
        assertTrue(codeTokens.isNotEmpty(), "Should have CODE tokens (SemVer)")
    }

    // ==================== Performance Considerations ====================

    @Test
    fun `should handle long text efficiently`() {
        val longText = "data processing ".repeat(100) + "人工智能".repeat(50)

        // Simply verify it completes without timeout (test framework handles timeout)
        val keywords = FallbackNlpTokenizer.extractKeywords(longText, maxKeywords = 10)

        assertTrue(keywords.isNotEmpty(), "Should extract keywords from long text")
    }
}

