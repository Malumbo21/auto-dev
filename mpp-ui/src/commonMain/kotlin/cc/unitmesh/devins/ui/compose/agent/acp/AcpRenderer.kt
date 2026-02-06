package cc.unitmesh.devins.ui.compose.agent.acp

import androidx.compose.runtime.*
import cc.unitmesh.agent.render.BaseRenderer
import cc.unitmesh.agent.render.TimelineItem
import cc.unitmesh.agent.tool.ToolType
import cc.unitmesh.llm.compression.TokenInfo
import kotlinx.datetime.Clock

/**
 * ACP-optimized renderer: aggregates streaming writes, batches reads aggressively.
 *
 * Key design decisions for ACP streaming:
 * - Tool call deduplication is done upstream by [AcpClient.renderSessionUpdate]:
 *   only COMPLETED/FAILED tool calls arrive here, so no need to filter IN_PROGRESS.
 * - [renderToolCallWithParams] is the primary entry point (AcpClient calls this, not renderToolCall).
 * - Streaming text chunks are buffered in [_streamingBuffer] and flushed periodically
 *   to reduce Compose recomposition frequency.
 * - WriteFile to same path: merge into single updating item (not 2000+ items).
 * - ReadFile batch threshold: 3 consecutive reads are collapsed into a batch item.
 */
class AcpRenderer : BaseRenderer() {
    private val _timeline = mutableStateListOf<TimelineItem>()
    val timeline: List<TimelineItem> = _timeline

    // Aggressive batching for ACP
    private val READ_BATCH_THRESHOLD = 3
    private val WRITE_MERGE_ENABLED = true

    // Track write streams (file path -> timeline index)
    private val activeWrites = mutableMapOf<String, Int>()

    // Track read batches
    private data class ReadBatch(var count: Int, var firstIndex: Int, val files: MutableList<String>)
    private var currentReadBatch: ReadBatch? = null

    // Streaming text buffer - reduces Compose recomposition frequency
    private val _streamingBuffer = StringBuilder()
    private var _currentStreamingOutput by mutableStateOf("")
    val currentStreamingOutput: String get() = _currentStreamingOutput

    // Streaming thinking content
    private var _currentThinkingOutput by mutableStateOf("")
    val currentThinkingOutput: String get() = _currentThinkingOutput
    private val _thinkingStreamBuffer = StringBuilder()

    private var _isProcessing by mutableStateOf(false)
    val isProcessing: Boolean get() = _isProcessing

    private var _errorMessage by mutableStateOf<String?>(null)
    val errorMessage: String? get() = _errorMessage

    // Track tool call IDs for matching results
    private val pendingToolCallIds = mutableMapOf<String, Int>()

    override fun renderLLMResponseStart() {
        super.renderLLMResponseStart()
        _streamingBuffer.clear()
        _currentStreamingOutput = ""
        _currentThinkingOutput = ""
        _thinkingStreamBuffer.clear()
        _isProcessing = true
        _errorMessage = null

        // Reset batch/merge state
        currentReadBatch = null
        activeWrites.clear()
        pendingToolCallIds.clear()
    }

    override fun renderLLMResponseChunk(chunk: String) {
        reasoningBuffer.append(chunk)

        // Extract thinking content if present
        val extraction = extractThinkingContent(chunk)
        if (extraction.hasThinking) {
            _thinkingStreamBuffer.append(extraction.thinkingContent)
            _currentThinkingOutput = _thinkingStreamBuffer.toString()
        }

        val filtered = filterDevinBlocks(extraction.contentWithoutThinking)
        if (filtered.isNotEmpty()) {
            _streamingBuffer.append(filtered)
            // Flush to Compose state - batch small chunks together
            // Flush every 50 chars or on newlines to balance responsiveness and performance
            if (_streamingBuffer.length - _currentStreamingOutput.length >= 50 ||
                filtered.contains('\n')
            ) {
                _currentStreamingOutput = _streamingBuffer.toString()
            }
        }
    }

    override fun renderLLMResponseEnd() {
        super.renderLLMResponseEnd()
        _isProcessing = false

        // Final flush of any remaining buffered content
        _currentStreamingOutput = _streamingBuffer.toString()

        // Finalize any pending batches
        currentReadBatch = null
        activeWrites.clear()
    }

