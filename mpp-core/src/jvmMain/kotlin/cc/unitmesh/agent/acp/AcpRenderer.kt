package cc.unitmesh.agent.acp

import cc.unitmesh.agent.render.BaseRenderer
import cc.unitmesh.agent.tool.ToolResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger("AcpRenderer")

/**
 * CodingAgentRenderer implementation that forwards all render events as ACP session updates.
 *
 * This is a JVM-only adapter, used by ACP agent server implementations to stream
 * updates back to an ACP client.
 *
 * @param emitter The AcpUpdateEmitter to send events to
 * @param scope CoroutineScope for launching coroutines (needed because renderer methods are not suspend)
 */
class AcpRenderer(
    private val emitter: AcpUpdateEmitter,
    private val scope: CoroutineScope,
) : BaseRenderer() {
    private val responseBuffer = StringBuilder()
    private var currentToolCallId = 0

    private fun launchEmit(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    override fun renderIterationHeader(current: Int, max: Int) {
        launchEmit {
            emitter.emitThoughtChunk("Iteration $current/$max")
        }
    }

    override fun renderLLMResponseStart() {
        super.renderLLMResponseStart()
        responseBuffer.clear()
    }

    override fun renderLLMResponseChunk(chunk: String) {
        val filtered = filterDevinBlocks(chunk)
        if (filtered.isNotEmpty()) {
            responseBuffer.append(filtered)
            launchEmit { emitter.emitTextChunk(filtered) }
        }
    }

    override fun renderLLMResponseEnd() {
        super.renderLLMResponseEnd()
    }

    override fun renderThinkingChunk(chunk: String, isStart: Boolean, isEnd: Boolean) {
        if (chunk.isNotEmpty()) {
            launchEmit { emitter.emitThoughtChunk(chunk) }
        }
    }

    override fun renderToolCall(toolName: String, paramsStr: String) {
        val id = "tc-${++currentToolCallId}"
        launchEmit {
            emitter.emitToolCall(
                toolCallId = id,
                title = toolName,
                status = com.agentclientprotocol.model.ToolCallStatus.IN_PROGRESS,
                input = paramsStr
            )
        }
    }

    override fun renderToolCallWithParams(toolName: String, params: Map<String, Any>) {
        val id = "tc-${++currentToolCallId}"
        val paramsStr = params.entries.joinToString(", ") { "${it.key}=${it.value}" }
        launchEmit {
            emitter.emitToolCall(
                toolCallId = id,
                title = toolName,
                status = com.agentclientprotocol.model.ToolCallStatus.IN_PROGRESS,
                input = paramsStr
            )
        }
    }

    override fun renderToolResult(
        toolName: String,
        success: Boolean,
        output: String?,
        fullOutput: String?,
        metadata: Map<String, String>,
    ) {
        val status = if (success) {
            com.agentclientprotocol.model.ToolCallStatus.COMPLETED
        } else {
            com.agentclientprotocol.model.ToolCallStatus.FAILED
        }

        launchEmit {
            emitter.emitToolCall(
                toolCallId = "tc-$currentToolCallId",
                title = toolName,
                status = status,
                output = output ?: fullOutput
            )
        }
    }

    override fun renderTaskComplete(executionTimeMs: Long, toolsUsedCount: Int) {
        launchEmit {
            emitter.emitTextChunk("\nTask completed in ${executionTimeMs}ms using $toolsUsedCount tools.")
        }
    }

    override fun renderFinalResult(success: Boolean, message: String, iterations: Int) {
        logger.info { "AcpRenderer final result: success=$success, message=$message" }
    }

    override fun renderError(message: String) {
        launchEmit {
            emitter.emitTextChunk("\nError: $message")
        }
    }

    override fun renderRepeatWarning(toolName: String, count: Int) {
        launchEmit {
            emitter.emitThoughtChunk("Warning: Tool '$toolName' has been called $count times consecutively")
        }
    }

    override fun renderRecoveryAdvice(recoveryAdvice: String) {
        launchEmit {
            emitter.emitTextChunk("\nRecovery advice: $recoveryAdvice")
        }
    }

    override fun renderUserConfirmationRequest(toolName: String, params: Map<String, Any>) {
        logger.info { "AcpRenderer: auto-approving confirmation for $toolName" }
    }

    override fun renderInfo(message: String) {
        launchEmit { emitter.emitTextChunk(message) }
    }

    override suspend fun awaitSessionResult(sessionId: String, timeoutMs: Long): ToolResult {
        return ToolResult.Error("Async session not supported in ACP renderer")
    }
}

