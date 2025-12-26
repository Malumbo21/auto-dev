package cc.unitmesh.agent.chatdb

import cc.unitmesh.agent.config.McpToolConfigService
import cc.unitmesh.agent.core.MainAgent
import cc.unitmesh.agent.database.DatabaseConfig
import cc.unitmesh.agent.database.DatabaseConnection
import cc.unitmesh.agent.database.createDatabaseConnection
import cc.unitmesh.agent.logging.getLogger
import cc.unitmesh.agent.model.AgentDefinition
import cc.unitmesh.agent.model.PromptConfig
import cc.unitmesh.agent.model.RunConfig
import cc.unitmesh.agent.orchestrator.ToolOrchestrator
import cc.unitmesh.agent.policy.DefaultPolicyEngine
import cc.unitmesh.agent.render.CodingAgentRenderer
import cc.unitmesh.agent.render.DefaultCodingAgentRenderer
import cc.unitmesh.agent.tool.shell.DefaultShellExecutor
import cc.unitmesh.agent.tool.shell.ShellExecutor
import cc.unitmesh.agent.tool.ToolResult
import cc.unitmesh.agent.tool.filesystem.DefaultToolFileSystem
import cc.unitmesh.agent.tool.filesystem.ToolFileSystem
import cc.unitmesh.agent.tool.registry.ToolRegistry
import cc.unitmesh.llm.LLMService
import cc.unitmesh.llm.ModelConfig

/**
 * Multi-Database ChatDB Agent - Text2SQL Agent supporting multiple database connections
 * 
 * This agent converts natural language queries to SQL across multiple databases.
 * It merges schemas from all connected databases and lets the LLM decide which
 * database(s) to query based on the user's question.
 * 
 * Features:
 * - Multi-Database Schema Linking: Merges schemas from all databases with database prefixes
 * - Intelligent Database Selection: LLM determines which database to query
 * - Parallel Execution: Can execute queries on multiple databases simultaneously
 * - Unified Results: Combines results from multiple databases
 */
