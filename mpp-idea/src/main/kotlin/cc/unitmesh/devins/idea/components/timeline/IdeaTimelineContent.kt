package cc.unitmesh.devins.idea.components.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.agent.render.TimelineItem
import com.intellij.openapi.project.Project
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * Timeline content container for displaying chat history.
 * Similar to AgentMessageList in mpp-ui but using Jewel theming.
 */
@Composable
fun IdeaTimelineContent(
    timeline: List<TimelineItem>,
    streamingOutput: String,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    project: Project? = null,
    onProcessCancel: ((CancelEvent) -> Unit)? = null
) {
    if (timeline.isEmpty() && streamingOutput.isEmpty()) {
        IdeaEmptyStateMessage("Start a conversation with your AI Assistant!")
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(timeline, key = { it.id }) { item ->
                IdeaTimelineItemView(item, project, onProcessCancel)
            }

            // Show streaming output
            if (streamingOutput.isNotEmpty()) {
                item {
                    IdeaStreamingMessageBubble(streamingOutput)
                }
            }
        }
    }
}

/**
 * Dispatch timeline item to appropriate bubble component.
 */
@Composable
fun IdeaTimelineItemView(
    item: TimelineItem,
    project: Project? = null,
    onProcessCancel: ((CancelEvent) -> Unit)? = null
) {
    when (item) {
        is TimelineItem.MessageItem -> {
            IdeaMessageBubble(
                role = item.role,
                content = item.content
            )
        }
        is TimelineItem.ToolCallItem -> {
            IdeaToolCallBubble(item)
        }
        is TimelineItem.ErrorItem -> {
            IdeaErrorBubble(item.message)
        }
        is TimelineItem.TaskCompleteItem -> {
            IdeaTaskCompleteBubble(item)
        }
        is TimelineItem.TerminalOutputItem -> {
            IdeaTerminalOutputBubble(item, project = project)
        }
        is TimelineItem.LiveTerminalItem -> {
            // Live terminal with real-time output streaming
            IdeaLiveTerminalBubble(
                item = item,
                project = project,
                onCancel = onProcessCancel
            )
        }
        is TimelineItem.AgentSketchBlockItem -> {
            // Agent-generated sketch block (chart, nanodsl, mermaid, etc.)
            IdeaAgentSketchBlockBubble(item)
        }
    }
}

/**
 * Agent-generated sketch block bubble (chart, nanodsl, mermaid, etc.)
 */
@Composable
fun IdeaAgentSketchBlockBubble(item: TimelineItem.AgentSketchBlockItem) {
    val lines = item.code.lines()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Column {
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
                        text = "📊 ${item.agentName}",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = JewelTheme.globalColors.text.info
                        )
                    )
                }
                Text(
                    text = item.language,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 10.sp,
                        color = JewelTheme.globalColors.text.info.copy(alpha = 0.6f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Code content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        JewelTheme.globalColors.panelBackground,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(8.dp)
            ) {
                Column {
                    lines.take(20).forEachIndexed { index, line ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 10.sp,
                                    color = JewelTheme.globalColors.text.info.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.width(24.dp)
                            )
                            Text(
                                text = line,
                                style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
                            )
                        }
                    }
                    if (lines.size > 20) {
                        Text(
                            text = "... (${lines.size - 20} more lines)",
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 10.sp,
                                color = JewelTheme.globalColors.text.info.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            // Footer with line count
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${lines.size} lines of ${item.language} code",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 10.sp,
                    color = JewelTheme.globalColors.text.info.copy(alpha = 0.5f)
                )
            )
        }
    }
}

/**
 * Empty state message displayed when timeline is empty.
 */
@Composable
fun IdeaEmptyStateMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 14.sp,
                color = JewelTheme.globalColors.text.info
            )
        )
    }
}

