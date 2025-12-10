package cc.unitmesh.agent.chatdb

import cc.unitmesh.agent.database.DatabaseSchema

/**
 * Schema Linker - Abstract base class for Text2SQL schema linking
 *
 * This class finds relevant tables and columns based on natural language queries.
 * Different implementations can use different strategies:
 * - KeywordSchemaLinker: Keyword matching and fuzzy matching
 * - LlmSchemaLinker: LLM-based keyword extraction and schema linking
 * - VectorSchemaLinker: Vector similarity search using embeddings (future)
 */
abstract class SchemaLinker {

    /**
     * Link natural language query to relevant schema elements
     */
    abstract suspend fun link(query: String, schema: DatabaseSchema): SchemaLinkingResult

    /**
     * Extract keywords from natural language query
     */
    abstract suspend fun extractKeywords(query: String): List<String>

    companion object {
        /**
         * Common SQL keywords to filter out
         */
        val STOP_WORDS = setOf(
            "select", "from", "where", "and", "or", "not", "in", "is", "null",
            "order", "by", "group", "having", "limit", "offset", "join", "on",
            "left", "right", "inner", "outer", "cross", "union", "all", "distinct",
            "as", "asc", "desc", "between", "like", "exists", "case", "when", "then",
            "else", "end", "count", "sum", "avg", "min", "max", "the", "a", "an",
            "show", "me", "get", "find", "list", "display", "give", "what", "which",
            "how", "many", "much", "all", "each", "every", "any", "some", "most",
            "top", "first", "last", "recent", "latest", "oldest", "highest", "lowest",
            "total", "average", "number", "amount", "value", "data", "information"
        )

        /**
         * Calculate Levenshtein distance between two strings
         */
        fun levenshteinDistance(s1: String, s2: String): Int {
            val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
            for (i in 0..s1.length) dp[i][0] = i
            for (j in 0..s2.length) dp[0][j] = j
            for (i in 1..s1.length) {
                for (j in 1..s2.length) {
                    val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                    dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                }
            }
            return dp[s1.length][s2.length]
        }

        /**
         * Simple fuzzy matching using edit distance threshold
         */
        fun fuzzyMatch(s1: String, s2: String): Boolean {
            if (kotlin.math.abs(s1.length - s2.length) > 3) return false
            val distance = levenshteinDistance(s1, s2)
            val threshold = kotlin.math.min(s1.length, s2.length) / 3
            return distance <= threshold
        }
    }
}

