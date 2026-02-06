package cc.unitmesh.devins.ui.compose.agent.acp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.unitmesh.agent.AgentType
import cc.unitmesh.config.AcpAgentConfig
import cc.unitmesh.config.AutoDevConfigWrapper
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devins.ui.compose.config.AcpAgentConfigDialog
import cc.unitmesh.devins.ui.compose.editor.DevInEditorInput
import cc.unitmesh.devins.ui.compose.sketch.SketchRenderer
import cc.unitmesh.devins.workspace.WorkspaceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ACP Agent Page - the main UI for interacting with an external ACP agent.
 *
 * When active, all interaction goes through the external agent (e.g., Kimi CLI, Claude CLI).
 * The local LLM service is NOT used. This page:
 * - Shows connection status
 * - Renders the ACP agent's streaming responses, tool calls, and plans
 * - Provides a prompt input for sending messages to the agent
 */
@Composable
fun AcpAgentPage(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNotification: (String, String) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()
    val currentWorkspace by WorkspaceManager.workspaceFlow.collectAsState()
    val projectPath = currentWorkspace?.rootPath ?: "."

    // Load ACP agent configs
    var acpAgents by remember { mutableStateOf<Map<String, AcpAgentConfig>>(emptyMap()) }
    var activeAgentKey by remember { mutableStateOf<String?>(null) }
    var showConfigDialog by remember { mutableStateOf(false) }

    // ViewModel
    val viewModel = remember(projectPath) { AcpAgentViewModel(projectPath) }

    // Load config on start
    LaunchedEffect(Unit) {
        try {
            val wrapper = ConfigManager.load()
            acpAgents = wrapper.getAcpAgents()
            activeAgentKey = wrapper.getActiveAcpAgentKey()

            // Auto-connect if there's an active agent
            val activeConfig = wrapper.getActiveAcpAgent()
            if (activeConfig != null && activeConfig.isConfigured()) {
                viewModel.connect(activeConfig)
            } else if (acpAgents.isEmpty()) {
                // No agents configured, show config dialog
                showConfigDialog = true
            }
        } catch (e: Exception) {
            println("Failed to load ACP config: ${e.message}")
        }
    }

    // Cleanup on dispose
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.dispose()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar with agent info and controls
        AcpTopBar(
            agentName = viewModel.currentConfig?.name
                ?: activeAgentKey
                ?: "No Agent",
            isConnected = viewModel.isConnected,
            isConnecting = viewModel.isConnecting,
            connectionError = viewModel.connectionError,
            onConfigure = { showConfigDialog = true },
            onDisconnect = { viewModel.disconnect() },
            onReconnect = {
                val config = acpAgents[activeAgentKey]
                if (config != null) {
                    viewModel.disconnect()
                    viewModel.connect(config)
                }
            },
            onBack = onBack
        )

        // Timeline / chat area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (!isAcpSupported()) {
                // Platform not supported
                UnsupportedPlatformMessage(modifier = Modifier.align(Alignment.Center))
            } else if (acpAgents.isEmpty() && !viewModel.isConnected) {
                // No agents configured
                NoAgentConfiguredMessage(
                    modifier = Modifier.align(Alignment.Center),
                    onConfigure = { showConfigDialog = true }
                )
            } else {
                AcpTimeline(
                    timeline = viewModel.timeline,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Input area
        if (viewModel.isConnected) {
            AcpInputArea(
                isExecuting = viewModel.isExecuting,
                onSubmit = { text ->
                    viewModel.sendPrompt(text)
                },
                onCancel = {
                    viewModel.cancelPrompt()
                }
            )
        }
    }

    // Config dialog
    if (showConfigDialog) {
        AcpAgentConfigDialog(
            currentAgents = acpAgents,
            activeAgentKey = activeAgentKey,
            onDismiss = { showConfigDialog = false },
            onSave = { agents, activeKey ->
                acpAgents = agents
                activeAgentKey = activeKey
                showConfigDialog = false

                // Save to config
                scope.launch {
                    try {
                        AutoDevConfigWrapper.saveAcpAgents(agents, activeKey)

                        // Connect to the active agent
                        val config = activeKey?.let { agents[it] }
                        if (config != null && config.isConfigured()) {
                            viewModel.disconnect()
                            viewModel.clearTimeline()
                            viewModel.connect(config)
                        }
                    } catch (e: Exception) {
                        onNotification("Error", "Failed to save ACP config: ${e.message}")
                    }
                }
            }
        )
    }
}

