package cc.unitmesh.agent.subagent

import cc.unitmesh.agent.core.SubAgent
import cc.unitmesh.agent.logging.getLogger
import cc.unitmesh.agent.model.AgentDefinition
import cc.unitmesh.agent.model.PromptConfig
import cc.unitmesh.agent.model.RunConfig
import cc.unitmesh.agent.tool.ToolResult
import cc.unitmesh.agent.tool.schema.DeclarativeToolSchema
import cc.unitmesh.agent.tool.schema.SchemaPropertyBuilder.integer
import cc.unitmesh.agent.tool.schema.SchemaPropertyBuilder.string
import cc.unitmesh.devins.parser.CodeFence
import cc.unitmesh.llm.LLMService
import cc.unitmesh.llm.ModelConfig
import kotlinx.serialization.Serializable

/**
 * SQL Revise Agent Schema - Tool definition for SQL revision
 */
object SqlReviseAgentSchema : DeclarativeToolSchema(
    description = "Revise and fix SQL queries based on validation errors or execution failures",
    properties = mapOf(
        "originalQuery" to string(
            description = "The original natural language query from user",
            required = true
        ),
        "failedSql" to string(
            description = "The SQL query that failed validation or execution",
            required = true
        ),
        "errorMessage" to string(
            description = "The error message from validation or execution",
            required = true
        ),
        "schemaDescription" to string(
            description = "Database schema description for context",
            required = true
        ),
        "maxAttempts" to integer(
            description = "Maximum number of revision attempts",
            required = false
        )
    )
) {
    override fun getExampleUsage(toolName: String): String {
        return "/$toolName originalQuery=\"Show top customers\" failedSql=\"SELECT * FROM customer\" errorMessage=\"Table 'customer' doesn't exist\""
    }
}

/**
 * SQL Revise Agent - Self-correction loop for SQL queries
 * 
 * This SubAgent is responsible for:
 * 1. Analyzing SQL validation/execution errors
 * 2. Understanding the original user intent
 * 3. Generating corrected SQL based on schema and error context
 * 4. Iterating until a valid SQL is produced or max attempts reached
 * 
 * Based on GitHub Issue #508: https://github.com/phodal/auto-dev/issues/508
 * Implements the "Revise Agent (自我修正闭环)" feature
 */
