package cc.unitmesh.devins.ui.compose.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.agent.render.TimelineItem
import cc.unitmesh.devins.llm.Message
import cc.unitmesh.devins.llm.MessageRole
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons
import cc.unitmesh.devins.ui.compose.sketch.SketchRenderer
import cc.unitmesh.devins.ui.compose.sketch.getUtf8FontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AgentMessageList(
    renderer: ComposeRenderer,
    modifier: Modifier = Modifier,
    onOpenFileViewer: ((String) -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Create a stable snapshot of the timeline to prevent IndexOutOfBoundsException
    // when the list is modified during composition
    val timelineSnapshot = remember(renderer.timeline.size) {
        renderer.timeline.toList()
    }

    // Track if user manually scrolled away from bottom
    var userScrolledAway by remember { mutableStateOf(false) }

    // Track content updates from SketchRenderer for streaming content
    var streamingBlockCount by remember { mutableIntStateOf(0) }

    // Function to scroll to bottom
    fun scrollToBottomIfNeeded() {
        if (!userScrolledAway) {
            coroutineScope.launch {
                // Delay to ensure layout is complete before scrolling
                delay(50)
                val lastIndex = maxOf(0, listState.layoutInfo.totalItemsCount - 1)
                listState.scrollToItem(lastIndex)
            }
        }
    }

    // Monitor scroll state to detect user scrolling away
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            // If user scrolled to a position not near the bottom, they want to view history
            userScrolledAway = lastVisibleIndex < totalItems - 2
        }
    }

    // Scroll when timeline changes (new messages, tool calls, etc.)
    LaunchedEffect(timelineSnapshot.size) {
        if (timelineSnapshot.isNotEmpty()) {
            userScrolledAway = false
            coroutineScope.launch {
                delay(50)
                listState.animateScrollToItem(
                    index = maxOf(0, listState.layoutInfo.totalItemsCount - 1)
                )
            }
        }
    }

    // Scroll when streaming content changes
    LaunchedEffect(renderer.currentStreamingOutput) {
        if (renderer.currentStreamingOutput.isNotEmpty()) {
            // Calculate content signature based on line count and character chunks
            val lineCount = renderer.currentStreamingOutput.count { it == '\n' }
            val chunkIndex = renderer.currentStreamingOutput.length / 100
            val contentSignature = lineCount + chunkIndex

            // Delay to ensure Markdown layout is complete
            delay(100)
            scrollToBottomIfNeeded()
        }
    }

    // Scroll when SketchRenderer reports new blocks rendered
    LaunchedEffect(streamingBlockCount) {
        if (streamingBlockCount > 0) {
            delay(50)
            scrollToBottomIfNeeded()
        }
    }

    // Reset user scroll state when streaming starts
    LaunchedEffect(renderer.isProcessing) {
        if (renderer.isProcessing) {
            userScrolledAway = false
        }
    }

    LazyColumn(
        state = listState,
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(
            items = timelineSnapshot,
            key = { "${it.timestamp}_${it.hashCode()}" }
        ) { timelineItem ->
            RenderMessageItem(
                timelineItem = timelineItem,
                onOpenFileViewer = onOpenFileViewer,
                renderer = renderer,
                onExpand = {
                    coroutineScope.launch {
                        // Scroll to the bottom when an item expands
                        delay(50)
                        val totalItems = listState.layoutInfo.totalItemsCount
                        if (totalItems > 0) {
                            listState.animateScrollToItem(totalItems - 1)
                        }
                    }
                }
            )
        }

        if (renderer.currentStreamingOutput.isNotEmpty()) {
            item(key = "streaming") {
                StreamingMessageItem(
                    content = renderer.currentStreamingOutput,
                    onContentUpdate = { blockCount ->
                        // When SketchRenderer renders new blocks, trigger scroll
                        streamingBlockCount = blockCount
                    }
                )
            }
        }

        renderer.currentToolCall?.let { toolCall ->
            item(key = "current_tool_call") {
                CurrentToolCallItem(toolCall = toolCall)
            }
        }
    }
}