    override fun renderThinkingChunk(chunk: String, isStart: Boolean, isEnd: Boolean) {
        if (isEnd) {
            // Thinking block ended - content is preserved in _currentThinkingOutput
            return
        }
        if (chunk.isNotEmpty()) {
            _thinkingStreamBuffer.append(chunk)
            _currentThinkingOutput = _thinkingStreamBuffer.toString()
        }
    }

    /**
     * Primary entry point for ACP tool calls.
     * AcpClient.renderSessionUpdate() calls this with structured params (kind, status, input).
     * Only COMPLETED/FAILED tool calls arrive here (deduplication is done upstream).
     */
    override fun renderToolCallWithParams(toolName: String, params: Map<String, Any>) {
        val kind = params["kind"]?.toString() ?: ""
        val status = params["status"]?.toString() ?: ""
        val input = params["input"]?.toString() ?: ""

        // Classify the tool call by analyzing the title from ACP
        // ACP titles look like: "ReadFile: build.gradle.kts", "WriteFile: src/main.kt", "Shell: ls -la"
        val normalizedName = toolName.substringBefore(":").trim().lowercase()
        val filePath = toolName.substringAfter(":", "").trim().takeIf { it.isNotBlank() }

        // Reset read batch when a non-read tool call arrives
        if (normalizedName != "readfile" && normalizedName != "read-file" && normalizedName != "read_file") {
            currentReadBatch = null
        }

        when (normalizedName) {
            "readfile", "read-file", "read_file" -> handleReadFile(toolName, filePath ?: input, status)
            "writefile", "write-file", "write_file" -> handleWriteFile(toolName, filePath ?: input, status)
            else -> handleGenericTool(toolName, kind, status, input)
        }
    }

    override fun renderToolCall(toolName: String, paramsStr: String) {
        // Fallback path for non-ACP renderers. Route to renderToolCallWithParams.
        renderToolCallWithParams(toolName, mapOf("input" to paramsStr))
    }

    private fun handleReadFile(title: String, filePath: String, status: String) {
        val batch = currentReadBatch
        if (batch != null) {
            batch.count++
            batch.files.add(filePath)

            if (batch.count >= READ_BATCH_THRESHOLD) {
                // Collapse previous items
                if (batch.firstIndex < _timeline.size) {
                    val toRemove = _timeline.subList(batch.firstIndex, _timeline.size).toList()
                    _timeline.removeAll(toRemove)
                }

                _timeline.add(createReadBatchItem(batch))
                return
            }
        } else {
            currentReadBatch = ReadBatch(1, _timeline.size, mutableListOf(filePath))
        }

        val isSuccess = status == "COMPLETED"
        _timeline.add(
            TimelineItem.ToolCallItem(
                toolName = title,
                description = "Read ${filePath.substringAfterLast('/')}",
                params = filePath,
                fullParams = filePath,
                filePath = filePath,
                toolType = ToolType.ReadFile,
                success = if (status == "COMPLETED" || status == "FAILED") isSuccess else null,
                summary = if (isSuccess) "Done" else if (status == "FAILED") "Failed" else null,
                output = null,
                fullOutput = null,
                executionTimeMs = null
            )
        )
    }

    private fun handleWriteFile(title: String, filePath: String, status: String) {
        if (!WRITE_MERGE_ENABLED) {
            handleGenericTool(title, "edit", status, filePath)
            return
        }

        val existingIndex = activeWrites[filePath]
        val isSuccess = status == "COMPLETED"

        if (existingIndex != null && existingIndex < _timeline.size) {
            // Update existing write item
            val existing = _timeline[existingIndex] as? TimelineItem.ToolCallItem
            if (existing != null) {
                _timeline[existingIndex] = existing.copy(
                    description = "Write ${filePath.substringAfterLast('/')}",
                    success = if (status == "COMPLETED" || status == "FAILED") isSuccess else null,
                    summary = if (isSuccess) "Done" else if (status == "FAILED") "Failed" else null,
                )
            }
        } else {
            // New write
            val newIndex = _timeline.size
            activeWrites[filePath] = newIndex

            _timeline.add(
                TimelineItem.ToolCallItem(
                    toolName = title,
                    description = "Write ${filePath.substringAfterLast('/')}",
                    params = filePath,
                    fullParams = filePath,
                    filePath = filePath,
                    toolType = ToolType.WriteFile,
                    success = if (status == "COMPLETED" || status == "FAILED") isSuccess else null,
                    summary = if (isSuccess) "Done" else if (status == "FAILED") "Failed" else null,
                    output = null,
                    fullOutput = null,
                    executionTimeMs = null
                )
            )
        }
    }

