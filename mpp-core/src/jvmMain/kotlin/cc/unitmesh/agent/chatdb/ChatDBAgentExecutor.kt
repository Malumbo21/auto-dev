package cc.unitmesh.agent.chatdb

import cc.unitmesh.agent.conversation.ConversationManager
import cc.unitmesh.agent.database.*
import cc.unitmesh.agent.executor.BaseAgentExecutor
import cc.unitmesh.agent.logging.getLogger
import cc.unitmesh.agent.orchestrator.ToolExecutionContext
import cc.unitmesh.agent.orchestrator.ToolExecutionResult
import cc.unitmesh.agent.orchestrator.ToolOrchestrator
import cc.unitmesh.agent.render.CodingAgentRenderer
import cc.unitmesh.agent.state.ToolCall
import cc.unitmesh.agent.state.ToolExecutionState
import cc.unitmesh.agent.tool.ToolResult
import cc.unitmesh.agent.tool.schema.ToolResultFormatter
import cc.unitmesh.devins.parser.CodeFence
import cc.unitmesh.llm.KoogLLMService
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock

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
    enableLLMStreaming: Boolean = true
) : BaseAgentExecutor(
    projectPath = projectPath,
    llmService = llmService,
    toolOrchestrator = toolOrchestrator,
    renderer = renderer,
    maxIterations = maxIterations,
    enableLLMStreaming = enableLLMStreaming
) {
    private val logger = getLogger("ChatDBAgentExecutor")
    private val schemaLinker = SchemaLinker()
    private val sqlValidator = SqlValidator()
    private val maxRevisionAttempts = 3

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
            
            // Step 2: Schema Linking
            onProgress("🔗 Performing schema linking...")
            val linkingResult = schemaLinker.link(task.query, schema)
            logger.info { "Schema linking found ${linkingResult.relevantTables.size} relevant tables" }
            
            // Step 3: Build context with relevant schema
            val relevantSchema = buildRelevantSchemaDescription(schema, linkingResult)
            val initialMessage = buildInitialUserMessage(task, relevantSchema, linkingResult)
            
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
            
            // Step 6: Validate and revise SQL
            var validatedSql = generatedSql
            while (revisionAttempts < maxRevisionAttempts) {
                val validation = sqlValidator.validate(validatedSql!!)
                if (validation.isSafe) {
                    break
                }
                
                revisionAttempts++
                onProgress("🔄 Revising SQL (attempt $revisionAttempts)...")
                
                val revisionContext = SqlRevisionContext(
                    originalQuery = task.query,
                    failedSql = validatedSql,
                    errorMessage = validation.errors.joinToString("; "),
                    schemaDescription = relevantSchema
                )
                
                validatedSql = reviseSql(revisionContext, onProgress)
                if (validatedSql == null) {
                    errors.add("SQL revision failed after $revisionAttempts attempts")
                    break
                }
            }
            
            generatedSql = validatedSql
            
            // Step 7: Execute SQL
            if (generatedSql != null) {
                onProgress("⚡ Executing SQL query...")
                try {
                    queryResult = databaseConnection.executeQuery(generatedSql)
                    onProgress("✅ Query returned ${queryResult.rowCount} rows")
                } catch (e: Exception) {
                    errors.add("Query execution failed: ${e.message}")
                    logger.error(e) { "Query execution failed" }
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

    private fun buildRelevantSchemaDescription(
        schema: DatabaseSchema,
        linkingResult: SchemaLinkingResult
    ): String {
        val relevantTables = schema.tables.filter { it.name in linkingResult.relevantTables }
        return buildString {
            appendLine("## Relevant Database Schema")
            appendLine()
            relevantTables.forEach { table ->
                append(table.getDescription())
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
            appendLine("Please generate a SQL query for the following request:")
            appendLine()
            appendLine("**User Query**: ${task.query}")
            appendLine()
            if (task.additionalContext.isNotBlank()) {
                appendLine("**Additional Context**: ${task.additionalContext}")
                appendLine()
            }
            appendLine("**Maximum Rows**: ${task.maxRows}")
            appendLine()
            appendLine(schemaDescription)
            appendLine()
            appendLine("**Schema Linking Keywords**: ${linkingResult.keywords.joinToString(", ")}")
            appendLine("**Confidence**: ${String.format("%.2f", linkingResult.confidence)}")
            appendLine()
            appendLine("Please generate a safe, read-only SQL query. Wrap the SQL in a ```sql code block.")
        }
    }

    private fun extractSqlFromResponse(response: String): String? {
        val codeFence = CodeFence.parse(response)
        if (codeFence.languageId.lowercase() == "sql" && codeFence.text.isNotBlank()) {
            return codeFence.text.trim()
        }

        // Try to find SQL block manually
        val sqlPattern = Regex("```sql\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val match = sqlPattern.find(response)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        // Last resort: look for SELECT statement
        val selectPattern = Regex("(SELECT[\\s\\S]*?;)", RegexOption.IGNORE_CASE)
        val selectMatch = selectPattern.find(response)
        return selectMatch?.groupValues?.get(1)?.trim()
    }

    private suspend fun reviseSql(
        context: SqlRevisionContext,
        onProgress: (String) -> Unit
    ): String? {
        val revisionPrompt = buildString {
            appendLine("The following SQL query has errors and needs to be fixed:")
            appendLine()
            appendLine("**Original User Query**: ${context.originalQuery}")
            appendLine()
            appendLine("**Failed SQL**:")
            appendLine("```sql")
            appendLine(context.failedSql)
            appendLine("```")
            appendLine()
            appendLine("**Error**: ${context.errorMessage}")
            appendLine()
            appendLine("**Schema**:")
            appendLine(context.schemaDescription)
            appendLine()
            appendLine("Please fix the SQL and provide a corrected version. Wrap the SQL in a ```sql code block.")
        }

        try {
            val response = getLLMResponse(revisionPrompt, compileDevIns = false) { chunk ->
                onProgress(chunk)
            }
            return extractSqlFromResponse(response)
        } catch (e: Exception) {
            logger.error(e) { "SQL revision failed" }
            return null
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

