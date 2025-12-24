package cc.unitmesh.agent.executor

import cc.unitmesh.agent.*
import cc.unitmesh.agent.conversation.ConversationManager
import cc.unitmesh.agent.core.SubAgentManager
import cc.unitmesh.agent.logging.getLogger
import cc.unitmesh.agent.tool.schema.ToolResultFormatter
import cc.unitmesh.agent.orchestrator.ToolExecutionResult
import cc.unitmesh.agent.orchestrator.ToolOrchestrator
import cc.unitmesh.agent.render.CodingAgentRenderer
import cc.unitmesh.agent.state.ToolCall
import cc.unitmesh.agent.state.ToolExecutionState
import cc.unitmesh.agent.plan.PlanSummaryData
import cc.unitmesh.agent.tool.ToolResult
import cc.unitmesh.agent.tool.ToolType
import cc.unitmesh.agent.tool.toToolType
import cc.unitmesh.devins.parser.CodeFence
import cc.unitmesh.llm.KoogLLMService
import kotlinx.coroutines.yield
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.datetime.Clock
import cc.unitmesh.agent.orchestrator.ToolExecutionContext as OrchestratorContext

/**
 * Configuration for async shell execution timeout behavior
 */
data class AsyncShellConfig(
    /** Initial wait timeout in milliseconds before notifying AI that process is still running */
    val initialWaitTimeoutMs: Long = 60_000L, // 1 minute
    /** Maximum total wait time in milliseconds (2 minutes, similar to Cursor/Claude Code) */
    val maxWaitTimeoutMs: Long = 120_000L, // 2 minutes
    /** Interval for checking process status after initial timeout */
    val checkIntervalMs: Long = 30_000L // 30 seconds
)

