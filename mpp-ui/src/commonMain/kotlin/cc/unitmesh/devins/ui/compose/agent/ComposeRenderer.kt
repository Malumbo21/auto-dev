package cc.unitmesh.devins.ui.compose.agent

import androidx.compose.runtime.*
import cc.unitmesh.agent.database.DryRunResult
import cc.unitmesh.agent.plan.AgentPlan
import cc.unitmesh.agent.plan.MarkdownPlanParser
import cc.unitmesh.agent.render.BaseRenderer
import cc.unitmesh.agent.render.ChatDBStepStatus
import cc.unitmesh.agent.render.ChatDBStepType
import cc.unitmesh.agent.render.ImageInfo
import cc.unitmesh.agent.render.MultimodalAnalysisStatus
import cc.unitmesh.agent.render.RendererUtils
import cc.unitmesh.agent.render.TaskInfo
import cc.unitmesh.agent.render.TaskStatus
import cc.unitmesh.agent.render.TimelineItem
import cc.unitmesh.agent.render.TimelineItem.*
import cc.unitmesh.agent.render.ToolCallInfo
import cc.unitmesh.agent.subagent.SqlOperationType
import cc.unitmesh.agent.tool.ToolType
import cc.unitmesh.agent.tool.impl.docql.DocQLSearchStats
import cc.unitmesh.agent.tool.toToolType
import cc.unitmesh.devins.llm.Message
import cc.unitmesh.devins.llm.MessageRole
import cc.unitmesh.devins.llm.TimelineItemType
import cc.unitmesh.llm.compression.TokenInfo
import kotlinx.datetime.Clock

/**
 * Compose UI Renderer that extends BaseRenderer
 *
 * Implements CodingAgentRenderer interface from mpp-core.
 * Integrates the BaseRenderer architecture with Compose state management.
 *
 * This renderer maintains a unified timeline of all agent activities (messages, tool calls, results)
 * and exposes them as Compose state for reactive UI updates.
 *
 * @see cc.unitmesh.agent.render.CodingAgentRenderer - The core interface
 * @see cc.unitmesh.agent.render.BaseRenderer - Common functionality
 */
class ComposeRenderer : BaseRenderer() {
    // Unified timeline for all events (messages, tool calls, results)
    private val _timeline = mutableStateListOf<TimelineItem>()
    val timeline: List<TimelineItem> = _timeline

    private var _currentStreamingOutput by mutableStateOf("")
    val currentStreamingOutput: String get() = _currentStreamingOutput

    // Thinking content state - displayed in a collapsible, scrolling area
    private var _currentThinkingOutput by mutableStateOf("")
    val currentThinkingOutput: String get() = _currentThinkingOutput

    private var _isThinking by mutableStateOf(false)
    val isThinking: Boolean get() = _isThinking

    private var _isProcessing by mutableStateOf(false)
    val isProcessing: Boolean get() = _isProcessing

    private var _currentIteration by mutableStateOf(0)
    val currentIteration: Int get() = _currentIteration

    private var _maxIterations by mutableStateOf(100)
    val maxIterations: Int get() = _maxIterations

    private var _currentToolCall by mutableStateOf<ToolCallInfo?>(null)
    val currentToolCall: ToolCallInfo? get() = _currentToolCall

    private var _errorMessage by mutableStateOf<String?>(null)
    val errorMessage: String? get() = _errorMessage

    private var _taskCompleted by mutableStateOf(false)

    private var _executionStartTime by mutableStateOf(0L)
    val executionStartTime: Long get() = _executionStartTime

    private var _currentExecutionTime by mutableStateOf(0L)
    val currentExecutionTime: Long get() = _currentExecutionTime

    // Token tracking
    private var _totalTokenInfo by mutableStateOf(TokenInfo())
    val totalTokenInfo: TokenInfo get() = _totalTokenInfo

    private var _lastMessageTokenInfo by mutableStateOf<TokenInfo?>(null)

    // File viewer state
    private var _currentViewingFile by mutableStateOf<String?>(null)
    val currentViewingFile: String? get() = _currentViewingFile

    private val _tasks = mutableStateListOf<TaskInfo>()
    val tasks: List<TaskInfo> = _tasks

    // Plan tracking from plan management tool
    private var _currentPlan by mutableStateOf<AgentPlan?>(null)
    val currentPlan: AgentPlan? get() = _currentPlan

    // SQL approval state
    private var _pendingSqlApproval by mutableStateOf<SqlApprovalRequest?>(null)
    val pendingSqlApproval: SqlApprovalRequest? get() = _pendingSqlApproval

    // BaseRenderer implementation

    override fun renderIterationHeader(
        current: Int,
        max: Int
    ) {
        _currentIteration = current
        _maxIterations = max
        // Don't show iteration headers in Compose UI - they're handled by the UI components
    }

    override fun renderLLMResponseStart() {
        super.renderLLMResponseStart()
        _currentStreamingOutput = ""
        _currentThinkingOutput = ""
        _isThinking = false
        _isProcessing = true

        // Start timing if this is the first iteration
        if (_executionStartTime == 0L) {
            _executionStartTime = Clock.System.now().toEpochMilliseconds()
        }
        _currentExecutionTime = Clock.System.now().toEpochMilliseconds() - _executionStartTime
    }

    override fun renderLLMResponseChunk(chunk: String) {
        reasoningBuffer.append(chunk)

        // Wait for more content if we detect an incomplete devin block
        if (hasIncompleteDevinBlock(reasoningBuffer.toString())) {
            return
        }

        // Extract thinking content
        val extraction = extractThinkingContent(reasoningBuffer.toString())

        // Handle thinking content
        if (extraction.hasThinking) {
            val thinkContent = extraction.thinkingContent.toString()
            if (thinkContent.isNotEmpty()) {
                val wasInThinkBlock = isInThinkBlock
                isInThinkBlock = extraction.hasIncompleteThinkBlock
                renderThinkingChunk(
                    thinkContent,
                    isStart = !wasInThinkBlock && (extraction.hasCompleteThinkBlock || extraction.hasIncompleteThinkBlock),
                    isEnd = extraction.hasCompleteThinkBlock && !extraction.hasIncompleteThinkBlock
                )
            }
        } else if (isInThinkBlock && !extraction.hasIncompleteThinkBlock) {
            isInThinkBlock = false
            _isThinking = false
        }

        // Process the buffer to filter out devin blocks
        val processedContent = filterDevinBlocks(extraction.contentWithoutThinking)
        val cleanContent = cleanNewlines(processedContent)

        // Update streaming output for Compose UI
        _currentStreamingOutput = cleanContent
    }

