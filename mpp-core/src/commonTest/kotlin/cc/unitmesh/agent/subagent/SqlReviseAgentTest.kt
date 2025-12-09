package cc.unitmesh.agent.subagent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for SqlReviseAgent data classes and schema
 */
class SqlReviseAgentTest {

    // ============= Input Validation Tests =============

    @Test
    fun testSqlRevisionInputCreation() {
        val input = SqlRevisionInput(
            originalQuery = "Show me all users",
            failedSql = "SELECT * FROM user",
            errorMessage = "Table 'user' doesn't exist",
            schemaDescription = "Tables: users (id, name, email)",
            maxAttempts = 3
        )

        assertEquals("Show me all users", input.originalQuery)
        assertEquals("SELECT * FROM user", input.failedSql)
        assertEquals("Table 'user' doesn't exist", input.errorMessage)
        assertEquals("Tables: users (id, name, email)", input.schemaDescription)
        assertEquals(3, input.maxAttempts)
    }

    @Test
    fun testSqlRevisionInputDefaultMaxAttempts() {
        val input = SqlRevisionInput(
            originalQuery = "Show me all users",
            failedSql = "SELECT * FROM user",
            errorMessage = "Table 'user' doesn't exist",
            schemaDescription = "Tables: users"
        )

        assertEquals(3, input.maxAttempts)
    }

    // ============= Schema Tests =============

    @Test
    fun testSqlReviseAgentSchemaExampleUsage() {
        val example = SqlReviseAgentSchema.getExampleUsage("sql-revise")

        assertTrue(example.contains("/sql-revise"))
        assertTrue(example.contains("originalQuery="))
        assertTrue(example.contains("failedSql="))
        assertTrue(example.contains("errorMessage="))
    }

    @Test
    fun testSqlReviseAgentSchemaToJsonSchema() {
        val jsonSchema = SqlReviseAgentSchema.toJsonSchema()

        assertNotNull(jsonSchema)
        val schemaString = jsonSchema.toString()
        assertTrue(schemaString.contains("originalQuery"))
        assertTrue(schemaString.contains("failedSql"))
        assertTrue(schemaString.contains("errorMessage"))
        assertTrue(schemaString.contains("schemaDescription"))
        assertTrue(schemaString.contains("maxAttempts"))
    }

    @Test
    fun testSqlReviseAgentSchemaDescription() {
        val description = SqlReviseAgentSchema.description

        assertTrue(description.contains("Revise"))
        assertTrue(description.contains("SQL"))
    }

    // ============= Validation Result Tests =============

    @Test
    fun testSqlValidationResultSuccess() {
        val result = SqlValidationResult(
            isValid = true,
            errors = emptyList(),
            warnings = listOf("Using SELECT *")
        )

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.warnings.size)
        assertEquals("Using SELECT *", result.warnings[0])
    }

    @Test
    fun testSqlValidationResultFailure() {
        val result = SqlValidationResult(
            isValid = false,
            errors = listOf("Syntax error near 'FORM'"),
            warnings = emptyList()
        )

        assertEquals(false, result.isValid)
        assertEquals("Syntax error near 'FORM'", result.errors[0])
        assertTrue(result.warnings.isEmpty())
    }

    // ============= Edge Cases =============

    @Test
    fun testSqlRevisionInputWithEmptySchema() {
        val input = SqlRevisionInput(
            originalQuery = "Show me all users",
            failedSql = "SELECT * FROM users",
            errorMessage = "Unknown error",
            schemaDescription = ""
        )

        assertEquals("", input.schemaDescription)
    }

    @Test
    fun testSqlRevisionInputWithComplexQuery() {
        val complexSql = """
            SELECT u.name, COUNT(o.id) as order_count
            FROM users u
            LEFT JOIN orders o ON u.id = o.user_id
            WHERE u.created_at > '2023-01-01'
            GROUP BY u.name
            HAVING COUNT(o.id) > 5
            ORDER BY order_count DESC
            LIMIT 10
        """.trimIndent()

        val input = SqlRevisionInput(
            originalQuery = "Show top 10 users with most orders since 2023",
            failedSql = complexSql,
            errorMessage = "Column 'created_at' not found",
            schemaDescription = "Tables: users (id, name, created_date), orders (id, user_id, total)"
        )

        assertTrue(input.failedSql.contains("LEFT JOIN"))
        assertTrue(input.failedSql.contains("GROUP BY"))
        assertTrue(input.failedSql.contains("HAVING"))
    }

    @Test
    fun testSqlValidationResultWithMultipleWarnings() {
        val result = SqlValidationResult(
            isValid = true,
            errors = emptyList(),
            warnings = listOf(
                "Using SELECT *",
                "No LIMIT clause",
                "Consider adding index on user_id"
            )
        )

        assertTrue(result.isValid)
        assertEquals(3, result.warnings.size)
    }
}