class CodingAgentExecutor(
    projectPath: String,
    llmService: KoogLLMService,
    toolOrchestrator: ToolOrchestrator,
    renderer: CodingAgentRenderer,
    maxIterations: Int = 100,
    private val subAgentManager: SubAgentManager? = null,
    enableLLMStreaming: Boolean = true,
    private val asyncShellConfig: AsyncShellConfig = AsyncShellConfig(),
    /**
     * When true, only execute the first tool call per LLM response.
     * This enforces the "one tool per response" rule even when LLM returns multiple tool calls.
     * Default is false to enable parallel tool execution for better performance.
     */
    private val singleToolPerIteration: Boolean = false
) : BaseAgentExecutor(
    projectPath = projectPath,
    llmService = llmService,
    toolOrchestrator = toolOrchestrator,
    renderer = renderer,
    maxIterations = maxIterations,
    enableLLMStreaming = enableLLMStreaming
) {
    private val logger = getLogger("CodingAgentExecutor")
    private val steps = mutableListOf<AgentStep>()
    private val edits = mutableListOf<AgentEdit>()

    private val recentToolCalls = mutableListOf<String>()
    private val MAX_REPEAT_COUNT = 3

    // Track task execution time
    private var taskStartTime: Long = 0L

    // Track if we have an active conversation
    private var hasActiveConversation: Boolean = false

    // Store the system prompt for conversation initialization
    private var currentSystemPrompt: String? = null

    /**
     * 执行 Agent 任务（新任务或继续对话）
     *
     * @param task 任务描述
     * @param systemPrompt 系统提示词
     * @param onProgress 进度回调
     * @param continueConversation 是否继续现有对话（true=继续，false=新任务）
     */
    suspend fun execute(
        task: AgentTask,
        systemPrompt: String,
        onProgress: (String) -> Unit = {},
        continueConversation: Boolean = false
    ): AgentResult {
        // Only reset if starting a new conversation
        if (!continueConversation || !hasActiveConversation) {
            resetExecution()

            // Start tracking execution time
            taskStartTime = Platform.getCurrentTimestamp()

            // Create new ConversationManager only for new conversations
            conversationManager = ConversationManager(llmService, systemPrompt)
            currentSystemPrompt = systemPrompt
            hasActiveConversation = true

            // Set up token tracking callback to update renderer
            conversationManager?.onTokenUpdate = { tokenInfo ->
                renderer.updateTokenInfo(tokenInfo)
            }

            onProgress("🚀 CodingAgent started")
            onProgress("Project: ${task.projectPath}")
            onProgress("Task: ${task.requirement}")
        } else {
            // Continuing existing conversation - just reset iteration counter for this turn
            currentIteration = 0
            taskStartTime = Platform.getCurrentTimestamp()

            onProgress("💬 Continuing conversation...")
            onProgress("User: ${task.requirement}")
        }

        val initialUserMessage = buildInitialUserMessage(task)

        while (shouldContinue()) {
            yield()

            currentIteration++
            renderer.renderIterationHeader(currentIteration, maxIterations)

            val llmResponse = StringBuilder()

            try {
                val message = if (currentIteration == 1) initialUserMessage else buildContinuationMessage()
                val compileDevIns = (currentIteration == 1) // Only compile DevIns on first iteration
                val response = getLLMResponse(message, compileDevIns)
                llmResponse.append(response)
            } catch (e: Exception) {
                break
            }

            val allToolCalls = toolCallParser.parseToolCalls(llmResponse.toString())
            if (allToolCalls.isEmpty()) {
                val executionTimeMs = Platform.getCurrentTimestamp() - taskStartTime
                // Even if the final iteration contains no tool calls, the session may have used tools earlier.
                renderer.renderTaskComplete(executionTimeMs, steps.size)
                break
            }

            // When singleToolPerIteration is enabled, only execute the first tool call
            // This enforces the "one tool per response" rule even when LLM returns multiple tool calls
            val toolCalls = if (singleToolPerIteration && allToolCalls.size > 1) {
                logger.warn { "LLM returned ${allToolCalls.size} tool calls, but singleToolPerIteration is enabled. Only executing the first one: ${allToolCalls.first().toolName}" }
                logger.warn { "Tool calls: ${allToolCalls.joinToString()}" }
                renderer.renderError("Warning: LLM returned ${allToolCalls.size} tool calls, only executing the first one")
                listOf(allToolCalls.first())
            } else {
                allToolCalls
            }

            val toolResults = executeToolCalls(toolCalls)
            val toolResultsText = ToolResultFormatter.formatMultipleToolResults(toolResults)
            conversationManager!!.addToolResults(toolResultsText)

            if (isTaskComplete(llmResponse.toString())) {
                val executionTimeMs = Platform.getCurrentTimestamp() - taskStartTime
                val toolsUsedCount = steps.size
                renderer.renderTaskComplete(executionTimeMs, toolsUsedCount)
                break
            }

            if (isStuck()) {
                renderer.renderError("Agent appears to be stuck. Stopping.")
                break
            }
        }

        return buildResult()
    }

    private fun resetExecution() {
        currentIteration = 0
        steps.clear()
        edits.clear()
        recentToolCalls.clear()
        taskStartTime = 0L
    }

    /**
     * Clear the current conversation and start fresh.
     * Call this when user explicitly wants to start a new task/session.
     */
    fun clearConversation() {
        resetExecution()
        conversationManager?.clearHistory()
        hasActiveConversation = false
        currentSystemPrompt = null
        logger.info { "Conversation cleared, ready for new task" }
    }

    /**
     * Check if there's an active conversation that can be continued
     */
    fun hasActiveConversation(): Boolean = hasActiveConversation && conversationManager != null

    private fun buildInitialUserMessage(task: AgentTask): String {
        // For continuation, just send the user's message directly
        // For new tasks, prefix with "Task:" to indicate it's a new task
        return if (hasActiveConversation && conversationManager?.getHistory()?.size ?: 0 > 1) {
            task.requirement
        } else {
            "Task: ${task.requirement}"
        }
    }

    override fun buildContinuationMessage(): String {
        return "Please continue with the task based on the tool execution results above. " +
                "Use additional tools if needed, or summarize if the task is complete."
    }

    /**
     * 并行执行多个工具调用
     *
     * 策略：
     * 1. 预先检查所有工具是否重复
     * 2. 先渲染所有工具调用（让用户看到即将执行的工具）
     * 3. 并行启动所有工具执行
     * 4. 等待所有工具完成后按顺序渲染结果
     * 5. 统一处理后续逻辑（步骤记录、错误恢复等）
     */
    private suspend fun executeToolCalls(toolCalls: List<ToolCall>): List<Triple<String, Map<String, Any>, ToolExecutionResult>> = coroutineScope {
        val results = mutableListOf<Triple<String, Map<String, Any>, ToolExecutionResult>>()

        // Phase 1: Pre-check for repeated tool calls
        val toolsToExecute = mutableListOf<Pair<Int, ToolCall>>() // (index, toolCall)
        var hasRepeatError = false

        for ((index, toolCall) in toolCalls.withIndex()) {
            if (hasRepeatError) break

            val toolName = toolCall.toolName
            val params = toolCall.params.mapValues { it.value as Any }
            val paramsStr = params.entries.joinToString(" ") { (key, value) ->
                "$key=\"$value\""
            }
            val toolSignature = "$toolName:$paramsStr"

            recentToolCalls.add(toolSignature)
            if (recentToolCalls.size > 10) {
                recentToolCalls.removeAt(0)
            }

            val exactMatches = recentToolCalls.takeLast(MAX_REPEAT_COUNT).count { it == toolSignature }
            val toolType = toolName.toToolType()
            val maxAllowedRepeats = when (toolType) {
                ToolType.ReadFile, ToolType.WriteFile -> 3
                ToolType.Shell -> 2
                else -> when (toolName) {
                    ToolType.ReadFile.name, ToolType.WriteFile.name -> 3
                    ToolType.Shell.name -> 2
                    else -> 2
                }
            }

            if (exactMatches >= maxAllowedRepeats) {
                renderer.renderRepeatWarning(toolName, exactMatches)
                val currentTime = Clock.System.now().toEpochMilliseconds()
                val errorResult = ToolExecutionResult(
                    executionId = "repeat-error-$currentTime",
                    toolName = toolName,
                    result = ToolResult.Error("Stopped due to repeated tool calls"),
                    startTime = currentTime,
                    endTime = currentTime,
                    state = ToolExecutionState.Failed(
                        "repeat-error-$currentTime",
                        "Stopped due to repeated tool calls",
                        0
                    )
                )
                results.add(Triple(toolName, params, errorResult))
                hasRepeatError = true
                break
            }

            toolsToExecute.add(index to toolCall)
        }

        if (hasRepeatError) {
            return@coroutineScope results
        }

        // Phase 2: Render all tool calls first (so user sees what's about to execute)
        val isParallel = toolsToExecute.size > 1
        if (isParallel) {
            logger.info { "Executing ${toolsToExecute.size} tool calls in parallel" }
        }

        for ((index, toolCall) in toolsToExecute) {
            val toolName = toolCall.toolName
            val params = toolCall.params.mapValues { it.value as Any }
            // Render tool call with index for parallel execution
            if (isParallel) {
                renderer.renderToolCallWithParams(toolName, params + ("_parallel_index" to (index + 1)))
            } else {
                renderer.renderToolCallWithParams(toolName, params)
            }
        }

        // Phase 3: Execute all tools in parallel
        data class ToolExecutionData(
            val index: Int,
            val toolName: String,
            val params: Map<String, Any>,
            val executionResult: ToolExecutionResult
        )

        val executionJobs = toolsToExecute.map { indexedToolCall ->
            val index = indexedToolCall.first
            val toolCall = indexedToolCall.second
            async {
                val toolName = toolCall.toolName
                val params = toolCall.params.mapValues { it.value as Any }

                val executionContext = OrchestratorContext(
                    workingDirectory = projectPath,
                    environment = emptyMap(),
                    timeout = asyncShellConfig.maxWaitTimeoutMs
                )

                var executionResult = toolOrchestrator.executeToolCall(
                    toolName,
                    params,
                    executionContext
                )

                // Handle Pending result (async shell execution)
                if (executionResult.isPending) {
                    executionResult = handlePendingResult(executionResult, toolName, params)
                }

                ToolExecutionData(index, toolName, params, executionResult)
            }
        }

        // Wait for all tools to complete
        val executionResults = executionJobs.awaitAll()
            .sortedBy { it.index }

        // Phase 4: Process results in order (render, record steps, handle errors)
        for ((resultIndex, execData) in executionResults.withIndex()) {
            val toolName = execData.toolName
            val params = execData.params
            val executionResult = execData.executionResult

            results.add(Triple(toolName, params, executionResult))

            val stepResult = AgentStep(
                step = currentIteration,
                action = toolName,
                tool = toolName,
                params = params,
                result = executionResult.content,
                success = executionResult.isSuccess
            )
            steps.add(stepResult)

            val fullOutput = when (val result = executionResult.result) {
                is ToolResult.Error -> {
                    buildString {
                        appendLine("Error: ${result.message}")
                        appendLine("Error Type: ${result.errorType}")
                        executionResult.metadata["stderr"]?.let { stderr ->
                            if (stderr.isNotEmpty()) {
                                appendLine("\nStderr:")
                                appendLine(stderr)
                            }
                        }
                        executionResult.metadata["stdout"]?.let { stdout ->
                            if (stdout.isNotEmpty()) {
                                appendLine("\nStdout:")
                                appendLine(stdout)
                            }
                        }
                    }
                }
                is ToolResult.AgentResult -> if (!result.success) result.content else stepResult.result
                is ToolResult.Pending -> stepResult.result
                is ToolResult.Success -> stepResult.result
            }

            val contentHandlerResult = checkForLongContent(toolName, fullOutput ?: "", executionResult)
            val displayOutput = contentHandlerResult?.content ?: fullOutput

            // Render result with index for parallel execution
            val metadata = if (isParallel) {
                executionResult.metadata + ("_parallel_index" to (resultIndex + 1).toString())
            } else {
                executionResult.metadata
            }

            renderer.renderToolResult(
                toolName,
                stepResult.success,
                stepResult.result,
                displayOutput,
                metadata
            )

            // Render Agent-generated sketch blocks
            if (executionResult.isSuccess && executionResult.result is ToolResult.AgentResult) {
                val agentResult = executionResult.result as ToolResult.AgentResult
                renderAgentSketchBlocks(toolName, agentResult)
            }

            // Render plan summary bar after plan tool execution
            if (toolName == "plan" && executionResult.isSuccess) {
                renderPlanSummaryIfAvailable()
            }

            val currentToolType = toolName.toToolType()
            if ((currentToolType == ToolType.WriteFile) && executionResult.isSuccess) {
                recordFileEdit(params)
            }

            // Error handling - skip user cancelled scenarios
            val wasCancelledByUser = executionResult.metadata["cancelled"] == "true"
            if (!executionResult.isSuccess && !executionResult.isPending && !wasCancelledByUser) {
                val errorMessage = executionResult.content ?: "Unknown error"
                renderer.renderError("Tool execution failed: $errorMessage")
            }
        }

        results
    }

    /**
     * Handle a Pending result from async shell execution.
     * Waits for the session to complete with timeout handling.
     * If the process takes longer than initialWaitTimeoutMs, returns a special result
     * indicating the process is still running (similar to Augment's behavior).
     */
    private suspend fun handlePendingResult(
        pendingResult: ToolExecutionResult,
        toolName: String,
        params: Map<String, Any>
    ): ToolExecutionResult {
        val pending = pendingResult.result as? ToolResult.Pending
            ?: return pendingResult

        val sessionId = pending.sessionId
        val command = pending.command
        val startTime = pendingResult.startTime

        // First, try to wait for the initial timeout
        val initialResult = renderer.awaitSessionResult(sessionId, asyncShellConfig.initialWaitTimeoutMs)

        return when (initialResult) {
            is ToolResult.Success -> {
                // Process completed within initial timeout
                val endTime = Clock.System.now().toEpochMilliseconds()
                ToolExecutionResult.success(
                    executionId = pendingResult.executionId,
                    toolName = toolName,
                    content = initialResult.content,
                    startTime = startTime,
                    endTime = endTime,
                    metadata = initialResult.metadata + mapOf("sessionId" to sessionId)
                )
            }
            is ToolResult.Error -> {
                // Process failed
                val endTime = Clock.System.now().toEpochMilliseconds()
                ToolExecutionResult.failure(
                    executionId = pendingResult.executionId,
                    toolName = toolName,
                    error = initialResult.message,
                    startTime = startTime,
                    endTime = endTime,
                    metadata = initialResult.metadata + mapOf("sessionId" to sessionId)
                )
            }
            is ToolResult.Pending -> {
                // Process is still running after initial timeout
                // Return a special result to inform the AI
                val elapsedSeconds = (Clock.System.now().toEpochMilliseconds() - startTime) / 1000
                val partialOutput = initialResult.metadata["partial_output"] ?: ""
                val outputLength = initialResult.metadata["output_length"]?.toIntOrNull()
                val maxOutputChars = 4000
                val outputSnippet =
                    if (partialOutput.length > maxOutputChars) {
                        partialOutput.takeLast(maxOutputChars)
                    } else {
                        partialOutput
                    }
                val stillRunningMessage = buildString {
                    appendLine("⏳ Process is still running after ${elapsedSeconds}s")
                    appendLine("Command: $command")
                    appendLine("Session ID: $sessionId")
                    appendLine()
                    appendLine("The process is executing in the background. You can:")
                    appendLine("1. Continue with other tasks while waiting")
                    appendLine("2. Check the terminal output in the UI for real-time progress")
                    appendLine("3. The result will be available when the process completes")

                    if (outputSnippet.isNotBlank()) {
                        appendLine()
                        val lengthHint = outputLength?.let { " (last ${outputSnippet.length} chars of $it)" } ?: ""
                        appendLine("Output so far$lengthHint:")
                        appendLine(outputSnippet)
                    }
                }

                // Return as a "success" with the still-running message
                // This allows the agent to continue and make decisions
                val endTime = Clock.System.now().toEpochMilliseconds()
                ToolExecutionResult(
                    executionId = pendingResult.executionId,
                    toolName = toolName,
                    result = ToolResult.Success(
                        content = stillRunningMessage,
                        metadata = mapOf(
                            "status" to "still_running",
                            "sessionId" to sessionId,
                            "command" to command,
                            "elapsedSeconds" to elapsedSeconds.toString(),
                            "partial_output" to outputSnippet,
                            "output_length" to (outputLength?.toString() ?: "")
                        )
                    ),
                    startTime = startTime,
                    endTime = endTime,
                    state = ToolExecutionState.Executing(pendingResult.executionId, startTime),
                    metadata = mapOf(
                        "sessionId" to sessionId,
                        "isAsync" to "true",
                        "stillRunning" to "true",
                        // Mark as live session to avoid long-content rewriting. Live output is already visible in UI.
                        "isLiveSession" to "true",
                        "partial_output" to outputSnippet,
                        "output_length" to (outputLength?.toString() ?: "")
                    )
                )
            }
            is ToolResult.AgentResult -> {
                // Unexpected, but handle it
                val endTime = Clock.System.now().toEpochMilliseconds()
                ToolExecutionResult(
                    executionId = pendingResult.executionId,
                    toolName = toolName,
                    result = initialResult,
                    startTime = startTime,
                    endTime = endTime,
                    state = if (initialResult.success) {
                        ToolExecutionState.Success(pendingResult.executionId, initialResult, endTime - startTime)
                    } else {
                        ToolExecutionState.Failed(pendingResult.executionId, initialResult.content, endTime - startTime)
                    },
                    metadata = mapOf("sessionId" to sessionId)
                )
            }
        }
    }

    private fun recordFileEdit(params: Map<String, Any>) {
        val path = params["path"] as? String
        val content = params["content"] as? String
        val mode = params["mode"] as? String

        if (path != null && content != null) {
            edits.add(
                AgentEdit(
                    file = path,
                    operation = if (mode == "create") AgentEditOperation.CREATE else AgentEditOperation.UPDATE,
                    content = content
                )
            )
        }
    }

    private fun isTaskComplete(llmResponse: String): Boolean {
        val completeKeywords = listOf(
            "TASK_COMPLETE",
            "task complete",
            "Task completed",
            "implementation is complete",
            "all done",
            "finished"
        )

        return completeKeywords.any { keyword ->
            llmResponse.contains(keyword, ignoreCase = true)
        }
    }

    private fun isStuck(): Boolean {
        return currentIteration > 5 &&
                steps.takeLast(5).all { !it.success || it.result?.contains("already exists") == true }
    }

    private fun buildResult(): AgentResult {
        val success = steps.any { it.success }
        val message = if (success) {
            "Task completed after $currentIteration iterations"
        } else {
            "Task incomplete after $currentIteration iterations"
        }

        return AgentResult(
            success = success,
            message = message,
            steps = steps,
            edits = edits
        )
    }

    /**
     * 检查工具输出是否需要长内容处理
     */
    private suspend fun checkForLongContent(
        toolName: String,
        output: String,
        executionResult: ToolExecutionResult
    ): ToolResult.AgentResult? {

        if (subAgentManager == null) {
            return null
        }

        // 对于 Live Session，不要用分析结果替换原始输出
        // Live Terminal 已经在 Timeline 中显示实时输出了
        val isLiveSession = executionResult.metadata["isLiveSession"] == "true"
        if (isLiveSession) {
            return null
        }

        // 对于用户取消的命令，不需要分析输出
        // 用户取消是明确的意图，不需要对取消前的输出做分析
        val wasCancelledByUser = executionResult.metadata["cancelled"] == "true"
        if (wasCancelledByUser) {
            return null
        }

        // 检测内容类型
        val contentType = when {
            toolName == "glob" -> "file-list"
            toolName == "shell" -> "shell-output"
            toolName == "grep" -> "search-results"
            toolName == "read-file" -> "file-content"
            output.startsWith("{") || output.startsWith("[") -> "json"
            output.contains("<?xml") -> "xml"
            else -> "text"
        }

        // 构建元数据
        val metadata = mutableMapOf<String, String>()
        metadata["toolName"] = toolName
        metadata["executionId"] = executionResult.executionId
        metadata["success"] = executionResult.isSuccess.toString()

        executionResult.metadata.forEach { (key, value) ->
            metadata["tool_$key"] = value
        }

        return subAgentManager.checkAndHandleLongContent(
            content = output,
            contentType = contentType,
            source = toolName,
            metadata = metadata
        )
    }

    /**
     * 获取对话历史
     */
    fun getConversationHistory(): List<cc.unitmesh.devins.llm.Message> {
        return conversationManager?.getHistory() ?: emptyList()
    }

    /**
     * Render plan summary bar if a plan is available
     */
    private fun renderPlanSummaryIfAvailable() {
        val planStateService = toolOrchestrator.getPlanStateService() ?: return
        val currentPlan = planStateService.currentPlan.value ?: return
        val summary = PlanSummaryData.from(currentPlan)
        renderer.renderPlanSummary(summary)
    }

    /**
     * Render Agent-generated sketch blocks from AgentResult content.
     * Detects special code blocks (chart, nanodsl, mermaid) and calls renderAgentSketchBlock.
     */
    private fun renderAgentSketchBlocks(agentName: String, agentResult: ToolResult.AgentResult) {
        if (!agentResult.success) return

        val content = agentResult.content
        if (content.isBlank()) return

        // Parse all code blocks from the content
        val codeFences = CodeFence.parseAll(content)

        // Supported sketch block languages
        val sketchLanguages = setOf("chart", "graph", "nanodsl", "nano", "mermaid", "mmd")

        for (fence in codeFences) {
            val language = fence.languageId.lowercase()
            if (language in sketchLanguages && fence.text.isNotBlank()) {
                renderer.renderAgentSketchBlock(
                    agentName = agentName,
                    language = language,
                    code = fence.text,
                    metadata = agentResult.metadata
                )
            }
        }
    }
}