    override fun renderThinkingChunk(chunk: String, isStart: Boolean, isEnd: Boolean) {
        if (isStart) {
            _currentThinkingOutput = ""
            _isThinking = true
        }

        // Append thinking content - keep only last N lines for scrolling effect
        val maxLines = 5
        val currentLines = _currentThinkingOutput.lines().toMutableList()
        val newLines = chunk.lines()
        currentLines.addAll(newLines)

        // Keep only last N lines
        val trimmedLines = if (currentLines.size > maxLines) {
            currentLines.takeLast(maxLines)
        } else {
            currentLines
        }
        _currentThinkingOutput = trimmedLines.joinToString("\n")

        if (isEnd) {
            _isThinking = false
        }
    }

    override fun renderLLMResponseEnd() {
        super.renderLLMResponseEnd()

        // Save content and token info before clearing
        val finalContent = _currentStreamingOutput.trim()
        val tokenInfo = _lastMessageTokenInfo

        // IMPORTANT: Clear streaming output FIRST to avoid showing both
        // StreamingMessageItem and MessageItem simultaneously (double progress bar issue)
        _currentStreamingOutput = ""
        _currentThinkingOutput = ""
        _isThinking = false
        _isProcessing = false
        _lastMessageTokenInfo = null

        // Then add the completed message to timeline
        if (finalContent.isNotEmpty()) {
            _timeline.add(
                TimelineItem.MessageItem(
                    message =
                        Message(
                            role = MessageRole.ASSISTANT,
                            content = finalContent
                        ),
                    tokenInfo = tokenInfo
                )
            )
        }
    }

    override fun renderToolCall(
        toolName: String,
        paramsStr: String
    ) {
        val toolInfo = formatToolCallDisplay(toolName, paramsStr)
        val params = parseParamsString(paramsStr)
        val toolType = toolName.toToolType()

        // Handle task-boundary tool - update task list
        if (toolName == "task-boundary") {
            updateTaskFromToolCall(params)
        }

        // Handle plan management tool - update plan state
        if (toolName == "plan") {
            updatePlanFromToolCall(params)
            // Skip rendering plan tool to timeline - it's shown in PlanSummaryBar
            return
        }

        renderToolCallInternal(toolName, toolInfo, params, paramsStr, toolType)
    }

    /**
     * Render a tool call with parsed parameters.
     * This is the preferred method as it avoids string parsing issues with complex values.
     */
    override fun renderToolCallWithParams(toolName: String, params: Map<String, Any>) {
        // Convert params to string format for display
        val paramsStr = params.entries.joinToString(" ") { (key, value) ->
            "$key=\"$value\""
        }
        val toolInfo = formatToolCallDisplay(toolName, paramsStr)
        val toolType = toolName.toToolType()

        // Convert Map<String, Any> to Map<String, String> for internal use
        val stringParams = params.mapValues { it.value.toString() }

        // Handle task-boundary tool - update task list
        if (toolName == "task-boundary") {
            updateTaskFromToolCall(stringParams)
        }

        // Handle plan management tool - update plan state with original params
        if (toolName == "plan") {
            updatePlanFromToolCallWithAnyParams(params)
        }

        // Skip rendering plan tool to timeline - it's shown in PlanSummaryBar
        if (toolName != "plan") {
            renderToolCallInternal(toolName, toolInfo, stringParams, paramsStr, toolType)
        }
    }

    /**
     * Internal method to render tool call UI elements
     */
    private fun renderToolCallInternal(
        toolName: String,
        toolInfo: ToolCallInfo,
        params: Map<String, String>,
        paramsStr: String,
        toolType: ToolType?
    ) {
        // Extract file path for read/write operations
        val filePath =
            when (toolType) {
                ToolType.ReadFile, ToolType.WriteFile -> params["path"]
                else -> null
            }

        // Create a tool call item with only call information (result will be added later)
        _timeline.add(
            ToolCallItem(
                toolName = toolInfo.toolName,
                description = toolInfo.description,
                params = toolInfo.details ?: "",
                fullParams = paramsStr, // 保存完整的原始参数
                filePath = filePath, // 保存文件路径
                toolType = toolType, // 保存工具类型
                success = null, // null indicates still executing
                summary = null,
                output = null,
                fullOutput = null,
                executionTimeMs = null
            )
        )

        _currentToolCall =
            ToolCallInfo(
                toolName = toolInfo.toolName,
                description = toolInfo.description,
                details = toolInfo.details
            )
    }

