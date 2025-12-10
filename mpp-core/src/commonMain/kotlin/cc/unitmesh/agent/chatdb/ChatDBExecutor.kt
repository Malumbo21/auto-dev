package cc.unitmesh.agent.chatdb

import cc.unitmesh.agent.conversation.ConversationManager
import cc.unitmesh.agent.database.*
import cc.unitmesh.agent.executor.BaseAgentExecutor
import cc.unitmesh.agent.logging.getLogger
import cc.unitmesh.agent.orchestrator.ToolOrchestrator
import cc.unitmesh.agent.render.ChatDBStepStatus
import cc.unitmesh.agent.render.ChatDBStepType
import cc.unitmesh.agent.render.CodingAgentRenderer
import cc.unitmesh.agent.subagent.PlotDSLAgent
import cc.unitmesh.agent.subagent.PlotDSLContext
import cc.unitmesh.agent.subagent.SqlOperationType
import cc.unitmesh.agent.subagent.SqlValidator
import cc.unitmesh.agent.subagent.SqlReviseAgent
import cc.unitmesh.agent.subagent.SqlRevisionInput
import cc.unitmesh.devins.parser.CodeFence
import cc.unitmesh.llm.KoogLLMService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Multi-Database ChatDB Executor - Executes Text2SQL across multiple databases
 *
 * Key differences from single-database executor:
 * 1. Merges schemas from all databases with database name prefixes
 * 2. Parses SQL comments to determine target database
 * 3. Can execute queries on multiple databases in parallel
 * 4. Combines results from multiple databases
 *
 * Flow:
 * 1. FETCH_SCHEMA - Fetch schemas from all databases
 * 2. SCHEMA_LINKING - Use LLM/Keyword linker to find relevant tables
 * 3. GENERATE_SQL - Generate SQL with database routing comments
 * 4. VALIDATE_SQL - Validate SQL syntax and table names
 * 5. REVISE_SQL - Fix SQL if validation fails
 * 6. EXECUTE_SQL - Execute on target databases with retry
 * 7. GENERATE_VISUALIZATION - Optional visualization
 * 8. FINAL_RESULT - Return combined results
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
    private val keywordSchemaLinker = KeywordSchemaLinker()
    private val sqlValidator = SqlValidator()
    private val sqlReviseAgent = SqlReviseAgent(llmService, sqlValidator)
    private val plotDSLAgent = PlotDSLAgent(llmService)
    private val maxRevisionAttempts = 3
    private val maxExecutionRetries = 3

    // Cache for merged schema
    private var mergedSchema: MergedDatabaseSchema? = null

    // Track if we have an active conversation
    private var hasActiveConversation: Boolean = false

    // Store the system prompt for conversation initialization
    private var currentSystemPrompt: String? = null

    /**
     * Check if there's an active conversation that can be continued
     */
    fun hasActiveConversation(): Boolean = hasActiveConversation && conversationManager != null

    /**
     * Clear the current conversation and start fresh.
     * Call this when user explicitly wants to start a new task/session.
     */
    fun clearConversation() {
        currentIteration = 0
        conversationManager?.clearHistory()
        hasActiveConversation = false
        currentSystemPrompt = null
        logger.info { "ChatDB conversation cleared, ready for new query" }
    }

    /**
     * Execute a ChatDB task, optionally continuing an existing conversation.
     *
     * @param task The ChatDB task to execute
     * @param systemPrompt The system prompt for the conversation
     * @param onProgress Progress callback
     * @param continueConversation If true, continues the existing conversation context.
     *                             If false, starts a new conversation (clears history).
     */
    suspend fun execute(
        task: ChatDBTask,
        systemPrompt: String,
        onProgress: (String) -> Unit = {},
        continueConversation: Boolean = false
    ): MultiDatabaseChatDBResult {
        // Only reset if starting a new conversation
        if (!continueConversation || !hasActiveConversation) {
            currentIteration = 0
            conversationManager = ConversationManager(llmService, systemPrompt)
            currentSystemPrompt = systemPrompt
            hasActiveConversation = true

            onProgress("🚀 ChatDB Agent started (new session)")
        } else {
            // Continuing existing conversation - just reset iteration counter for this turn
            currentIteration = 0
            onProgress("💬 Continuing ChatDB conversation...")
        }

        val errors = mutableListOf<String>()
        var generatedSql: String? = null
        val queryResults = mutableMapOf<String, QueryResult>()
        var revisionAttempts = 0
        val targetDatabases = mutableListOf<String>()
        var plotDslCode: String? = null

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
                    "totalTables" to merged.totalTableCount,
                    "tableSchemas" to merged.databases.flatMap { (dbName, schema) ->
                        schema.tables.map { table ->
                            mapOf(
                                "name" to "[$dbName].${table.name}",
                                "comment" to (table.comment ?: ""),
                                "columns" to table.columns.map { col ->
                                    mapOf(
                                        "name" to col.name,
                                        "type" to col.type,
                                        "nullable" to col.nullable,
                                        "isPrimaryKey" to col.isPrimaryKey,
                                        "isForeignKey" to col.isForeignKey,
                                        "comment" to (col.comment ?: "")
                                    )
                                }
                            )
                        }
                    }
                )
            )

            // Step 2: Schema Linking - Find relevant tables using keyword linker
            renderer.renderChatDBStep(
                stepType = ChatDBStepType.SCHEMA_LINKING,
                status = ChatDBStepStatus.IN_PROGRESS,
                title = "Performing schema linking across ${merged.databases.size} databases..."
            )
            onProgress("🔗 Performing schema linking...")

            // Perform schema linking for each database
            val linkingResults = mutableMapOf<String, SchemaLinkingResult>()
            val allRelevantTables = mutableListOf<Map<String, Any>>()
            val allKeywords = mutableSetOf<String>()

            for ((dbId, schema) in merged.databases) {
                val linkingResult = keywordSchemaLinker.link(task.query, schema)
                linkingResults[dbId] = linkingResult
                allKeywords.addAll(linkingResult.keywords)

                // Collect relevant table schemas for UI
                linkingResult.relevantTables.forEach { tableName ->
                    schema.getTable(tableName)?.let { table ->
                        allRelevantTables.add(mapOf(
                            "name" to "[$dbId].${table.name}",
                            "comment" to (table.comment ?: ""),
                            "columns" to table.columns.map { col ->
                                mapOf(
                                    "name" to col.name,
                                    "type" to col.type,
                                    "nullable" to col.nullable,
                                    "isPrimaryKey" to col.isPrimaryKey,
                                    "isForeignKey" to col.isForeignKey
                                )
                            }
                        ))
                    }
                }
            }

            val schemaContext = buildMultiDatabaseSchemaContext(merged, task.query, linkingResults)

            renderer.renderChatDBStep(
                stepType = ChatDBStepType.SCHEMA_LINKING,
                status = ChatDBStepStatus.SUCCESS,
                title = "Schema linking complete - found ${allRelevantTables.size} relevant tables",
                details = mapOf(
                    "databasesAnalyzed" to merged.databases.keys.toList(),
                    "keywords" to allKeywords.toList(),
                    "relevantTableSchemas" to allRelevantTables,
                    "schemaContext" to schemaContext.take(500) + if (schemaContext.length > 500) "..." else ""
                )
            )

            // Step 3: Generate SQL with multi-database context
            renderer.renderChatDBStep(
                stepType = ChatDBStepType.GENERATE_SQL,
                status = ChatDBStepStatus.IN_PROGRESS,
                title = "Generating SQL query..."
            )
            onProgress("🤖 Generating SQL query...")

            val sqlPrompt = buildMultiDatabaseSqlPrompt(task.query, schemaContext, task.maxRows)
            val sqlResponse = getLLMResponse(sqlPrompt, compileDevIns = false) { chunk ->
                onProgress(chunk)
            }

            // Parse SQL blocks with database targets
            var sqlBlocks = parseSqlBlocksWithTargets(sqlResponse)

            if (sqlBlocks.isEmpty()) {
                renderer.renderChatDBStep(
                    stepType = ChatDBStepType.GENERATE_SQL,
                    status = ChatDBStepStatus.ERROR,
                    title = "Failed to extract SQL",
                    error = "Could not find SQL code block in LLM response"
                )
                throw DatabaseException("No valid SQL generated")
            }

            generatedSql = sqlBlocks.joinToString("\n\n") { "-- database: ${it.database}\n${it.sql}" }
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

            // Step 4: Validate SQL for each database
            renderer.renderChatDBStep(
                stepType = ChatDBStepType.VALIDATE_SQL,
                status = ChatDBStepStatus.IN_PROGRESS,
                title = "Validating SQL..."
            )
            onProgress("🔍 Validating SQL...")

            val validatedBlocks = mutableListOf<SqlBlock>()
            var hasValidationErrors = false

            for (block in sqlBlocks) {
                val dbSchema = merged.databases[block.database]
                val allTableNames = dbSchema?.tables?.map { it.name }?.toSet() ?: emptySet()

                val syntaxValidation = sqlValidator.validate(block.sql)
                val tableValidation = if (syntaxValidation.isValid) {
                    sqlValidator.validateWithTableWhitelist(block.sql, allTableNames)
                } else {
                    syntaxValidation
                }

                if (!tableValidation.isValid) {
                    hasValidationErrors = true
                    val errorType = if (!syntaxValidation.isValid) "syntax" else "table name"

                    renderer.renderChatDBStep(
                        stepType = ChatDBStepType.VALIDATE_SQL,
                        status = ChatDBStepStatus.ERROR,
                        title = "SQL validation failed for ${block.database}",
                        details = mapOf(
                            "database" to block.database,
                            "errorType" to errorType,
                            "errors" to tableValidation.errors
                        ),
                        error = tableValidation.errors.joinToString("; ")
                    )

                    // Step 5: Revise SQL
                    onProgress("🔄 SQL validation failed ($errorType), invoking SqlReviseAgent...")

                    renderer.renderChatDBStep(
                        stepType = ChatDBStepType.REVISE_SQL,
                        status = ChatDBStepStatus.IN_PROGRESS,
                        title = "Revising SQL for ${block.database}..."
                    )

                    val relevantSchema = buildSchemaDescriptionForDatabase(block.database, merged)
                    val revisionInput = SqlRevisionInput(
                        originalQuery = task.query,
                        failedSql = block.sql,
                        errorMessage = tableValidation.errors.joinToString("; "),
                        schemaDescription = relevantSchema,
                        maxAttempts = maxRevisionAttempts
                    )

                    val revisionResult = sqlReviseAgent.execute(revisionInput) { progress ->
                        onProgress(progress)
                    }

                    revisionAttempts += revisionResult.metadata["attempts"]?.toIntOrNull() ?: 0

                    if (revisionResult.success) {
                        validatedBlocks.add(SqlBlock(block.database, revisionResult.content))
                        renderer.renderChatDBStep(
                            stepType = ChatDBStepType.REVISE_SQL,
                            status = ChatDBStepStatus.SUCCESS,
                            title = "SQL revised successfully for ${block.database}",
                            details = mapOf(
                                "database" to block.database,
                                "attempts" to revisionAttempts,
                                "sql" to revisionResult.content
                            )
                        )
                        onProgress("✅ SQL revised successfully")
                    } else {
                        renderer.renderChatDBStep(
                            stepType = ChatDBStepType.REVISE_SQL,
                            status = ChatDBStepStatus.ERROR,
                            title = "SQL revision failed for ${block.database}",
                            error = revisionResult.content
                        )
                        errors.add("[${block.database}] SQL revision failed: ${revisionResult.content}")
                    }
                } else {
                    validatedBlocks.add(block)
                }
            }

            if (!hasValidationErrors) {
                renderer.renderChatDBStep(
                    stepType = ChatDBStepType.VALIDATE_SQL,
                    status = ChatDBStepStatus.SUCCESS,
                    title = "SQL validation passed for all databases"
                )
            }

            sqlBlocks = validatedBlocks
            generatedSql = sqlBlocks.joinToString("\n\n") { "-- database: ${it.database}\n${it.sql}" }

            // Step 6: Execute SQL on target databases with retry
            for (sqlBlock in sqlBlocks) {
                val dbName = sqlBlock.database
                var sql = sqlBlock.sql
                val connection = databaseConnections[dbName]

                if (connection == null) {
                    errors.add("Database '$dbName' not connected")
                    continue
                }

                // Detect SQL operation type
                val operationType = sqlValidator.detectSqlType(sql)
                val isWriteOperation = operationType.requiresApproval()
                val isHighRisk = operationType.isHighRisk()

                // If write operation, perform dry run first, then request approval
                if (isWriteOperation) {
                    val affectedTables = extractTablesFromSql(sql, merged.databases[dbName])

                    // Step: Dry run to validate SQL before asking for approval
                    renderer.renderChatDBStep(
                        stepType = ChatDBStepType.DRY_RUN,
                        status = ChatDBStepStatus.IN_PROGRESS,
                        title = "Validating ${operationType.name} operation...",
                        details = mapOf(
                            "database" to dbName,
                            "operationType" to operationType.name,
                            "sql" to sql
                        )
                    )
                    onProgress("🔍 Performing dry run validation...")

                    var dryRunResult = connection.dryRun(sql)

                    if (!dryRunResult.isValid) {
                        // Dry run failed - SQL has errors
                        renderer.renderChatDBStep(
                            stepType = ChatDBStepType.DRY_RUN,
                            status = ChatDBStepStatus.ERROR,
                            title = "Dry run failed: SQL validation error",
                            details = mapOf(
                                "database" to dbName,
                                "operationType" to operationType.name,
                                "sql" to sql,
                                "errors" to dryRunResult.errors
                            ),
                            error = dryRunResult.message ?: dryRunResult.errors.firstOrNull() ?: "Unknown error"
                        )
                        errors.add("[$dbName] Dry run failed: ${dryRunResult.message}")

                        // Try to revise SQL based on dry run error
                        onProgress("🔄 SQL validation failed, attempting to revise...")

                        renderer.renderChatDBStep(
                            stepType = ChatDBStepType.REVISE_SQL,
                            status = ChatDBStepStatus.IN_PROGRESS,
                            title = "Revising SQL based on validation error..."
                        )

                        val relevantSchema = buildSchemaDescriptionForDatabase(dbName, merged)
                        val revisionInput = SqlRevisionInput(
                            originalQuery = task.query,
                            failedSql = sql,
                            errorMessage = "Dry run error: ${dryRunResult.message}",
                            schemaDescription = relevantSchema,
                            maxAttempts = maxRevisionAttempts
                        )

                        val revisionResult = sqlReviseAgent.execute(revisionInput) { progress ->
                            onProgress(progress)
                        }

                        if (revisionResult.success) {
                            sql = revisionResult.content
                            revisionAttempts++

                            renderer.renderChatDBStep(
                                stepType = ChatDBStepType.REVISE_SQL,
                                status = ChatDBStepStatus.SUCCESS,
                                title = "SQL revised successfully",
                                details = mapOf("database" to dbName, "sql" to sql)
                            )

                            // Re-run dry run with revised SQL
                            val revisedDryRunResult = connection.dryRun(sql)
                            if (!revisedDryRunResult.isValid) {
                                renderer.renderChatDBStep(
                                    stepType = ChatDBStepType.DRY_RUN,
                                    status = ChatDBStepStatus.ERROR,
                                    title = "Revised SQL still has errors",
                                    error = revisedDryRunResult.message
                                )
                                errors.add("[$dbName] Revised SQL still invalid: ${revisedDryRunResult.message}")
                                continue
                            }
                            // Update dryRunResult with the successful revised result
                            dryRunResult = revisedDryRunResult

                            // Render success for revised dry run
                            val estimatedInfo = if (dryRunResult.estimatedRows != null) {
                                " (estimated ${dryRunResult.estimatedRows} row(s) affected)"
                            } else ""
                            renderer.renderChatDBStep(
                                stepType = ChatDBStepType.DRY_RUN,
                                status = ChatDBStepStatus.SUCCESS,
                                title = "Revised SQL passed validation$estimatedInfo",
                                details = mapOf(
                                    "database" to dbName,
                                    "operationType" to operationType.name,
                                    "sql" to sql,
                                    "estimatedRows" to (dryRunResult.estimatedRows ?: "unknown"),
                                    "warnings" to dryRunResult.warnings
                                )
                            )
                            onProgress("✅ Revised SQL validation passed$estimatedInfo")
                        } else {
                            renderer.renderChatDBStep(
                                stepType = ChatDBStepType.REVISE_SQL,
                                status = ChatDBStepStatus.ERROR,
                                title = "SQL revision failed",
                                error = revisionResult.content
                            )
                            continue
                        }
                    } else {
                        // Dry run succeeded
                        val estimatedInfo = if (dryRunResult.estimatedRows != null) {
                            " (estimated ${dryRunResult.estimatedRows} row(s) affected)"
                        } else ""

                        renderer.renderChatDBStep(
                            stepType = ChatDBStepType.DRY_RUN,
                            status = ChatDBStepStatus.SUCCESS,
                            title = "Dry run passed$estimatedInfo",
                            details = mapOf(
                                "database" to dbName,
                                "operationType" to operationType.name,
                                "sql" to sql,
                                "estimatedRows" to (dryRunResult.estimatedRows ?: "unknown"),
                                "warnings" to dryRunResult.warnings
                            )
                        )
                        onProgress("✅ Dry run validation passed$estimatedInfo")
                    }

                    // Now request user approval (with the latest dryRunResult)
                    val approved = requestSqlApproval(
                        sql = sql,
                        operationType = operationType,
                        affectedTables = affectedTables,
                        isHighRisk = isHighRisk,
                        dryRunResult = dryRunResult,
                        onProgress = onProgress
                    )

                    if (!approved) {
                        renderer.renderChatDBStep(
                            stepType = ChatDBStepType.EXECUTE_WRITE,
                            status = ChatDBStepStatus.REJECTED,
                            title = "Write operation rejected by user",
                            details = mapOf(
                                "database" to dbName,
                                "operationType" to operationType.name,
                                "sql" to sql
                            )
                        )
                        errors.add("[$dbName] Write operation rejected by user: ${operationType.name}")
                        continue
                    }

                    // Execute write operation
                    renderer.renderChatDBStep(
                        stepType = ChatDBStepType.EXECUTE_WRITE,
                        status = ChatDBStepStatus.APPROVED,
                        title = "Write operation approved, executing...",
                        details = mapOf(
                            "database" to dbName,
                            "operationType" to operationType.name,
                            "sql" to sql
                        )
                    )
                    onProgress("✅ Write operation approved, executing...")

                    try {
                        val updateResult = connection.executeUpdate(sql)

                        if (updateResult.success) {
                            renderer.renderChatDBStep(
                                stepType = ChatDBStepType.EXECUTE_WRITE,
                                status = ChatDBStepStatus.SUCCESS,
                                title = "Write operation completed on $dbName",
                                details = mapOf(
                                    "database" to dbName,
                                    "operationType" to operationType.name,
                                    "affectedRows" to updateResult.affectedRows,
                                    "message" to (updateResult.message ?: "")
                                )
                            )
                            onProgress("✅ ${operationType.name} completed: ${updateResult.affectedRows} row(s) affected")

                            // Create a synthetic QueryResult for write operations
                            queryResults[dbName] = QueryResult(
                                columns = listOf("Operation", "Affected Rows", "Status"),
                                rows = listOf(listOf(operationType.name, updateResult.affectedRows.toString(), "Success")),
                                rowCount = 1
                            )
                        } else {
                            renderer.renderChatDBStep(
                                stepType = ChatDBStepType.EXECUTE_WRITE,
                                status = ChatDBStepStatus.ERROR,
                                title = "Write operation failed on $dbName",
                                error = updateResult.message ?: "Unknown error"
                            )
                            errors.add("[$dbName] Write operation failed: ${updateResult.message}")
                        }
                    } catch (e: Exception) {
                        val errorMsg = e.message ?: "Unknown execution error"
                        renderer.renderChatDBStep(
                            stepType = ChatDBStepType.EXECUTE_WRITE,
                            status = ChatDBStepStatus.ERROR,
                            title = "Write operation failed on $dbName",
                            error = errorMsg
                        )
                        errors.add("[$dbName] Write operation failed: $errorMsg")
                    }
                    continue
                }

                // Regular SELECT query execution with retry
                var executionRetries = 0
                var lastExecutionError: String? = null
                var result: QueryResult? = null

                while (executionRetries < maxExecutionRetries && result == null) {
                    renderer.renderChatDBStep(
                        stepType = ChatDBStepType.EXECUTE_SQL,
                        status = ChatDBStepStatus.IN_PROGRESS,
                        title = "Executing on $dbName${if (executionRetries > 0) " (retry $executionRetries)" else ""}...",
                        details = mapOf(
                            "database" to dbName,
                            "sql" to sql,
                            "attempt" to (executionRetries + 1)
                        )
                    )
                    onProgress("⚡ Executing SQL on $dbName${if (executionRetries > 0) " (retry $executionRetries)" else ""}...")

                    try {
                        result = connection.executeQuery(sql)
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
                        onProgress("✅ Query returned ${result.rowCount} rows from $dbName")
                    } catch (e: Exception) {
                        lastExecutionError = e.message ?: "Unknown execution error"
                        logger.warn { "Query execution failed on $dbName (attempt ${executionRetries + 1}): $lastExecutionError" }

                        renderer.renderChatDBStep(
                            stepType = ChatDBStepType.EXECUTE_SQL,
                            status = ChatDBStepStatus.ERROR,
                            title = "Query execution failed on $dbName",
                            details = mapOf(
                                "database" to dbName,
                                "attempt" to (executionRetries + 1),
                                "maxAttempts" to maxExecutionRetries
                            ),
                            error = lastExecutionError
                        )

                        // Try to revise SQL based on execution error
                        if (executionRetries < maxExecutionRetries - 1) {
                            onProgress("🔄 Attempting to fix SQL based on execution error...")

                            renderer.renderChatDBStep(
                                stepType = ChatDBStepType.REVISE_SQL,
                                status = ChatDBStepStatus.IN_PROGRESS,
                                title = "Revising SQL based on execution error..."
                            )

                            val relevantSchema = buildSchemaDescriptionForDatabase(dbName, merged)
                            val revisionInput = SqlRevisionInput(
                                originalQuery = task.query,
                                failedSql = sql,
                                errorMessage = "Execution error: $lastExecutionError",
                                schemaDescription = relevantSchema,
                                maxAttempts = 1
                            )

                            val revisionResult = sqlReviseAgent.execute(revisionInput) { progress ->
                                onProgress(progress)
                            }

                            if (revisionResult.success && revisionResult.content != sql) {
                                sql = revisionResult.content
                                revisionAttempts++

                                renderer.renderChatDBStep(
                                    stepType = ChatDBStepType.REVISE_SQL,
                                    status = ChatDBStepStatus.SUCCESS,
                                    title = "SQL revised based on execution error",
                                    details = mapOf("database" to dbName, "sql" to sql)
                                )
                                onProgress("🔧 SQL revised, retrying execution...")
                            } else {
                                renderer.renderChatDBStep(
                                    stepType = ChatDBStepType.REVISE_SQL,
                                    status = ChatDBStepStatus.WARNING,
                                    title = "SQL revision did not help",
                                    error = "Revision did not produce a different SQL"
                                )
                                break
                            }
                        }
                        executionRetries++
                    }
                }

                if (result == null && lastExecutionError != null) {
                    errors.add("[$dbName] Query execution failed after $executionRetries retries: $lastExecutionError")
                }
            }

            // Step 7: Generate visualization if requested
            val combinedResult = combineResults(queryResults)
            if (task.generateVisualization && combinedResult.rowCount > 0) {
                renderer.renderChatDBStep(
                    stepType = ChatDBStepType.GENERATE_VISUALIZATION,
                    status = ChatDBStepStatus.IN_PROGRESS,
                    title = "Generating visualization..."
                )
                onProgress("📈 Generating visualization...")

                plotDslCode = generateVisualization(task.query, combinedResult, onProgress)

                if (plotDslCode != null) {
                    renderer.renderChatDBStep(
                        stepType = ChatDBStepType.GENERATE_VISUALIZATION,
                        status = ChatDBStepStatus.SUCCESS,
                        title = "Visualization generated",
                        details = mapOf("code" to plotDslCode)
                    )
                } else {
                    renderer.renderChatDBStep(
                        stepType = ChatDBStepType.GENERATE_VISUALIZATION,
                        status = ChatDBStepStatus.WARNING,
                        title = "Visualization not generated"
                    )
                }
            }

            // Step 8: Final result
            val success = queryResults.isNotEmpty()

            val resultMessage = buildResultMessage(
                success = success,
                generatedSql = generatedSql,
                queryResults = queryResults,
                combinedResult = combinedResult,
                revisionAttempts = revisionAttempts,
                plotDslCode = plotDslCode,
                errors = errors
            )

            renderer.renderChatDBStep(
                stepType = ChatDBStepType.FINAL_RESULT,
                status = if (success) ChatDBStepStatus.SUCCESS else ChatDBStepStatus.ERROR,
                title = if (success) "Query completed on ${queryResults.size} database(s)" else "Query failed",
                details = mapOf(
                    "databases" to queryResults.keys.toList(),
                    "totalRows" to combinedResult.rowCount,
                    "columns" to combinedResult.columns,
                    "previewRows" to combinedResult.rows.take(10),
                    "revisionAttempts" to revisionAttempts,
                    "errors" to errors
                )
            )

            // Render final message
            if (success) {
                renderer.renderLLMResponseStart()
                renderer.renderLLMResponseChunk(resultMessage)
                renderer.renderLLMResponseEnd()
            } else {
                renderer.renderError(resultMessage)
            }

            return MultiDatabaseChatDBResult(
                success = success,
                message = resultMessage,
                generatedSql = generatedSql,
                queryResult = combinedResult,
                queryResultsByDatabase = queryResults,
                targetDatabases = targetDatabases,
                plotDslCode = plotDslCode,
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
            renderer.renderError("Query failed: ${e.message}")
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
     * Build schema context for multi-database prompt with schema linking results
     */
    private fun buildMultiDatabaseSchemaContext(
        merged: MergedDatabaseSchema,
        query: String,
        linkingResults: Map<String, SchemaLinkingResult> = emptyMap()
    ): String {
        val sb = StringBuilder()
        sb.append("=== AVAILABLE DATABASES AND TABLES ===\n\n")

        for ((dbId, schema) in merged.databases) {
            val displayName = databaseConfigs[dbId]?.databaseName ?: dbId
            val linkingResult = linkingResults[dbId]
            val relevantTables = linkingResult?.relevantTables?.toSet() ?: schema.tables.map { it.name }.toSet()

            sb.append("DATABASE: $dbId ($displayName)\n")
            sb.append("-".repeat(40)).append("\n")

            // Show relevant tables first (if schema linking was performed)
            val sortedTables = if (linkingResult != null) {
                schema.tables.sortedByDescending { it.name in relevantTables }
            } else {
                schema.tables
            }

            for (table in sortedTables) {
                val isRelevant = table.name in relevantTables
                val marker = if (isRelevant && linkingResult != null) " [RELEVANT]" else ""
                sb.append("  Table: ${table.name}$marker\n")
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
     * Build schema description for a specific database (for SQL revision)
     */
    private fun buildSchemaDescriptionForDatabase(dbId: String, merged: MergedDatabaseSchema): String {
        val schema = merged.databases[dbId] ?: return ""
        val displayName = databaseConfigs[dbId]?.databaseName ?: dbId

        return buildString {
            appendLine("## Database Schema: $displayName (USE ONLY THESE TABLES)")
            appendLine()
            for (table in schema.tables) {
                appendLine("Table: ${table.name}")
                appendLine("Columns: ${table.columns.joinToString(", ") { "${it.name} (${it.type})" }}")
                appendLine()
            }
        }
    }

    /**
     * Build result message for final output
     */
    private fun buildResultMessage(
        success: Boolean,
        generatedSql: String?,
        queryResults: Map<String, QueryResult>,
        combinedResult: QueryResult,
        revisionAttempts: Int,
        plotDslCode: String?,
        errors: List<String>
    ): String {
        return if (success) {
            buildString {
                appendLine("## ✅ Query Executed Successfully")
                appendLine()

                if (queryResults.size > 1) {
                    appendLine("**Databases Queried:** ${queryResults.keys.joinToString(", ")}")
                    appendLine()
                }

                if (generatedSql != null) {
                    appendLine("**Executed SQL:**")
                    appendLine("```sql")
                    appendLine(generatedSql)
                    appendLine("```")
                    appendLine()
                }

                if (revisionAttempts > 0) {
                    appendLine("*Note: SQL was revised $revisionAttempts time(s) to fix validation/execution errors*")
                    appendLine()
                }

                appendLine("**Results** (${combinedResult.rowCount} row${if (combinedResult.rowCount != 1) "s" else ""}):")
                appendLine()
                appendLine(combinedResult.toTableString())

                if (plotDslCode != null) {
                    appendLine()
                    appendLine("**Visualization:**")
                    appendLine("```plotdsl")
                    appendLine(plotDslCode)
                    appendLine("```")
                }
            }
        } else {
            buildString {
                appendLine("## ❌ Query Failed")
                appendLine()
                appendLine("**Errors:**")
                errors.forEach { error ->
                    appendLine("- $error")
                }
                if (generatedSql != null) {
                    appendLine()
                    appendLine("**Failed SQL:**")
                    appendLine("```sql")
                    appendLine(generatedSql)
                    appendLine("```")
                }
            }
        }
    }

    /**
     * Generate visualization for query results using PlotDSLAgent
     */
    private suspend fun generateVisualization(
        query: String,
        result: QueryResult,
        onProgress: (String) -> Unit
    ): String? {
        // Check if PlotDSLAgent is available on this platform
        if (!plotDSLAgent.isAvailable) {
            logger.info { "PlotDSLAgent not available on this platform, skipping visualization" }
            return null
        }

        // Build description for PlotDSLAgent
        val description = buildString {
            appendLine("Create a visualization for the following database query result:")
            appendLine()
            appendLine("**Original Query**: $query")
            appendLine()
            appendLine("**Data** (${result.rowCount} rows, columns: ${result.columns.joinToString(", ")}):")
            appendLine("```csv")
            appendLine(result.toCsvString())
            appendLine("```")
            appendLine()
            appendLine("Choose the most appropriate chart type based on the data structure.")
        }

        try {
            val plotContext = PlotDSLContext(description = description)
            val agentResult = plotDSLAgent.execute(plotContext, onProgress)

            if (agentResult.success) {
                // Extract PlotDSL code from the result
                val content = agentResult.content
                val codeFence = CodeFence.parse(content)
                if (codeFence.languageId.lowercase() == "plotdsl" && codeFence.text.isNotBlank()) {
                    return codeFence.text.trim()
                }

                // Try to find plotdsl block manually
                val plotPattern = Regex("```plotdsl\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
                val match = plotPattern.find(content)
                return match?.groupValues?.get(1)?.trim()
            } else {
                logger.warn { "PlotDSLAgent failed: ${agentResult.content}" }
                return null
            }
        } catch (e: Exception) {
            logger.error(e) { "Visualization generation failed" }
            return null
        }
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

    /**
     * Request user approval for SQL write operation
     * Returns true if approved, false if rejected
     */
    private suspend fun requestSqlApproval(
        sql: String,
        operationType: SqlOperationType,
        affectedTables: List<String>,
        isHighRisk: Boolean,
        dryRunResult: DryRunResult? = null,
        onProgress: (String) -> Unit
    ): Boolean {
        val riskLevel = if (isHighRisk) "⚠️ HIGH RISK" else "⚡ Write Operation"
        val dryRunInfo = if (dryRunResult?.estimatedRows != null) {
            " (dry run: ${dryRunResult.estimatedRows} row(s) would be affected)"
        } else ""
        onProgress("$riskLevel: ${operationType.name} requires approval$dryRunInfo")

        return suspendCancellableCoroutine { continuation ->
            renderer.renderSqlApprovalRequest(
                sql = sql,
                operationType = operationType,
                affectedTables = affectedTables,
                isHighRisk = isHighRisk,
                dryRunResult = dryRunResult,
                onApprove = {
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                },
                onReject = {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            )
        }
    }

    /**
     * Extract table names from SQL statement
     */
    private fun extractTablesFromSql(sql: String, schema: DatabaseSchema?): List<String> {
        if (schema == null) return emptyList()

        val tableNames = schema.tables.map { it.name.lowercase() }.toSet()
        val sqlLower = sql.lowercase()

        return tableNames.filter { tableName ->
            // Check for common SQL patterns that reference tables
            sqlLower.contains(" $tableName ") ||
            sqlLower.contains(" $tableName;") ||
            sqlLower.contains(" $tableName\n") ||
            sqlLower.contains("from $tableName") ||
            sqlLower.contains("into $tableName") ||
            sqlLower.contains("update $tableName") ||
            sqlLower.contains("table $tableName") ||
            sqlLower.contains("join $tableName")
        }.map { tableName ->
            // Return original case from schema
            schema.tables.find { it.name.lowercase() == tableName }?.name ?: tableName
        }
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

