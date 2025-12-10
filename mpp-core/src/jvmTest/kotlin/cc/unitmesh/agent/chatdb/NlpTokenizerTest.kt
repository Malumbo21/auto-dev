package cc.unitmesh.agent.chatdb

import org.junit.Test
import kotlin.test.assertTrue

/**
 * Test NLP tokenizer functionality on JVM using MyNLP.
 */
class NlpTokenizerTest {
    
    private val stopWords = SchemaLinker.STOP_WORDS
    
    @Test
    fun `test Chinese tokenization with MyNLP`() {
        val query = "查询所有用户的订单金额"
        val keywords = NlpTokenizer.extractKeywords(query, stopWords)
        
        println("Query: $query")
        println("Keywords: ${keywords.joinToString(", ")}")
        
        // MyNLP should extract meaningful words like "查询", "用户", "订单", "金额"
        // instead of just individual characters
        assertTrue(keywords.isNotEmpty(), "Should extract keywords from Chinese text")
        
        // Check that we get proper word segmentation (not just single characters)
        val multiCharWords = keywords.filter { it.length > 1 }
        assertTrue(multiCharWords.isNotEmpty(), "Should have multi-character words from Chinese segmentation")
    }
    
    @Test
    fun `test mixed Chinese and English tokenization`() {
        val query = "查询user表中的order数据"
        val keywords = NlpTokenizer.extractKeywords(query, stopWords)
        
        println("Query: $query")
        println("Keywords: ${keywords.joinToString(", ")}")
        
        // Should extract both Chinese words and English words
        assertTrue(keywords.isNotEmpty(), "Should extract keywords from mixed text")
        assertTrue(keywords.any { it.matches(Regex("[a-z]+")) }, "Should contain English words")
    }
    
    @Test
    fun `test English only tokenization`() {
        val query = "Show me the top 10 customers by order amount"
        val keywords = NlpTokenizer.extractKeywords(query, stopWords)
        
        println("Query: $query")
        println("Keywords: ${keywords.joinToString(", ")}")
        
        // Should extract English words, filtering out stop words
        assertTrue(keywords.isNotEmpty(), "Should extract keywords from English text")
        assertTrue(keywords.contains("customers") || keywords.contains("amount"), 
            "Should contain meaningful English words")
    }
    
    @Test
    fun `compare NLP vs Fallback tokenization for Chinese`() {
        val query = "统计每个部门的员工人数"
        
        val nlpKeywords = NlpTokenizer.extractKeywords(query, stopWords)
        val fallbackKeywords = FallbackNlpTokenizer.extractKeywords(query, stopWords)
        
        println("Query: $query")
        println("NLP Keywords:      ${nlpKeywords.joinToString(", ")}")
        println("Fallback Keywords: ${fallbackKeywords.joinToString(", ")}")
        
        // NLP should produce better segmentation than fallback
        // Fallback will include single characters, NLP should have proper words
        val nlpMultiCharWords = nlpKeywords.filter { it.length > 1 }
        val fallbackMultiCharWords = fallbackKeywords.filter { it.length > 1 }
        
        println("NLP multi-char words: ${nlpMultiCharWords.size}")
        println("Fallback multi-char words: ${fallbackMultiCharWords.size}")
        
        // NLP should have more meaningful multi-character words
        // (fallback just adds the whole string plus individual chars)
        assertTrue(nlpMultiCharWords.isNotEmpty(), "NLP should produce multi-character words")
    }
    
    @Test
    fun `test database related Chinese queries`() {
        val queries = listOf(
            "查询用户表中年龄大于30的用户",
            "统计2024年每月的销售额",
            "显示所有未支付的订单",
            "找出购买金额最高的前10个客户"
        )
        
        for (query in queries) {
            val keywords = NlpTokenizer.extractKeywords(query, stopWords)
            println("Query: $query")
            println("Keywords: ${keywords.joinToString(", ")}")
            println()
            
            assertTrue(keywords.isNotEmpty(), "Should extract keywords from: $query")
        }
    }
}

