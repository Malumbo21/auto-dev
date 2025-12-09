package cc.unitmesh.agent.chatdb

import cc.unitmesh.agent.database.DatabaseSchema
import cc.unitmesh.agent.database.TableSchema

/**
 * Schema Linker - Keyword-based schema linking for Text2SQL
 * 
 * This class finds relevant tables and columns based on natural language queries.
 * It uses keyword matching and fuzzy matching to identify relevant schema elements.
 * 
 * Future enhancements:
 * - Vector similarity search using embeddings
 * - LLM-based schema linking
 * - Historical query pattern matching
 */
class SchemaLinker {
    
    /**
     * Common SQL keywords to filter out
     */
    private val stopWords = setOf(
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
     * Link natural language query to relevant schema elements
     */
    fun link(query: String, schema: DatabaseSchema): SchemaLinkingResult {
        val keywords = extractKeywords(query)
        val relevantTables = mutableListOf<String>()
        val relevantColumns = mutableListOf<String>()
        var totalScore = 0.0
        var matchCount = 0
        
        for (table in schema.tables) {
            val tableScore = calculateTableRelevance(table, keywords)
            if (tableScore > 0) {
                relevantTables.add(table.name)
                totalScore += tableScore
                matchCount++
                
                // Find relevant columns in this table
                for (column in table.columns) {
                    val columnScore = calculateColumnRelevance(column.name, column.comment, keywords)
                    if (columnScore > 0) {
                        relevantColumns.add("${table.name}.${column.name}")
                    }
                }
            }
        }
        
        // If no tables matched, include all tables (fallback)
        if (relevantTables.isEmpty()) {
            relevantTables.addAll(schema.tables.map { it.name })
        }
        
        val confidence = if (matchCount > 0) (totalScore / matchCount).coerceIn(0.0, 1.0) else 0.0
        
        return SchemaLinkingResult(
            relevantTables = relevantTables,
            relevantColumns = relevantColumns,
            keywords = keywords,
            confidence = confidence
        )
    }
    
    /**
     * Extract keywords from natural language query
     */
    fun extractKeywords(query: String): List<String> {
        return query.lowercase()
            .replace(Regex("[^a-z0-9\\s_]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in stopWords }
            .distinct()
    }
    
    /**
     * Calculate relevance score for a table
     */
    private fun calculateTableRelevance(table: TableSchema, keywords: List<String>): Double {
        var score = 0.0
        val tableName = table.name.lowercase()
        val tableComment = table.comment?.lowercase() ?: ""
        
        for (keyword in keywords) {
            // Exact match in table name
            if (tableName == keyword) {
                score += 1.0
            }
            // Partial match in table name
            else if (tableName.contains(keyword) || keyword.contains(tableName)) {
                score += 0.7
            }
            // Match in table comment
            else if (tableComment.contains(keyword)) {
                score += 0.5
            }
            // Fuzzy match (Levenshtein distance)
            else if (fuzzyMatch(tableName, keyword)) {
                score += 0.3
            }
            
            // Check column names
            for (column in table.columns) {
                val colName = column.name.lowercase()
                if (colName == keyword || colName.contains(keyword)) {
                    score += 0.4
                }
            }
        }
        
        return score
    }
    
    /**
     * Calculate relevance score for a column
     */
    private fun calculateColumnRelevance(columnName: String, comment: String?, keywords: List<String>): Double {
        var score = 0.0
        val colName = columnName.lowercase()
        val colComment = comment?.lowercase() ?: ""
        
        for (keyword in keywords) {
            if (colName == keyword) score += 1.0
            else if (colName.contains(keyword)) score += 0.7
            else if (colComment.contains(keyword)) score += 0.5
            else if (fuzzyMatch(colName, keyword)) score += 0.3
        }
        
        return score
    }
    
    /**
     * Simple fuzzy matching using edit distance threshold
     */
    private fun fuzzyMatch(s1: String, s2: String): Boolean {
        if (kotlin.math.abs(s1.length - s2.length) > 3) return false
        val distance = levenshteinDistance(s1, s2)
        val threshold = kotlin.math.min(s1.length, s2.length) / 3
        return distance <= threshold
    }
    
    /**
     * Calculate Levenshtein distance between two strings
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
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
}

