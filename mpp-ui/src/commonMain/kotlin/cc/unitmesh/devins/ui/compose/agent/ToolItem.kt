package cc.unitmesh.devins.ui.compose.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.agent.render.ToolCallInfo
import cc.unitmesh.agent.render.RendererUtils
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors

@Composable
fun ToolResultItem(
    toolName: String,
    success: Boolean,
    summary: String,
    output: String?,
    fullOutput: String? = null
) {
    var expanded by remember { mutableStateOf(!success) }
    var showFullOutput by remember { mutableStateOf(!success) }
    val clipboardManager = LocalClipboardManager.current

    val displayOutput = if (showFullOutput) fullOutput else output
    val hasFullOutput = fullOutput != null && fullOutput != output

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { if (displayOutput != null) expanded = !expanded },
                verticalAlignment = Alignment.Companion.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (success) AutoDevComposeIcons.Check else AutoDevComposeIcons.Error,
                    contentDescription = if (success) "Success" else "Error",
                    tint = if (success) AutoDevColors.Signal.success else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = toolName,
                    fontWeight = FontWeight.Companion.Medium,
                    color =
                        if (success) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "→ $summary",
                    color =
                        if (success) {
                            AutoDevColors.Signal.success
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Companion.Medium
                )

                if (displayOutput != null) {
                    Icon(
                        imageVector = if (expanded) AutoDevComposeIcons.ExpandLess else AutoDevComposeIcons.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint =
                            if (success) {
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (expanded && displayOutput != null) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Companion.Top
                ) {
                    Column {
                        Text(
                            text = "Output:",
                            color =
                                if (success) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (hasFullOutput) {
                            TextButton(
                                onClick = { showFullOutput = !showFullOutput },
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = if (showFullOutput) "Show Less" else "Show Full Output",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Row {
                        IconButton(
                            onClick = { clipboardManager.setText(AnnotatedString(displayOutput ?: "")) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = AutoDevComposeIcons.ContentCopy,
                                contentDescription = "Copy output",
                                tint =
                                    if (success) {
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                    },
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Copy entire block button (always copy full output if available)
                        IconButton(
                            onClick = {
                                val blockText =
                                    buildString {
                                        val status = if (success) "SUCCESS" else "FAILED"
                                        appendLine("[Tool Result]: $toolName - $status")
                                        appendLine("Summary: $summary")
                                        appendLine("Output: ${fullOutput ?: output ?: ""}")
                                    }
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(blockText))
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = AutoDevComposeIcons.ContentCopy,
                                contentDescription = "Copy entire block",
                                tint =
                                    if (success) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formatOutput(displayOutput),
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Companion.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun ToolErrorItem(
    error: String,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var expanded by remember(error) { mutableStateOf(false) }

    val isMultiLine = remember(error) { error.contains('\n') }
    val previewLine = remember(error) { error.lineSequence().firstOrNull().orEmpty() }
    val preview = remember(error) {
        val maxChars = 140
        val trimmed = previewLine.trim()
        if (trimmed.length > maxChars) "${trimmed.take(maxChars)}…" else trimmed
    }
    val canExpand = remember(error) { isMultiLine || error.length > preview.length }

    // These timeline "errors" are often recoverable (agent retries); render as a compact neutral notice.
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canExpand) { expanded = !expanded },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = AutoDevComposeIcons.Info,
                    contentDescription = "Notice",
                    tint = contentColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )

                Text(
                    text = preview.ifBlank { "Notice" },
                    color = contentColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(error)) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = AutoDevComposeIcons.ContentCopy,
                        contentDescription = "Copy notice",
                        tint = contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = AutoDevComposeIcons.Close,
                        contentDescription = "Dismiss notice",
                        tint = contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (canExpand) {
                    Icon(
                        imageVector = if (expanded) AutoDevComposeIcons.ExpandLess else AutoDevComposeIcons.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                SelectionContainer {
                    PlatformMessageTextContainer(text = error) {
                        Text(
                            text = error,
                            color = contentColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CurrentToolCallItem(toolCall: ToolCallInfo) {
    val isRetrievalToolCall = remember(toolCall.toolName) {
        RendererUtils.isRetrievalToolCall(toolName = toolCall.toolName)
    }

    Surface(
        color = if (isRetrievalToolCall) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isRetrievalToolCall) 10.dp else 12.dp, vertical = if (isRetrievalToolCall) 6.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (isRetrievalToolCall) 16.dp else 20.dp),
                strokeWidth = 2.dp,
                color = if (isRetrievalToolCall) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = AutoDevComposeIcons.Build,
                    contentDescription = "Tool",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(if (isRetrievalToolCall) 14.dp else 16.dp)
                )
                Text(
                    text = "${toolCall.toolName} — ${toolCall.description}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isRetrievalToolCall) 0.75f else 0.9f),
                    style = if (isRetrievalToolCall) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // "Executing" badge
            if (!isRetrievalToolCall) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "EXECUTING",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