class SqlReviseAgent(
    private val llmService: LLMService,
    private val sqlValidator: SqlValidatorInterface? = null
) : SubAgent<SqlRevisionInput, ToolResult.AgentResult>(
    definition = createDefinition()
) {
    private val logger = getLogger("SqlReviseAgent")

    override fun validateInput(input: Map<String, Any>): SqlRevisionInput {
        return SqlRevisionInput(
            originalQuery = input["originalQuery"] as? String
                ?: throw IllegalArgumentException("originalQuery is required"),
            failedSql = input["failedSql"] as? String
                ?: throw IllegalArgumentException("failedSql is required"),
            errorMessage = input["errorMessage"] as? String
                ?: throw IllegalArgumentException("errorMessage is required"),
            schemaDescription = input["schemaDescription"] as? String ?: "",
            previousAttempts = (input["previousAttempts"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            maxAttempts = (input["maxAttempts"] as? Number)?.toInt() ?: 3
        )
    }

    override fun getParameterClass(): String = SqlRevisionInput::class.simpleName ?: "SqlRevisionInput"

    override suspend fun execute(
        input: SqlRevisionInput,
        onProgress: (String) -> Unit
    ): ToolResult.AgentResult {
        onProgress("🔄 SQL Revise Agent - Starting revision")
        onProgress("Original query: ${input.originalQuery.take(50)}...")
        onProgress("Error: ${input.errorMessage.take(80)}...")

        var currentSql = input.failedSql
        var currentError = input.errorMessage
        val attempts = input.previousAttempts.toMutableList()
        var attemptCount = attempts.size

        while (attemptCount < input.maxAttempts) {
            attemptCount++
            onProgress("📝 Revision attempt $attemptCount/${input.maxAttempts}")

            // Build revision context
            val context = buildRevisionContext(input, currentSql, currentError, attempts)

            // Ask LLM for revised SQL
            val revisedSql = askLLMForRevision(context, onProgress)

            if (revisedSql == null) {
                onProgress("❌ Failed to generate revised SQL")
                return ToolResult.AgentResult(
                    success = false,
                    content = "Failed to generate revised SQL after $attemptCount attempts",
                    metadata = mapOf(
                        "attempts" to attemptCount.toString(),
                        "lastError" to currentError
                    )
                )
            }

            attempts.add(revisedSql)

            // Validate the revised SQL if validator is available
            if (sqlValidator != null) {
                val validation = sqlValidator.validate(revisedSql)
                if (validation.isValid) {
                    onProgress("✅ SQL validated successfully")
                    return ToolResult.AgentResult(
                        success = true,
                        content = revisedSql,
                        metadata = mapOf(
                            "attempts" to attemptCount.toString(),
                            "validated" to "true"
                        )
                    )
                } else {
                    currentSql = revisedSql
                    currentError = validation.errors.joinToString("; ")
                    onProgress("⚠️ Validation failed: ${currentError.take(50)}...")
                }
            } else {
                // No validator, return the revised SQL
                onProgress("✅ SQL revised (no validation)")
                return ToolResult.AgentResult(
                    success = true,
                    content = revisedSql,
                    metadata = mapOf(
                        "attempts" to attemptCount.toString(),
                        "validated" to "false"
                    )
                )
            }
        }

        onProgress("❌ Max revision attempts reached")
        return ToolResult.AgentResult(
            success = false,
            content = "Max revision attempts ($attemptCount) reached. Last SQL: $currentSql",
            metadata = mapOf(
                "attempts" to attemptCount.toString(),
                "lastError" to currentError,
                "lastSql" to currentSql
            )
        )
    }

    override fun formatOutput(output: ToolResult.AgentResult): String = output.content

    private fun buildRevisionContext(
        input: SqlRevisionInput,
        currentSql: String,
        currentError: String,
        previousAttempts: List<String>
    ): String = buildString {
        appendLine("# SQL Revision Task")
        appendLine()
        appendLine("## User Query")
        appendLine(input.originalQuery)
        appendLine()
        appendLine("## Available Schema (USE ONLY THESE TABLES AND COLUMNS)")
        appendLine("```")
        appendLine(input.schemaDescription.take(2000))
        appendLine("```")
        appendLine()
        appendLine("## Failed SQL")
        appendLine("```sql")
        appendLine(currentSql)
        appendLine("```")
        appendLine()
        appendLine("## Error")
        appendLine(currentError)
        if (previousAttempts.isNotEmpty()) {
            appendLine()
            appendLine("## Previous Failed Attempts (do not repeat)")
            previousAttempts.forEachIndexed { i, sql ->
                appendLine("${i + 1}: $sql")
            }
        }
    }

    private suspend fun askLLMForRevision(context: String, onProgress: (String) -> Unit): String? {
        val systemPrompt = """
You are a SQL Revision Agent. Fix SQL queries that failed validation or execution.

CRITICAL RULES:
1. ONLY use table names from the provided schema - NEVER invent table names
2. ONLY use column names from the provided schema - NEVER invent column names
3. If the error says a table doesn't exist, find the correct table name from the schema
4. Analyze the error message carefully and fix the specific issue
5. Avoid repeating previous failed attempts

OUTPUT FORMAT:
Return ONLY the corrected SQL in ```sql code block. No explanations.

```sql
SELECT column FROM table WHERE condition LIMIT 100;
```
""".trimIndent()

        val userPrompt = """
$context

**Task:** Generate a corrected SQL query that fixes the error while preserving the original intent.
""".trimIndent()

        return try {
            val response = llmService.sendPrompt("$systemPrompt\n\n$userPrompt")
            extractSqlFromResponse(response)
        } catch (e: Exception) {
            logger.error(e) { "LLM revision failed: ${e.message}" }
            null
        }
    }

    private fun extractSqlFromResponse(response: String): String? {
        val codeFences = CodeFence.parseAll(response)
        val sqlFence = codeFences.find { it.languageId.lowercase() == "sql" }
        if (sqlFence != null) {
            return sqlFence.text.trim()
        }
        // Fallback: try to extract from markdown
        val sqlMatch = Regex("```sql\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
            .find(response)?.groupValues?.get(1)
        return sqlMatch?.trim()
    }

    companion object {
        private fun createDefinition() = AgentDefinition(
            name = "SqlReviseAgent",
            displayName = "SQL Revise Agent",
            description = "Revises and fixes SQL queries based on validation errors or execution failures",
            promptConfig = PromptConfig(
                systemPrompt = "You are a SQL Revision Agent specialized in fixing SQL queries."
            ),
            modelConfig = ModelConfig.default(),
            runConfig = RunConfig(maxTurns = 5, maxTimeMinutes = 2)
        )
    }
}

/**
 * Input for SQL revision
 */
@Serializable
data class SqlRevisionInput(
    val originalQuery: String,
    val failedSql: String,
    val errorMessage: String,
    val schemaDescription: String = "",
    val previousAttempts: List<String> = emptyList(),
    val maxAttempts: Int = 3
)

/**
 * Interface for SQL validation - platform-specific implementations
 */
interface SqlValidatorInterface {
    fun validate(sql: String): SqlValidationResult
    fun validateWithTableWhitelist(sql: String, allowedTables: Set<String>): SqlValidationResult
    fun extractTableNames(sql: String): List<String>

    /**
     * Detect the type of SQL statement.
     * Used to determine if approval is needed for write operations.
     *
     * @param sql The SQL statement to analyze
     * @return The detected SQL operation type
     */
    fun detectSqlType(sql: String): SqlOperationType
}

/**
 * SQL operation types for determining approval requirements
 */
enum class SqlOperationType {
    /** SELECT queries - read-only, no approval needed */
    SELECT,
    /** INSERT statements - requires approval */
    INSERT,
    /** UPDATE statements - requires approval */
    UPDATE,
    /** DELETE statements - requires approval */
    DELETE,
    /** CREATE statements (tables, indexes, etc.) - requires approval */
    CREATE,
    /** ALTER statements - requires approval */
    ALTER,
    /** DROP statements - requires approval, high risk */
    DROP,
    /** TRUNCATE statements - requires approval, high risk */
    TRUNCATE,
    /** Other DDL/DCL/TCL statements */
    OTHER,
    /** Unknown or unparseable SQL */
    UNKNOWN;

    /**
     * Check if this operation type requires user approval
     */
    fun requiresApproval(): Boolean = this != SELECT && this != UNKNOWN

    /**
     * Check if this is a high-risk operation (DROP, TRUNCATE)
     */
    fun isHighRisk(): Boolean = this == DROP || this == TRUNCATE

    /**
     * Check if this is a write operation (INSERT, UPDATE, DELETE)
     */
    fun isWriteOperation(): Boolean = this == INSERT || this == UPDATE || this == DELETE

    /**
     * Check if this is a DDL operation (CREATE, ALTER, DROP, TRUNCATE)
     */
    fun isDdlOperation(): Boolean = this == CREATE || this == ALTER || this == DROP || this == TRUNCATE
}

/**
 * Result of SQL validation
 */
@Serializable
data class SqlValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

