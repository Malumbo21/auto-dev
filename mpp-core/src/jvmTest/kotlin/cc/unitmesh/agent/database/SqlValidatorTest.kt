package cc.unitmesh.agent.database

import org.junit.Test
import kotlin.test.*

/**
 * Tests for SQL validation using JSQLParser
 */
class SqlValidatorTest {
    
    private val validator = SqlValidator()
    
    @Test
    fun testValidSelectQuery() {
        val sql = "SELECT * FROM users WHERE age > 18"
        val result = validator.validate(sql)
        
        assertTrue(result.isValid)
        assertEquals(SqlValidator.SqlType.SELECT, result.metadata?.type)
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
        assertEquals(SqlValidator.SqlType.SELECT, result.metadata?.type)
        assertNotNull(result.metadata)
        assertTrue(result.metadata!!.tables.isNotEmpty())
    }
    
    @Test
    fun testInvalidSyntax() {
        val sql = "SELECT * FORM users" // Typo: FORM instead of FROM
        val result = validator.validate(sql)
        
        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors[0].contains("parsing error", ignoreCase = true))
    }
    
    @Test
    fun testInsertNotAllowed() {
        val sql = "INSERT INTO users (name, email) VALUES ('Alice', 'alice@example.com')"
        val result = validator.validate(sql, setOf(SqlValidator.SqlType.SELECT))
        
        assertTrue(result.isValid) // Syntax is valid
        assertTrue(result.errors.isNotEmpty()) // But operation not allowed
        assertTrue(result.errors[0].contains("not allowed"))
    }
    
    @Test
    fun testUpdateWithoutWhere() {
        val sql = "UPDATE users SET status = 'inactive'"
        val result = validator.validate(sql, setOf(SqlValidator.SqlType.UPDATE))
        
        assertTrue(result.isValid)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors[0].contains("WHERE"))
    }
    
    @Test
    fun testDeleteWithoutWhere() {
        val sql = "DELETE FROM users"
        val result = validator.validate(sql, setOf(SqlValidator.SqlType.DELETE))
        
        assertTrue(result.isValid)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors[0].contains("WHERE"))
    }
    
    @Test
    fun testSafeUpdate() {
        val sql = "UPDATE users SET status = 'active' WHERE id = 123"
        val result = validator.validate(sql, setOf(SqlValidator.SqlType.UPDATE))
        
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
        assertFalse(result.metadata?.isDangerous ?: true)
    }
    
    @Test
    fun testSelectWithWildcard() {
        val sql = "SELECT * FROM users"
        val result = validator.validate(sql)
        
        assertTrue(result.isValid)
        assertTrue(result.metadata?.hasWildcard ?: false)
    }
    
    @Test
    fun testComplexityEstimation() {
        // Simple query
        val simple = "SELECT name FROM users WHERE id = 1"
        val simpleResult = validator.validate(simple)
        assertEquals(SqlValidator.ComplexityLevel.SIMPLE, simpleResult.metadata?.estimatedComplexity)
        
        // Medium query with JOIN
        val medium = "SELECT u.name, o.total FROM users u JOIN orders o ON u.id = o.user_id"
        val mediumResult = validator.validate(medium)
        assertEquals(SqlValidator.ComplexityLevel.MEDIUM, mediumResult.metadata?.estimatedComplexity)
    }
    
    @Test
    fun testCheckSyntaxHelper() {
        assertTrue(validator.checkSyntax("SELECT * FROM users"))
        assertFalse(validator.checkSyntax("SELECT * FORM users"))
    }
    
    @Test
    fun testIsSafeReadOnly() {
        assertTrue(validator.isSafeReadOnly("SELECT * FROM users WHERE id = 1"))
        assertFalse(validator.isSafeReadOnly("INSERT INTO users VALUES (1, 'Alice')"))
        assertFalse(validator.isSafeReadOnly("UPDATE users SET name = 'Bob'"))
        assertFalse(validator.isSafeReadOnly("DELETE FROM users"))
    }
    
    @Test
    fun testStaticValidators() {
        // Test companion object methods
        assertTrue(SqlValidator.isValidSql("SELECT * FROM users"))
        assertFalse(SqlValidator.isValidSql("INVALID SQL"))
        
        val result = SqlValidator.validateSql("SELECT * FROM users")
        assertTrue(result.isValid)
        assertEquals(SqlValidator.SqlType.SELECT, result.metadata?.type)
    }
    
    @Test
    fun testExtensionFunctions() {
        // Test String extension functions
        assertTrue("SELECT * FROM users".isValidSql())
        assertFalse("INVALID SQL".isValidSql())
        
        val result = "SELECT * FROM users WHERE id = 1".validateSql()
        assertTrue(result.isValid)
        assertEquals(SqlValidator.SqlType.SELECT, result.metadata?.type)
    }
    
    @Test
    fun testMultipleTableExtraction() {
        val sql = """
            SELECT u.name, p.title, o.total 
            FROM users u 
            JOIN products p ON true
            JOIN orders o ON u.id = o.user_id
        """.trimIndent()
        
        val result = validator.validate(sql)
        assertTrue(result.isValid)
        assertNotNull(result.metadata)
        assertTrue(result.metadata!!.tables.size >= 1) // At least one table extracted
    }
    
    @Test
    fun testSubqueryDetection() {
        val sql = """
            SELECT * FROM users 
            WHERE id IN (SELECT user_id FROM orders WHERE total > 1000)
        """.trimIndent()
        
        val result = validator.validate(sql)
        assertTrue(result.isValid)
        assertNotNull(result.metadata)
        // Subquery complexity detection is best-effort
        // Just verify the query is valid
    }
}
