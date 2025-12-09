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
import cc.unitmesh.llm.KoogLLMService
import cc.unitmesh.llm.ModelConfig

/**
 * ChatDB Agent - Text2SQL Agent for natural language database queries
 * 
 * This agent converts natural language queries to SQL, executes them,
 * and optionally generates visualizations of the results.
 * 
 * Features:
 * - Schema Linking: Keyword-based search to find relevant tables/columns
 * - SQL Generation: LLM generates SQL from natural language
 * - Revise Agent: Self-correction loop using JSqlParser for SQL validation
 * - Query Execution: Execute validated SQL and return results
 * - Visualization: Optional PlotDSL generation for data visualization
 * 
 * Based on GitHub Issue #508: https://github.com/phodal/auto-dev/issues/508
 */
class ChatDBAgent(
    private val projectPath: String,
    private val llmService: KoogLLMService,
    private val databaseConfig: DatabaseConfig,
    override val maxIterations: Int = 10,
    private val renderer: CodingAgentRenderer = DefaultCodingAgentRenderer(),
    private val fileSystem: ToolFileSystem? = null,
    private val shellExecutor: ShellExecutor? = null,
    private val mcpToolConfigService: McpToolConfigService,
    private val enableLLMStreaming: Boolean = true
) : MainAgent<ChatDBTask, ToolResult.AgentResult>(
    AgentDefinition(
        name = "ChatDBAgent",
        displayName = "ChatDB Agent",
        description = "Text2SQL Agent that converts natural language to SQL queries with schema linking and self-correction",
        promptConfig = PromptConfig(
            systemPrompt = SYSTEM_PROMPT
        ),
        modelConfig = ModelConfig.default(),
        runConfig = RunConfig(maxTurns = 10, maxTimeMinutes = 5)
    )
) {
    private val logger = getLogger("ChatDBAgent")
    
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
    
    private var databaseConnection: DatabaseConnection? = null
    
    private val executor: ChatDBAgentExecutor by lazy {
        val connection = databaseConnection ?: createDatabaseConnection(databaseConfig)
        databaseConnection = connection
        
        ChatDBAgentExecutor(
            projectPath = projectPath,
            llmService = llmService,
            toolOrchestrator = toolOrchestrator,
            renderer = renderer,
            databaseConnection = connection,
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
        logger.info { "Starting ChatDB Agent for query: ${input.query}" }
        
        val systemPrompt = buildSystemPrompt()
        val result = executor.execute(input, systemPrompt, onProgress)
        
        return ToolResult.AgentResult(
            success = result.success,
            content = result.message,
            metadata = mapOf(
                "generatedSql" to (result.generatedSql ?: ""),
                "rowCount" to (result.queryResult?.rowCount?.toString() ?: "0"),
                "revisionAttempts" to result.revisionAttempts.toString(),
                "hasVisualization" to (result.plotDslCode != null).toString()
            )
        )
    }

    private fun buildSystemPrompt(): String {
        return SYSTEM_PROMPT
    }

    override fun formatOutput(output: ToolResult.AgentResult): String {
        return output.content
    }

    override fun getParameterClass(): String = "ChatDBTask"

    /**
     * Close database connection when done
     */
    suspend fun close() {
        databaseConnection?.close()
        databaseConnection = null
    }

    companion object {
        const val SYSTEM_PROMPT = """You are an expert SQL developer and data analyst. Your task is to:

1. Understand the user's natural language query
2. Generate accurate, safe, and efficient SQL queries
3. Only generate SELECT queries (read-only operations)
4. Use proper SQL syntax for the target database
5. Consider performance implications (use indexes, avoid SELECT *)
6. Handle edge cases and NULL values appropriately

When generating SQL:
- Always wrap SQL in ```sql code blocks
- Use meaningful aliases for tables and columns
- Add comments for complex queries
- Limit results appropriately (use LIMIT clause)
- Prefer explicit column names over SELECT *

For visualization:
- When asked to visualize data, generate PlotDSL code
- Choose appropriate chart types based on data characteristics
- Wrap PlotDSL in ```plotdsl code blocks"""
    }
}

