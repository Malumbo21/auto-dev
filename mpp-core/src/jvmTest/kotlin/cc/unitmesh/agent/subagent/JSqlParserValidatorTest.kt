package cc.unitmesh.agent.subagent

import org.junit.Test
import kotlin.test.*

/**
 * Tests for JSqlParserValidator - JVM-specific SQL validation using JSqlParser
 */
class JSqlParserValidatorTest {

    private val validator = JSqlParserValidator()

    // ============= Basic Validation Tests =============

    @Test
    fun testValidSelectQuery() {
        val result = validator.validate("SELECT * FROM users WHERE age > 18")

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun testValidSelectWithJoin() {
        val sql = """
            SELECT u.name, o.total
            FROM users u
            JOIN orders o ON u.id = o.user_id
            WHERE o.total > 100
        """.trimIndent()

        val result = validator.validate(sql)

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun testInvalidSyntax() {
        val result = validator.validate("SELECT * FORM users") // Typo: FORM instead of FROM

        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun testEmptySql() {
        val result = validator.validate("")

        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun testBlankSql() {
        val result = validator.validate("   ")

        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
    }

    // ============= Warning Tests =============

    @Test
    fun testSelectStarWarning() {
        val result = validator.validate("SELECT * FROM users")

        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.contains("SELECT *") })
    }

    @Test
    fun testUpdateWithoutWhereWarning() {
        val result = validator.validate("UPDATE users SET status = 'inactive'")

        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.contains("WHERE") })
    }

    @Test
    fun testDeleteWithoutWhereWarning() {
        val result = validator.validate("DELETE FROM users")

        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.contains("WHERE") })
    }

    @Test
    fun testSafeUpdateNoWarning() {
        val result = validator.validate("UPDATE users SET status = 'active' WHERE id = 123")

        assertTrue(result.isValid)
        assertFalse(result.warnings.any { it.contains("UPDATE/DELETE without WHERE") })
    }

    @Test
    fun testSafeDeleteNoWarning() {
        val result = validator.validate("DELETE FROM users WHERE id = 123")

        assertTrue(result.isValid)
        assertFalse(result.warnings.any { it.contains("UPDATE/DELETE without WHERE") })
    }

    // ============= Complex Query Tests =============

    @Test
    fun testComplexSelectWithSubquery() {
        val sql = """
            SELECT * FROM users
            WHERE id IN (SELECT user_id FROM orders WHERE total > 1000)
        """.trimIndent()

        val result = validator.validate(sql)

        assertTrue(result.isValid)
    }

    @Test
    fun testSelectWithGroupByAndHaving() {
        val sql = """
            SELECT u.name, COUNT(o.id) as order_count
            FROM users u
            LEFT JOIN orders o ON u.id = o.user_id
            GROUP BY u.name
            HAVING COUNT(o.id) > 5
            ORDER BY order_count DESC
        """.trimIndent()

        val result = validator.validate(sql)

        assertTrue(result.isValid)
    }

    @Test
    fun testInsertStatement() {
        val sql = "INSERT INTO users (name, email) VALUES ('Alice', 'alice@example.com')"

        val result = validator.validate(sql)

        assertTrue(result.isValid)
    }

    // ============= ValidateAndParse Tests =============

    @Test
    fun testValidateAndParseValid() {
        val (result, statement) = validator.validateAndParse("SELECT * FROM users")

        assertTrue(result.isValid)
        assertNotNull(statement)
    }

    @Test
    fun testValidateAndParseInvalid() {
        val (result, statement) = validator.validateAndParse("SELECT * FORM users")

        assertFalse(result.isValid)
        assertNull(statement)
    }

    // ============= Edge Cases =============

    @Test
    fun testMultipleStatements() {
        // JSqlParser may handle this differently
        val sql = "SELECT * FROM users; SELECT * FROM orders"

        val result = validator.validate(sql)
        // Just verify it doesn't crash - behavior may vary
        assertNotNull(result)
    }

    @Test
    fun testSqlWithComments() {
        val sql = """
            -- This is a comment
            SELECT * FROM users WHERE id = 1
        """.trimIndent()

        val result = validator.validate(sql)

        assertTrue(result.isValid)
    }
}

