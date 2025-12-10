package cc.unitmesh.agent.render

import cc.unitmesh.agent.database.DryRunResult
import cc.unitmesh.agent.plan.PlanSummaryData
import cc.unitmesh.agent.subagent.SqlOperationType
import cc.unitmesh.agent.tool.ToolResult
import cc.unitmesh.llm.compression.TokenInfo

interface CodingAgentRenderer {
    fun renderIterationHeader(current: Int, max: Int)
    fun renderLLMResponseStart()
    fun renderLLMResponseChunk(chunk: String)
    fun renderLLMResponseEnd()

    fun renderToolCall(toolName: String, paramsStr: String)

    /**
     * Render a tool call with parsed parameters.
     * This is the preferred method as it avoids string parsing issues with complex values.
     *
     * @param toolName The name of the tool being called
     * @param params The parsed parameters map
     */
    fun renderToolCallWithParams(toolName: String, params: Map<String, Any>) {
        // Default implementation: convert to string format for backward compatibility
        val paramsStr = params.entries.joinToString(" ") { (key, value) ->
            "$key=\"$value\""
        }
        renderToolCall(toolName, paramsStr)
    }

    fun renderToolResult(
        toolName: String,
        success: Boolean,
        output: String?,
        fullOutput: String?,
        metadata: Map<String, String> = emptyMap()
    )

    /**
     * Render task completion message with execution time and tool usage statistics.
     *
     * @param executionTimeMs Total execution time in milliseconds from task start to completion
     * @param toolsUsedCount Number of tools used during execution
     */
    fun renderTaskComplete(executionTimeMs: Long = 0L, toolsUsedCount: Int = 0)
    fun renderFinalResult(success: Boolean, message: String, iterations: Int)
    fun renderError(message: String)

    /**
     * Render an informational message (non-error, non-warning)
     * Used for status updates, progress information, etc.
     */
    fun renderInfo(message: String) {
        // Default: no-op, renderers can override to display info messages
    }

    fun renderRepeatWarning(toolName: String, count: Int)

    fun renderRecoveryAdvice(recoveryAdvice: String)

    fun updateTokenInfo(tokenInfo: TokenInfo) {}

    /**
     * Handle task-boundary tool call to update task progress display.
     * Called when the agent uses the task-boundary tool to mark task status.
     *
     * This is an optional method primarily used by UI renderers that display
     * task progress visually. Console and server renderers typically don't need
     * to implement this.
     *
     * @param taskName The name of the task
     * @param status The task status (e.g., "WORKING", "DONE", "FAILED")
     * @param summary Optional summary of the task progress
     */
    fun handleTaskBoundary(taskName: String, status: String, summary: String = "") {
        // Default: no-op for renderers that don't display task progress
    }

    /**
     * Render a ChatDB execution step.
     * This is an optional method primarily used by UI renderers (ComposeRenderer, JewelRenderer).
     * Console renderers can ignore this or provide simple text output.
     *
     * @param stepType The type of step being executed
     * @param status The current status of the step
     * @param title The display title for the step (defaults to stepType.displayName)
     * @param details Additional details about the step (e.g., table names, row counts, SQL)
     * @param error Error message if the step failed
     */
    fun renderChatDBStep(
        stepType: ChatDBStepType,
        status: ChatDBStepStatus,
        title: String = stepType.displayName,
        details: Map<String, Any> = emptyMap(),
        error: String? = null
    ) {
        // Default: no-op for renderers that don't support ChatDB steps
    }

    /**
     * Render a compact plan summary bar.
     * Called when plan is created or updated to show progress in a compact format.
     *
     * Example display:
     * ```
     * 📋 Plan: Create Tag System (3/5 steps, 60%) ████████░░░░░░░░
     * ```
     *
     * @param summary The plan summary data containing progress information
     */
    fun renderPlanSummary(summary: PlanSummaryData) {
        // Default: no-op for renderers that don't support plan summary bar
    }

    fun renderUserConfirmationRequest(toolName: String, params: Map<String, Any>)

    /**
     * Request user approval for a SQL write operation.
     * This is called when a write operation (INSERT, UPDATE, DELETE, CREATE, etc.) is detected.
     * The renderer should display the SQL and allow the user to approve or reject.
     *
     * @param sql The SQL statement to be executed
     * @param operationType The type of SQL operation (INSERT, UPDATE, DELETE, CREATE, etc.)
     * @param affectedTables List of tables that will be affected
     * @param isHighRisk Whether this is a high-risk operation (DROP, TRUNCATE)
     * @param dryRunResult Optional result from dry run validation (if available)
     * @param onApprove Callback to invoke when user approves the operation
     * @param onReject Callback to invoke when user rejects the operation
     */
    fun renderSqlApprovalRequest(
        sql: String,
        operationType: SqlOperationType,
        affectedTables: List<String>,
        isHighRisk: Boolean,
        dryRunResult: DryRunResult? = null,
        onApprove: () -> Unit,
        onReject: () -> Unit
    ) {
        // Default: auto-reject for safety (renderers should override to show UI)
        onReject()
    }

    /**
     * Add a live terminal session to the timeline.
     * Called when a Shell tool starts execution with PTY support.
     */
    fun addLiveTerminal(
        sessionId: String,
        command: String,
        workingDirectory: String?,
        ptyHandle: Any?
    ) {
        // Default: no-op for renderers that don't support live terminals
    }

    /**
     * Update the status of a live terminal session.
     * Called when the shell command completes (either success or failure).
     *
     * @param sessionId The session ID of the live terminal
     * @param exitCode The exit code of the command (0 = success)
     * @param executionTimeMs The total execution time in milliseconds
     * @param output The captured output (optional, may be null if output is streamed via PTY)
     * @param cancelledByUser Whether the command was cancelled by the user (exit code 137)
     */
    fun updateLiveTerminalStatus(
        sessionId: String,
        exitCode: Int,
        executionTimeMs: Long,
        output: String? = null,
        cancelledByUser: Boolean = false
    ) {
        // Default: no-op for renderers that don't support live terminals
    }

    /**
     * Render an Agent-generated sketch block (chart, nanodsl, mermaid, etc.)
     * Called when a SubAgent returns content containing special code blocks that
     * should be rendered as interactive UI components.
     *
     * This method is called by the executor when it detects that an AgentResult
     * contains renderable code blocks. UI renderers can override this to display
     * interactive visualizations (charts, diagrams, UI previews).
     *
     * @param agentName The name of the agent that generated the content (e.g., "chart-agent", "nanodsl-agent")
     * @param language The language identifier of the code block (e.g., "chart", "nanodsl", "mermaid")
     * @param code The code content to render
     * @param metadata Additional metadata from the agent result
     */
    fun renderAgentSketchBlock(
        agentName: String,
        language: String,
        code: String,
        metadata: Map<String, String> = emptyMap()
    ) {
        // Default: no-op for renderers that don't support sketch blocks
        // UI renderers (ComposeRenderer, JewelRenderer) should override this
        // to render interactive components
    }

    /**
     * Await the result of an async session.
     * Used when the Agent needs to wait for a shell command to complete before proceeding.
     *
     * @param sessionId The session ID to wait for
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return The final ToolResult (Success or Error)
     */
    suspend fun awaitSessionResult(sessionId: String, timeoutMs: Long): ToolResult {
        // Default: return error for renderers that don't support async sessions
        return ToolResult.Error("Async session not supported by this renderer")
    }
}
