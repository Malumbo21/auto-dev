package cc.unitmesh.agent

import cc.unitmesh.agent.database.DryRunResult
import cc.unitmesh.agent.plan.PlanSummaryData
import cc.unitmesh.agent.plan.StepSummary
import cc.unitmesh.agent.plan.TaskSummary
import cc.unitmesh.agent.render.CodingAgentRenderer
import cc.unitmesh.agent.subagent.SqlOperationType
import kotlin.js.JsExport

/**
 * JS-friendly step summary data
 */
@JsExport
data class JsStepSummary(
    val id: String,
    val description: String,
    val status: String
) {
    companion object {
        fun from(step: StepSummary): JsStepSummary {
            return JsStepSummary(
                id = step.id,
                description = step.description,
                status = step.status.name
            )
        }
    }
}

/**
 * JS-friendly task summary data
 */
@JsExport
data class JsTaskSummary(
    val id: String,
    val title: String,
    val status: String,
    val completedSteps: Int,
    val totalSteps: Int,
    val steps: Array<JsStepSummary>
) {
    companion object {
        fun from(task: TaskSummary): JsTaskSummary {
            return JsTaskSummary(
                id = task.id,
                title = task.title,
                status = task.status.name,
                completedSteps = task.completedSteps,
                totalSteps = task.totalSteps,
                steps = task.steps.map { JsStepSummary.from(it) }.toTypedArray()
            )
        }
    }
}

/**
 * JS-friendly plan summary data
 */
@JsExport
data class JsPlanSummaryData(
    val planId: String,
    val title: String,
    val totalSteps: Int,
    val completedSteps: Int,
    val failedSteps: Int,
    val progressPercent: Int,
    val status: String,
    val currentStepDescription: String?,
    val tasks: Array<JsTaskSummary>
) {
    companion object {
        fun from(summary: PlanSummaryData): JsPlanSummaryData {
            return JsPlanSummaryData(
                planId = summary.planId,
                title = summary.title,
                totalSteps = summary.totalSteps,
                completedSteps = summary.completedSteps,
                failedSteps = summary.failedSteps,
                progressPercent = summary.progressPercent,
                status = summary.status.name,
                currentStepDescription = summary.currentStepDescription,
                tasks = summary.tasks.map { JsTaskSummary.from(it) }.toTypedArray()
            )
        }
    }
}

/**
 * JS-friendly renderer interface
 * Allows TypeScript to provide custom rendering implementations
 * This interface mirrors the Kotlin CodingAgentRenderer interface
 */
@JsExport
interface JsCodingAgentRenderer {
    // Lifecycle methods
    fun renderIterationHeader(current: Int, max: Int)
    fun renderLLMResponseStart()
    fun renderLLMResponseChunk(chunk: String)
    fun renderLLMResponseEnd()

    // Tool execution methods
    fun renderToolCall(toolName: String, paramsStr: String)
    fun renderToolResult(toolName: String, success: Boolean, output: String?, fullOutput: String?)

    // Status and completion methods
    fun renderTaskComplete(executionTimeMs: Double = 0.0, toolsUsedCount: Int = 0)
    fun renderFinalResult(success: Boolean, message: String, iterations: Int)
    fun renderError(message: String)
    fun renderRepeatWarning(toolName: String, count: Int)

    // Error recovery methods
    fun renderRecoveryAdvice(recoveryAdvice: String)

    // Plan summary bar (optional - default no-op in BaseRenderer)
    fun renderPlanSummary(summary: JsPlanSummaryData) {}

    /**
     * Render an Agent-generated sketch block (chart, nanodsl, mermaid, etc.)
     * Called when a SubAgent returns content containing special code blocks.
     *
     * @param agentName The name of the agent that generated the content
     * @param language The language identifier of the code block
     * @param code The code content to render
     * @param metadata Additional metadata as JSON object
     */
    fun renderAgentSketchBlock(
        agentName: String,
        language: String,
        code: String,
        metadata: dynamic
    ) {}
}

/**
 * Renderer factory for creating different types of renderers
 */
@JsExport
object RendererFactory {
    /**
     * Create a renderer adapter from JS implementation
     */
    fun createRenderer(jsRenderer: JsCodingAgentRenderer): CodingAgentRenderer {
        return JsRendererAdapter(jsRenderer)
    }
}

/**
 * Adapter to convert JS renderer to Kotlin renderer
 */
class JsRendererAdapter(private val jsRenderer: JsCodingAgentRenderer) : CodingAgentRenderer {
    override fun renderIterationHeader(current: Int, max: Int) {
        jsRenderer.renderIterationHeader(current, max)
    }

    override fun renderLLMResponseStart() {
        jsRenderer.renderLLMResponseStart()
    }

    override fun renderLLMResponseChunk(chunk: String) {
        jsRenderer.renderLLMResponseChunk(chunk)
    }

    override fun renderLLMResponseEnd() {
        jsRenderer.renderLLMResponseEnd()
    }

    override fun renderToolCall(toolName: String, paramsStr: String) {
        jsRenderer.renderToolCall(toolName, paramsStr)
    }

    override fun renderToolResult(toolName: String, success: Boolean, output: String?, fullOutput: String?, metadata: Map<String, String>) {
        jsRenderer.renderToolResult(toolName, success, output, fullOutput)
    }

    override fun renderTaskComplete(executionTimeMs: Long, toolsUsedCount: Int) {
        jsRenderer.renderTaskComplete(executionTimeMs.toDouble(), toolsUsedCount)
    }

    override fun renderFinalResult(success: Boolean, message: String, iterations: Int) {
        jsRenderer.renderFinalResult(success, message, iterations)
    }

    override fun renderError(message: String) {
        jsRenderer.renderError(message)
    }

    override fun renderRepeatWarning(toolName: String, count: Int) {
        jsRenderer.renderRepeatWarning(toolName, count)
    }

    override fun renderRecoveryAdvice(recoveryAdvice: String) {
        jsRenderer.renderRecoveryAdvice(recoveryAdvice)
    }

    override fun renderUserConfirmationRequest(toolName: String, params: Map<String, Any>) {
        // For now, just use error rendering since JS renderer doesn't have this method yet
        jsRenderer.renderError("Tool '$toolName' requires user confirmation: $params (Auto-approved)")
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
        // JS renderer auto-rejects for safety
        val dryRunInfo = if (dryRunResult != null) " (dry run: ${if (dryRunResult.isValid) "passed" else "failed"})" else ""
        jsRenderer.renderError("SQL write operation requires approval: ${operationType.name} on ${affectedTables.joinToString(", ")}$dryRunInfo (Auto-rejected)")
        onReject()
    }

    override fun renderPlanSummary(summary: PlanSummaryData) {
        jsRenderer.renderPlanSummary(JsPlanSummaryData.from(summary))
    }

    override fun renderAgentSketchBlock(
        agentName: String,
        language: String,
        code: String,
        metadata: Map<String, String>
    ) {
        // Convert Kotlin Map to JS object
        val jsMetadata = js("{}")
        metadata.forEach { (key, value) ->
            jsMetadata[key] = value
        }
        jsRenderer.renderAgentSketchBlock(agentName, language, code, jsMetadata)
    }
}