    /**
     * Update task list from task-boundary tool call
     */
    private fun updateTaskFromToolCall(params: Map<String, String>) {
        val taskName = params["taskName"] ?: return
        val statusStr = params["status"] ?: "WORKING"
        val summary = params["summary"] ?: ""
        val status = TaskStatus.fromString(statusStr)

        // Find existing task or create new one
        val existingIndex = _tasks.indexOfFirst { it.taskName == taskName }

        if (existingIndex >= 0) {
            // Update existing task
            val existingTask = _tasks[existingIndex]
            _tasks[existingIndex] = existingTask.copy(
                status = status,
                summary = summary,
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
        } else {
            // Add new task
            _tasks.add(
                TaskInfo(
                    taskName = taskName,
                    status = status,
                    summary = summary
                )
            )
        }

        // Remove completed or cancelled tasks after a delay (keep them visible briefly)
        if (status == TaskStatus.COMPLETED || status == TaskStatus.CANCELLED) {
            // Keep completed tasks visible for review
            // You could add auto-removal logic here if desired
        }
    }

    /**
     * Handle task-boundary tool call to update task progress display.
     * Overrides the interface method to provide UI-specific task tracking.
     */
    override fun handleTaskBoundary(taskName: String, status: String, summary: String) {
        updateTaskFromToolCall(mapOf(
            "taskName" to taskName,
            "status" to status,
            "summary" to summary
        ))
    }

    /**
     * Update plan state from plan management tool call (string params version)
     */
    private fun updatePlanFromToolCall(params: Map<String, String>) {
        val action = params["action"]?.uppercase() ?: return
        val planMarkdown = params["planMarkdown"] ?: ""
        val taskIndex = params["taskIndex"]?.toIntOrNull()
        val stepIndex = params["stepIndex"]?.toIntOrNull()

        updatePlanState(action, planMarkdown, taskIndex, stepIndex)
    }

    /**
     * Update plan state from plan management tool call with Any params.
     * This is the preferred method as it handles complex values correctly.
     */
    private fun updatePlanFromToolCallWithAnyParams(params: Map<String, Any>) {
        val action = (params["action"] as? String)?.uppercase() ?: return
        val planMarkdown = params["planMarkdown"] as? String ?: ""
        val taskIndex = when (val v = params["taskIndex"]) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull()
            else -> null
        }
        val stepIndex = when (val v = params["stepIndex"]) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull()
            else -> null
        }

