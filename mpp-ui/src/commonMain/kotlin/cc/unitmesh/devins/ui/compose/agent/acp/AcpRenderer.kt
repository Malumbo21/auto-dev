package cc.unitmesh.devins.ui.compose.agent.acp

import androidx.compose.runtime.*
import cc.unitmesh.agent.render.BaseRenderer
import cc.unitmesh.agent.render.TimelineItem
import cc.unitmesh.agent.render.ToolCallInfo
import cc.unitmesh.agent.tool.ToolType
import cc.unitmesh.llm.compression.TokenInfo
import kotlinx.datetime.Clock

/**
 * ACP-optimized renderer: aggregates streaming writes, batches reads aggressively.
 * 
 * Key differences from ComposeRenderer:
 * - WriteFile to same path: merge into single updating item (not 2000+ items)
 * - ReadFile batch threshold: 3 (vs 5) 
 * - Filter noisy IN_PROGRESS events with empty input
 */
class AcpRenderer : BaseRenderer() {
    private val _timeline = mutableStateListOf<TimelineItem>()
    val timeline: List<TimelineItem> = _timeline
    
    // Aggressive batching for ACP
    private val READ_BATCH_THRESHOLD = 3
    private val WRITE_MERGE_ENABLED = true
    
    // Track write streams (file → timeline index)
    private val activeWrites = mutableMapOf<String, Int>()
    private data class WriteProgress(var path: String, var chunks: Int, var lastUpdate: Long)
    
    // Track read batches
    private data class ReadBatch(var count: Int, var firstIndex: Int, val files: MutableList<String>)
    private var currentReadBatch: ReadBatch? = null
    
    private var _currentStreamingOutput by mutableStateOf("")
    val currentStreamingOutput: String get() = _currentStreamingOutput
    
    private var _isProcessing by mutableStateOf(false)
    val isProcessing: Boolean get() = _isProcessing
    
    private var _errorMessage by mutableStateOf<String?>(null)
    val errorMessage: String? get() = _errorMessage
    
    override fun renderLLMResponseStart() {
        super.renderLLMResponseStart()
        _currentStreamingOutput = ""
        _isProcessing = true
        _errorMessage = null
        
        // Reset batch/merge state
        currentReadBatch = null
        activeWrites.clear()
    }
    
    override fun renderLLMResponseChunk(chunk: String) {
        reasoningBuffer.append(chunk)
        val filtered = filterDevinBlocks(chunk)
        _currentStreamingOutput += filtered
    }
    
    override fun renderLLMResponseEnd() {
        super.renderLLMResponseEnd()
        _isProcessing = false
        
        // Finalize any pending batches
        currentReadBatch = null
        activeWrites.clear()
    }
    
    override fun renderToolCall(toolName: String, paramsStr: String) {
        // Filter truly noisy events (no path/file info)
        val isNoisy = paramsStr.contains("status=\"IN_PROGRESS\"") && 
                      paramsStr.contains("input=\"\"") &&
                      !paramsStr.contains("output=") &&
                      !paramsStr.contains("path=")
        
        if (isNoisy) {
            return
        }
        
        when (toolName) {
            "ReadFile", "read-file" -> handleReadFile(paramsStr)
            "WriteFile", "write-file" -> handleWriteFile(paramsStr)
            else -> handleGenericTool(toolName, paramsStr)
        }
    }
    
