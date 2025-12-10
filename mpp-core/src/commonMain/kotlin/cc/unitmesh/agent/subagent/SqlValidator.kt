package cc.unitmesh.agent.subagent

/**
 * Platform-agnostic SQL validator.
 *
 * JVM platforms use JSqlParser for full SQL parsing and validation.
 * Non-JVM platforms perform basic syntax checks.
 *
 * Usage:
 * ```kotlin
 * val validator = SqlValidator()
 * val result = validator.validate("SELECT * FROM users")
 * if (result.isValid) {
 *     // SQL is valid
 * } else {
 *     // Handle errors: result.errors
 * }
 * ```
 */
expect class SqlValidator() : SqlValidatorInterface {
    /**
     * Validate SQL syntax.
     *
     * @param sql The SQL query to validate
     * @return Validation result with errors and warnings
     */
    override fun validate(sql: String): SqlValidationResult

    /**
     * Validate SQL with table whitelist - ensures only allowed tables are used.
     *
     * On JVM platforms, this uses JSqlParser to extract table names and validate.
     * On non-JVM platforms, this performs basic regex-based table name extraction.
     *
     * @param sql The SQL query to validate
     * @param allowedTables Set of table names that are allowed in the query
     * @return SqlValidationResult with errors if invalid tables are used
     */
    override fun validateWithTableWhitelist(sql: String, allowedTables: Set<String>): SqlValidationResult

    /**
     * Extract table names from SQL query.
     *
     * On JVM platforms, this uses JSqlParser for accurate extraction.
     * On non-JVM platforms, this uses regex-based extraction which may be less accurate.
     *
     * @param sql The SQL query to extract table names from
     * @return List of table names found in the query
     */
    override fun extractTableNames(sql: String): List<String>
}

