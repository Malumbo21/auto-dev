package cc.unitmesh.devins.ui.compose.agent.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.agent.Platform
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons

/**
 * Compact code block display for artifacts.
 * Collapsed by default - shows only header with title and stats.
 * Users can expand to see the full code.
 * 
 * Similar to ToolCallItem pattern but optimized for showing generated artifact code.
 */
@Composable
fun ArtifactCodeBlock(
    title: String,
    code: String,
    language: String = "html",
    isStreaming: Boolean = false,
    identifier: String? = null,
    modifier: Modifier = Modifier
) {
    // Key the expanded state by title/language so it never "leaks" across different files/runs.
    var expanded by remember(identifier ?: title, language) { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val isDesktop = Platform.isJvm && !Platform.isAndroid

    // Ensure a new streaming run always starts collapsed (even if identifier/title repeats).
    LaunchedEffect(isStreaming, identifier, title) {
        if (isStreaming) {
            expanded = false
        }
    }
    
    // Stats for display
    val lineCount = code.lines().size
    val charCount = code.length
    val displayStats = buildString {
        append("$lineCount lines")
        if (charCount > 1000) {
            append(" · ${charCount / 1000}k chars")
        } else {
            append(" · $charCount chars")
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(0.dp)) {
            // Header - always visible. Expansion is explicit via the arrow button (no "click anywhere" toggle).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Status icon
                    if (isStreaming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = AutoDevComposeIcons.Code,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // Title
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Language badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = language.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Stats
                    Text(
                        text = displayStats,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    
                    // Copy button
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(code)) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = AutoDevComposeIcons.ContentCopy,
                            contentDescription = "Copy code",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Expand/collapse button (explicit)
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) AutoDevComposeIcons.ExpandLess else AutoDevComposeIcons.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Code content - only shown when expanded
            if (expanded) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val horizontalScrollState = rememberScrollState()
                    
                    SelectionContainer {
                        Text(
                            text = code,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = if (isDesktop) 12.sp else 11.sp,
                                lineHeight = if (isDesktop) 18.sp else 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(horizontalScrollState)
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Streaming indicator for artifact generation
 */
@Composable
fun ArtifactStreamingIndicator(
    title: String,
    charCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Generating: $title",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$charCount characters generated...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
