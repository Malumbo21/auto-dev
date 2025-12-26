package cc.unitmesh.agent

import cc.unitmesh.agent.config.McpToolConfigService
import cc.unitmesh.agent.config.McpToolConfigManager
import cc.unitmesh.agent.config.ToolItem
import cc.unitmesh.agent.tool.BaseExecutableTool
import cc.unitmesh.agent.tool.ToolExecutionContext
import cc.unitmesh.agent.tool.ToolInvocation
import cc.unitmesh.agent.core.MainAgent
import cc.unitmesh.agent.core.SubAgentManager
import cc.unitmesh.agent.executor.CodingAgentExecutor
import cc.unitmesh.agent.mcp.McpServerConfig
import cc.unitmesh.agent.mcp.McpToolsInitializer
import cc.unitmesh.agent.model.AgentDefinition
import cc.unitmesh.agent.model.PromptConfig
import cc.unitmesh.agent.model.RunConfig
import cc.unitmesh.agent.orchestrator.ToolOrchestrator
import cc.unitmesh.agent.policy.DefaultPolicyEngine
import cc.unitmesh.agent.render.CodingAgentRenderer
import cc.unitmesh.agent.render.DefaultCodingAgentRenderer
import cc.unitmesh.agent.subagent.AnalysisAgent
import cc.unitmesh.agent.subagent.ChartAgent
import cc.unitmesh.agent.subagent.ErrorRecoveryAgent
import cc.unitmesh.agent.subagent.NanoDSLAgent
import cc.unitmesh.agent.subagent.PlotDSLAgent
import cc.unitmesh.agent.tool.*
import cc.unitmesh.agent.tool.filesystem.DefaultToolFileSystem
import cc.unitmesh.agent.tool.filesystem.ToolFileSystem
import cc.unitmesh.agent.tool.registry.ToolRegistry
import cc.unitmesh.agent.tool.shell.DefaultShellExecutor
import cc.unitmesh.agent.tool.shell.ShellExecutor
import cc.unitmesh.agent.logging.getLogger
import cc.unitmesh.agent.tool.schema.ToolCategory
import cc.unitmesh.llm.LLMService
import cc.unitmesh.llm.ModelConfig
import cc.unitmesh.llm.image.ImageGenerationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CodingAgent(
    private val projectPath: String,
    private val llmService: LLMService,
    override val maxIterations: Int = 100,
    private val renderer: CodingAgentRenderer = DefaultCodingAgentRenderer(),
    private val fileSystem: ToolFileSystem? = null,
    private val shellExecutor: ShellExecutor? = null,
    private val mcpServers: Map<String, McpServerConfig>? = null,
    private val mcpToolConfigService: McpToolConfigService,
    private val enableLLMStreaming: Boolean = true,
) : MainAgent<AgentTask, ToolResult.AgentResult>(
    AgentDefinition(
        name = "CodingAgent",
        displayName = "Autonomous Coding Agent",
        description = "Autonomous coding agent for development tasks",
        promptConfig = PromptConfig(
            systemPrompt = "You are an autonomous coding agent.",
            queryTemplate = null,
            initialMessages = emptyList()
        ),
        modelConfig = ModelConfig.default(),
        runConfig = RunConfig(
            maxTurns = 100,
            maxTimeMinutes = 30,
            terminateOnError = false
        )
    )
), CodingAgentService {

    private val logger = getLogger("CodingAgent")
    private val promptRenderer = CodingAgentPromptRenderer()

    private val configService = mcpToolConfigService

    private val subAgentManager = SubAgentManager()

    private val toolRegistry = run {
        logger.info { "Initializing ToolRegistry with configService." }
        ToolRegistry(
            fileSystem = fileSystem ?: DefaultToolFileSystem(projectPath = projectPath),
            shellExecutor = shellExecutor ?: DefaultShellExecutor(),
            configService = mcpToolConfigService,
            subAgentManager = subAgentManager,
            llmService = llmService
        )
    }

    private val policyEngine = DefaultPolicyEngine()
    private val toolOrchestrator = ToolOrchestrator(toolRegistry, policyEngine, renderer, mcpConfigService = mcpToolConfigService)

    /**
     * Get the PlanStateService for observing plan state changes.
     * Returns null if no plan tool is registered.
     */
    fun getPlanStateService(): cc.unitmesh.agent.plan.PlanStateService? {
        return toolOrchestrator.getPlanStateService()
    }

    private val errorRecoveryAgent = ErrorRecoveryAgent(projectPath, llmService)
    private val analysisAgent = AnalysisAgent(llmService, contentThreshold = 15000)
    private val nanoDSLAgent = NanoDSLAgent(llmService, imageGenerationService =
        ImageGenerationService.create(llmService.activeConfig)
    )
    private val chartAgent = ChartAgent(llmService)
    private val plotDSLAgent = PlotDSLAgent(llmService)
    private val mcpToolsInitializer = McpToolsInitializer()

    // 执行器
    private val executor = CodingAgentExecutor(
        projectPath = projectPath,
        llmService = llmService,
        toolOrchestrator = toolOrchestrator,
        renderer = renderer,
        maxIterations = maxIterations,
        subAgentManager = subAgentManager,
        enableLLMStreaming = enableLLMStreaming  // 传递流式配置
    )

    // 标记 MCP 工具是否已初始化
    private var mcpToolsInitialized = false

    init {
        // Register Sub-Agents (as Tools) - Always enabled as they are built-in tools
        registerTool(errorRecoveryAgent)
        toolRegistry.registerTool(errorRecoveryAgent)
        subAgentManager.registerSubAgent(errorRecoveryAgent)

        registerTool(analysisAgent)
        toolRegistry.registerTool(analysisAgent)
        subAgentManager.registerSubAgent(analysisAgent)

        registerTool(nanoDSLAgent)
        toolRegistry.registerTool(nanoDSLAgent)
        subAgentManager.registerSubAgent(nanoDSLAgent)

        registerTool(chartAgent)
        toolRegistry.registerTool(chartAgent)
        subAgentManager.registerSubAgent(chartAgent)

        // PlotDSLAgent - only available on JVM/Android (checks isAvailable internally)
        if (plotDSLAgent.isAvailable) {
            registerTool(plotDSLAgent)
            toolRegistry.registerTool(plotDSLAgent)
        }

        subAgentManager.registerSubAgent(plotDSLAgent)

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            initializeWorkspace(projectPath)
        }
    }

    override suspend fun execute(
        input: AgentTask,
        onProgress: (String) -> Unit
    ): ToolResult.AgentResult {
        // Note: initializeWorkspace is already called in init block, no need to call again here
        // The buildContext() will handle MCP tools initialization if needed

        val context = buildContext(input)
        val systemPrompt = buildSystemPrompt(context, input.language)

        // Check if we should continue existing conversation
        val continueConversation = executor.hasActiveConversation()

        val result = executor.execute(input, systemPrompt, onProgress, continueConversation)

        return ToolResult.AgentResult(
            success = result.success,
            content = result.message,
            metadata = mapOf(
                "iterations" to "0", // executor 内部管理迭代
                "steps" to result.steps.size.toString(),
                "edits" to result.edits.size.toString()
            )
        )
    }

    /**
     * Execute a task, optionally continuing an existing conversation.
     *
     * @param task The task to execute
     * @param continueConversation If true, continues the existing conversation context.
     *                             If false, starts a new conversation (clears history).
     */
    override suspend fun executeTask(task: AgentTask): AgentResult {
        val context = buildContext(task)
        val systemPrompt = buildSystemPrompt(context, task.language)

        // Check if we should continue existing conversation
        val continueConversation = executor.hasActiveConversation()

        return executor.execute(task, systemPrompt, continueConversation = continueConversation)
    }

    /**
     * Continue an existing conversation with a new user message.
     * This preserves the conversation history and context.
     *
     * @param userMessage The user's follow-up message
     * @param language Language for the prompt (EN or ZH), defaults to EN
     * @return The agent's result
     */
    suspend fun continueConversation(userMessage: String, language: String = "EN"): AgentResult {
        val task = AgentTask(
            requirement = userMessage,
            projectPath = projectPath,
            language = language
        )

        val context = buildContext(task)
        val systemPrompt = buildSystemPrompt(context, task.language)

        // Always continue conversation when using this method
        return executor.execute(task, systemPrompt, continueConversation = true)
    }

    /**
     * Start a new conversation, clearing any existing history.
     *
     * @param task The new task to start
     * @return The agent's result
     */
    suspend fun startNewConversation(task: AgentTask): AgentResult {
        // Clear existing conversation first
        executor.clearConversation()

        val context = buildContext(task)
        val systemPrompt = buildSystemPrompt(context, task.language)

        return executor.execute(task, systemPrompt, continueConversation = false)
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

    override fun buildSystemPrompt(context: CodingAgentContext, language: String): String {
        return promptRenderer.render(context, language)
    }

    override suspend fun initializeWorkspace(projectPath: String) {
        val mcpServersToInit = configService.getEnabledMcpServers().takeIf { it.isNotEmpty() }
            ?: mcpServers
        
        if (!mcpServersToInit.isNullOrEmpty()) {
            initializeMcpTools(mcpServersToInit)
        }
    }
    
    /**
     * Initialize and register MCP tools from configuration
     */
    private suspend fun initializeMcpTools(mcpServers: Map<String, McpServerConfig>) {
        logger.info { "Initializing MCP tools from ${mcpServers.size} servers..." }

        // Debug: Print server configurations
        mcpServers.forEach { (name, config) ->
            logger.debug { "Server '$name': ${config.command} ${config.args.joinToString(" ")} (disabled: ${config.disabled})" }
        }

        try {
            val mcpTools = mcpToolsInitializer.initialize(mcpServers)
            logger.info { "Discovered ${mcpTools.size} MCP tools" }
            logger.debug { "MCP tools initialization returned ${mcpTools.size} tools" }

            if (mcpTools.isNotEmpty()) {
                // Debug: Print discovered tools
                mcpTools.forEach { tool ->
                    logger.debug { "Discovered tool: ${tool.name} (${tool::class.simpleName})" }
                }

                val filteredMcpTools = configService.filterMcpTools(mcpTools)
                logger.info { "Filtered to ${filteredMcpTools.size} enabled tools" }

                // Debug: Print filtered tools
                filteredMcpTools.forEach { tool ->
                    logger.debug { "Enabled tool: ${tool.name}" }
                }

                filteredMcpTools.forEach { tool ->
                    registerTool(tool)
                }

                logger.info { "Registered ${filteredMcpTools.size}/${mcpTools.size} MCP tools from ${mcpServers.size} servers" }
            } else {
                logger.info { "No MCP tools discovered from ${mcpServers.size} servers" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Warning: Failed to initialize MCP tools: ${e.message}" }
        }
    }
    
    /**
     * Shutdown MCP connections
     */
    suspend fun shutdown() {
        mcpToolsInitializer.shutdown()
    }

    private suspend fun buildContext(task: AgentTask): CodingAgentContext {
        if (!mcpToolsInitialized) {
            logger.debug { "Checking for preloaded MCP tools..." }

            val mcpServersToUse = configService.getEnabledMcpServers().takeIf { it.isNotEmpty() }
                ?: mcpServers

            if (!mcpServersToUse.isNullOrEmpty()) {
                try {
                    val enabledMcpTools = configService.toolConfig.enabledMcpTools.toSet()
                    val cachedMcpTools = McpToolConfigManager.discoverMcpTools(mcpServersToUse, enabledMcpTools)

                    if (cachedMcpTools.isNotEmpty()) {
                        logger.info { "Found ${cachedMcpTools.values.sumOf { it.size }} preloaded MCP tools" }

                        // 将预加载的工具转换为 ExecutableTool 并注册
                        cachedMcpTools.values.flatten().forEach { toolItem ->
                            if (toolItem.enabled) {
                                // 创建一个简单的 MCP 工具适配器
                                val mcpTool = createMcpToolFromItem(toolItem)
                                registerTool(mcpTool)
                                logger.debug { "Registered MCP tool: ${toolItem.name}" }
                            }
                        }

                        mcpToolsInitialized = true
                        logger.info { "Successfully registered ${cachedMcpTools.values.sumOf { it.count { tool -> tool.enabled } }} MCP tools from cache" }
                    } else {
                        logger.debug { "No preloaded MCP tools found, falling back to direct initialization..." }
                        initializeMcpTools(mcpServersToUse)
                        mcpToolsInitialized = true
                    }
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to use preloaded MCP tools: ${e.message}" }
                    if (mcpServers != null) {
                        logger.debug { "Falling back to direct initialization..." }
                        initializeMcpTools(mcpServers)
                        mcpToolsInitialized = true
                    }
                }
            }
        }

        logger.debug { "Getting all available tools..." }
        val allTools = getAllAvailableTools()
        logger.debug { "Got ${allTools.size} tools for context" }

        return CodingAgentContext.fromTask(
            task,
            fileSystem = fileSystem,
            toolList = allTools
        )
    }

    /**
     * 获取所有可用的工具，包括内置工具、SubAgent 和 MCP 工具
     */
    private fun getAllAvailableTools(): List<ExecutableTool<*, *>> {
        val allTools = mutableListOf<ExecutableTool<*, *>>()
        allTools.addAll(toolRegistry.getAllTools().values)

        val registryToolNames = toolRegistry.getAllTools().keys
        val mainAgentTools = getAllTools().filter { it.name !in registryToolNames }
        allTools.addAll(mainAgentTools)

        logger.debug { "总共获取到 ${allTools.size} 个工具" }
        allTools.forEach { tool ->
            logger.debug { "- ${tool.name} (${tool::class.simpleName})" }
        }

        return allTools
    }

    private fun createMcpToolFromItem(toolItem: ToolItem): ExecutableTool<*, *> {
        return object : BaseExecutableTool<Map<String, Any>, ToolResult.Success>() {
            override val name: String = toolItem.name
            override val description: String = toolItem.description
            
            override val metadata: ToolMetadata = ToolMetadata(
                displayName = toolItem.displayName,
                tuiEmoji = "🔌",
                composeIcon = "extension",
                category = ToolCategory.Utility,
                schema = object : cc.unitmesh.agent.tool.schema.DeclarativeToolSchema(
                    description = toolItem.description,
                    properties = emptyMap()
                ) {
                    override fun getExampleUsage(toolName: String): String = "/$toolName"
                }
            )

            override fun getParameterClass(): String = "Map<String, Any>"

            override fun createToolInvocation(params: Map<String, Any>): ToolInvocation<Map<String, Any>, ToolResult.Success> {
                val outerTool = this
                return object : ToolInvocation<Map<String, Any>, ToolResult.Success> {
                    override val params: Map<String, Any> = params
                    override val tool: ExecutableTool<Map<String, Any>, ToolResult.Success> = outerTool

                    override fun getDescription(): String = toolItem.description
                    override fun getToolLocations(): List<cc.unitmesh.agent.tool.ToolLocation> = emptyList()

                    override suspend fun execute(context: ToolExecutionContext): ToolResult.Success {
                        // 这里应该调用实际的 MCP 工具执行
                        // 但是为了简化，我们先返回一个占位符结果
                        return ToolResult.Success("MCP tool ${toolItem.name} executed (placeholder)")
                    }
                }
            }
        }
    }


    override fun validateInput(input: Map<String, Any>): AgentTask {
        val requirement = input["requirement"] as? String
            ?: throw IllegalArgumentException("requirement is required")
        val projectPath = input["projectPath"] as? String
            ?: throw IllegalArgumentException("projectPath is required")

        return AgentTask(requirement, projectPath)
    }

    override fun formatOutput(output: ToolResult.AgentResult): String {
        return output.content
    }

    /**
     * 获取对话历史
     */
    fun getConversationHistory(): List<cc.unitmesh.devins.llm.Message> {
        return executor.getConversationHistory()
    }
}
