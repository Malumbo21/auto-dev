package cc.unitmesh.agent.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import java.sql.DatabaseMetaData
import java.sql.ResultSet

/**
 * JVM platform database connection implementation using Exposed framework
 */
class ExposedDatabaseConnection(
    private val database: Database,
    private val hikariDataSource: HikariDataSource
) : DatabaseConnection {

    override suspend fun executeQuery(sql: String): QueryResult = withContext(Dispatchers.IO) {
        try {
            hikariDataSource.connection.use { connection ->
                val stmt = connection.prepareStatement(
                    sql,
                    ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY
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
            }
        } catch (e: Exception) {
            throw DatabaseException.queryFailed(sql, e.message ?: "Unknown error")
        }
    }

    override suspend fun executeUpdate(sql: String): UpdateResult = withContext(Dispatchers.IO) {
        try {
            hikariDataSource.connection.use { connection ->
                val stmt = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)
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
            }
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
            hikariDataSource.connection.use { connection ->
                val originalAutoCommit = connection.autoCommit
                try {
                    // Disable auto-commit to use transaction
                    connection.autoCommit = false

                    val sqlUpper = sql.trim().uppercase()
                    val warnings = mutableListOf<String>()

                    when {
                        // For SELECT, use EXPLAIN to validate
                        sqlUpper.startsWith("SELECT") -> {
                            val explainSql = "EXPLAIN $sql"
                            val stmt = connection.prepareStatement(explainSql)
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
                            val stmt = connection.prepareStatement(sql)
                            try {
                                val affectedRows = stmt.executeUpdate()
                                stmt.close()
                                // Rollback to undo the changes
                                connection.rollback()
                                DryRunResult.valid(
                                    "Statement is valid (would affect $affectedRows row(s))",
                                    estimatedRows = affectedRows
                                )
                            } catch (e: Exception) {
                                connection.rollback()
                                DryRunResult.invalid("Statement validation failed: ${e.message}")
                            }
                        }

                        // For DDL (CREATE, ALTER, DROP), we can't safely dry run
                        // Just validate syntax using EXPLAIN if possible
                        sqlUpper.startsWith("CREATE") ||
                        sqlUpper.startsWith("ALTER") ||
                        sqlUpper.startsWith("DROP") ||
                        sqlUpper.startsWith("TRUNCATE") -> {
                            // For DDL, we can try to parse it but can't execute safely
                            // Return a warning that DDL cannot be fully validated
                            warnings.add("DDL statements cannot be fully validated without execution")

                            // Try basic syntax check by preparing the statement
                            try {
                                val stmt = connection.prepareStatement(sql)
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
                                val stmt = connection.prepareStatement(sql)
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
                        connection.rollback() // Ensure any pending changes are rolled back
                        connection.autoCommit = originalAutoCommit
                    } catch (e: Exception) {
                        // Ignore cleanup errors
                    }
                }
            }
        } catch (e: Exception) {
            DryRunResult.invalid("Dry run failed: ${e.message}")
        }
    }

    override suspend fun getSchema(): DatabaseSchema = withContext(Dispatchers.IO) {
        try {
            hikariDataSource.connection.use { connection ->
                val metadata = connection.metaData
                val tables = mutableListOf<TableSchema>()

                // Get current database/catalog name to filter tables
                val currentCatalog = connection.catalog

                // Get tables only from current database (catalog)
                val tableTypes = arrayOf("TABLE", "VIEW")
                val tableRs = metadata.getTables(currentCatalog, null, "%", tableTypes)

                while (tableRs.next()) {
                    val tableName = tableRs.getString("TABLE_NAME")
                    val tableComment = try {
                        tableRs.getString("REMARKS")
                    } catch (e: Exception) {
                        null
                    }

                    // Get primary keys for current catalog
                    val primaryKeys = mutableSetOf<String>()
                    try {
                        val pkRs = metadata.getPrimaryKeys(currentCatalog, null, tableName)
                        while (pkRs.next()) {
                            primaryKeys.add(pkRs.getString("COLUMN_NAME"))
                        }
                        pkRs.close()
                    } catch (e: Exception) {
                        // Ignore if primary keys cannot be retrieved
                    }

                    // Get columns for current catalog
                    val columnRs = metadata.getColumns(currentCatalog, null, tableName, null)
                    val columns = mutableListOf<ColumnSchema>()

                    while (columnRs.next()) {
                        val columnName = columnRs.getString("COLUMN_NAME")
                        val columnType = columnRs.getString("TYPE_NAME")
                        val nullable = columnRs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls
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
                                isForeignKey = false, // Can be enhanced later
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
                    databaseName = metadata.databaseProductName,
                    tables = tables
                )
            }
        } catch (e: Exception) {
            throw DatabaseException.connectionFailed("Failed to get schema: ${e.message}")
        }
    }

    override suspend fun close() {
        try {
            hikariDataSource.close()
        } catch (e: Exception) {
            throw DatabaseException("Failed to close database connection: ${e.message}", e)
        }
    }

    companion object {
        /**
         * Create Exposed database connection
         */
        fun create(config: DatabaseConfig): ExposedDatabaseConnection {
            val hikariConfig = HikariConfig().apply {
                jdbcUrl = config.getJdbcUrl()
                username = config.username
                password = config.password
                maximumPoolSize = 10
                minimumIdle = 2
                connectionTimeout = 30000
                idleTimeout = 600000
                maxLifetime = 1800000

                // Add additional parameters
                config.additionalParams.forEach { (key, value) ->
                    addDataSourceProperty(key, value)
                }
            }

            val dataSource = HikariDataSource(hikariConfig)
            val database = Database.connect(dataSource)

            return ExposedDatabaseConnection(database, dataSource)
        }
    }
}

/**
 * JVM platform factory method for creating database connections
 */
actual fun createDatabaseConnection(config: DatabaseConfig): DatabaseConnection {
    return ExposedDatabaseConnection.create(config)
}
