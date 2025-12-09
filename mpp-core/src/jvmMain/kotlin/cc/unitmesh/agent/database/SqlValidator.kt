package cc.unitmesh.agent.database

import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.statement.insert.Insert
import net.sf.jsqlparser.statement.update.Update
import net.sf.jsqlparser.statement.delete.Delete

/**
 * SQL validation and parsing utilities using JSQLParser
 * 
 * This class provides SQL syntax validation, safety checks, and metadata extraction
 * to ensure generated SQL queries are safe and valid before execution.
 */
class SqlValidator {
    
    /**
     * Validation result containing parsed statement and any warnings
     */
    data class ValidationResult(
        val isValid: Boolean,
        val statement: Statement? = null,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val metadata: SqlMetadata? = null
    ) {
        val isSafe: Boolean
            get() = isValid && errors.isEmpty() && (metadata?.isDangerous != true)
    }
    
    /**
     * Metadata extracted from SQL statement
     */
    data class SqlMetadata(
        val type: SqlType,
        val tables: List<String>,
        val columns: List<String>,
        val isDangerous: Boolean,
        val hasWildcard: Boolean,
        val estimatedComplexity: ComplexityLevel
    )
    
    enum class SqlType {
        SELECT, INSERT, UPDATE, DELETE, 
        DDL, DCL, TCL, UNKNOWN
    }
    
    enum class ComplexityLevel {
        SIMPLE,     // Single table, few columns
        MEDIUM,     // Joins, subqueries
        COMPLEX     // Multiple joins, nested subqueries, complex conditions
    }
    
    /**
     * Validate SQL syntax and safety
     */
    fun validate(sql: String, allowedOperations: Set<SqlType> = setOf(SqlType.SELECT)): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        try {
            // Parse SQL
            val statement = CCJSqlParserUtil.parse(sql)
            
            // Extract metadata
            val metadata = extractMetadata(statement)
            
            // Check allowed operations
            if (metadata.type !in allowedOperations) {
                errors.add("Operation ${metadata.type} is not allowed. Allowed: $allowedOperations")
            }
            
            // Safety checks
            if (metadata.isDangerous) {
                warnings.add("Query contains potentially dangerous operations")
            }
            
            // Check for missing WHERE clause in UPDATE/DELETE
            if (metadata.type in setOf(SqlType.UPDATE, SqlType.DELETE)) {
                if (statement is Update && statement.where == null) {
                    errors.add("UPDATE without WHERE clause is dangerous")
                } else if (statement is Delete && statement.where == null) {
                    errors.add("DELETE without WHERE clause is dangerous")
                }
            }
            
            return ValidationResult(
                isValid = true,
                statement = statement,
                errors = errors,
                warnings = warnings,
                metadata = metadata
            )
            
        } catch (e: Exception) {
            errors.add("SQL parsing error: ${e.message}")
            return ValidationResult(
                isValid = false,
                errors = errors
            )
        }
    }
    
    /**
     * Extract metadata from parsed statement
     */
    private fun extractMetadata(statement: Statement): SqlMetadata {
        val tables = mutableListOf<String>()
        val columns = mutableListOf<String>()
        var hasWildcard = false
        var isDangerous = false
        
        val type = when (statement) {
            is Select -> {
                // Extract table names from SELECT
                val selectBody = statement.selectBody
                tables.addAll(extractTablesFromSelect(selectBody))
                
                // Check for SELECT *
                hasWildcard = statement.toString().contains("SELECT *", ignoreCase = true)
                
                SqlType.SELECT
            }
            is Insert -> {
                tables.add(statement.table.name)
                SqlType.INSERT
            }
            is Update -> {
                tables.add(statement.table.name)
                isDangerous = statement.where == null
                SqlType.UPDATE
            }
            is Delete -> {
                tables.add(statement.table.name)
                isDangerous = statement.where == null
                SqlType.DELETE
            }
            else -> SqlType.UNKNOWN
        }
        
        // Estimate complexity
        val complexity = when {
            tables.size > 3 || statement.toString().contains("SUBQUERY", ignoreCase = true) -> ComplexityLevel.COMPLEX
            tables.size > 1 || statement.toString().contains("JOIN", ignoreCase = true) -> ComplexityLevel.MEDIUM
            else -> ComplexityLevel.SIMPLE
        }
        
        return SqlMetadata(
            type = type,
            tables = tables,
            columns = columns,
            isDangerous = isDangerous,
            hasWildcard = hasWildcard,
            estimatedComplexity = complexity
        )
    }
    
    /**
     * Extract table names from SELECT body
     */
    private fun extractTablesFromSelect(selectBody: Any): List<String> {
        val tables = mutableListOf<String>()
        
        try {
            // Use toString() and simple parsing for now
            // More sophisticated extraction can be added later
            val sql = selectBody.toString()
            val fromIndex = sql.indexOf("FROM", ignoreCase = true)
            if (fromIndex >= 0) {
                val afterFrom = sql.substring(fromIndex + 4).trim()
                val tableName = afterFrom.split(" ", ",", "\n", "JOIN")[0].trim()
                if (tableName.isNotEmpty()) {
                    tables.add(tableName)
                }
            }
        } catch (e: Exception) {
            // Ignore extraction errors
        }
        
        return tables
    }
    
    /**
     * Quick syntax check without full validation
     */
    fun checkSyntax(sql: String): Boolean {
        return try {
            CCJSqlParserUtil.parse(sql)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if SQL is a safe read-only query
     */
    fun isSafeReadOnly(sql: String): Boolean {
        val result = validate(sql, setOf(SqlType.SELECT))
        return result.isSafe && result.metadata?.type == SqlType.SELECT
    }
    
    companion object {
        /**
         * Default validator instance
         */
        val default = SqlValidator()
        
        /**
         * Validate SQL with default validator
         */
        fun validateSql(sql: String): ValidationResult {
            return default.validate(sql)
        }
        
        /**
         * Quick syntax check
         */
        fun isValidSql(sql: String): Boolean {
            return default.checkSyntax(sql)
        }
    }
}

/**
 * Extension function for DatabaseConnection to validate SQL before execution
 */
suspend fun DatabaseConnection.executeValidatedQuery(sql: String): QueryResult {
    val validation = SqlValidator.default.validate(sql, setOf(SqlValidator.SqlType.SELECT))
    
    if (!validation.isValid) {
        throw DatabaseException("SQL validation failed: ${validation.errors.joinToString(", ")}")
    }
    
    if (!validation.isSafe) {
        throw DatabaseException("SQL query is not safe: ${validation.warnings.joinToString(", ")}")
    }
    
    return executeQuery(sql)
}

/**
 * Extension function to validate SQL syntax only
 */
fun String.isValidSql(): Boolean {
    return SqlValidator.isValidSql(this)
}

/**
 * Extension function to get SQL validation result
 */
fun String.validateSql(): SqlValidator.ValidationResult {
    return SqlValidator.validateSql(this)
}
