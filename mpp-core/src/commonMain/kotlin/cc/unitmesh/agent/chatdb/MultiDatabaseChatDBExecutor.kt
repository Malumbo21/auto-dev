package cc.unitmesh.agent.chatdb

import cc.unitmesh.agent.conversation.ConversationManager
import cc.unitmesh.agent.database.*
import cc.unitmesh.agent.executor.BaseAgentExecutor
import cc.unitmesh.agent.logging.getLogger
import cc.unitmesh.agent.orchestrator.ToolOrchestrator
import cc.unitmesh.agent.render.ChatDBStepStatus
import cc.unitmesh.agent.render.ChatDBStepType
import cc.unitmesh.agent.render.CodingAgentRenderer
import cc.unitmesh.agent.subagent.SqlValidator
import cc.unitmesh.agent.subagent.SqlReviseAgent
import cc.unitmesh.devins.parser.CodeFence
import cc.unitmesh.llm.KoogLLMService

/**
 * Multi-Database ChatDB Executor - Executes Text2SQL across multiple databases
 * 
 * Key differences from single-database executor:
 * 1. Merges schemas from all databases with database name prefixes
 * 2. Parses SQL comments to determine target database
 * 3. Can execute queries on multiple databases in parallel
 * 4. Combines results from multiple databases
 */