    private fun handleGenericTool(toolName: String, kind: String, status: String, input: String) {
        val isSuccess = status == "COMPLETED"
        _timeline.add(
            TimelineItem.ToolCallItem(
                toolName = toolName,
                description = toolName,
                params = input.take(100),
                fullParams = input,
                filePath = null,
                toolType = null,
                success = if (status == "COMPLETED" || status == "FAILED") isSuccess else null,
                summary = if (isSuccess) "Done" else if (status == "FAILED") "Failed" else null,
                output = null,
                fullOutput = null,
                executionTimeMs = null
            )
        )
    }

    private fun createReadBatchItem(batch: ReadBatch): TimelineItem.ToolCallItem {
        val summary = batch.files.take(3).map { it.substringAfterLast('/') }.joinToString(", ")
        val detail = if (batch.files.size > 3) "$summary + ${batch.files.size - 3} more" else summary

        return TimelineItem.ToolCallItem(
            toolName = "batch:read-file",
            description = "Read ${batch.count} files",
            params = detail,
            fullParams = batch.files.joinToString("\n"),
            filePath = null,
            toolType = ToolType.ReadFile,
            success = true,
            summary = "Completed",
            output = null,
            fullOutput = null,
            executionTimeMs = null
        )
    }

    override fun renderToolResult(
        toolName: String,
        success: Boolean,
        output: String?,
        fullOutput: String?,
        metadata: Map<String, String>,
    ) {
        // Find the matching tool call by name (last unresolved match)
        val lastIndex = _timeline.indexOfLast {
            it is TimelineItem.ToolCallItem && it.success == null
        }

        if (lastIndex >= 0) {
            val item = _timeline[lastIndex] as TimelineItem.ToolCallItem
            _timeline[lastIndex] = item.copy(
                success = success,
                summary = if (success) "Done" else "Failed",
                output = output?.take(200),
                fullOutput = fullOutput
            )
        }
    }

    override fun renderTaskComplete(executionTimeMs: Long, toolsUsedCount: Int) {
        _isProcessing = false
    }

    override fun renderFinalResult(success: Boolean, message: String, iterations: Int) {
        _isProcessing = false
        // Final flush
        _currentStreamingOutput = _streamingBuffer.toString()
    }

    override fun renderError(message: String) {
        _errorMessage = message
        _timeline.add(
            TimelineItem.MessageItem(
                message = cc.unitmesh.devins.llm.Message(
                    role = cc.unitmesh.devins.llm.MessageRole.ASSISTANT,
                    content = "Error: $message"
                )
            )
        )
    }

    override fun renderInfo(message: String) {
        _timeline.add(
            TimelineItem.MessageItem(
                message = cc.unitmesh.devins.llm.Message(
                    role = cc.unitmesh.devins.llm.MessageRole.ASSISTANT,
                    content = message
                )
            )
        )
    }

    override fun renderIterationHeader(current: Int, max: Int) {}
    override fun renderRepeatWarning(toolName: String, count: Int) {}
    override fun renderRecoveryAdvice(recoveryAdvice: String) {}
    override fun renderUserConfirmationRequest(toolName: String, params: Map<String, Any>) {}
    override fun updateTokenInfo(tokenInfo: TokenInfo) {}

    fun clearMessages() {
        _timeline.clear()
        _streamingBuffer.clear()
        _currentStreamingOutput = ""
        _currentThinkingOutput = ""
        _thinkingStreamBuffer.clear()
        _errorMessage = null
        _isProcessing = false
        currentReadBatch = null
        activeWrites.clear()
        pendingToolCallIds.clear()
    }
}
