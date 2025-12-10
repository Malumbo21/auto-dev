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
