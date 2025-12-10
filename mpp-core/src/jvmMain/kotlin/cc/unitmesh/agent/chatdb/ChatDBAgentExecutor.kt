package cc.unitmesh.agent.chatdb

import cc.unitmesh.agent.conversation.ConversationManager
import cc.unitmesh.agent.database.*
import cc.unitmesh.agent.executor.BaseAgentExecutor
import cc.unitmesh.agent.logging.getLogger
import cc.unitmesh.agent.orchestrator.ToolOrchestrator
import cc.unitmesh.agent.render.CodingAgentRenderer
import cc.unitmesh.agent.subagent.JSqlParserValidator
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
        // Use DatabaseContentSchemaLinker for better accuracy (RSL-SQL approach)
        // It filters system tables and uses sample data for semantic matching
        DatabaseContentSchemaLinker(llmService, databaseConnection, keywordSchemaLinker)
    } else {
        keywordSchemaLinker
    }
    private val jsqlValidator = JSqlParserValidator()
    private val sqlReviseAgent = SqlReviseAgent(llmService, jsqlValidator)
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
            onProgress("📊 Fetching database schema...")
            val schema = task.schema ?: databaseConnection.getSchema()
            logger.info { "Database has ${schema.tables.size} tables: ${schema.tables.map { it.name }}" }

            // Step 2: Schema Linking
            onProgress("🔗 Performing schema linking...")
            val linkingResult = schemaLinker.link(task.query, schema)
            logger.info { "Schema linking found ${linkingResult.relevantTables.size} relevant tables: ${linkingResult.relevantTables}" }
            logger.info { "Schema linking keywords: ${linkingResult.keywords}" }

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
                return buildResult(false, errors, null, null, null, 0)
            }

            // Step 6: Validate SQL syntax and table names using SqlReviseAgent
            var validatedSql = generatedSql

            // Get all table names from schema for whitelist validation
            val allTableNames = schema.tables.map { it.name }.toSet()

            // First validate syntax, then validate table names
            val syntaxValidation = jsqlValidator.validate(validatedSql!!)
            val tableValidation = if (syntaxValidation.isValid) {
                jsqlValidator.validateWithTableWhitelist(validatedSql, allTableNames)
            } else {
                syntaxValidation
            }

            if (!tableValidation.isValid) {
                val errorType = if (!syntaxValidation.isValid) "syntax" else "table name"
                onProgress("🔄 SQL validation failed ($errorType), invoking SqlReviseAgent...")

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
                    onProgress("✅ SQL revised successfully after $revisionAttempts attempts")
                } else {
                    errors.add("SQL revision failed: ${revisionResult.content}")
                }
            }

            generatedSql = validatedSql

            // Step 7: Execute SQL with retry loop for execution errors
            if (generatedSql != null) {
                var executionRetries = 0
                var lastExecutionError: String? = null

                while (executionRetries < maxExecutionRetries && queryResult == null) {
                    onProgress("⚡ Executing SQL query${if (executionRetries > 0) " (retry $executionRetries)" else ""}...")
                    try {
                        queryResult = databaseConnection.executeQuery(generatedSql!!)
                        onProgress("✅ Query returned ${queryResult.rowCount} rows")
                    } catch (e: Exception) {
                        lastExecutionError = e.message ?: "Unknown execution error"
                        logger.warn { "Query execution failed (attempt ${executionRetries + 1}): $lastExecutionError" }

                        // Try to revise SQL based on execution error
                        if (executionRetries < maxExecutionRetries - 1) {
                            onProgress("🔄 Execution failed, attempting to fix SQL...")

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
                                onProgress("🔧 SQL revised, retrying execution...")
                            } else {
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
                onProgress("📈 Generating visualization...")
                plotDslCode = generateVisualization(task.query, queryResult, onProgress)
            }
            
        } catch (e: Exception) {
            logger.error(e) { "ChatDB execution failed" }
            errors.add("Execution failed: ${e.message}")
        }
        
        return buildResult(
            success = errors.isEmpty() && queryResult != null,
            errors = errors,
            generatedSql = generatedSql,
            queryResult = queryResult,
            plotDslCode = plotDslCode,
            revisionAttempts = revisionAttempts
        )
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
                appendLine("Query executed successfully!")
                if (queryResult != null) {
                    appendLine()
                    appendLine("**Results** (${queryResult.rowCount} rows):")
                    appendLine(queryResult.toTableString())
                }
                if (plotDslCode != null) {
                    appendLine()
                    appendLine("**Visualization**:")
                    appendLine("```plotdsl")
                    appendLine(plotDslCode)
                    appendLine("```")
                }
            }
        } else {
            "Query failed: ${errors.joinToString("; ")}"
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