    private fun handleReadFile(params: String) {
        val fileMatch = Regex("""(?:path|input)="([^"]+)"""").find(params)
        val filePath = fileMatch?.groups?.get(1)?.value ?: "unknown"
        
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
        
        // Add individual item (may be collapsed later)
        _timeline.add(TimelineItem.ToolCallItem(
            toolName = "read-file",
            description = "Reading file",
            params = filePath,
            fullParams = params,
            filePath = filePath,
            toolType = ToolType.ReadFile,
            success = null,
            summary = null,
            output = null,
            fullOutput = null,
            executionTimeMs = null
        ))
    }
    
    private fun handleWriteFile(params: String) {
        if (!WRITE_MERGE_ENABLED) {
            handleGenericTool("write-file", params)
            return
        }
        
        val fileMatch = Regex("""(?:path|output)="([^"]+)"""").find(params)
        val filePath = fileMatch?.groups?.get(1)?.value ?: "unknown"
        
        val existingIndex = activeWrites[filePath]
        if (existingIndex != null && existingIndex < _timeline.size) {
            // Update existing write item
            val existing = _timeline[existingIndex] as? TimelineItem.ToolCallItem
            if (existing != null) {
                _timeline[existingIndex] = existing.copy(
                    description = "Writing ${filePath.substringAfterLast('/')} (streaming...)",
                    params = "Updated ${Clock.System.now()}"
                )
            }
        } else {
            // New write stream
            val newIndex = _timeline.size
            activeWrites[filePath] = newIndex
            
            _timeline.add(TimelineItem.ToolCallItem(
                toolName = "write-file",
                description = "Writing ${filePath.substringAfterLast('/')}",
                params = filePath,
                fullParams = params,
                filePath = filePath,
                toolType = ToolType.WriteFile,
                success = null,
                summary = null,
                output = null,
                fullOutput = null,
                executionTimeMs = null
            ))
        }
    }
    
    private fun handleGenericTool(toolName: String, params: String) {
        _timeline.add(TimelineItem.ToolCallItem(
            toolName = toolName,
            description = toolName,
            params = params.take(100),
            fullParams = params,
            filePath = null,
            toolType = null,
            success = null,
            summary = null,
            output = null,
            fullOutput = null,
            executionTimeMs = null
        ))
    }
    
    private fun createReadBatchItem(batch: ReadBatch): TimelineItem.ToolCallItem {
        val summary = batch.files.take(3).map { it.substringAfterLast('/') }.joinToString(", ")
        val detail = if (batch.files.size > 3) "$summary + ${batch.files.size - 3} more" else summary
        
        return TimelineItem.ToolCallItem(
            toolName = "batch:read-file",
            description = "📦 Read ${batch.count} files",
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
        metadata: Map<String, String>
    ) {
        // ACP often doesn't send results for streaming writes
        // Find last tool call and mark as complete
        val lastIndex = _timeline.indexOfLast { 
            it is TimelineItem.ToolCallItem && it.success == null 
        }
        
        if (lastIndex >= 0) {
            val item = _timeline[lastIndex] as TimelineItem.ToolCallItem
            _timeline[lastIndex] = item.copy(
                success = success,
                summary = if (success) "Completed" else "Failed",
                output = output?.take(200)
            )
        }
    }
    
    override fun renderTaskComplete(executionTimeMs: Long, toolsUsedCount: Int) {
        _isProcessing = false
    }
    
    override fun renderFinalResult(success: Boolean, message: String, iterations: Int) {
        _isProcessing = false
    }
    
    override fun renderError(message: String) {
        _errorMessage = message
        _timeline.add(TimelineItem.MessageItem(
            message = cc.unitmesh.devins.llm.Message(
                role = cc.unitmesh.devins.llm.MessageRole.ASSISTANT,
                content = "❌ Error: $message"
            )
        ))
    }
    
    override fun renderIterationHeader(current: Int, max: Int) {}
    override fun renderRepeatWarning(toolName: String, count: Int) {}
    override fun renderRecoveryAdvice(recoveryAdvice: String) {}
    override fun renderUserConfirmationRequest(toolName: String, params: Map<String, Any>) {}
    override fun updateTokenInfo(tokenInfo: TokenInfo) {}
    
    fun clearMessages() {
        _timeline.clear()
        _currentStreamingOutput = ""
        _errorMessage = null
        _isProcessing = false
        currentReadBatch = null
        activeWrites.clear()
    }
}
