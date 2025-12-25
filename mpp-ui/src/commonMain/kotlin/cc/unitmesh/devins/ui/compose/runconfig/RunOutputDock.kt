package cc.unitmesh.devins.ui.compose.runconfig

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons
import cc.unitmesh.devins.ui.compose.terminal.PlatformTerminalDisplay

/**
 * RunOutputDock - a lightweight terminal-like dock for showing RunConfig output.
 * This is intentionally simple and cross-platform:
 * - JVM: renders via JediTerm (PlatformTerminalDisplay actual)
 * - Other: ANSI text renderer fallback
 * 
 * Used for both:
 * - AI Analysis output (shows LLM reasoning and discovered configs)
 * - Run command output (shows command execution logs)
 */
@Composable
fun RunOutputDock(
    isVisible: Boolean,
    title: String = "Run Output",
    output: String,
    isRunning: Boolean = false,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onStop: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isAnalysis = title.contains("Analysis", ignoreCase = true)
    val headerColor = if (isAnalysis) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val headerIcon = if (isAnalysis) {
        AutoDevComposeIcons.SmartToy
    } else {
        AutoDevComposeIcons.Terminal
    }
    
    AnimatedVisibility(visible = isVisible) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerColor.copy(alpha = 0.3f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = headerIcon,
                            contentDescription = null,
                            tint = if (isAnalysis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Show Stop button when running (not during analysis)
                        if (isRunning && !isAnalysis && onStop != null) {
                            IconButton(
                                onClick = onStop,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Icon(
                                    imageVector = AutoDevComposeIcons.Stop,
                                    contentDescription = "Stop",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = AutoDevComposeIcons.Delete,
                                contentDescription = "Clear output"
                            )
                        }
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = AutoDevComposeIcons.Close,
                                contentDescription = "Close"
                            )
                        }
                    }
                }
                HorizontalDivider()
                // Dynamic height based on content type
                val dockHeight = if (isAnalysis) 220.dp else 180.dp
                
                if (output.isBlank()) {
                    Text(
                        text = if (isAnalysis) "AI is analyzing..." else "Waiting for output...",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    PlatformTerminalDisplay(
                        output = output,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dockHeight)
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}


