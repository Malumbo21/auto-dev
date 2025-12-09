package cc.unitmesh.agent.chatdb

import cc.unitmesh.agent.database.ColumnSchema
import cc.unitmesh.agent.database.DatabaseSchema
import cc.unitmesh.agent.database.TableSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Tests for SchemaLinker - keyword-based schema linking for Text2SQL
 */
class SchemaLinkerTest {

    private val schemaLinker = SchemaLinker()

    // ============= Keyword Extraction Tests =============

    @Test
    fun testExtractKeywordsBasic() {
        val keywords = schemaLinker.extractKeywords("Show me all users")

        // "show" and "me" are stop words, "all" is also filtered
        assertTrue(keywords.contains("users"))
    }

    @Test
    fun testExtractKeywordsFiltersStopWords() {
        val keywords = schemaLinker.extractKeywords("Show me the top 10 users with the most orders")

        // Stop words should be filtered (show, me, the, top, most are in stopWords)
        assertTrue(keywords.none { it in listOf("show", "me", "the", "top", "most") })
        // Important words should remain
        assertTrue(keywords.contains("users"))
        assertTrue(keywords.contains("orders"))
    }

    @Test
    fun testExtractKeywordsFiltersShortWords() {
        val keywords = schemaLinker.extractKeywords("Get a list of all items")

        // Short words (length <= 2) should be filtered
        assertTrue(keywords.none { it.length <= 2 })
    }

    @Test
    fun testExtractKeywordsLowercase() {
        val keywords = schemaLinker.extractKeywords("Show USERS and ORDERS")

        assertTrue(keywords.contains("users"))
        assertTrue(keywords.contains("orders"))
        assertTrue(keywords.none { word -> word.any { c -> c.isUpperCase() } })
    }

    // ============= Schema Linking Tests =============

    private fun createTestSchema(): DatabaseSchema {
        return DatabaseSchema(
            tables = listOf(
                TableSchema(
                    name = "users",
                    columns = listOf(
                        ColumnSchema("id", "INT", false, null),
                        ColumnSchema("name", "VARCHAR", false, null),
                        ColumnSchema("email", "VARCHAR", false, null),
                        ColumnSchema("created_at", "DATETIME", false, null)
                    )
                ),
                TableSchema(
                    name = "orders",
                    columns = listOf(
                        ColumnSchema("id", "INT", false, null),
                        ColumnSchema("user_id", "INT", false, null),
                        ColumnSchema("total", "DECIMAL", false, null),
                        ColumnSchema("status", "VARCHAR", false, null)
                    )
                ),
                TableSchema(
                    name = "products",
                    columns = listOf(
                        ColumnSchema("id", "INT", false, null),
                        ColumnSchema("name", "VARCHAR", false, null),
                        ColumnSchema("price", "DECIMAL", false, null),
                        ColumnSchema("category", "VARCHAR", false, null)
                    )
                )
            )
        )
    }

    @Test
    fun testLinkSchemaFindsRelevantTables() {
        val schema = createTestSchema()

        val result = schemaLinker.link("Show me all users with their orders", schema)

        assertTrue(result.relevantTables.contains("users"))
        assertTrue(result.relevantTables.contains("orders"))
    }

    @Test
    fun testLinkSchemaFindsRelevantColumns() {
        val schema = createTestSchema()

        val result = schemaLinker.link("Show user names and order totals", schema)

        assertTrue(result.relevantColumns.any { it.contains("name") })
    }

    @Test
    fun testLinkSchemaWithNoMatches() {
        val schema = createTestSchema()

        val result = schemaLinker.link("Show me the weather forecast", schema)

        // Should still return a result - fallback includes all tables
        assertNotNull(result)
        assertTrue(result.relevantTables.isNotEmpty())
    }

    @Test
    fun testLinkSchemaWithPartialMatch() {
        val schema = DatabaseSchema(
            tables = listOf(
                TableSchema(
                    name = "user_accounts",
                    columns = listOf(
                        ColumnSchema("id", "INT", false, null),
                        ColumnSchema("username", "VARCHAR", false, null),
                        ColumnSchema("email", "VARCHAR", false, null)
                    )
                ),
                TableSchema(
                    name = "customer_orders",
                    columns = listOf(
                        ColumnSchema("id", "INT", false, null),
                        ColumnSchema("customer_id", "INT", false, null),
                        ColumnSchema("amount", "DECIMAL", false, null)
                    )
                )
            )
        )

        val result = schemaLinker.link("Show me all users", schema)

        // Should find user_accounts due to partial match
        assertTrue(result.relevantTables.any { it.contains("user") })
    }

    // ============= Edge Cases =============

    @Test
    fun testExtractKeywordsEmptyQuery() {
        val keywords = schemaLinker.extractKeywords("")

        assertTrue(keywords.isEmpty())
    }

    @Test
    fun testLinkSchemaEmptySchema() {
        val schema = DatabaseSchema(tables = emptyList())
        val result = schemaLinker.link("Show me all users", schema)

        assertTrue(result.relevantTables.isEmpty())
        assertTrue(result.relevantColumns.isEmpty())
    }

    @Test
    fun testLinkSchemaWithSpecialCharacters() {
        val schema = createTestSchema()

        val result = schemaLinker.link("Show me users' emails!", schema)

        assertTrue(result.relevantTables.contains("users"))
    }

    @Test
    fun testLinkSchemaWithNumbers() {
        val schema = createTestSchema()

        val result = schemaLinker.link("Show top 10 users with orders over 100", schema)

        assertTrue(result.relevantTables.contains("users"))
        assertTrue(result.relevantTables.contains("orders"))
    }

    // ============= Schema Linking Result Tests =============

    @Test
    fun testSchemaLinkingResultDescription() {
        val result = SchemaLinkingResult(
            relevantTables = listOf("users", "orders"),
            relevantColumns = listOf("users.name", "orders.total"),
            keywords = listOf("users", "orders"),
            confidence = 0.9
        )

        assertEquals(2, result.relevantTables.size)
        assertEquals(2, result.relevantColumns.size)
        assertEquals(2, result.keywords.size)
        assertEquals(0.9, result.confidence)
    }
}

