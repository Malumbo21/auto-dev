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
import cc.unitmesh.devins.idea.renderer.sketch.chart.IdeaChartRenderer
import cc.unitmesh.devins.idea.renderer.sketch.IdeaMermaidRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
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
            IdeaAgentSketchBlockBubble(item, project = project)
        }
        is TimelineItem.ChatDBStepItem -> {
            // ChatDB execution step - display as info bubble
            IdeaInfoBubble(
                message = "${item.stepType.icon} ${item.stepType.displayName}: ${item.title}",
                status = item.status.displayName
            )
        }
        is TimelineItem.InfoItem -> {
            // Info message bubble
            IdeaInfoBubble(message = item.message)
        }
        is TimelineItem.MultimodalAnalysisItem -> {
            // Multimodal analysis (vision model) bubble
            IdeaMultimodalAnalysisBubble(item)
        }
    }
}

/**
 * Agent-generated sketch block bubble (chart, nanodsl, mermaid, etc.)
 * Uses appropriate renderer based on language type.
 */
@Composable
fun IdeaAgentSketchBlockBubble(
    item: TimelineItem.AgentSketchBlockItem,
    project: Project? = null,
    parentDisposable: Disposable? = null
) {
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

            // Render content based on language type
            when (item.language.lowercase()) {
                "chart", "graph" -> {
                    IdeaChartRenderer(
                        chartCode = item.code,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                "mermaid", "mmd" -> {
                    val disposable = parentDisposable ?: Disposer.newDisposable("AgentSketchBlock")
                    IdeaMermaidRenderer(
                        mermaidCode = item.code,
                        project = project,
                        isDarkTheme = true,
                        parentDisposable = disposable,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {
                    // Fallback: display raw code
                    RenderCodeFallback(item.code, item.language)
                }
            }
        }
    }
}

/**
 * Fallback renderer for code content when no specific renderer is available.
 */
@Composable
private fun RenderCodeFallback(code: String, language: String) {
    val lines = code.lines()
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
        text = "${lines.size} lines of $language code",
        style = JewelTheme.defaultTextStyle.copy(
            fontSize = 10.sp,
            color = JewelTheme.globalColors.text.info.copy(alpha = 0.5f)
        )
    )
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

/**
 * Info bubble for displaying informational messages.
 */
@Composable
fun IdeaInfoBubble(
    message: String,
    status: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(
                JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.info
                )
            )
            if (status != null) {
                Text(
                    text = "[$status]",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 10.sp,
                        color = JewelTheme.globalColors.text.info.copy(alpha = 0.6f)
                    )
                )
            }
        }
    }
}

/**
 * Multimodal analysis bubble for displaying vision model analysis progress and results.
 * Shows image info, analysis status, and streaming/final results.
 */
@Composable
fun IdeaMultimodalAnalysisBubble(item: TimelineItem.MultimodalAnalysisItem) {
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
                    Text(
                        text = "👁️ Vision Analysis",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = JewelTheme.globalColors.text.info
                        )
                    )
                    Text(
                        text = "(${item.visionModel})",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 10.sp,
                            color = JewelTheme.globalColors.text.info.copy(alpha = 0.6f)
                        )
                    )
                }
                // Status badge
                val statusColor = when (item.status) {
                    cc.unitmesh.agent.render.MultimodalAnalysisStatus.COMPLETED ->
                        JewelTheme.globalColors.text.info
                    cc.unitmesh.agent.render.MultimodalAnalysisStatus.FAILED ->
                        JewelTheme.globalColors.text.error
                    else -> JewelTheme.globalColors.text.info.copy(alpha = 0.7f)
                }
                Text(
                    text = item.status.displayName,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 10.sp,
                        color = statusColor
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Image thumbnails info
            if (item.images.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item.images.forEach { image ->
                        Box(
                            modifier = Modifier
                                .background(
                                    JewelTheme.globalColors.panelBackground,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "🖼️",
                                    style = JewelTheme.defaultTextStyle.copy(fontSize = 16.sp)
                                )
                                Text(
                                    text = image.name,
                                    style = JewelTheme.defaultTextStyle.copy(
                                        fontSize = 9.sp,
                                        color = JewelTheme.globalColors.text.info.copy(alpha = 0.7f)
                                    ),
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
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            color = JewelTheme.globalColors.text.error
                        )
                    )
                }
                item.streamingResult.isNotEmpty() -> {
                    // Show streaming/final result
                    Text(
                        text = item.streamingResult,
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
                    )
                }
                item.progress != null -> {
                    Text(
                        text = item.progress!!,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.info.copy(alpha = 0.7f)
                        )
                    )
                }
            }

            // Execution time for completed analysis
            if (item.executionTimeMs != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Completed in ${item.executionTimeMs}ms",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 10.sp,
                        color = JewelTheme.globalColors.text.info.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}
