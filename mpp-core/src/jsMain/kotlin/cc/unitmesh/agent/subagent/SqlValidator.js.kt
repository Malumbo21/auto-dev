package cc.unitmesh.agent.subagent

/**
 * JavaScript implementation of SqlValidator.
 * 
 * Performs basic SQL syntax validation without full parsing.
 * Full parsing with JSqlParser is only available on JVM platforms.
 */
actual class SqlValidator actual constructor() : SqlValidatorInterface {
    
    actual override fun validate(sql: String): SqlValidationResult {
        if (sql.isBlank()) {
            return SqlValidationResult(
                isValid = false,
                errors = listOf("Empty SQL query")
            )
        }
        
        return performBasicValidation(sql)
    }
    
    actual override fun validateWithTableWhitelist(sql: String, allowedTables: Set<String>): SqlValidationResult {
        val basicResult = validate(sql)
        if (!basicResult.isValid) {
            return basicResult
        }
        
        // Extract table names using regex and validate against whitelist
        val usedTables = extractTableNames(sql)
        val allowedTablesLower = allowedTables.map { it.lowercase() }.toSet()
        val invalidTables = usedTables.filter { tableName ->
            tableName.lowercase() !in allowedTablesLower
        }
        
        return if (invalidTables.isNotEmpty()) {
            SqlValidationResult(
                isValid = false,
                errors = listOf(
                    "Invalid table(s) used: ${invalidTables.joinToString(", ")}. " +
                    "Available tables: ${allowedTables.joinToString(", ")}"
                ),
                warnings = basicResult.warnings
            )
        } else {
            basicResult
        }
    }
    
    actual override fun extractTableNames(sql: String): List<String> {
        val tables = mutableListOf<String>()

        // Match FROM clause: FROM table_name or FROM schema.table_name
        val fromPattern = Regex("""FROM\s+([`"\[]?\w+[`"\]]?(?:\.[`"\[]?\w+[`"\]]?)?)""", RegexOption.IGNORE_CASE)
        fromPattern.findAll(sql).forEach { match ->
            tables.add(cleanTableName(match.groupValues[1]))
        }

        // Match JOIN clause: JOIN table_name or LEFT/RIGHT/INNER/OUTER JOIN table_name
        val joinPattern = Regex("""(?:LEFT|RIGHT|INNER|OUTER|CROSS|FULL)?\s*JOIN\s+([`"\[]?\w+[`"\]]?(?:\.[`"\[]?\w+[`"\]]?)?)""", RegexOption.IGNORE_CASE)
        joinPattern.findAll(sql).forEach { match ->
            tables.add(cleanTableName(match.groupValues[1]))
        }

        // Match UPDATE clause: UPDATE table_name
        val updatePattern = Regex("""UPDATE\s+([`"\[]?\w+[`"\]]?(?:\.[`"\[]?\w+[`"\]]?)?)""", RegexOption.IGNORE_CASE)
        updatePattern.findAll(sql).forEach { match ->
            tables.add(cleanTableName(match.groupValues[1]))
        }

        // Match INSERT INTO clause: INSERT INTO table_name
        val insertPattern = Regex("""INSERT\s+INTO\s+([`"\[]?\w+[`"\]]?(?:\.[`"\[]?\w+[`"\]]?)?)""", RegexOption.IGNORE_CASE)
        insertPattern.findAll(sql).forEach { match ->
            tables.add(cleanTableName(match.groupValues[1]))
        }

        // Match DELETE FROM clause: DELETE FROM table_name
        val deletePattern = Regex("""DELETE\s+FROM\s+([`"\[]?\w+[`"\]]?(?:\.[`"\[]?\w+[`"\]]?)?)""", RegexOption.IGNORE_CASE)
        deletePattern.findAll(sql).forEach { match ->
            tables.add(cleanTableName(match.groupValues[1]))
        }

        return tables.distinct()
    }

    /**
     * Detect SQL type using regex-based detection
     */
    actual override fun detectSqlType(sql: String): SqlOperationType {
        val trimmedSql = sql.trim().uppercase()
        return when {
            trimmedSql.startsWith("SELECT") || trimmedSql.startsWith("WITH") -> SqlOperationType.SELECT
            trimmedSql.startsWith("INSERT") -> SqlOperationType.INSERT
            trimmedSql.startsWith("UPDATE") -> SqlOperationType.UPDATE
            trimmedSql.startsWith("DELETE") -> SqlOperationType.DELETE
            trimmedSql.startsWith("CREATE") -> SqlOperationType.CREATE
            trimmedSql.startsWith("ALTER") -> SqlOperationType.ALTER
            trimmedSql.startsWith("DROP") -> SqlOperationType.DROP
            trimmedSql.startsWith("TRUNCATE") -> SqlOperationType.TRUNCATE
            else -> SqlOperationType.UNKNOWN
        }
    }
    
    private fun cleanTableName(name: String): String {
        // Remove quotes and brackets, extract table name from schema.table format
        val cleaned = name.replace(Regex("""[`"\[\]]"""), "")
        return if (cleaned.contains(".")) {
            cleaned.substringAfterLast(".")
        } else {
            cleaned
        }
    }
    
    private fun performBasicValidation(sql: String): SqlValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        val upperSql = sql.uppercase()
        
        // Check for basic SQL structure
        val hasValidStart = upperSql.trimStart().let { trimmed ->
            trimmed.startsWith("SELECT") ||
            trimmed.startsWith("INSERT") ||
            trimmed.startsWith("UPDATE") ||
            trimmed.startsWith("DELETE") ||
            trimmed.startsWith("CREATE") ||
            trimmed.startsWith("ALTER") ||
            trimmed.startsWith("DROP") ||
            trimmed.startsWith("WITH")
        }
        
        if (!hasValidStart) {
            errors.add("SQL must start with a valid statement (SELECT, INSERT, UPDATE, DELETE, etc.)")
        }
        
        // Check for balanced parentheses
        var parenCount = 0
        for (char in sql) {
            when (char) {
                '(' -> parenCount++
                ')' -> parenCount--
            }
            if (parenCount < 0) {
                errors.add("Unbalanced parentheses: unexpected ')'")
                break
            }
        }
        if (parenCount > 0) {
            errors.add("Unbalanced parentheses: missing ')'")
        }
        
        // Warnings
        if (upperSql.contains("SELECT *")) {
            warnings.add("Consider specifying explicit columns instead of SELECT *")
        }
        
        if (!upperSql.contains("WHERE") && 
            (upperSql.contains("UPDATE") || upperSql.contains("DELETE"))) {
            warnings.add("UPDATE/DELETE without WHERE clause will affect all rows")
        }
        
        return SqlValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
}