@Composable
fun RenderMessageItem(
    timelineItem: TimelineItem,
    onOpenFileViewer: ((String) -> Unit)?,
    renderer: ComposeRenderer,
    onExpand: () -> Unit = {}
) {
    when (timelineItem) {
        is TimelineItem.MessageItem -> {
            val msg = timelineItem.message ?: Message(
                role = timelineItem.role,
                content = timelineItem.content,
                timestamp = timelineItem.timestamp
            )
            MessageItem(
                message = msg,
                tokenInfo = timelineItem.tokenInfo
            )
        }

        is TimelineItem.ToolCallItem -> {
            ToolItem(
                toolName = timelineItem.toolName,
                details = timelineItem.params,
                fullParams = timelineItem.fullParams,
                filePath = timelineItem.filePath,
                toolType = timelineItem.toolType,
                success = timelineItem.success,
                summary = timelineItem.summary,
                output = timelineItem.output,
                fullOutput = timelineItem.fullOutput,
                executionTimeMs = timelineItem.executionTimeMs,
                docqlStats = timelineItem.docqlStats,
                onOpenFileViewer = onOpenFileViewer,
                onExpand = onExpand
            )
        }

        is TimelineItem.ErrorItem -> {
            ToolErrorItem(error = timelineItem.message, onDismiss = { renderer.clearError() })
        }

        is TimelineItem.TaskCompleteItem -> {
            TaskCompletedItem(
                success = timelineItem.success,
                message = timelineItem.message
            )
        }

        is TimelineItem.TerminalOutputItem -> {
            TerminalOutputItem(
                command = timelineItem.command,
                output = timelineItem.output,
                exitCode = timelineItem.exitCode,
                executionTimeMs = timelineItem.executionTimeMs,
                onExpand = onExpand
            )
        }

        is TimelineItem.LiveTerminalItem -> {
            LiveTerminalItem(
                sessionId = timelineItem.sessionId,
                command = timelineItem.command,
                workingDirectory = timelineItem.workingDirectory,
                ptyHandle = timelineItem.ptyHandle,
                exitCode = timelineItem.exitCode,
                executionTimeMs = timelineItem.executionTimeMs,
                output = timelineItem.output
            )
        }

        is TimelineItem.AgentSketchBlockItem -> {
            AgentSketchBlockItem(
                agentName = timelineItem.agentName,
                language = timelineItem.language,
                code = timelineItem.code,
                metadata = timelineItem.metadata
            )
        }

        is TimelineItem.ChatDBStepItem -> {
            cc.unitmesh.devins.ui.compose.agent.chatdb.components.ChatDBStepCard(
                step = timelineItem,
                onApprove = { renderer.approveSqlOperation() },
                onReject = { renderer.rejectSqlOperation() }
            )
        }

        is TimelineItem.InfoItem -> {
            InfoMessageItem(message = timelineItem.message)
        }

        is TimelineItem.MultimodalAnalysisItem -> {
            MultimodalAnalysisItemView(
                item = timelineItem,
                onExpand = onExpand
            )
        }
    }
}

/**
 * Simple info message item for displaying informational messages
 */