        updatePlanState(action, planMarkdown, taskIndex, stepIndex)
    }

    /**
     * Internal method to update plan state
     */
    private fun updatePlanState(action: String, planMarkdown: String, taskIndex: Int?, stepIndex: Int?) {
        when (action) {
            "CREATE", "UPDATE" -> {
                if (planMarkdown.isNotBlank()) {
                    _currentPlan = MarkdownPlanParser.parseToPlan(planMarkdown)
                }
            }
            "COMPLETE_STEP" -> {
                if (taskIndex == null || stepIndex == null) return
                _currentPlan?.let { plan ->
                    if (taskIndex in 1..plan.tasks.size) {
                        val task = plan.tasks[taskIndex - 1]
                        if (stepIndex in 1..task.steps.size) {
                            val step = task.steps[stepIndex - 1]
                            step.complete()
                            task.updateStatusFromSteps()
                            // Trigger recomposition by creating a new plan instance
                            _currentPlan = plan.copy(updatedAt = Clock.System.now().toEpochMilliseconds())
                        }
                    }
                }
            }
            "FAIL_STEP" -> {
                if (taskIndex == null || stepIndex == null) return
                _currentPlan?.let { plan ->
                    if (taskIndex in 1..plan.tasks.size) {
                        val task = plan.tasks[taskIndex - 1]
                        if (stepIndex in 1..task.steps.size) {
                            val step = task.steps[stepIndex - 1]
                            step.fail()
                            task.updateStatusFromSteps()
                            _currentPlan = plan.copy(updatedAt = Clock.System.now().toEpochMilliseconds())
                        }
                    }
                }
            }
            // VIEW action doesn't modify state
        }
    }

    override fun renderToolResult(
        toolName: String,
        success: Boolean,
        output: String?,
        fullOutput: String?,
        metadata: Map<String, String>
    ) {
        val summary = formatToolResultSummary(toolName, success, output)

        // Check if this was a live terminal session
        val isLiveSession = metadata["isLiveSession"] == "true"
        val liveExitCode = metadata["exit_code"]?.toIntOrNull()

        // For shell commands, use special terminal output rendering
        val toolType = toolName.toToolType()
        val stillRunning = metadata["stillRunning"] == "true"
        if (toolType == ToolType.Shell && stillRunning) {
            // Don't convert to TerminalOutputItem on initial timeout.
            // Live output is already shown via LiveTerminalItem; keep the tool call in "executing" state.
            _currentToolCall = null
            return
        }
        if (toolType == ToolType.Shell && output != null) {
            // Try to extract shell result information
            val exitCode = liveExitCode ?: (if (success) 0 else 1)
            val executionTime = metadata["execution_time_ms"]?.toLongOrNull() ?: 0L
            val cancelledByUser = metadata["cancelled"] == "true" || exitCode == 129 || exitCode == 130 || exitCode == 137

            // Extract command from the last tool call if available
            val command = _currentToolCall?.details?.removePrefix("Executing: ") ?: "unknown"

            // IMPORTANT: Clear currentToolCall FIRST to avoid showing both
            // CurrentToolCallItem and the result item simultaneously (double progress bar issue)
            _currentToolCall = null

            // User-cancelled shell commands (e.g. Stop button) should not add a separate TerminalOutputItem.
            // The LiveTerminalItem already shows the final exit code and the user intent is explicit.
            if (cancelledByUser) {
                // Best-effort: mark the latest shell ToolCallItem as cancelled so it doesn't stay "executing".
                val lastShellIndex = _timeline.indexOfLast {
                    it is ToolCallItem && it.toolType == ToolType.Shell && it.success == null
                }
                if (lastShellIndex >= 0) {
                    val item = _timeline[lastShellIndex] as ToolCallItem
                    _timeline[lastShellIndex] = item.copy(
                        success = false,
                        summary = "Cancelled",
                        output = null,
                        fullOutput = null,
                        executionTimeMs = executionTime
                    )
                }
                return
            }

            // For Live sessions, we show both the terminal widget and the result summary
            // Don't remove anything, just add a result item after the live terminal
            if (isLiveSession) {
                // Add a summary result item after the live terminal
                _timeline.add(
                    TimelineItem.TerminalOutputItem(
                        command = command,
                        output = fullOutput ?: output,
                        exitCode = exitCode,
                        executionTimeMs = executionTime
                    )
                )
            } else {
                // For non-live sessions, replace the tool call item with terminal output
                val lastItem = _timeline.lastOrNull()
                if (lastItem is ToolCallItem && lastItem.toolType == ToolType.Shell) {
                    _timeline.removeAt(_timeline.size - 1)
                }

                _timeline.add(
                    TimelineItem.TerminalOutputItem(
                        command = command,
                        output = fullOutput ?: output,
                        exitCode = exitCode,
                        executionTimeMs = executionTime
                    )
                )
            }
        } else {
            // IMPORTANT: Clear currentToolCall FIRST to avoid showing both
            // CurrentToolCallItem and the result item simultaneously (double progress bar issue)
            _currentToolCall = null

            // Update the last ToolCallItem with result information
            val lastItem = _timeline.lastOrNull()
            if (lastItem is ToolCallItem && lastItem.success == null) {
                // Remove the incomplete item
                _timeline.removeAt(_timeline.size - 1)

                // Add the complete item with result
                val executionTime = metadata["execution_time_ms"]?.toLongOrNull()

                // Extract DocQL search stats if available
                val docqlStats = DocQLSearchStats.fromMetadata(metadata)

                // For DocQL, use detailedResults from stats as fullOutput if available
                // output should be the compact summary, fullOutput should be the detailed results
                val finalFullOutput = when {
                    // If fullOutput is explicitly provided, use it
                    !fullOutput.isNullOrBlank() -> fullOutput
                    // For DocQL, use detailedResults from stats if available
                    toolName.lowercase() == "docql" && docqlStats?.detailedResults != null -> docqlStats.detailedResults
                    // Otherwise, use output as fallback
                    else -> output
                }

                _timeline.add(
                    lastItem.copy(
                        success = success,
                        summary = summary,
                        output = if (success && output != null) {
                            // For file search tools, keep full output; for others, limit to 2000 chars for direct display
                            when (toolName) {
                                "glob", "grep" -> output
                                // For DocQL, output is already the compact summary, so use it as-is
                                "docql" -> output
                                else -> if (output.length <= 2000) output else "${output.take(2000)}...\n[Output truncated - click to view full]"
                            }
                        } else {
                            null
                        },
                        fullOutput = finalFullOutput,
                        executionTimeMs = executionTime,
                        docqlStats = docqlStats
                    )
                )
            }
        }
    }

    override fun renderTaskComplete(executionTimeMs: Long, toolsUsedCount: Int) {
        _taskCompleted = true
        _isProcessing = false

        // Add a completion message with execution time and tool usage to the timeline
        val parts = mutableListOf<String>()

        if (executionTimeMs > 0) {
            val seconds = executionTimeMs / 1000.0
            val rounded = (seconds * 100).toLong() / 100.0
            parts.add("${rounded}s")
        }

        if (toolsUsedCount > 0) {
            parts.add("$toolsUsedCount tools")
        }

        if (parts.isNotEmpty()) {
            _timeline.add(
                TimelineItem.MessageItem(
                    message = Message(
                        role = MessageRole.ASSISTANT,
                        content = "✓ Task marked as complete (${parts.joinToString(", ")})"
                    )
                )
            )
        }
    }

    override fun renderFinalResult(
        success: Boolean,
        message: String,
        iterations: Int
    ) {
        _timeline.add(
            TimelineItem.TaskCompleteItem(
                success = success,
                message = message
            )
        )
        _isProcessing = false
        _taskCompleted = true
    }

    override fun renderError(message: String) {
        _timeline.add(ErrorItem(message = message))
        _errorMessage = message
        _isProcessing = false
    }

    override fun renderInfo(message: String) {
        _timeline.add(TimelineItem.InfoItem(message = message))
    }

    override fun renderRepeatWarning(
        toolName: String,
        count: Int
    ) {
        _timeline.add(
            TimelineItem.MessageItem(
                message =
                    Message(
                        role = MessageRole.ASSISTANT,
                        content = "⚠️ Warning: Tool '$toolName' has been called $count times in a row"
                    )
            )
        )
    }

    override fun renderRecoveryAdvice(recoveryAdvice: String) {
        _timeline.add(
            TimelineItem.MessageItem(
                message =
                    Message(
                        role = MessageRole.ASSISTANT,
                        content = "🔧 ERROR RECOVERY ADVICE:\n$recoveryAdvice"
                    )
            )
        )
    }

    override fun renderUserConfirmationRequest(
        toolName: String,
        params: Map<String, Any>
    ) {
        // For now, just use error rendering since JS renderer doesn't have this method yet
    }

    override fun renderSqlApprovalRequest(
        sql: String,
        operationType: SqlOperationType,
        affectedTables: List<String>,
        isHighRisk: Boolean,
        dryRunResult: DryRunResult?,
        onApprove: () -> Unit,
        onReject: () -> Unit
    ) {
        _pendingSqlApproval = SqlApprovalRequest(
            sql = sql,
            operationType = operationType,
            affectedTables = affectedTables,
            isHighRisk = isHighRisk,
            dryRunResult = dryRunResult,
            onApprove = {
                _pendingSqlApproval = null
                onApprove()
            },
            onReject = {
                _pendingSqlApproval = null
                onReject()
            }
        )

        // Build details map with dry run info
        val details = mutableMapOf<String, Any>(
            "sql" to sql,
            "operationType" to operationType.name,
            "affectedTables" to affectedTables.joinToString(", "),
            "isHighRisk" to isHighRisk
        )
        if (dryRunResult != null) {
            details["dryRunValid"] = dryRunResult.isValid
            if (dryRunResult.estimatedRows != null) {
                details["estimatedRows"] = dryRunResult.estimatedRows!!
            }
            if (dryRunResult.warnings.isNotEmpty()) {
                details["warnings"] = dryRunResult.warnings.joinToString(", ")
            }
        }

        // Also add to timeline for visibility
        renderChatDBStep(
            stepType = ChatDBStepType.AWAIT_APPROVAL,
            status = ChatDBStepStatus.AWAITING_APPROVAL,
            title = "Awaiting Approval: ${operationType.name}",
            details = details
        )
    }

    /**
     * Approve the pending SQL operation
     */
    fun approveSqlOperation() {
        _pendingSqlApproval?.onApprove?.invoke()
    }

    /**
     * Reject the pending SQL operation
     */
    fun rejectSqlOperation() {
        _pendingSqlApproval?.onReject?.invoke()
    }

    // Public methods for UI interaction
    fun addUserMessage(content: String) {
        _timeline.add(
            TimelineItem.MessageItem(
                message =
                    Message(
                        role = MessageRole.USER,
                        content = content
                    )
            )
        )
    }

    fun clearMessages() {
        _timeline.clear()
        _currentStreamingOutput = ""
        _errorMessage = null
        _taskCompleted = false
        _isProcessing = false
        _executionStartTime = 0L
        _currentExecutionTime = 0L
        _totalTokenInfo = TokenInfo()
        _lastMessageTokenInfo = null
        // Clear plan and tasks state for new session
        _currentPlan = null
        _tasks.clear()
    }

    fun clearError() {
        _errorMessage = null
    }

    /**
     * Dismiss a timeline item (e.g. non-critical warning/error notices) by id.
     * This keeps the chat view compact while still allowing transient notices to be shown.
     */
    fun dismissTimelineItem(itemId: String) {
        val index = _timeline.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            _timeline.removeAt(index)
        }
    }

    fun openFileViewer(filePath: String) {
        _currentViewingFile = filePath
    }

    fun closeFileViewer() {
        _currentViewingFile = null
    }

    /**
     * Adds a live terminal session to the timeline.
     * This is called when a Shell tool is executed with PTY support.
     *
     * Note: We keep the ToolCallItem so the user can see both the command call
     * and the live terminal output side by side.
     */
    override fun addLiveTerminal(
        sessionId: String,
        command: String,
        workingDirectory: String?,
        ptyHandle: Any?
    ) {
        // Add the live terminal item to the timeline
        // We no longer remove the ToolCallItem - both should be shown for complete visibility
        _timeline.add(
            TimelineItem.LiveTerminalItem(
                sessionId = sessionId,
                command = command,
                workingDirectory = workingDirectory,
                ptyHandle = ptyHandle
            )
        )
    }

    /**
     * Update the status of a live terminal session when it completes.
     * This is called from the background monitoring coroutine in ToolOrchestrator.
     */
    override fun updateLiveTerminalStatus(
        sessionId: String,
        exitCode: Int,
        executionTimeMs: Long,
        output: String?,
        cancelledByUser: Boolean
    ) {
        // Find and update the LiveTerminalItem in the timeline
        val index = _timeline.indexOfFirst {
            it is TimelineItem.LiveTerminalItem && it.sessionId == sessionId
        }

        if (index >= 0) {
            val existingItem = _timeline[index] as TimelineItem.LiveTerminalItem
            // Replace with updated item containing exit code and execution time
            _timeline[index] = existingItem.copy(
                exitCode = exitCode,
                executionTimeMs = executionTimeMs,
                output = output
            )
        }

        // Also notify any waiting coroutines via the session result channel
        sessionResultChannels[sessionId]?.let { channel ->
            // Check cancelledByUser first to handle cancelled commands even if exitCode is 0
            val result = when {
                cancelledByUser -> {
                    val errorMessage =
                        buildString {
                            appendLine("⚠️ Command cancelled by user")
                            appendLine()
                            appendLine("Exit code: $exitCode (SIGKILL)")
                            appendLine()
                            if (!output.isNullOrEmpty()) {
                                appendLine("Output before cancellation:")
                                appendLine(output)
                            } else {
                                appendLine("(no output captured before cancellation)")
                            }
                        }
                    cc.unitmesh.agent.tool.ToolResult.Error(
                        message = errorMessage,
                        errorType = "CANCELLED_BY_USER",
                        metadata = mapOf(
                            "exit_code" to exitCode.toString(),
                            "execution_time_ms" to executionTimeMs.toString(),
                            "output" to (output ?: ""),
                            "cancelled" to "true"
                        )
                    )
                }

                exitCode == 0 -> {
                    cc.unitmesh.agent.tool.ToolResult.Success(
                        content = output ?: "",
                        metadata = mapOf(
                            "exit_code" to exitCode.toString(),
                            "execution_time_ms" to executionTimeMs.toString()
                        )
                    )
                }

                else -> {
                    cc.unitmesh.agent.tool.ToolResult.Error(
                        message = "Command failed with exit code: $exitCode\n${output ?: ""}",
                        errorType = cc.unitmesh.agent.tool.ToolErrorType.COMMAND_FAILED.code,
                        metadata = mapOf(
                            "exit_code" to exitCode.toString(),
                            "execution_time_ms" to executionTimeMs.toString(),
                            "output" to (output ?: ""),
                            "cancelled" to "false"
                        )
                    )
                }
            }
            channel.trySend(result)
            sessionResultChannels.remove(sessionId)
        }
    }

    // Channel map for awaiting session results
    private val sessionResultChannels = mutableMapOf<String, kotlinx.coroutines.channels.Channel<cc.unitmesh.agent.tool.ToolResult>>()

    /**
     * Await the result of an async shell session.
     * Used when the Agent needs to wait for a shell command to complete before proceeding.
     */
    override suspend fun awaitSessionResult(sessionId: String, timeoutMs: Long): cc.unitmesh.agent.tool.ToolResult {
        // Check if the session is already completed
        val existingItem = _timeline.find {
            it is TimelineItem.LiveTerminalItem && it.sessionId == sessionId
        } as? TimelineItem.LiveTerminalItem

        if (existingItem?.exitCode != null) {
            // Session already completed
            return if (existingItem.exitCode == 0) {
                cc.unitmesh.agent.tool.ToolResult.Success(
                    content = "",
                    metadata = mapOf(
                        "exit_code" to existingItem.exitCode.toString(),
                        "execution_time_ms" to (existingItem.executionTimeMs ?: 0L).toString()
                    )
                )
            } else {
                cc.unitmesh.agent.tool.ToolResult.Error(
                    message = buildString {
                        appendLine("Command failed with exit code: ${existingItem.exitCode}")
                        if (!existingItem.output.isNullOrEmpty()) {
                            appendLine()
                            appendLine(existingItem.output)
                        }
                    },
                    metadata = mapOf(
                        "exit_code" to existingItem.exitCode.toString(),
                        "execution_time_ms" to (existingItem.executionTimeMs ?: 0L).toString()
                    )
                )
            }
        }

        // Create a channel to wait for the result
        val channel = kotlinx.coroutines.channels.Channel<cc.unitmesh.agent.tool.ToolResult>(1)
        sessionResultChannels[sessionId] = channel

        return try {
            kotlinx.coroutines.withTimeout(timeoutMs) {
                channel.receive()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            sessionResultChannels.remove(sessionId)
            // Session is likely still running (e.g. long-running server). Return Pending with an output snapshot
            // so the executor can include it in the prompt.
            val runningItem = _timeline.find {
                it is TimelineItem.LiveTerminalItem && it.sessionId == sessionId
            } as? TimelineItem.LiveTerminalItem

            val command = runningItem?.command?.ifBlank { "unknown" } ?: "unknown"

            val rawOutput = try {
                val session = cc.unitmesh.agent.tool.shell.ShellSessionManager.getSession(sessionId)
                session?.getOutput().orEmpty()
            } catch (_: Exception) {
                ""
            }

            val cleanOutput = cc.unitmesh.agent.tool.shell.AnsiStripper.stripAndNormalize(rawOutput)
            val outputLength = cleanOutput.length
            val maxChars = 4000
            val snippet = if (outputLength > maxChars) cleanOutput.takeLast(maxChars) else cleanOutput

            cc.unitmesh.agent.tool.ToolResult.Pending(
                sessionId = sessionId,
                toolName = "shell",
                command = command,
                message = "Process still running after ${timeoutMs}ms",
                metadata = mapOf(
                    "sessionId" to sessionId,
                    "command" to command,
                    "elapsed_ms" to timeoutMs.toString(),
                    "partial_output" to snippet,
                    "output_length" to outputLength.toString()
                )
            )
        }
    }

    fun forceStop() {
        // If there's streaming output, save it as a message first
        val currentOutput = _currentStreamingOutput.trim()
        if (currentOutput.isNotEmpty()) {
            _timeline.add(
                TimelineItem.MessageItem(
                    message =
                        Message(
                            role = MessageRole.ASSISTANT,
                            content = "$currentOutput\n\n[Interrupted]"
                        )
                )
            )
        }

        _isProcessing = false
        _currentStreamingOutput = ""
        _currentToolCall = null
    }

    private fun formatToolCallDisplay(toolName: String, paramsStr: String): ToolCallInfo {
        return RendererUtils.toToolCallInfo(RendererUtils.formatToolCallDisplay(toolName, paramsStr))
    }

    private fun formatToolResultSummary(toolName: String, success: Boolean, output: String?): String {
        return RendererUtils.formatToolResultSummary(toolName, success, output)
    }

    private fun parseParamsString(paramsStr: String): Map<String, String> {
        return RendererUtils.parseParamsString(paramsStr)
    }

    /**
     * Update token information from LLM response
     * Called when StreamFrame.End is received with token metadata
     */
    override fun updateTokenInfo(tokenInfo: TokenInfo) {
        _lastMessageTokenInfo = tokenInfo
        // Accumulate total tokens
        _totalTokenInfo = TokenInfo(
            totalTokens = _totalTokenInfo.totalTokens + tokenInfo.totalTokens,
            inputTokens = _totalTokenInfo.inputTokens + tokenInfo.inputTokens,
            outputTokens = _totalTokenInfo.outputTokens + tokenInfo.outputTokens,
            timestamp = tokenInfo.timestamp
        )
    }

    /**
     * Render a ChatDB execution step.
     * Adds or updates a step in the timeline for interactive display.
     */
    override fun renderChatDBStep(
        stepType: ChatDBStepType,
        status: ChatDBStepStatus,
        title: String,
        details: Map<String, Any>,
        error: String?
    ) {
        // Check if this step already exists in the timeline
        val existingIndex = _timeline.indexOfLast {
            it is ChatDBStepItem && it.stepType == stepType
        }

        val stepItem = ChatDBStepItem(
            stepType = stepType,
            status = status,
            title = title,
            details = details,
            error = error
        )

        if (existingIndex >= 0) {
            // Update existing step
            _timeline[existingIndex] = stepItem
        } else {
            // Add new step
            _timeline.add(stepItem)
        }
    }

    /**
     * Render an Agent-generated sketch block (chart, nanodsl, mermaid, etc.)
     * Adds the sketch block to the timeline for interactive rendering.
     */
    override fun renderAgentSketchBlock(
        agentName: String,
        language: String,
        code: String,
        metadata: Map<String, String>
    ) {
        _timeline.add(
            TimelineItem.AgentSketchBlockItem(
                agentName = agentName,
                language = language,
                code = code,
                metadata = metadata
            )
        )
    }

    // ============================================================
    // Multimodal Analysis Support
    // ============================================================

    /**
     * Start multimodal analysis - adds a new MultimodalAnalysisItem to timeline.
     */
    fun startMultimodalAnalysis(
        images: List<ImageInfo>,
        prompt: String,
        visionModel: String
    ): String {
        val item = TimelineItem.MultimodalAnalysisItem(
            images = images,
            prompt = prompt,
            visionModel = visionModel,
            status = MultimodalAnalysisStatus.COMPRESSING
        )
        _timeline.add(item)
        return item.id
    }

    /**
     * Update multimodal analysis status.
     */
    fun updateMultimodalAnalysisStatus(
        itemId: String,
        status: MultimodalAnalysisStatus,
        progress: String? = null
    ) {
        val index = _timeline.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            val item = _timeline[index] as? TimelineItem.MultimodalAnalysisItem ?: return
            _timeline[index] = item.copy(
                status = status,
                progress = progress
            )
        }
    }

    /**
     * Append streaming result to multimodal analysis.
     */
    fun appendMultimodalAnalysisChunk(itemId: String, chunk: String) {
        val index = _timeline.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            val item = _timeline[index] as? TimelineItem.MultimodalAnalysisItem ?: return
            _timeline[index] = item.copy(
                status = MultimodalAnalysisStatus.STREAMING,
                streamingResult = item.streamingResult + chunk
            )
        }
    }

    /**
     * Complete multimodal analysis with final result.
     */
    fun completeMultimodalAnalysis(
        itemId: String,
        result: String,
        executionTimeMs: Long
    ) {
        val index = _timeline.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            val item = _timeline[index] as? TimelineItem.MultimodalAnalysisItem ?: return
            _timeline[index] = item.copy(
                status = MultimodalAnalysisStatus.COMPLETED,
                finalResult = result,
                streamingResult = result,
                executionTimeMs = executionTimeMs
            )
        }
    }

    /**
     * Fail multimodal analysis with error.
     */
    fun failMultimodalAnalysis(itemId: String, error: String) {
        val index = _timeline.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            val item = _timeline[index] as? TimelineItem.MultimodalAnalysisItem ?: return
            _timeline[index] = item.copy(
                status = MultimodalAnalysisStatus.FAILED,
                error = error
            )
        }
    }

    /**
     * Convert a TimelineItem to MessageMetadata for persistence
     */
    private fun toMessageMetadata(item: TimelineItem): cc.unitmesh.devins.llm.MessageMetadata? {
        return when (item) {
            is TimelineItem.MessageItem -> {
                cc.unitmesh.devins.llm.MessageMetadata(
                    itemType = cc.unitmesh.devins.llm.TimelineItemType.MESSAGE,
                    tokenInfoTotal = item.tokenInfo?.totalTokens,
                    tokenInfoInput = item.tokenInfo?.inputTokens,
                    tokenInfoOutput = item.tokenInfo?.outputTokens
                )
            }

            is ToolCallItem -> {
                val stats = item.docqlStats
                cc.unitmesh.devins.llm.MessageMetadata(
                    itemType = cc.unitmesh.devins.llm.TimelineItemType.COMBINED_TOOL,
                    toolName = item.toolName,
                    description = item.description,
                    details = item.params,
                    fullParams = item.fullParams,
                    filePath = item.filePath,
                    toolType = item.toolType?.name,
                    success = item.success,
                    summary = item.summary,
                    output = item.output,
                    fullOutput = item.fullOutput,
                    executionTimeMs = item.executionTimeMs,
                    // DocQL stats
                    docqlSearchType = stats?.searchType?.name,
                    docqlQuery = stats?.query,
                    docqlDocumentPath = stats?.documentPath,
                    docqlChannels = stats?.channels?.joinToString(","),
                    docqlDocsSearched = stats?.documentsSearched,
                    docqlRawResults = stats?.totalRawResults,
                    docqlRerankedResults = stats?.resultsAfterRerank,
                    docqlTruncated = stats?.truncated,
                    docqlUsedFallback = stats?.usedFallback,
                    docqlDetailedResults = stats?.detailedResults,
                    docqlSmartSummary = stats?.smartSummary
                )
            }

            is ErrorItem -> {
                cc.unitmesh.devins.llm.MessageMetadata(
                    itemType = cc.unitmesh.devins.llm.TimelineItemType.TOOL_ERROR,
                    taskMessage = item.message
                )
            }

            is TimelineItem.TaskCompleteItem -> {
                cc.unitmesh.devins.llm.MessageMetadata(
                    itemType = cc.unitmesh.devins.llm.TimelineItemType.TASK_COMPLETE,
                    taskSuccess = item.success,
                    taskMessage = item.message
                )
            }

            is TimelineItem.TerminalOutputItem -> {
                cc.unitmesh.devins.llm.MessageMetadata(
                    itemType = cc.unitmesh.devins.llm.TimelineItemType.TERMINAL_OUTPUT,
                    command = item.command,
                    output = item.output,
                    exitCode = item.exitCode,
                    executionTimeMs = item.executionTimeMs
                )
            }

            is TimelineItem.LiveTerminalItem -> {
                // Live terminal items are not persisted (they're runtime-only)
                null
            }

            is TimelineItem.AgentSketchBlockItem -> {
                cc.unitmesh.devins.llm.MessageMetadata(
                    itemType = cc.unitmesh.devins.llm.TimelineItemType.AGENT_SKETCH_BLOCK,
                    agentName = item.agentName,
                    sketchLanguage = item.language,
                    sketchCode = item.code
                )
            }

            is ChatDBStepItem -> {
                // ChatDB steps are not persisted (they're runtime-only for UI display)
                null
            }

            is TimelineItem.InfoItem -> {
                // Info items are not persisted (they're runtime-only for UI display)
                null
            }

            is TimelineItem.MultimodalAnalysisItem -> {
                // Multimodal analysis items are not persisted (they're runtime-only)
                null
            }
        }
    }

    /**
     * Convert MessageMetadata back to a TimelineItem
     */
    private fun fromMessageMetadata(
        metadata: cc.unitmesh.devins.llm.MessageMetadata,
        message: cc.unitmesh.devins.llm.Message
    ): TimelineItem? {
        return when (metadata.itemType) {
            TimelineItemType.MESSAGE -> {
                val totalTokens = metadata.tokenInfoTotal
                val tokenInfo = if (totalTokens != null) {
                    TokenInfo(
                        totalTokens = totalTokens,
                        inputTokens = metadata.tokenInfoInput ?: 0,
                        outputTokens = metadata.tokenInfoOutput ?: 0
                    )
                } else null

                MessageItem(
                    message = message,
                    tokenInfo = tokenInfo,
                    timestamp = message.timestamp
                )
            }

            TimelineItemType.COMBINED_TOOL -> {
                // Restore DocQL stats if available
                val searchTypeStr = metadata.docqlSearchType
                val docqlStats = if (searchTypeStr != null) {
                    val searchType = try {
                        DocQLSearchStats.SearchType.valueOf(searchTypeStr)
                    } catch (_: IllegalArgumentException) {
                        null
                    }

                    searchType?.let {
                        DocQLSearchStats(
                            searchType = it,
                            query = metadata.docqlQuery ?: "",
                            documentPath = metadata.docqlDocumentPath,
                            channels = metadata.docqlChannels?.split(",")?.filter { ch -> ch.isNotBlank() } ?: emptyList(),
                            documentsSearched = metadata.docqlDocsSearched ?: 0,
                            totalRawResults = metadata.docqlRawResults ?: 0,
                            resultsAfterRerank = metadata.docqlRerankedResults ?: 0,
                            truncated = metadata.docqlTruncated ?: false,
                            usedFallback = metadata.docqlUsedFallback ?: false,
                            detailedResults = metadata.docqlDetailedResults ?: "",
                            smartSummary = metadata.docqlSmartSummary
                        )
                    }
                } else null

                ToolCallItem(
                    toolName = metadata.toolName ?: "",
                    description = metadata.description ?: "",
                    params = metadata.details ?: "",
                    fullParams = metadata.fullParams,
                    filePath = metadata.filePath,
                    toolType = metadata.toolType?.toToolType(),
                    success = metadata.success,
                    summary = metadata.summary,
                    output = metadata.output,
                    fullOutput = metadata.fullOutput,
                    executionTimeMs = metadata.executionTimeMs,
                    docqlStats = docqlStats,
                    timestamp = message.timestamp
                )
            }

            TimelineItemType.TOOL_RESULT -> {
                // Legacy support: convert old ToolResultItem to ToolCallItem
                ToolCallItem(
                    toolName = metadata.toolName ?: "",
                    description = "",
                    params = "",
                    success = metadata.success,
                    summary = metadata.summary,
                    output = metadata.output,
                    fullOutput = metadata.fullOutput,
                    timestamp = message.timestamp
                )
            }

            TimelineItemType.TOOL_ERROR -> {
                ErrorItem(
                    message = metadata.taskMessage ?: "Unknown error",
                    timestamp = message.timestamp
                )
            }

            TimelineItemType.TASK_COMPLETE -> {
                TaskCompleteItem(
                    success = metadata.taskSuccess ?: false,
                    message = metadata.taskMessage ?: "",
                    timestamp = message.timestamp
                )
            }

            TimelineItemType.TERMINAL_OUTPUT -> {
                TerminalOutputItem(
                    command = metadata.command ?: "",
                    output = message.content,
                    exitCode = metadata.exitCode ?: 0,
                    executionTimeMs = metadata.executionTimeMs ?: 0,
                    timestamp = message.timestamp
                )
            }

            TimelineItemType.AGENT_SKETCH_BLOCK -> {
                AgentSketchBlockItem(
                    agentName = metadata.agentName ?: "",
                    language = metadata.sketchLanguage ?: "",
                    code = metadata.sketchCode ?: message.content,
                    timestamp = message.timestamp
                )
            }

            TimelineItemType.LIVE_TERMINAL -> {
                // Live terminal items are runtime-only and cannot be restored from metadata
                null
            }
        }
    }

    /**
     * Load timeline from a list of messages
     * This is used when switching sessions or loading history
     */
    fun loadFromMessages(messages: List<cc.unitmesh.devins.llm.Message>) {
        _timeline.clear()

        messages.forEach { message ->
            val messageMetadata = message.metadata
            val timelineItem = if (messageMetadata != null) {
                // Try to reconstruct from metadata
                fromMessageMetadata(messageMetadata, message)
            } else {
                // Fallback: create a simple MessageItem for messages without metadata
                MessageItem(
                    message = message,
                    tokenInfo = null,
                    timestamp = message.timestamp
                )
            }

            timelineItem?.let { _timeline.add(it) }
        }
    }

    /**
     * Get current timeline as messages with metadata
     * This is used when saving conversation history
     */
    fun getTimelineSnapshot(): List<cc.unitmesh.devins.llm.Message> {
        return _timeline.mapNotNull { item ->
            when (item) {
                is MessageItem -> {
                    // Return the original message with metadata
                    item.message?.copy(
                        metadata = toMessageMetadata(item)
                    ) ?: cc.unitmesh.devins.llm.Message(
                        role = item.role,
                        content = item.content,
                        timestamp = item.timestamp,
                        metadata = toMessageMetadata(item)
                    )
                }

                is ToolCallItem -> {
                    // Create a message representing the tool call and result
                    val content = buildString {
                        append("[${item.toolName}] ")
                        append(item.description)
                        if (item.summary != null) {
                            append(" -> ${item.summary}")
                        }
                    }
                    cc.unitmesh.devins.llm.Message(
                        role = MessageRole.ASSISTANT,
                        content = content,
                        timestamp = item.timestamp,
                        metadata = toMessageMetadata(item)
                    )
                }

                is TerminalOutputItem -> {
                    cc.unitmesh.devins.llm.Message(
                        role = MessageRole.ASSISTANT,
                        content = item.output,
                        timestamp = item.timestamp,
                        metadata = toMessageMetadata(item)
                    )
                }

                is TaskCompleteItem -> {
                    cc.unitmesh.devins.llm.Message(
                        role = MessageRole.ASSISTANT,
                        content = item.message,
                        timestamp = item.timestamp,
                        metadata = toMessageMetadata(item)
                    )
                }

                is ErrorItem -> {
                    cc.unitmesh.devins.llm.Message(
                        role = MessageRole.ASSISTANT,
                        content = item.message,
                        timestamp = item.timestamp,
                        metadata = toMessageMetadata(item)
                    )
                }

                is LiveTerminalItem -> null

                is TimelineItem.AgentSketchBlockItem -> {
                    cc.unitmesh.devins.llm.Message(
                        role = MessageRole.ASSISTANT,
                        content = "```${item.language}\n${item.code}\n```",
                        timestamp = item.timestamp,
                        metadata = toMessageMetadata(item)
                    )
                }

                is ChatDBStepItem -> {
                    // ChatDB steps are not persisted as messages
                    null
                }

                is TimelineItem.InfoItem -> {
                    // Info items are not persisted as messages
                    null
                }

                is TimelineItem.MultimodalAnalysisItem -> {
                    // Multimodal analysis items can be saved with their final result
                    if (item.finalResult != null) {
                        cc.unitmesh.devins.llm.Message(
                            role = MessageRole.ASSISTANT,
                            content = "[Vision Analysis]\n${item.finalResult}",
                            timestamp = item.timestamp,
                            metadata = null // No specific metadata needed
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }
}

/**
 * Data class representing a pending SQL approval request
 */
data class SqlApprovalRequest(
    val sql: String,
    val operationType: SqlOperationType,
    val affectedTables: List<String>,
    val isHighRisk: Boolean,
    val dryRunResult: DryRunResult? = null,
    val onApprove: () -> Unit,
    val onReject: () -> Unit
)

