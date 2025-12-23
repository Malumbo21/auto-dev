package cc.unitmesh.devins.ui.compose.agent

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.agent.tool.shell.ShellSessionManager
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons
import cc.unitmesh.devins.ui.compose.terminal.ProcessTtyConnector
import cc.unitmesh.devins.ui.compose.terminal.TerminalWidget
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors
import cc.unitmesh.devins.ui.compose.terminal.PlatformTerminalDisplay
import com.jediterm.terminal.ui.JediTermWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.KeyStroke

/**
 * JVM implementation of LiveTerminalItem.
 * Uses JediTerm with PTY process for real-time terminal emulation.
 *
 * Modern compact design inspired by IntelliJ Terminal plugin:
 * - Compact header (32-36dp) to save space in timeline
 * - Inline status indicator
 * - Clean, minimal design using AutoDevColors
 * - Shows completion status when exitCode is provided
 */
@Composable
actual fun LiveTerminalItem(
    sessionId: String,
    command: String,
    workingDirectory: String?,
    ptyHandle: Any?,
    exitCode: Int?,
    executionTimeMs: Long?,
    output: String?
) {
    var expanded by remember { mutableStateOf(true) } // Auto-expand live terminal
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // Mirror PTY output into ShellSessionManager so ToolOrchestrator can capture the final output
    // without double-reading the same PTY stream.
    val outputChannel = remember(sessionId) { Channel<String>(capacity = Channel.UNLIMITED) }
    var managedSession by remember {
        mutableStateOf<cc.unitmesh.agent.tool.shell.ManagedSession?>(null)
    }

    LaunchedEffect(sessionId) {
        managedSession = ShellSessionManager.getSession(sessionId)
    }

    LaunchedEffect(managedSession, outputChannel) {
        val session = managedSession ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            for (chunk in outputChannel) {
                session.appendOutput(chunk)
            }
        }
    }

    LaunchedEffect(exitCode) {
        // Stop mirroring once the session is completed and the final output is captured.
        if (exitCode != null) {
            outputChannel.close()
        }
    }

    DisposableEffect(outputChannel) {
        onDispose {
            outputChannel.close()
        }
    }

    val process = remember(ptyHandle) { ptyHandle as? Process }

    // Create TtyConnector from the process
    val ttyConnector =
        remember(process, outputChannel) {
            process?.let {
                ProcessTtyConnector(
                    process = it,
                    onReadChunk = { chunk -> outputChannel.trySend(chunk) }
                )
            }
        }

    // Determine if running: if exitCode is provided, it's completed
    val isRunning = exitCode == null && (process?.isAlive == true)

    val interrupt: () -> Unit = {
        try {
            // Ctrl+C (ETX) - terminal break / SIGINT
            ttyConnector?.write(byteArrayOf(3))
        } catch (_: Exception) {
            // Best-effort fallback
            try {
                process?.outputStream?.write(byteArrayOf(3))
                process?.outputStream?.flush()
            } catch (_: Exception) {
                // Ignore
            }
        }
    }

    val stop: () -> Unit = {
        // Mark cancel so ToolOrchestrator can label the completion correctly.
        ShellSessionManager.markSessionCancelledByUser(sessionId)

        // Try graceful interrupt first, then force-kill if still running.
        interrupt()
        coroutineScope.launch(Dispatchers.IO) {
            delay(1500)
            if (process?.isAlive == true) {
                process.destroyForcibly()
            }
        }
    }

    val copyCurrentOutput: () -> Unit = {
        coroutineScope.launch {
            val textToCopy = when {
                exitCode != null -> output.orEmpty()
                managedSession != null -> withContext(Dispatchers.IO) { managedSession?.getOutput().orEmpty() }
                else -> ""
            }
            if (textToCopy.isNotEmpty()) {
                clipboardManager.setText(AnnotatedString(textToCopy))
            }
        }
    }

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
        shape = RoundedCornerShape(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        // Smooth height changes
        Column(
            modifier =
                Modifier
                    .padding(8.dp)
                    .animateContentSize()
        ) {
            // Compact header - inspired by IntelliJ Terminal
            // Compact height: 32dp
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Collapse/Expand icon
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )

                // Status indicator - small dot
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRunning) AutoDevColors.Green.c400 else MaterialTheme.colorScheme.outline
                            )
                )

                // Terminal icon + command in one line
                Text(
                    text = "💻",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 14.sp
                )

                // Command text - truncated if too long
                Text(
                    text = command,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                // Status badge - compact, shows exit code when completed
                val (statusText, statusColor) = when {
                    isRunning -> "RUNNING" to AutoDevColors.Green.c400
                    exitCode == 0 -> "✓ EXIT 0" to AutoDevColors.Green.c400
                    exitCode != null -> "✗ EXIT $exitCode" to AutoDevColors.Red.c400
                    else -> "DONE" to MaterialTheme.colorScheme.onSurfaceVariant
                }

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Show execution time when completed
                        if (executionTimeMs != null) {
                            Text(
                                text = "${executionTimeMs}ms",
                                color = statusColor.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                if (isRunning) {
                    IconButton(
                        onClick = stop,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = AutoDevComposeIcons.Stop,
                            contentDescription = "Terminate",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = copyCurrentOutput,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = AutoDevComposeIcons.ContentCopy,
                        contentDescription = "Copy output",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Working directory - only show when expanded and exists
            if (expanded && workingDirectory != null) {
                Text(
                    text = "📁 $workingDirectory",
                    modifier = Modifier.padding(start = 30.dp, top = 2.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(4.dp))

                // When completed, render captured output instead of the live PTY terminal.
                // The PTY-backed terminal is ephemeral and may appear empty after process exit.
                if (exitCode != null) {
                    val finalOutput = output ?: ""
                    if (finalOutput.isNotEmpty()) {
                        SelectionContainer {
                            PlatformTerminalDisplay(
                                output = finalOutput,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text(
                            text = "(no output captured)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 30.dp, top = 4.dp)
                        )
                    }
                } else if (ttyConnector != null) {
                    val terminalHeight = 60.dp

                    TerminalWidget(
                        ttyConnector = ttyConnector,
                        modifier = Modifier.fillMaxWidth().height(terminalHeight),
                        onTerminalReady = { widget ->
                            widget.requestFocusInWindow()
                            installCtrlCInterrupt(widget, onInterrupt = interrupt)
                        }
                    )
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ Failed to connect to terminal process",
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

private fun installCtrlCInterrupt(
    widget: JediTermWidget,
    onInterrupt: () -> Unit
) {
    // Ensure Ctrl+C behaves like a terminal break (SIGINT) even if focus/shortcuts are inconsistent.
    val keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK)
    val actionKey = "autodev.terminal.interrupt"

    widget.inputMap.put(keyStroke, actionKey)
    widget.actionMap.put(
        actionKey,
        object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                onInterrupt()
            }
        }
    )
}