@Composable
private fun InfoMessageItem(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                AutoDevComposeIcons.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Platform-specific live terminal display.
 * On JVM with PTY support: Renders an interactive terminal widget
 * On other platforms: Shows a message that live terminal is not available
 *
 * @param exitCode Exit code when completed (null if still running)
 * @param executionTimeMs Execution time when completed (null if still running)
 * @param output Captured output when completed (null if still running or not captured)
 */
@Composable
expect fun LiveTerminalItem(
    sessionId: String,
    command: String,
    workingDirectory: String?,
    ptyHandle: Any?,
    exitCode: Int? = null,
    executionTimeMs: Long? = null,
    output: String? = null
)

/**
 * Image analysis section delimiter - used to detect and collapse image analysis results
 */
private const val IMAGE_ANALYSIS_START = "########################################\n# Image Analysis Result"
private const val IMAGE_ANALYSIS_END = "# End of Image Analysis\n########################################"

@Composable
fun MessageItem(
    message: Message,
    tokenInfo: cc.unitmesh.llm.compression.TokenInfo? = null
) {
    val isUser = message.role == MessageRole.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            PlatformMessageTextContainer(text = message.content) {
                Column(modifier = Modifier.padding(8.dp)) {
                    if (!isUser) {
                        SketchRenderer.RenderResponse(
                            content = message.content,
                            isComplete = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Check if message contains image analysis result
                        val analysisStartIndex = message.content.indexOf(IMAGE_ANALYSIS_START)
                        if (analysisStartIndex > 0) {
                            // User message with image analysis - show collapsible
                            UserMessageWithImageAnalysis(
                                content = message.content,
                                analysisStartIndex = analysisStartIndex
                            )
                        } else {
                            Text(
                                text = message.content,
                                fontFamily = getUtf8FontFamily(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    if (!isUser && tokenInfo != null && tokenInfo.totalTokens > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${tokenInfo.inputTokens} + ${tokenInfo.outputTokens} (${tokenInfo.totalTokens} tokens)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

/**
 * User message with collapsible image analysis result section
 */
@Composable
private fun UserMessageWithImageAnalysis(
    content: String,
    analysisStartIndex: Int
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Extract user's original text (before the analysis section)
    val userText = content.substring(0, analysisStartIndex).trim()

    // Extract analysis content (between the delimiters)
    val analysisEndIndex = content.indexOf(IMAGE_ANALYSIS_END)
    val analysisContent = if (analysisEndIndex > analysisStartIndex) {
        content.substring(analysisStartIndex, analysisEndIndex + IMAGE_ANALYSIS_END.length)
            .removePrefix(IMAGE_ANALYSIS_START)
            .removeSuffix(IMAGE_ANALYSIS_END)
            .trim()
            .lines()
            .dropWhile { it.isBlank() || it.startsWith("#") || it.contains("vision model analyzed") }
            .joinToString("\n")
            .trim()
    } else {
        content.substring(analysisStartIndex)
            .removePrefix(IMAGE_ANALYSIS_START)
            .trim()
    }

    Column {
        // User's original text
        if (userText.isNotBlank()) {
            Text(
                text = userText,
                fontFamily = getUtf8FontFamily(),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Collapsible image analysis section
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = AutoDevComposeIcons.Vision,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Image Analysis",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) AutoDevComposeIcons.ExpandLess else AutoDevComposeIcons.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isExpanded && analysisContent.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = analysisContent,
                        fontFamily = getUtf8FontFamily(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
expect fun PlatformMessageTextContainer(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
)

@Composable
fun StreamingMessageItem(
    content: String,
    onContentUpdate: (blockCount: Int) -> Unit = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (content.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                // Use SketchRenderer to support thinking blocks in streaming content
                // Pass onContentUpdate to trigger scroll when new blocks are rendered
                SketchRenderer.RenderResponse(
                    content = content,
                    isComplete = false,
                    onContentUpdate = onContentUpdate,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun TaskCompletedItem(
    success: Boolean,
    message: String
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (success) {
                Text(
                    text = "completed",
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f),
                    fontSize = 10.sp
                )
            } else {
                Icon(
                    imageVector = AutoDevComposeIcons.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = message,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * NanoDSL Timeline Item - displays generated NanoDSL code
 * @param irJson The IR JSON representation (reserved for future preview feature)
 */
@Composable
fun NanoDSLTimelineItem(
    source: String,
    @Suppress("unused") irJson: String?, // Reserved for future live preview feature
    componentName: String?,
    generationAttempts: Int,
    isValid: Boolean,
    warnings: List<String>,
    modifier: Modifier = Modifier
) {
    // TODO: Add live preview toggle when NanoRenderer integration is ready
    // var showPreview by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header row with component name and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🎨",
                        fontSize = 16.sp
                    )
                    Text(
                        text = componentName ?: "Generated UI",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    if (generationAttempts > 1) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "$generationAttempts attempts",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                // Validity indicator
                Text(
                    text = if (isValid) "✅ Valid" else "⚠️ Invalid",
                    fontSize = 12.sp,
                    color = if (isValid) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                )
            }

            // Warnings
            if (warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                warnings.forEach { warning ->
                    Text(
                        text = "⚠ $warning",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Code display
            val lines = source.lines()
            val maxLineNumWidth = lines.size.toString().length

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Line numbers and code
                    lines.forEachIndexed { index, line ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = (index + 1).toString().padStart(maxLineNumWidth, ' '),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = line,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Footer with line count
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${lines.size} lines of NanoDSL code",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Composable for rendering Agent-generated sketch blocks (chart, nanodsl, mermaid, etc.)
 * Uses SketchRenderer to handle all code block types uniformly.
 */
@Composable
fun AgentSketchBlockItem(
    agentName: String,
    language: String,
    code: String,
    metadata: Map<String, String> = emptyMap()
) {
    // Construct code fence format for SketchRenderer to parse
    val fencedContent = "```$language\n$code\n```"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header with agent name and language
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📊",
                        fontSize = 14.sp
                    )
                    Text(
                        text = agentName,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = language,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Use SketchRenderer to render the content uniformly
            SketchRenderer.Render(
                content = fencedContent,
                isComplete = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Composable for rendering multimodal analysis progress and results.
 * Shows image thumbnails, analysis status, and streaming/final results.
 */
@Composable
fun MultimodalAnalysisItemView(
    item: TimelineItem.MultimodalAnalysisItem,
    onExpand: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header with vision model and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = AutoDevComposeIcons.Vision,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Vision Analysis",
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "(${item.visionModel})",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status indicator
                StatusBadge(status = item.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Image thumbnails row
            if (item.images.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item.images.forEach { image ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = AutoDevComposeIcons.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = image.name,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Progress or result
            when {
                item.status == cc.unitmesh.agent.render.MultimodalAnalysisStatus.FAILED && item.error != null -> {
                    Text(
                        text = "Error: ${item.error}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                item.streamingResult.isNotEmpty() -> {
                    // Show streaming/final result
                    SketchRenderer.Render(
                        content = item.streamingResult,
                        isComplete = item.status == cc.unitmesh.agent.render.MultimodalAnalysisStatus.COMPLETED,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item.progress != null -> {
                    Text(
                        text = item.progress!!,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Execution time for completed analysis
            if (item.executionTimeMs != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Completed in ${item.executionTimeMs}ms",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Status badge for multimodal analysis
 */
@Composable
private fun StatusBadge(status: cc.unitmesh.agent.render.MultimodalAnalysisStatus) {
    val (text, color) = when (status) {
        cc.unitmesh.agent.render.MultimodalAnalysisStatus.COMPRESSING -> "Compressing" to MaterialTheme.colorScheme.tertiary
        cc.unitmesh.agent.render.MultimodalAnalysisStatus.UPLOADING -> "Uploading" to MaterialTheme.colorScheme.tertiary
        cc.unitmesh.agent.render.MultimodalAnalysisStatus.ANALYZING -> "Analyzing" to MaterialTheme.colorScheme.primary
        cc.unitmesh.agent.render.MultimodalAnalysisStatus.STREAMING -> "Receiving" to MaterialTheme.colorScheme.primary
        cc.unitmesh.agent.render.MultimodalAnalysisStatus.COMPLETED -> "Done" to MaterialTheme.colorScheme.primary
        cc.unitmesh.agent.render.MultimodalAnalysisStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