class ChatDBAgent(
    private val projectPath: String,
    private val llmService: LLMService,
    private val databaseConfigs: Map<String, DatabaseConfig>,
    override val maxIterations: Int = 10,
    private val renderer: CodingAgentRenderer = DefaultCodingAgentRenderer(),
    private val fileSystem: ToolFileSystem? = null,
    private val shellExecutor: ShellExecutor? = null,
    private val mcpToolConfigService: McpToolConfigService,
    private val enableLLMStreaming: Boolean = true
) : MainAgent<ChatDBTask, ToolResult.AgentResult>(
    AgentDefinition(
        name = "MultiDatabaseChatDBAgent",
        displayName = "Multi-Database ChatDB Agent",
        description = "Text2SQL Agent that queries across multiple databases with intelligent database selection",
        promptConfig = PromptConfig(
            systemPrompt = MULTI_DB_SYSTEM_PROMPT
        ),
        modelConfig = ModelConfig.default(),
        runConfig = RunConfig(maxTurns = 10, maxTimeMinutes = 5)
    )
) {
    private val logger = getLogger("MultiDatabaseChatDBAgent")
    
    private val actualFileSystem = fileSystem ?: DefaultToolFileSystem(projectPath = projectPath)
    
    private val toolRegistry = ToolRegistry(
        fileSystem = actualFileSystem,
        shellExecutor = shellExecutor ?: DefaultShellExecutor(),
        configService = mcpToolConfigService,
        llmService = llmService
    )
    
    private val policyEngine = DefaultPolicyEngine()
    
    private val toolOrchestrator = ToolOrchestrator(
        registry = toolRegistry,
        policyEngine = policyEngine,
        renderer = renderer,
        mcpConfigService = mcpToolConfigService
    )
    
    // Database connections keyed by database name/id
    private val databaseConnections: MutableMap<String, DatabaseConnection> = mutableMapOf()
    
    private val executor: MultiDatabaseChatDBExecutor by lazy {
        // Create connections for all configured databases
        databaseConfigs.forEach { (id, config) ->
            if (!databaseConnections.containsKey(id)) {
                databaseConnections[id] = createDatabaseConnection(config)
            }
        }
        
        MultiDatabaseChatDBExecutor(
            projectPath = projectPath,
            llmService = llmService,
            toolOrchestrator = toolOrchestrator,
            renderer = renderer,
            databaseConnections = databaseConnections,
            databaseConfigs = databaseConfigs,
            maxIterations = maxIterations,
            enableLLMStreaming = enableLLMStreaming
        )
    }

    override fun validateInput(input: Map<String, Any>): ChatDBTask {
        val query = input["query"] as? String
            ?: throw IllegalArgumentException("Missing required parameter: query")
        
        return ChatDBTask(
            query = query,
            additionalContext = input["additionalContext"] as? String ?: "",
            maxRows = (input["maxRows"] as? Number)?.toInt() ?: 100,
            generateVisualization = input["generateVisualization"] as? Boolean ?: true
        )
    }

    override suspend fun execute(
        input: ChatDBTask,
        onProgress: (String) -> Unit
    ): ToolResult.AgentResult {
        logger.info { "Starting Multi-Database ChatDB Agent for query: ${input.query}" }
        logger.info { "Connected databases: ${databaseConfigs.keys}" }

        val systemPrompt = buildSystemPrompt()

        // Check if we should continue existing conversation
        val continueConversation = executor.hasActiveConversation()

        val result = executor.execute(input, systemPrompt, onProgress, continueConversation)

        return ToolResult.AgentResult(
            success = result.success,
            content = result.message,
            metadata = mapOf(
                "generatedSql" to (result.generatedSql ?: ""),
                "rowCount" to (result.queryResult?.rowCount?.toString() ?: "0"),
                "revisionAttempts" to result.revisionAttempts.toString(),
                "hasVisualization" to (result.plotDslCode != null).toString(),
                "targetDatabases" to (result.targetDatabases?.joinToString(",") ?: "")
            )
        )
    }

    /**
     * Execute a query, optionally continuing an existing conversation.
     *
     * @param input The ChatDB task to execute
     * @param continueConversation If true, continues the existing conversation context.
     *                             If false, starts a new conversation (clears history).
     */
    suspend fun executeQuery(input: ChatDBTask, continueConversation: Boolean = true): MultiDatabaseChatDBResult {
        logger.info { "Executing ChatDB query: ${input.query}, continueConversation=$continueConversation" }
        logger.info { "Connected databases: ${databaseConfigs.keys}" }

        val systemPrompt = buildSystemPrompt()
        return executor.execute(input, systemPrompt, {}, continueConversation)
    }

    /**
     * Continue an existing conversation with a new query.
     * This preserves the conversation history and context.
     *
     * @param query The user's follow-up query
     * @return The query result
     */
    suspend fun continueConversation(query: String): MultiDatabaseChatDBResult {
        val task = ChatDBTask(
            query = query,
            additionalContext = "",
            maxRows = 100,
            generateVisualization = true
        )

        val systemPrompt = buildSystemPrompt()

        // Always continue conversation when using this method
        return executor.execute(task, systemPrompt, {}, continueConversation = true)
    }

    /**
     * Start a new conversation, clearing any existing history.
     *
     * @param input The new ChatDB task to start
     * @return The query result
     */
    suspend fun startNewConversation(input: ChatDBTask): MultiDatabaseChatDBResult {
        // Clear existing conversation first
        executor.clearConversation()

        val systemPrompt = buildSystemPrompt()
        return executor.execute(input, systemPrompt, {}, continueConversation = false)
    }

    /**
     * Check if there's an active conversation that can be continued
     */
    fun hasActiveConversation(): Boolean = executor.hasActiveConversation()

    /**
     * Clear the current conversation and start fresh
     */
    fun clearConversation() {
        executor.clearConversation()
    }

    private fun buildSystemPrompt(): String {
        return MULTI_DB_SYSTEM_PROMPT
    }

    override fun formatOutput(output: ToolResult.AgentResult): String {
        return output.content
    }

    override fun getParameterClass(): String = "ChatDBTask"

    /**
     * Close all database connections when done
     */
    suspend fun close() {
        databaseConnections.values.forEach { it.close() }
        databaseConnections.clear()
    }

    companion object {
        const val MULTI_DB_SYSTEM_PROMPT = """You are an expert SQL developer working with MULTIPLE databases.

IMPORTANT: You are connected to multiple databases. Each table in the schema is prefixed with its database name.
Format: [database_name].table_name

CRITICAL RULES:
1. ONLY use table names provided in the schema - NEVER invent or guess table names
2. ONLY use column names provided in the schema - NEVER invent or guess column names
3. When generating SQL, use the table name WITHOUT the database prefix (the system will route to the correct database)
4. If the user's question relates to tables in multiple databases, generate SEPARATE SQL queries for each database
5. Always add LIMIT clause for SELECT queries to prevent large result sets

SUPPORTED OPERATIONS:
- SELECT: Read data (no approval required)
- INSERT: Add new records (requires user approval)
- UPDATE: Modify existing records (requires user approval)
- DELETE: Remove records (requires user approval, HIGH RISK)
- CREATE TABLE: Create new tables (requires user approval)
- ALTER TABLE: Modify table structure (requires user approval, HIGH RISK)
- DROP TABLE: Delete tables (requires user approval, HIGH RISK)
- TRUNCATE: Remove all records (requires user approval, HIGH RISK)

⚠️ WRITE OPERATIONS WARNING:
- All write operations (INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, TRUNCATE) require explicit user approval before execution
- HIGH RISK operations (DELETE, DROP, TRUNCATE, ALTER) will be highlighted with additional warnings
- Always confirm with the user before generating destructive SQL

OUTPUT FORMAT:
- For single database query:
```sql
-- database: <database_name>
SELECT id, name FROM users WHERE status = 'active' LIMIT 100;
```

- For multiple database queries:
```sql
-- database: db1
SELECT * FROM users LIMIT 100;
```
```sql
-- database: db2
SELECT * FROM customers LIMIT 100;
```

- For write operations:
```sql
-- database: <database_name>
INSERT INTO users (name, email) VALUES ('John', 'john@example.com');
```

```sql
-- database: <database_name>
CREATE TABLE new_table (
    id INT PRIMARY KEY,
    name VARCHAR(100)
);
```

The "-- database: <name>" comment is REQUIRED to specify which database to execute the query on."""
    }
}