class MultiDatabaseChatDBExecutor(
    projectPath: String,
    llmService: KoogLLMService,
    toolOrchestrator: ToolOrchestrator,
    renderer: CodingAgentRenderer,
    private val databaseConnections: Map<String, DatabaseConnection>,
    private val databaseConfigs: Map<String, DatabaseConfig>,
    maxIterations: Int = 10,
    enableLLMStreaming: Boolean = true
) : BaseAgentExecutor(
    projectPath = projectPath,
    llmService = llmService,
    toolOrchestrator = toolOrchestrator,
    renderer = renderer,
    maxIterations = maxIterations,
    enableLLMStreaming = enableLLMStreaming
) {
    private val logger = getLogger("MultiDatabaseChatDBExecutor")
    private val sqlValidator = SqlValidator()
    private val sqlReviseAgent = SqlReviseAgent(llmService, sqlValidator)
    private val maxRevisionAttempts = 3
    private val maxExecutionRetries = 3

    // Cache for merged schema
    private var mergedSchema: MergedDatabaseSchema? = null

    suspend fun execute(
        task: ChatDBTask,
        systemPrompt: String,
        onProgress: (String) -> Unit = {}
    ): MultiDatabaseChatDBResult {
        currentIteration = 0
        conversationManager = ConversationManager(llmService, systemPrompt)
        
        val errors = mutableListOf<String>()
        var generatedSql: String? = null
        val queryResults = mutableMapOf<String, QueryResult>()
        var revisionAttempts = 0
        val targetDatabases = mutableListOf<String>()

        try {
            // Step 1: Fetch and merge schemas from all databases
            renderer.renderChatDBStep(
                stepType = ChatDBStepType.FETCH_SCHEMA,
                status = ChatDBStepStatus.IN_PROGRESS,
                title = "Fetching schemas from ${databaseConnections.size} databases..."
            )
            onProgress("📊 Fetching schemas from ${databaseConnections.size} databases...")

            val merged = fetchAndMergeSchemas()
            mergedSchema = merged

            renderer.renderChatDBStep(
                stepType = ChatDBStepType.FETCH_SCHEMA,
                status = ChatDBStepStatus.SUCCESS,
                title = "Schemas fetched from ${merged.databases.size} databases",
                details = mapOf(
                    "databases" to merged.databases.map { (name, schema) ->
                        mapOf(
                            "name" to name,
                            "displayName" to (databaseConfigs[name]?.databaseName ?: name),
                            "tableCount" to schema.tables.size,
                            "tables" to schema.tables.map { it.name }
                        )
                    },
                    "totalTables" to merged.totalTableCount
                )
            )

            // Step 2: Schema Linking with merged schema
            renderer.renderChatDBStep(
                stepType = ChatDBStepType.SCHEMA_LINKING,
                status = ChatDBStepStatus.IN_PROGRESS,
                title = "Analyzing query across ${merged.databases.size} databases..."
            )
            onProgress("🔗 Analyzing query across databases...")

            val schemaContext = buildMultiDatabaseSchemaContext(merged, task.query)
            
            renderer.renderChatDBStep(
                stepType = ChatDBStepType.SCHEMA_LINKING,
                status = ChatDBStepStatus.SUCCESS,
                title = "Schema analysis complete",
                details = mapOf(
                    "databasesAnalyzed" to merged.databases.keys.toList(),
                    "schemaContext" to schemaContext.take(500) + "..."
                )
            )

            // Step 3: Generate SQL with multi-database context
            renderer.renderChatDBStep(
                stepType = ChatDBStepType.GENERATE_SQL,
                status = ChatDBStepStatus.IN_PROGRESS,
                title = "Generating SQL..."
            )
            onProgress("🤖 Generating SQL...")

            val sqlPrompt = buildMultiDatabaseSqlPrompt(task.query, schemaContext, task.maxRows)
            val sqlResponse = getLLMResponse(sqlPrompt, compileDevIns = false) { chunk ->
                onProgress(chunk)
            }

            // Parse SQL blocks with database targets
            val sqlBlocks = parseSqlBlocksWithTargets(sqlResponse)

            if (sqlBlocks.isEmpty()) {
                throw DatabaseException("No valid SQL generated")
            }

            generatedSql = sqlBlocks.map { "${it.database}: ${it.sql}" }.joinToString("\n")
            targetDatabases.addAll(sqlBlocks.map { it.database }.distinct())

            renderer.renderChatDBStep(
                stepType = ChatDBStepType.GENERATE_SQL,
                status = ChatDBStepStatus.SUCCESS,
                title = "SQL generated for ${targetDatabases.size} database(s)",
                details = mapOf(
                    "targetDatabases" to targetDatabases,
                    "sqlBlocks" to sqlBlocks.map { mapOf("database" to it.database, "sql" to it.sql) }
                )
            )

            // Step 4: Execute SQL on target databases
            for (sqlBlock in sqlBlocks) {
                val dbName = sqlBlock.database
                val sql = sqlBlock.sql
                val connection = databaseConnections[dbName]

                if (connection == null) {
                    errors.add("Database '$dbName' not connected")
                    continue
                }

                renderer.renderChatDBStep(
                    stepType = ChatDBStepType.EXECUTE_SQL,
                    status = ChatDBStepStatus.IN_PROGRESS,
                    title = "Executing on $dbName...",
                    details = mapOf("database" to dbName, "sql" to sql)
                )
                onProgress("⚡ Executing SQL on $dbName...")

                try {
                    val result = connection.executeQuery(sql)
                    queryResults[dbName] = result

                    renderer.renderChatDBStep(
                        stepType = ChatDBStepType.EXECUTE_SQL,
                        status = ChatDBStepStatus.SUCCESS,
                        title = "Query executed on $dbName",
                        details = mapOf(
                            "database" to dbName,
                            "sql" to sql,
                            "rowCount" to result.rowCount,
                            "columns" to result.columns,
                            "previewRows" to result.rows.take(5)
                        )
                    )
                } catch (e: Exception) {
                    errors.add("[$dbName] ${e.message}")
                    renderer.renderChatDBStep(
                        stepType = ChatDBStepType.EXECUTE_SQL,
                        status = ChatDBStepStatus.ERROR,
                        title = "Query failed on $dbName",
                        details = mapOf("database" to dbName, "sql" to sql, "error" to (e.message ?: "Unknown error"))
                    )
                }
            }

            // Step 5: Final result
            val success = queryResults.isNotEmpty()
            val combinedResult = combineResults(queryResults)

            renderer.renderChatDBStep(
                stepType = ChatDBStepType.FINAL_RESULT,
                status = if (success) ChatDBStepStatus.SUCCESS else ChatDBStepStatus.ERROR,
                title = if (success) "Query completed on ${queryResults.size} database(s)" else "Query failed",
                details = mapOf(
                    "databases" to queryResults.keys.toList(),
                    "totalRows" to combinedResult.rowCount,
                    "columns" to combinedResult.columns,
                    "previewRows" to combinedResult.rows.take(10),
                    "errors" to errors
                )
            )

            return MultiDatabaseChatDBResult(
                success = success,
                message = if (success) "Query executed successfully on ${queryResults.size} database(s)" else errors.joinToString("\n"),
                generatedSql = generatedSql,
                queryResult = combinedResult,
                queryResultsByDatabase = queryResults,
                targetDatabases = targetDatabases,
                revisionAttempts = revisionAttempts,
                errors = errors
            )

        } catch (e: Exception) {
            logger.error(e) { "Multi-database query failed: ${e.message}" }
            renderer.renderChatDBStep(
                stepType = ChatDBStepType.FINAL_RESULT,
                status = ChatDBStepStatus.ERROR,
                title = "Query failed",
                details = mapOf("error" to (e.message ?: "Unknown error"))
            )
            return MultiDatabaseChatDBResult(
                success = false,
                message = "Error: ${e.message}",
                errors = listOf(e.message ?: "Unknown error")
            )
        }
    }

    /**
     * Fetch schemas from all databases and merge them
     */
    private suspend fun fetchAndMergeSchemas(): MergedDatabaseSchema {
        val schemas = mutableMapOf<String, DatabaseSchema>()

        for ((dbId, connection) in databaseConnections) {
            try {
                val schema = connection.getSchema()
                schemas[dbId] = schema
                logger.info { "Fetched schema for $dbId: ${schema.tables.size} tables" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to fetch schema for $dbId" }
            }
        }

        return MergedDatabaseSchema(schemas)
    }

    /**
     * Build schema context for multi-database prompt
     */
    private fun buildMultiDatabaseSchemaContext(merged: MergedDatabaseSchema, query: String): String {
        val sb = StringBuilder()
        sb.append("=== AVAILABLE DATABASES AND TABLES ===\n\n")

        for ((dbId, schema) in merged.databases) {
            val displayName = databaseConfigs[dbId]?.databaseName ?: dbId
            sb.append("DATABASE: $dbId ($displayName)\n")
            sb.append("-".repeat(40)).append("\n")

            for (table in schema.tables) {
                sb.append("  Table: ${table.name}\n")
                if (table.comment != null) {
                    sb.append("    Comment: ${table.comment}\n")
                }
                sb.append("    Columns:\n")
                for (col in table.columns) {
                    val flags = mutableListOf<String>()
                    if (col.isPrimaryKey) flags.add("PK")
                    if (col.isForeignKey) flags.add("FK")
                    if (!col.nullable) flags.add("NOT NULL")
                    val flagStr = if (flags.isNotEmpty()) " [${flags.joinToString(", ")}]" else ""
                    sb.append("      - ${col.name}: ${col.type}$flagStr\n")
                }
                sb.append("\n")
            }
            sb.append("\n")
        }

        return sb.toString()
    }

    /**
     * Build SQL generation prompt for multi-database context
     */
    private fun buildMultiDatabaseSqlPrompt(query: String, schemaContext: String, maxRows: Int): String {
        return """
$schemaContext

USER QUERY: $query

INSTRUCTIONS:
1. Analyze which database(s) contain the relevant tables for this query
2. Generate SQL for the appropriate database(s)
3. Each SQL block MUST start with a comment specifying the target database: -- database: <database_id>
4. Use LIMIT $maxRows to restrict results
5. Only generate SELECT queries

Generate the SQL:
""".trimIndent()
    }

    /**
     * Parse SQL response to extract SQL blocks with their target databases
     */
    private fun parseSqlBlocksWithTargets(response: String): List<SqlBlock> {
        val blocks = mutableListOf<SqlBlock>()
        val codeFences = CodeFence.parseAll(response)

        for (fence in codeFences) {
            if (fence.languageId.lowercase() == "sql") {
                val sql = fence.text.trim()
                val database = extractDatabaseFromSql(sql)
                val cleanSql = removeDatabaseComment(sql)

                if (database != null && cleanSql.isNotBlank()) {
                    blocks.add(SqlBlock(database, cleanSql))
                } else if (databaseConnections.size == 1) {
                    // If only one database, use it as default
                    val defaultDb = databaseConnections.keys.first()
                    blocks.add(SqlBlock(defaultDb, cleanSql.ifBlank { sql }))
                }
            }
        }

        return blocks
    }

    /**
     * Extract database name from SQL comment
     */
    private fun extractDatabaseFromSql(sql: String): String? {
        val regex = Regex("--\\s*database:\\s*(\\S+)", RegexOption.IGNORE_CASE)
        val match = regex.find(sql)
        return match?.groupValues?.get(1)
    }

    /**
     * Remove database comment from SQL
     */
    private fun removeDatabaseComment(sql: String): String {
        return sql.replace(Regex("--\\s*database:\\s*\\S+\\s*\n?", RegexOption.IGNORE_CASE), "").trim()
    }

    /**
     * Combine results from multiple databases
     */
    private fun combineResults(results: Map<String, QueryResult>): QueryResult {
        if (results.isEmpty()) {
            return QueryResult(emptyList(), emptyList(), 0)
        }

        if (results.size == 1) {
            return results.values.first()
        }

        // For multiple results, add a "database" column and combine
        val allColumns = mutableListOf("_database")
        val allRows = mutableListOf<List<String>>()

        for ((dbName, result) in results) {
            if (allColumns.size == 1) {
                allColumns.addAll(result.columns)
            }
            for (row in result.rows) {
                allRows.add(listOf(dbName) + row)
            }
        }

        return QueryResult(allColumns, allRows, allRows.size)
    }
}

/**
 * SQL block with target database
 */
data class SqlBlock(
    val database: String,
    val sql: String
)

/**
 * Merged schema from multiple databases
 */
data class MergedDatabaseSchema(
    val databases: Map<String, DatabaseSchema>
) {
    val totalTableCount: Int
        get() = databases.values.sumOf { it.tables.size }

    fun getTablesForDatabase(dbId: String): List<TableSchema> {
        return databases[dbId]?.tables ?: emptyList()
    }
}

/**
 * Result from multi-database query
 */
data class MultiDatabaseChatDBResult(
    val success: Boolean,
    val message: String,
    val generatedSql: String? = null,
    val queryResult: QueryResult? = null,
    val queryResultsByDatabase: Map<String, QueryResult> = emptyMap(),
    val targetDatabases: List<String>? = null,
    val plotDslCode: String? = null,
    val revisionAttempts: Int = 0,
    val errors: List<String> = emptyList()
)

