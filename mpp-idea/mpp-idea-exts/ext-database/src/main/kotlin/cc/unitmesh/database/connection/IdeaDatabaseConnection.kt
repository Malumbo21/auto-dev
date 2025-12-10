package cc.unitmesh.database.connection

import cc.unitmesh.agent.database.*
import com.intellij.database.psi.DbDataSource
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection

/**
 * IDEA platform database connection implementation
 *
 * Leverages IDEA's built-in Database tools and connection configuration.
 * Can directly use data sources configured in IDEA Database tool window.
 */
class IdeaDatabaseConnection(
    private val ideaConnection: Connection,
    private val dataSourceName: String
) : cc.unitmesh.agent.database.DatabaseConnection {

    override suspend fun executeQuery(sql: String): QueryResult = withContext(Dispatchers.IO) {
        try {
            val stmt = ideaConnection.prepareStatement(
                sql,
                java.sql.ResultSet.TYPE_SCROLL_INSENSITIVE,
                java.sql.ResultSet.CONCUR_READ_ONLY
            )
            val resultSet = stmt.executeQuery()

            val metaData = resultSet.metaData
            val columnCount = metaData.columnCount
            val columns = (1..columnCount).map { metaData.getColumnLabel(it) }

            val rows = mutableListOf<List<String>>()
            while (resultSet.next()) {
                val row = mutableListOf<String>()
                for (i in 1..columnCount) {
                    val value = resultSet.getObject(i)
                    row.add(value?.toString() ?: "")
                }
                rows.add(row)
            }

            resultSet.close()
            stmt.close()

            QueryResult(
                columns = columns,
                rows = rows,
                rowCount = rows.size
            )
        } catch (e: Exception) {
            throw DatabaseException.queryFailed(sql, e.message ?: "Unknown error")
        }
    }

    override suspend fun executeUpdate(sql: String): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val stmt = ideaConnection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)
            val affectedRows = stmt.executeUpdate()

            // Try to get generated keys
            val generatedKeys = mutableListOf<String>()
            try {
                val keysRs = stmt.generatedKeys
                while (keysRs.next()) {
                    generatedKeys.add(keysRs.getString(1))
                }
                keysRs.close()
            } catch (e: Exception) {
                // Ignore if generated keys are not available
            }

            stmt.close()

            UpdateResult.success(affectedRows, generatedKeys)
        } catch (e: Exception) {
            throw DatabaseException.queryFailed(sql, e.message ?: "Unknown error")
        }
    }

    /**
     * Dry run SQL to validate without executing.
     * Uses transaction rollback to test DML statements safely.
     */
    override suspend fun dryRun(sql: String): DryRunResult = withContext(Dispatchers.IO) {
        try {
            val originalAutoCommit = ideaConnection.autoCommit
            try {
                // Disable auto-commit to use transaction
                ideaConnection.autoCommit = false

                val sqlUpper = sql.trim().uppercase()
                val warnings = mutableListOf<String>()

                when {
                    // For SELECT, use EXPLAIN to validate
                    sqlUpper.startsWith("SELECT") -> {
                        val explainSql = "EXPLAIN $sql"
                        val stmt = ideaConnection.prepareStatement(explainSql)
                        try {
                            stmt.executeQuery()
                            stmt.close()
                            DryRunResult.valid("SELECT query is valid")
                        } catch (e: Exception) {
                            DryRunResult.invalid("Query validation failed: ${e.message}")
                        }
                    }

                    // For INSERT/UPDATE/DELETE, execute in transaction and rollback
                    sqlUpper.startsWith("INSERT") ||
                    sqlUpper.startsWith("UPDATE") ||
                    sqlUpper.startsWith("DELETE") -> {
                        val stmt = ideaConnection.prepareStatement(sql)
                        try {
                            val affectedRows = stmt.executeUpdate()
                            stmt.close()
                            // Rollback to undo the changes
                            ideaConnection.rollback()
                            DryRunResult.valid(
                                "Statement is valid (would affect $affectedRows row(s))",
                                estimatedRows = affectedRows
                            )
                        } catch (e: Exception) {
                            ideaConnection.rollback()
                            DryRunResult.invalid("Statement validation failed: ${e.message}")
                        }
                    }

                    // For DDL (CREATE, ALTER, DROP), we can't safely dry run
                    sqlUpper.startsWith("CREATE") ||
                    sqlUpper.startsWith("ALTER") ||
                    sqlUpper.startsWith("DROP") ||
                    sqlUpper.startsWith("TRUNCATE") -> {
                        warnings.add("DDL statements cannot be fully validated without execution")

                        // Try basic syntax check by preparing the statement
                        try {
                            val stmt = ideaConnection.prepareStatement(sql)
                            stmt.close()
                            DryRunResult(
                                isValid = true,
                                message = "DDL syntax appears valid (cannot fully validate without execution)",
                                warnings = warnings
                            )
                        } catch (e: Exception) {
                            DryRunResult.invalid("DDL syntax error: ${e.message}")
                        }
                    }

                    else -> {
                        // Unknown statement type, try to prepare it
                        try {
                            val stmt = ideaConnection.prepareStatement(sql)
                            stmt.close()
                            DryRunResult.valid("Statement syntax is valid")
                        } catch (e: Exception) {
                            DryRunResult.invalid("Statement validation failed: ${e.message}")
                        }
                    }
                }
            } finally {
                // Restore original auto-commit setting
                try {
                    ideaConnection.rollback() // Ensure any pending changes are rolled back
                    ideaConnection.autoCommit = originalAutoCommit
                } catch (e: Exception) {
                    // Ignore cleanup errors
                }
            }
        } catch (e: Exception) {
            DryRunResult.invalid("Dry run failed: ${e.message}")
        }
    }

    override suspend fun getSchema(): DatabaseSchema = withContext(Dispatchers.IO) {
        try {
            val metadata = ideaConnection.metaData
            val tables = mutableListOf<TableSchema>()

            // Get all tables
            val tableTypes = arrayOf("TABLE", "VIEW")
            val tableRs = metadata.getTables(null, null, "%", tableTypes)

            while (tableRs.next()) {
                val tableName = tableRs.getString("TABLE_NAME")
                val tableComment = try {
                    tableRs.getString("REMARKS")
                } catch (e: Exception) {
                    null
                }

                // Get primary keys
                val primaryKeys = mutableSetOf<String>()
                try {
                    val pkRs = metadata.getPrimaryKeys(null, null, tableName)
                    while (pkRs.next()) {
                        primaryKeys.add(pkRs.getString("COLUMN_NAME"))
                    }
                    pkRs.close()
                } catch (e: Exception) {
                    // Ignore if primary keys cannot be retrieved
                }

                // Get columns
                val columnRs = metadata.getColumns(null, null, tableName, null)
                val columns = mutableListOf<ColumnSchema>()

                while (columnRs.next()) {
                    val columnName = columnRs.getString("COLUMN_NAME")
                    val columnType = columnRs.getString("TYPE_NAME")
                    val nullable = columnRs.getInt("NULLABLE") != java.sql.DatabaseMetaData.columnNoNulls
                    val columnComment = try {
                        columnRs.getString("REMARKS")
                    } catch (e: Exception) {
                        null
                    }
                    val defaultValue = try {
                        columnRs.getString("COLUMN_DEF")
                    } catch (e: Exception) {
                        null
                    }

                    columns.add(
                        ColumnSchema(
                            name = columnName,
                            type = columnType,
                            nullable = nullable,
                            comment = columnComment,
                            isPrimaryKey = primaryKeys.contains(columnName),
                            isForeignKey = false,
                            defaultValue = defaultValue
                        )
                    )
                }
                columnRs.close()

                tables.add(
                    TableSchema(
                        name = tableName,
                        columns = columns,
                        comment = tableComment
                    )
                )
            }
            tableRs.close()

            DatabaseSchema(
                databaseName = dataSourceName,
                tables = tables
            )
        } catch (e: Exception) {
            throw DatabaseException.connectionFailed("Failed to get schema: ${e.message}")
        }
    }

    override suspend fun close() {
        try {
            ideaConnection.close()
        } catch (e: Exception) {
            throw DatabaseException("Failed to close database connection: ${e.message}", e)
        }
    }

    companion object {
        /**
         * Create database connection from IDEA data source
         *
         * @param project IDEA project
         * @param dataSource Data source (configured in IDEA Database)
         * @return Database connection
         */
        fun createFromIdea(project: Project, dataSource: DbDataSource): IdeaDatabaseConnection {
            // Note: This is a simplified implementation
            // In real usage, you need to obtain the connection through IDEA's Database API
            throw UnsupportedOperationException(
                "IDEA Database integration requires IDEA-specific API access. " +
                "This will be implemented when integrated into the IDEA plugin."
            )
        }

        /**
         * Get all available data source names in IDEA
         */
        fun getAvailableDataSources(project: Project): List<String> {
            throw UnsupportedOperationException(
                "IDEA Database integration requires IDEA-specific API access. " +
                "This will be implemented when integrated into the IDEA plugin."
            )
        }
    }
}