@Composable
private fun AcpTopBar(
    agentName: String,
    isConnected: Boolean,
    isConnecting: Boolean,
    connectionError: String?,
    onConfigure: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            TextButton(onClick = onBack) {
                Text("<- Back")
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Agent name and status
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = agentName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status indicator
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = when {
                                    isConnected -> MaterialTheme.colorScheme.primary
                                    isConnecting -> MaterialTheme.colorScheme.tertiary
                                    connectionError != null -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.outline
                                },
                                shape = RoundedCornerShape(50)
                            )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when {
                            isConnected -> "Connected (ACP)"
                            isConnecting -> "Connecting..."
                            connectionError != null -> "Error: $connectionError"
                            else -> "Disconnected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Actions
            if (isConnected) {
                TextButton(onClick = onDisconnect) {
                    Text("Disconnect")
                }
            } else if (!isConnecting) {
                TextButton(onClick = onReconnect) {
                    Text("Reconnect")
                }
            }

            TextButton(onClick = onConfigure) {
                Text("Configure")
            }
        }
    }
}

@Composable
private fun AcpTimeline(
    timeline: List<AcpTimelineItem>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom
    LaunchedEffect(timeline.size) {
        if (timeline.isNotEmpty()) {
            delay(50)
            listState.animateScrollToItem(maxOf(0, timeline.size - 1))
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(
            items = timeline,
            key = { "${it::class.simpleName}_${it.timestamp}_${timeline.indexOf(it)}" }
        ) { item ->
            when (item) {
                is AcpTimelineItem.UserMessage -> UserMessageCard(item)
                is AcpTimelineItem.AgentMessage -> AgentMessageCard(item)
                is AcpTimelineItem.ThinkingBlock -> ThinkingBlockCard(item)
                is AcpTimelineItem.ToolCall -> ToolCallCard(item)
                is AcpTimelineItem.PlanBlock -> PlanBlockCard(item)
                is AcpTimelineItem.ErrorMessage -> ErrorMessageCard(item)
                is AcpTimelineItem.SystemMessage -> SystemMessageCard(item)
            }
        }
    }
}

@Composable
private fun UserMessageCard(item: AcpTimelineItem.UserMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            modifier = Modifier.widthIn(max = 600.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text(
                text = item.content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun AgentMessageCard(item: AcpTimelineItem.AgentMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Use SketchRenderer for rich markdown rendering
            SketchRenderer.RenderResponse(
                content = item.content,
                isComplete = !item.isStreaming
            )
        }
    }
}

@Composable
private fun ThinkingBlockCard(item: AcpTimelineItem.ThinkingBlock) {
    var isExpanded by remember { mutableStateOf(item.isActive) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (item.isActive) "Thinking..." else "Thought",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (isExpanded) "[-]" else "[+]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolCallCard(item: AcpTimelineItem.ToolCall) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tool: ${item.title}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    if (!item.input.isNullOrBlank()) {
                        Text(
                            text = "Input:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.input,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                        )
                    }
                    if (!item.output.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Output:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.output,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanBlockCard(item: AcpTimelineItem.PlanBlock) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Plan",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))

            item.entries.forEach { entry ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (entry.status.lowercase()) {
                            "completed", "done" -> "[x]"
                            "in_progress", "running" -> "[>]"
                            else -> "[ ]"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = entry.content,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorMessageCard(item: AcpTimelineItem.ErrorMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Text(
            text = item.message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun SystemMessageCard(item: AcpTimelineItem.SystemMessage) {
    Text(
        text = item.content,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AcpInputArea(
    isExecuting: Boolean,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Send a message to the agent...") },
                enabled = !isExecuting,
                minLines = 1,
                maxLines = 5
            )

            Spacer(modifier = Modifier.width(8.dp))

            if (isExecuting) {
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Cancel")
                }
            } else {
                Button(
                    onClick = {
                        val text = inputText.trim()
                        if (text.isNotEmpty()) {
                            onSubmit(text)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank()
                ) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
private fun UnsupportedPlatformMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ACP agents are not supported on this platform",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Custom ACP agents require process spawning, which is only available on Desktop (JVM).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoAgentConfiguredMessage(
    modifier: Modifier = Modifier,
    onConfigure: () -> Unit
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No Custom Agent Configured",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Configure an external ACP-compliant agent (e.g., Kimi CLI, Claude CLI) to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onConfigure) {
            Text("Configure Agent")
        }
    }
}
