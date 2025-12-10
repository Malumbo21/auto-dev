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
import cc.unitmesh.agent.subagent.SqlRevisionInput
import cc.unitmesh.devins.parser.CodeFence
import cc.unitmesh.llm.KoogLLMService

/**
 * ChatDB Agent Executor - Text2SQL Agent with Schema Linking and Revise Agent
 * 
 * Features:
 * 1. Schema Linking - Keyword-based search to find relevant tables/columns
 * 2. SQL Generation - LLM generates SQL from natural language
 * 3. SQL Validation - JSqlParser validates SQL syntax and safety
 * 4. Revise Agent - Self-correction loop for fixing SQL errors
 * 5. Query Execution - Execute validated SQL and return results
 * 6. Visualization - Optional PlotDSL generation for data visualization
 */
class ChatDBAgentExecutor(
    projectPath: String,
    llmService: KoogLLMService,
    toolOrchestrator: ToolOrchestrator,
    renderer: CodingAgentRenderer,
    private val databaseConnection: DatabaseConnection,
    maxIterations: Int = 10,
    enableLLMStreaming: Boolean = true,
    useLlmSchemaLinker: Boolean = true
) : BaseAgentExecutor(
    projectPath = projectPath,
    llmService = llmService,
    toolOrchestrator = toolOrchestrator,
    renderer = renderer,
    maxIterations = maxIterations,
    enableLLMStreaming = enableLLMStreaming
) {
    private val logger = getLogger("ChatDBAgentExecutor")
    private val keywordSchemaLinker = KeywordSchemaLinker()
    private val schemaLinker: SchemaLinker = if (useLlmSchemaLinker) {
        val fallbackLinker = LlmSchemaLinker(llmService, databaseConnection, keywordSchemaLinker)
        DatabaseContentSchemaLinker(llmService, databaseConnection, fallbackLinker)
    } else {
        keywordSchemaLinker
    }

    private val sqlValidator = SqlValidator()
    private val sqlReviseAgent = SqlReviseAgent(llmService, sqlValidator)
    private val maxRevisionAttempts = 3
    private val maxExecutionRetries = 3

    suspend fun execute(
        task: ChatDBTask,
        systemPrompt: String,
        onProgress: (String) -> Unit = {}
    ): ChatDBResult {
        resetExecution()
        conversationManager = ConversationManager(llmService, systemPrompt)
        
        val errors = mutableListOf<String>()
        var generatedSql: String? = null
        var queryResult: QueryResult? = null
        var plotDslCode: String? = null
        var revisionAttempts = 0

        try {
            // Step 1: Get database schema
            renderer.renderChatDBStep(
                stepType = ChatDBStepType.FETCH_SCHEMA,
                status = ChatDBStepStatus.IN_PROGRESS,
                title = "Fetching database schema..."
            )
            onProgress("📊 Fetching database schema...")

            val schema = task.schema ?: databaseConnection.getSchema()
            logger.info { "Database has ${schema.tables.size} tables: ${schema.tables.map { it.name }}" }

            renderer.renderChatDBStep(
                stepType = ChatDBStepType.FETCH_SCHEMA,
                status = ChatDBStepStatus.SUCCESS,
                title = "Database schema fetched",
                details = mapOf(
                    "totalTables" to schema.tables.size,
                    "tables" to schema.tables.map { it.name }
                )
            )

            // Step 2: Schema Linking
            renderer.renderChatDBStep(
                stepType = ChatDBStepType.SCHEMA_LINKING,
                status = ChatDBStepStatus.IN_PROGRESS,
                title = "Performing schema linking..."
            )
            onProgress("🔗 Performing schema linking...")

            val linkingResult = schemaLinker.link(task.query, schema)
            logger.info { "Schema linking found ${linkingResult.relevantTables.size} relevant tables: ${linkingResult.relevantTables}" }
            logger.info { "Schema linking keywords: ${linkingResult.keywords}" }

            renderer.renderChatDBStep(
                stepType = ChatDBStepType.SCHEMA_LINKING,
                status = ChatDBStepStatus.SUCCESS,
                title = "Schema linking completed",
                details = mapOf(
                    "relevantTables" to linkingResult.relevantTables,
                    "keywords" to linkingResult.keywords
                )
            )

            // Step 3: Build context with relevant schema
            // If schema linking found too few tables, use all tables to avoid missing important ones
            val effectiveLinkingResult = if (linkingResult.relevantTables.size < 2 && schema.tables.size <= 10) {
                logger.info { "Schema linking found few tables, using all ${schema.tables.size} tables" }
                linkingResult.copy(relevantTables = schema.tables.map { it.name })
            } else {
                linkingResult
            }

            val relevantSchema = buildRelevantSchemaDescription(schema, effectiveLinkingResult)
            val initialMessage = buildInitialUserMessage(task, relevantSchema, effectiveLinkingResult)

            // Step 4: Generate SQL with LLM
            renderer.renderChatDBStep(
                stepType = ChatDBStepType.GENERATE_SQL,
                status = ChatDBStepStatus.IN_PROGRESS,
                title = "Generating SQL query..."
            )
            onProgress("🤖 Generating SQL query...")

            val llmResponse = StringBuilder()
            val response = getLLMResponse(initialMessage, compileDevIns = false) { chunk ->
                onProgress(chunk)
            }
            llmResponse.append(response)
            
            // Step 5: Extract SQL from response
            generatedSql = extractSqlFromResponse(llmResponse.toString())
            if (generatedSql == null) {
                errors.add("Failed to extract SQL from LLM response")
                renderer.renderChatDBStep(
                    stepType = ChatDBStepType.GENERATE_SQL,
                    status = ChatDBStepStatus.ERROR,
                    title = "Failed to extract SQL",
                    error = "Could not find SQL code block in LLM response"
                )
                return buildResult(false, errors, null, null, null, 0)
            }

            renderer.renderChatDBStep(
                stepType = ChatDBStepType.GENERATE_SQL,
                status = ChatDBStepStatus.SUCCESS,
                title = "SQL query generated",
                details = mapOf("sql" to generatedSql)
            )

            // Step 6: Validate SQL syntax and table names using SqlReviseAgent
            renderer.renderChatDBStep(
                stepType = ChatDBStepType.VALIDATE_SQL,
                status = ChatDBStepStatus.IN_PROGRESS,
                title = "Validating SQL..."
            )

            var validatedSql = generatedSql

            // Get all table names from schema for whitelist validation
            val allTableNames = schema.tables.map { it.name }.toSet()

            // First validate syntax, then validate table names
            val syntaxValidation = sqlValidator.validate(validatedSql!!)
            val tableValidation = if (syntaxValidation.isValid) {
                sqlValidator.validateWithTableWhitelist(validatedSql, allTableNames)
            } else {
                syntaxValidation
            }

            if (!tableValidation.isValid) {
                val errorType = if (!syntaxValidation.isValid) "syntax" else "table name"

                renderer.renderChatDBStep(
                    stepType = ChatDBStepType.VALIDATE_SQL,
                    status = ChatDBStepStatus.ERROR,
                    title = "SQL validation failed",
                    details = mapOf(
                        "errorType" to errorType,
                        "errors" to tableValidation.errors
                    ),
                    error = tableValidation.errors.joinToString("; ")
                )

                onProgress("🔄 SQL validation failed ($errorType), invoking SqlReviseAgent...")

                renderer.renderChatDBStep(
                    stepType = ChatDBStepType.REVISE_SQL,
                    status = ChatDBStepStatus.IN_PROGRESS,
                    title = "Revising SQL..."
                )

                val revisionInput = SqlRevisionInput(
                    originalQuery = task.query,
                    failedSql = validatedSql,
                    errorMessage = tableValidation.errors.joinToString("; "),
                    schemaDescription = relevantSchema,
                    maxAttempts = maxRevisionAttempts
                )

                val revisionResult = sqlReviseAgent.execute(revisionInput) { progress ->
                    onProgress(progress)
                }

                revisionAttempts = revisionResult.metadata["attempts"]?.toIntOrNull() ?: 0

                if (revisionResult.success) {
                    validatedSql = revisionResult.content

                    renderer.renderChatDBStep(
                        stepType = ChatDBStepType.REVISE_SQL,
                        status = ChatDBStepStatus.SUCCESS,
                        title = "SQL revised successfully",
                        details = mapOf(
                            "attempts" to revisionAttempts,
                            "sql" to validatedSql
                        )
                    )

                    onProgress("✅ SQL revised successfully after $revisionAttempts attempts")
                } else {
                    renderer.renderChatDBStep(
                        stepType = ChatDBStepType.REVISE_SQL,
                        status = ChatDBStepStatus.ERROR,
                        title = "SQL revision failed",
                        error = revisionResult.content
                    )
                    errors.add("SQL revision failed: ${revisionResult.content}")
                }
            } else {
                renderer.renderChatDBStep(
                    stepType = ChatDBStepType.VALIDATE_SQL,
                    status = ChatDBStepStatus.SUCCESS,
                    title = "SQL validation passed"
                )
            }

            generatedSql = validatedSql

            // Step 7: Execute SQL with retry loop for execution errors
            if (generatedSql != null) {
                var executionRetries = 0
                var lastExecutionError: String? = null

                while (executionRetries < maxExecutionRetries && queryResult == null) {
                    renderer.renderChatDBStep(
                        stepType = ChatDBStepType.EXECUTE_SQL,
                        status = ChatDBStepStatus.IN_PROGRESS,
                        title = "Executing SQL query${if (executionRetries > 0) " (retry $executionRetries)" else ""}...",
                        details = mapOf(
                            "attempt" to (executionRetries + 1),
                            "sql" to generatedSql!!
                        )
                    )

                    onProgress("⚡ Executing SQL query${if (executionRetries > 0) " (retry $executionRetries)" else ""}...")

                    try {
                        queryResult = databaseConnection.executeQuery(generatedSql!!)

                        renderer.renderChatDBStep(
                            stepType = ChatDBStepType.EXECUTE_SQL,
                            status = ChatDBStepStatus.SUCCESS,
                            title = "Query executed successfully",
                            details = mapOf(
                                "rowCount" to queryResult.rowCount,
                                "columns" to queryResult.columns
                            )
                        )

                        onProgress("✅ Query returned ${queryResult.rowCount} rows")
                    } catch (e: Exception) {
                        lastExecutionError = e.message ?: "Unknown execution error"
                        logger.warn { "Query execution failed (attempt ${executionRetries + 1}): $lastExecutionError" }

                        renderer.renderChatDBStep(
                            stepType = ChatDBStepType.EXECUTE_SQL,
                            status = ChatDBStepStatus.ERROR,
                            title = "Query execution failed",
                            details = mapOf(
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

                            val revisionInput = SqlRevisionInput(
                                originalQuery = task.query,
                                failedSql = generatedSql!!,
                                errorMessage = "Execution error: $lastExecutionError",
                                schemaDescription = relevantSchema,
                                maxAttempts = 1
                            )

                            val revisionResult = sqlReviseAgent.execute(revisionInput) { progress ->
                                onProgress(progress)
                            }

                            if (revisionResult.success && revisionResult.content != generatedSql) {
                                generatedSql = revisionResult.content
                                revisionAttempts++

                                renderer.renderChatDBStep(
                                    stepType = ChatDBStepType.REVISE_SQL,
                                    status = ChatDBStepStatus.SUCCESS,
                                    title = "SQL revised based on execution error",
                                    details = mapOf(
                                        "sql" to generatedSql
                                    )
                                )

                                onProgress("🔧 SQL revised, retrying execution...")
                            } else {
                                renderer.renderChatDBStep(
                                    stepType = ChatDBStepType.REVISE_SQL,
                                    status = ChatDBStepStatus.WARNING,
                                    title = "SQL revision did not help",
                                    error = "Revision did not produce a different SQL"
                                )
                                // Revision didn't help, break the loop
                                break
                            }
                        }
                        executionRetries++
                    }
                }

                if (queryResult == null && lastExecutionError != null) {
                    errors.add("Query execution failed after $executionRetries retries: $lastExecutionError")
                    logger.error { "Query execution failed after $executionRetries retries" }
                }
            }

            // Step 8: Generate visualization if requested
            if (task.generateVisualization && queryResult != null && !queryResult.isEmpty()) {
                renderer.renderChatDBStep(
                    stepType = ChatDBStepType.GENERATE_VISUALIZATION,
                    status = ChatDBStepStatus.IN_PROGRESS,
                    title = "Generating visualization..."
                )

                onProgress("📈 Generating visualization...")

                plotDslCode = generateVisualization(task.query, queryResult, onProgress)

                if (plotDslCode != null) {
                    renderer.renderChatDBStep(
                        stepType = ChatDBStepType.GENERATE_VISUALIZATION,
                        status = ChatDBStepStatus.SUCCESS,
                        title = "Visualization generated",
                        details = mapOf(
                            "code" to plotDslCode
                        )
                    )
                } else {
                    renderer.renderChatDBStep(
                        stepType = ChatDBStepType.GENERATE_VISUALIZATION,
                        status = ChatDBStepStatus.WARNING,
                        title = "Visualization not generated"
                    )
                }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "ChatDB execution failed" }
            errors.add("Execution failed: ${e.message}")
        }

        val result = buildResult(
            success = errors.isEmpty() && queryResult != null,
            errors = errors,
            generatedSql = generatedSql,
            queryResult = queryResult,
            plotDslCode = plotDslCode,
            revisionAttempts = revisionAttempts
        )

        // Render the final result to the timeline
        if (result.success) {
            renderer.renderChatDBStep(
                stepType = ChatDBStepType.FINAL_RESULT,
                status = ChatDBStepStatus.SUCCESS,
                title = "Query completed successfully",
                details = buildMap {
                    queryResult?.let {
                        put("rowCount", it.rowCount)
                        put("columns", it.columns)
                    }
                    if (revisionAttempts > 0) {
                        put("revisionAttempts", revisionAttempts)
                    }
                    plotDslCode?.let { put("visualization", it) }
                }
            )

            renderer.renderLLMResponseStart()
            renderer.renderLLMResponseChunk(result.message)
            renderer.renderLLMResponseEnd()
        } else {
            renderer.renderChatDBStep(
                stepType = ChatDBStepType.FINAL_RESULT,
                status = ChatDBStepStatus.ERROR,
                title = "Query failed",
                details = buildMap {
                    generatedSql?.let { put("sql", it) }
                },
                error = errors.joinToString("; ")
            )

            renderer.renderError(result.message)
        }

        return result
    }

    private fun resetExecution() {
        currentIteration = 0
    }

    /**
     * Build schema description with sample data for SQL generation
     * Based on RSL-SQL research: sample data helps LLM understand table semantics
     */
    private suspend fun buildRelevantSchemaDescription(
        schema: DatabaseSchema,
        linkingResult: SchemaLinkingResult
    ): String {
        val relevantTables = schema.tables.filter { it.name in linkingResult.relevantTables }
        return buildString {
            appendLine("## Database Schema (USE ONLY THESE TABLES)")
            appendLine()
            relevantTables.forEach { table ->
                appendLine("Table: ${table.name}")
                appendLine("Columns: ${table.columns.joinToString(", ") { "${it.name} (${it.type})" }}")

                // Add sample data to help LLM understand table content
                try {
                    val sampleRows = databaseConnection.getSampleRows(table.name, 2)
                    if (!sampleRows.isEmpty()) {
                        appendLine("Sample Data:")
                        appendLine("  ${sampleRows.columns.joinToString(" | ")}")
                        sampleRows.rows.take(2).forEach { row ->
                            appendLine("  ${row.joinToString(" | ") { it.take(50) }}")
                        }
                    }
                } catch (e: Exception) {
                    // Ignore sample data errors
                }
                appendLine()
            }
        }
    }

    private fun buildInitialUserMessage(
        task: ChatDBTask,
        schemaDescription: String,
        linkingResult: SchemaLinkingResult
    ): String {
        return buildString {
            appendLine("Generate a SQL query for: ${task.query}")
            appendLine()
            if (task.additionalContext.isNotBlank()) {
                appendLine("Context: ${task.additionalContext}")
                appendLine()
            }
            appendLine("ALLOWED TABLES (use ONLY these): ${linkingResult.relevantTables.joinToString(", ")}")
            appendLine()
            appendLine(schemaDescription)
            appendLine()
            appendLine("Max rows: ${task.maxRows}")
            appendLine()
            appendLine("CRITICAL RULES:")
            appendLine("1. Return ONLY ONE SQL statement - never multiple statements")
            appendLine("2. Choose the BEST matching table if multiple similar tables exist")
            appendLine("3. Wrap the SQL in a ```sql code block")
            appendLine("4. No comments, no explanations, just the SQL")
        }
    }

    private fun extractSqlFromResponse(response: String): String? {
        val codeFence = CodeFence.parse(response)
        if (codeFence.languageId.lowercase() == "sql" && codeFence.text.isNotBlank()) {
            return extractFirstStatement(codeFence.text.trim())
        }

        // Try to find SQL block manually
        val sqlPattern = Regex("```sql\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val match = sqlPattern.find(response)
        if (match != null) {
            return extractFirstStatement(match.groupValues[1].trim())
        }

        // Last resort: look for SELECT statement
        val selectPattern = Regex("(SELECT[\\s\\S]*?;)", RegexOption.IGNORE_CASE)
        val selectMatch = selectPattern.find(response)
        return selectMatch?.groupValues?.get(1)?.trim()
    }

    /**
     * Extract only the first SQL statement if LLM returns multiple statements.
     * This prevents "multiple statements" errors.
     */
    private fun extractFirstStatement(sql: String): String {
        // Remove SQL comments (-- style)
        val withoutComments = sql.lines()
            .filterNot { it.trim().startsWith("--") }
            .joinToString("\n")
            .trim()

        // If there are multiple statements separated by semicolons, take only the first
        val statements = withoutComments.split(";")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return if (statements.isNotEmpty()) {
            statements.first() + ";"
        } else {
            withoutComments
        }
    }

    private suspend fun generateVisualization(
        query: String,
        result: QueryResult,
        onProgress: (String) -> Unit
    ): String? {
        val visualizationPrompt = buildString {
            appendLine("Based on the following query result, generate a PlotDSL visualization:")
            appendLine()
            appendLine("**Original Query**: $query")
            appendLine()
            appendLine("**Query Result** (${result.rowCount} rows):")
            appendLine("```csv")
            appendLine(result.toCsvString())
            appendLine("```")
            appendLine()
            appendLine("Generate a PlotDSL chart that best visualizes this data.")
            appendLine("Choose an appropriate chart type (bar, line, scatter, etc.) based on the data.")
            appendLine("Wrap the PlotDSL code in a ```plotdsl code block.")
        }

        try {
            val response = getLLMResponse(visualizationPrompt, compileDevIns = false) { chunk ->
                onProgress(chunk)
            }

            val codeFence = CodeFence.parse(response)
            if (codeFence.languageId.lowercase() == "plotdsl" && codeFence.text.isNotBlank()) {
                return codeFence.text.trim()
            }

            // Try to find plotdsl block manually
            val plotPattern = Regex("```plotdsl\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            val match = plotPattern.find(response)
            return match?.groupValues?.get(1)?.trim()
        } catch (e: Exception) {
            logger.error(e) { "Visualization generation failed" }
            return null
        }
    }

    private fun buildResult(
        success: Boolean,
        errors: List<String>,
        generatedSql: String?,
        queryResult: QueryResult?,
        plotDslCode: String?,
        revisionAttempts: Int
    ): ChatDBResult {
        val message = if (success) {
            buildString {
                appendLine("## ✅ Query Executed Successfully")
                appendLine()

                // Show SQL that was executed
                if (generatedSql != null) {
                    appendLine("**Executed SQL:**")
                    appendLine("```sql")
                    appendLine(generatedSql)
                    appendLine("```")
                    appendLine()
                }

                // Show revision info if applicable
                if (revisionAttempts > 0) {
                    appendLine("*Note: SQL was revised $revisionAttempts time(s) to fix validation/execution errors*")
                    appendLine()
                }

                // Show query results
                if (queryResult != null) {
                    appendLine("**Results** (${queryResult.rowCount} row${if (queryResult.rowCount != 1) "s" else ""}):")
                    appendLine()
                    appendLine(queryResult.toTableString())
                }

                // Show visualization if generated
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

        return ChatDBResult(
            success = success,
            message = message,
            generatedSql = generatedSql,
            queryResult = queryResult,
            plotDslCode = plotDslCode,
            revisionAttempts = revisionAttempts,
            errors = errors
        )
    }

    override fun buildContinuationMessage(): String {
        return "Please continue with the database query based on the results above."
    }
}

