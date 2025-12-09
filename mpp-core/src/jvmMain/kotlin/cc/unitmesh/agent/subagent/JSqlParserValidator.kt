package cc.unitmesh.agent.subagent

import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.Statement

/**
 * JVM implementation of SqlValidatorInterface using JSqlParser
 * 
 * This validator uses JSqlParser to validate SQL syntax.
 * It can detect:
 * - Syntax errors
 * - Malformed SQL statements
 * - Unsupported SQL constructs
 */
class JSqlParserValidator : SqlValidatorInterface {
    
    override fun validate(sql: String): SqlValidationResult {
        return try {
            val statement: Statement = CCJSqlParserUtil.parse(sql)
            SqlValidationResult(
                isValid = true,
                errors = emptyList(),
                warnings = collectWarnings(statement)
            )
        } catch (e: Exception) {
            SqlValidationResult(
                isValid = false,
                errors = listOf(extractErrorMessage(e)),
                warnings = emptyList()
            )
        }
    }
    
    /**
     * Validate SQL and return the parsed statement if valid
     */
    fun validateAndParse(sql: String): Pair<SqlValidationResult, Statement?> {
        return try {
            val statement: Statement = CCJSqlParserUtil.parse(sql)
            Pair(
                SqlValidationResult(
                    isValid = true,
                    errors = emptyList(),
                    warnings = collectWarnings(statement)
                ),
                statement
            )
        } catch (e: Exception) {
            Pair(
                SqlValidationResult(
                    isValid = false,
                    errors = listOf(extractErrorMessage(e)),
                    warnings = emptyList()
                ),
                null
            )
        }
    }
    
    /**
     * Extract a clean error message from the exception
     */
    private fun extractErrorMessage(e: Exception): String {
        val message = e.message ?: "Unknown SQL parsing error"
        // Clean up JSqlParser error messages
        return when {
            message.contains("Encountered") -> {
                // Parse error with position info
                val match = Regex("Encountered \"(.+?)\" at line (\\d+), column (\\d+)").find(message)
                if (match != null) {
                    val (token, line, column) = match.destructured
                    "Syntax error at line $line, column $column: unexpected token '$token'"
                } else {
                    message
                }
            }
            message.contains("Was expecting") -> {
                val match = Regex("Was expecting.*?:\\s*(.+)").find(message)
                if (match != null) {
                    "Expected: ${match.groupValues[1].take(100)}"
                } else {
                    message
                }
            }
            else -> message.take(200)
        }
    }
    
    /**
     * Collect warnings from parsed statement (e.g., deprecated syntax)
     */
    private fun collectWarnings(statement: Statement): List<String> {
        val warnings = mutableListOf<String>()
        
        // Check for common issues that aren't errors but might be problematic
        val sql = statement.toString()
        
        if (sql.contains("SELECT *")) {
            warnings.add("Consider specifying explicit columns instead of SELECT *")
        }
        
        if (!sql.contains("WHERE", ignoreCase = true) && 
            (sql.contains("UPDATE", ignoreCase = true) || sql.contains("DELETE", ignoreCase = true))) {
            warnings.add("UPDATE/DELETE without WHERE clause will affect all rows")
        }
        
        return warnings
    }
    
    companion object {
        /**
         * Quick validation check - returns true if SQL is syntactically valid
         */
        fun isValidSql(sql: String): Boolean {
            return try {
                CCJSqlParserUtil.parse(sql)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}

