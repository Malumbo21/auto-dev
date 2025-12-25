package cc.unitmesh.devins.ui.compose.agent.artifact

import androidx.compose.foundation.background
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
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors
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
    val scope = rememberCoroutineScope()
    
    // Auto-scroll to bottom when new content arrives
    LaunchedEffect(renderer.timeline.size, renderer.currentStreamingOutput) {
        if (renderer.timeline.isNotEmpty() || renderer.currentStreamingOutput.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(
                    index = maxOf(0, renderer.timeline.size)
                )
            }
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // Render timeline items
        items(renderer.timeline, key = { it.id }) { item ->
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
                ArtifactThinkingBlock(
                    content = renderer.currentThinkingOutput,
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
                modifier = Modifier.widthIn(max = 400.dp)
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
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp)
            )
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
            Text(
                text = "❌",
                style = MaterialTheme.typography.bodyMedium
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
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

