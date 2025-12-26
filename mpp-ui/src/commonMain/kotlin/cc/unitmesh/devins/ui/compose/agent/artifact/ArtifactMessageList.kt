package cc.unitmesh.devins.ui.compose.agent.artifact

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.unitmesh.agent.render.TimelineItem
import cc.unitmesh.devins.llm.MessageRole
import cc.unitmesh.devins.ui.compose.agent.ComposeRenderer
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons
import cc.unitmesh.devins.ui.compose.sketch.SketchRenderer
import cc.unitmesh.devins.ui.compose.sketch.ThinkingBlockRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Custom message list optimized for artifact generation.
 *
 * Unlike AgentMessageList which shows full code details, this component:
 * - Shows user messages compactly
 * - Displays streaming output in a height-limited scrollable code block
 * - Uses ArtifactCodeBlock for completed artifact code display
 * - Auto-scrolls to bottom during streaming
 */
@Composable
fun ArtifactMessageList(
    renderer: ComposeRenderer,
    streamingArtifact: StreamingArtifact?,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Create a stable snapshot of the timeline to prevent IndexOutOfBoundsException
    // when the list is modified during composition (same pattern as AgentMessageList).
    val timelineSnapshot = remember(renderer.timeline.size) {
        renderer.timeline.toList()
    }

    // Track if user manually scrolled away from bottom.
    var userScrolledAway by remember { mutableStateOf(false) }

    // Track content updates from SketchRenderer for streaming content.
    var streamingBlockCount by remember { mutableIntStateOf(0) }

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

    // Monitor scroll state to detect user scrolling away.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            // If user scrolled to a position not near the bottom, they want to view history.
            userScrolledAway = lastVisibleIndex < totalItems - 2
        }
    }

    // Scroll when timeline changes (new messages, errors, etc.).
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

    // Scroll when raw streaming content changes.
    LaunchedEffect(renderer.currentStreamingOutput) {
        if (renderer.currentStreamingOutput.isNotEmpty()) {
            delay(100)
            scrollToBottomIfNeeded()
        }
    }

    // Scroll when thinking content changes.
    LaunchedEffect(renderer.currentThinkingOutput) {
        if (renderer.currentThinkingOutput.isNotEmpty() && renderer.isThinking) {
            delay(50)
            scrollToBottomIfNeeded()
        }
    }

    // Scroll when SketchRenderer reports new blocks rendered.
    LaunchedEffect(streamingBlockCount) {
        if (streamingBlockCount > 0) {
            delay(50)
            scrollToBottomIfNeeded()
        }
    }

    // Reset user scroll state when streaming starts.
    LaunchedEffect(renderer.isProcessing) {
        if (renderer.isProcessing) {
            userScrolledAway = false
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // Render timeline items
        items(items = timelineSnapshot, key = { it.id }) { item ->
            when (item) {
                is TimelineItem.MessageItem -> {
                    ArtifactMessageItem(
                        role = item.message?.role ?: item.role,
                        content = item.message?.content ?: item.content
                    )
                }
                is TimelineItem.ErrorItem -> {
                    ArtifactErrorItem(message = item.message)
                }
                else -> {
                    // Other item types are not shown in artifact mode
                }
            }
        }

        // Show thinking block if active
        if (renderer.isThinking && renderer.currentThinkingOutput.isNotEmpty()) {
            item(key = "thinking") {
                ThinkingBlockRenderer(
                    thinkingContent = renderer.currentThinkingOutput,
                    isComplete = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Show streaming artifact content
        if (streamingArtifact != null) {
            item(key = "streaming-artifact") {
                if (streamingArtifact.content.isNotEmpty()) {
                    ArtifactCodeBlock(
                        title = streamingArtifact.title,
                        code = streamingArtifact.content,
                        language = streamingArtifact.type.substringAfterLast(".").ifEmpty { "html" },
                        isStreaming = !streamingArtifact.isComplete,
                        identifier = streamingArtifact.identifier,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    ArtifactStreamingIndicator(
                        title = streamingArtifact.title,
                        charCount = 0,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else if (renderer.currentStreamingOutput.isNotEmpty() && renderer.isProcessing) {
            // Show raw streaming output when no artifact detected yet
            item(key = "streaming-raw") {
                StreamingOutputBlock(
                    content = renderer.currentStreamingOutput,
                    onContentUpdate = { blockCount -> streamingBlockCount = blockCount },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ArtifactMessageItem(
    role: MessageRole,
    content: String
) {
    val isUser = role == MessageRole.USER

    if (isUser) {
        // User messages - right aligned, limited width
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = 12.dp,
                    bottomEnd = 4.dp
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    } else {
        // Assistant messages - full width
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                // Use SketchRenderer to reuse ThinkingBlockRenderer / CodeBlockRenderer, etc.
                SketchRenderer.RenderResponse(
                    content = content,
                    isComplete = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ArtifactErrorItem(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = AutoDevComposeIcons.Warning,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun StreamingOutputBlock(
    content: String,
    onContentUpdate: (blockCount: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Extract artifact info if present in streaming output
    val hasArtifactTag = content.contains("<autodev-artifact")

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (hasArtifactTag) "Generating artifact..." else "Processing...",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (!hasArtifactTag && content.length < 500) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(8.dp)) {
                        // Keep this block height-limited to avoid pushing the whole list during streaming.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                        ) {
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
        }
    }
}

