package cc.unitmesh.agent.subagent

import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.util.TablesNamesFinder

/**
 * Android implementation of SqlValidator using JSqlParser.
 *
 * This validator uses JSqlParser to validate SQL syntax.
 * It can detect:
 * - Syntax errors
 * - Malformed SQL statements
 * - Unsupported SQL constructs
 * - Table names not in whitelist (schema validation)
 */
actual class SqlValidator actual constructor() : SqlValidatorInterface {

    actual override fun validate(sql: String): SqlValidationResult {
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

    actual override fun validateWithTableWhitelist(sql: String, allowedTables: Set<String>): SqlValidationResult {
        return try {
            val statement: Statement = CCJSqlParserUtil.parse(sql)

            // Extract table names from the SQL
            val tablesNamesFinder = TablesNamesFinder()
            val usedTables = tablesNamesFinder.getTableList(statement)

            // Check if all used tables are in the whitelist (case-insensitive)
            val allowedTablesLower = allowedTables.map { it.lowercase() }.toSet()
            val invalidTables = usedTables.filter { tableName ->
                tableName.lowercase() !in allowedTablesLower
            }

            if (invalidTables.isNotEmpty()) {
                SqlValidationResult(
                    isValid = false,
                    errors = listOf(
                        "Invalid table(s) used: ${invalidTables.joinToString(", ")}. " +
                        "Available tables: ${allowedTables.joinToString(", ")}"
                    ),
                    warnings = collectWarnings(statement)
                )
            } else {
                SqlValidationResult(
                    isValid = true,
                    errors = emptyList(),
                    warnings = collectWarnings(statement)
                )
            }
        } catch (e: Exception) {
            SqlValidationResult(
                isValid = false,
                errors = listOf(extractErrorMessage(e)),
                warnings = emptyList()
            )
        }
    }

    actual override fun extractTableNames(sql: String): List<String> {
        return try {
            val statement: Statement = CCJSqlParserUtil.parse(sql)
            val tablesNamesFinder = TablesNamesFinder()
            tablesNamesFinder.getTableList(statement)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun extractErrorMessage(e: Exception): String {
        val message = e.message ?: "Unknown SQL parsing error"
        return when {
            message.contains("Encountered") -> {
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
    
    private fun collectWarnings(statement: Statement): List<String> {
        val warnings = mutableListOf<String>()
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
}

