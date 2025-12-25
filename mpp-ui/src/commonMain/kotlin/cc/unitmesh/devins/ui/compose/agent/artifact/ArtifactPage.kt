package cc.unitmesh.devins.ui.compose.agent.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.agent.ArtifactAgent
import cc.unitmesh.agent.Platform
import cc.unitmesh.devins.editor.EditorCallbacks
import cc.unitmesh.devins.llm.ChatHistoryManager
import cc.unitmesh.devins.ui.base.ResizableSplitPane
import cc.unitmesh.devins.ui.compose.editor.DevInEditorInput
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors
import cc.unitmesh.devins.workspace.WorkspaceManager
import cc.unitmesh.llm.KoogLLMService
import kotlinx.coroutines.launch

/**
 * ArtifactPage - Page for generating and previewing artifacts
 *
 * Layout:
 * - Left: Chat interface (reuses AgentMessageList and DevInEditorInput)
 * - Right (when artifact generated):
 *   - Top: WebView preview of generated HTML
 *   - Bottom: Console output panel
 *
 * This page follows the same architecture as CodingAgentPage, reusing:
 * - ComposeRenderer for state management
 * - AgentMessageList for message display
 * - DevInEditorInput for user input
 *
 * Supports loading from .unit bundle files for Load-Back functionality.
 */
@Composable
fun ArtifactPage(
    llmService: KoogLLMService?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNotification: (String, String) -> Unit = { _, _ -> },
    chatHistoryManager: ChatHistoryManager? = null,
    /** Optional: Initial bundle to load (for Load-Back support) */
    initialBundle: cc.unitmesh.agent.artifact.ArtifactBundle? = null
) {
    val scope = rememberCoroutineScope()
    val currentWorkspace by WorkspaceManager.workspaceFlow.collectAsState()

    // Create ViewModel following CodingAgentViewModel pattern
    // Use remember without keys to prevent recreation on recomposition
    // The ViewModel manages its own state and should persist across recompositions
    val viewModel = remember {
        ArtifactAgentViewModel(
            llmService = llmService,
            chatHistoryManager = chatHistoryManager
        )
    }

    // State for artifact preview
    var currentArtifact by remember { mutableStateOf<ArtifactAgent.Artifact?>(null) }
    var consoleLogs by remember { mutableStateOf<List<ConsoleLogItem>>(emptyList()) }
    var showPreview by remember { mutableStateOf(false) }

    // Track streaming artifact for real-time preview
    val streamingArtifact = viewModel.streamingArtifact

    // Load initial bundle if provided (for Load-Back support)
    LaunchedEffect(initialBundle) {
        if (initialBundle != null) {
            cc.unitmesh.agent.logging.AutoDevLogger.info("ArtifactPage") { "📦 ArtifactPage received initialBundle: ${initialBundle.name} (id: ${initialBundle.id})" }
            cc.unitmesh.agent.logging.AutoDevLogger.info("ArtifactPage") { "📦 Loading bundle into viewModel..." }
            viewModel.loadFromBundle(initialBundle)
            currentArtifact = viewModel.lastArtifact
            showPreview = currentArtifact != null
            cc.unitmesh.agent.logging.AutoDevLogger.info("ArtifactPage") { "📦 Bundle loaded: artifact=${currentArtifact?.title}, showPreview=$showPreview" }
            onNotification("info", "Loaded artifact: ${initialBundle.name}")
            consoleLogs = appendConsoleLog(
                logs = consoleLogs,
                level = "info",
                message = "Loaded from bundle: ${initialBundle.name}"
            )
        } else {
            cc.unitmesh.agent.logging.AutoDevLogger.info("ArtifactPage") { "📦 ArtifactPage: no initialBundle provided" }
        }
    }

    // Show preview when streaming starts (real-time preview)
    LaunchedEffect(streamingArtifact) {
        if (streamingArtifact != null && !showPreview) {
            showPreview = true
            consoleLogs = appendConsoleLog(
                logs = consoleLogs,
                level = "info",
                message = "Generating: ${streamingArtifact.title}"
            )
        }
    }

    // Listen for completed artifact
    LaunchedEffect(viewModel.lastArtifact) {
        viewModel.lastArtifact?.let { artifact ->
            currentArtifact = artifact
            showPreview = true
            onNotification("success", "Artifact generated: ${artifact.title}")
            consoleLogs = appendConsoleLog(
                logs = consoleLogs,
                level = "info",
                message = "Artifact completed: ${artifact.title}"
            )
        }
    }

    // Derive the artifact to display (streaming or completed)
    // Directly compute without remember to ensure reactive updates when any state changes
    val displayArtifact: ArtifactAgent.Artifact? = currentArtifact ?: viewModel.lastArtifact ?: streamingArtifact?.toArtifact()
    
    // Debug log only when relevant state changes (avoid INFO spam from recompositions)
    LaunchedEffect(
        displayArtifact?.title,
        showPreview,
        currentArtifact?.title,
        viewModel.lastArtifact?.title
    ) {
        cc.unitmesh.agent.logging.AutoDevLogger.debug("ArtifactPage") {
            "📦 [Render] displayArtifact: ${displayArtifact?.title ?: "null"}, showPreview=$showPreview, currentArtifact=${currentArtifact?.title}, viewModel.lastArtifact=${viewModel.lastArtifact?.title}"
        }
    }

    // Check if currently streaming
    val isStreaming = streamingArtifact != null && !streamingArtifact.isComplete

    // Create callbacks for DevInEditorInput
    val callbacks = remember(viewModel) {
        object : EditorCallbacks {
            override fun onSubmit(text: String) {
                viewModel.executeTask(text)
            }
        }
    }

    val isDesktop = Platform.isJvm && !Platform.isAndroid

    // Check if we should show welcome screen
    val hasMessages = viewModel.renderer.timeline.isNotEmpty() || 
                      viewModel.renderer.currentStreamingOutput.isNotEmpty()

    Row(modifier = modifier.fillMaxSize()) {
        // Left panel: Chat interface (reuses existing components)
        Column(
            modifier = Modifier
                .weight(if (showPreview) 0.4f else 1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Top bar
            ArtifactTopBar(
                onBack = onBack,
                showPreview = showPreview,
                onTogglePreview = { showPreview = !showPreview },
                hasArtifact = displayArtifact != null,
                isStreaming = isStreaming,
                onClear = {
                    viewModel.clearMessages()
                    currentArtifact = null
                    showPreview = false
                    consoleLogs = emptyList()
                },
                onExport = currentArtifact?.let { artifact ->
                    { onExportArtifact(artifact, viewModel, onNotification) }
                }
            )

            // Content area: Welcome or Chat messages
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (!hasMessages && !viewModel.isExecuting) {
                    // Show welcome screen with scenarios
                    ArtifactWelcome(
                        onSelectScenario = { prompt ->
                            viewModel.executeTask(prompt)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Custom artifact message list with compact code display
                    ArtifactMessageList(
                        renderer = viewModel.renderer,
                        streamingArtifact = streamingArtifact,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Input area - reuses DevInEditorInput
            DevInEditorInput(
                initialText = "",
                placeholder = "Describe what you want to create (e.g., 'Create a todo list app')...",
                callbacks = callbacks,
                completionManager = currentWorkspace?.completionManager,
                isCompactMode = true,
                isExecuting = viewModel.isExecuting,
                onStopClick = { viewModel.cancelTask() },
                renderer = viewModel.renderer,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        // Right panel: Preview (streaming or completed)
        if (showPreview && displayArtifact != null) {
            if (isDesktop) {
                // Desktop: Use resizable split pane
                ResizableSplitPane(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight(),
                    initialSplitRatio = 0.7f,
                    minRatio = 0.3f,
                    maxRatio = 0.9f,
                    first = {
                        // WebView preview with streaming indicator
                        ArtifactPreviewPanelWithStreaming(
                            artifact = displayArtifact,
                            isStreaming = isStreaming,
                            onConsoleLog = { level, message ->
                                consoleLogs = appendConsoleLog(consoleLogs, level, message)
                            },
                            onFixRequest = { art, error ->
                                viewModel.fixArtifact(art, error)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    },
                    second = {
                        // Console output panel
                        ConsolePanel(
                            logs = consoleLogs,
                            onClear = { consoleLogs = emptyList() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            } else {
                // Mobile: Simple vertical layout
                Column(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                ) {
                    ArtifactPreviewPanelWithStreaming(
                        artifact = displayArtifact,
                        isStreaming = isStreaming,
                        onConsoleLog = { level, message ->
                            consoleLogs = appendConsoleLog(consoleLogs, level, message)
                        },
                        onFixRequest = { art, error ->
                            viewModel.fixArtifact(art, error)
                        },
                        modifier = Modifier.weight(0.7f).fillMaxWidth()
                    )
                    ConsolePanel(
                        logs = consoleLogs,
                        onClear = { consoleLogs = emptyList() },
                        modifier = Modifier.weight(0.3f).fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Convert StreamingArtifact to ArtifactAgent.Artifact
 */
private fun StreamingArtifact.toArtifact(): ArtifactAgent.Artifact {
    val type = ArtifactAgent.Artifact.ArtifactType.fromMimeType(this.type)
        ?: ArtifactAgent.Artifact.ArtifactType.HTML
    return ArtifactAgent.Artifact(
        identifier = this.identifier,
        type = type,
        title = this.title,
        content = this.content
    )
}

/**
 * Wrapper component that shows streaming indicator on top of preview
 * with reload functionality when streaming is complete
 */
@Composable
private fun ArtifactPreviewPanelWithStreaming(
    artifact: ArtifactAgent.Artifact,
    isStreaming: Boolean,
    onConsoleLog: (String, String) -> Unit,
    onFixRequest: ((ArtifactAgent.Artifact, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Reload key to force WebView refresh
    var reloadKey by remember { mutableStateOf(0) }
    
    // Track if we've completed streaming (for showing reload button)
    var hasCompletedStreaming by remember { mutableStateOf(false) }
    
    // Update hasCompletedStreaming when streaming finishes
    LaunchedEffect(isStreaming) {
        if (!isStreaming && !hasCompletedStreaming) {
            hasCompletedStreaming = true
        }
    }
    
    // Reset when artifact changes
    LaunchedEffect(artifact.identifier, artifact.content.length) {
        if (isStreaming) {
            hasCompletedStreaming = false
        }
    }

    Box(modifier = modifier) {
        // Use key to force recomposition and reload WebView
        key(reloadKey, artifact.identifier) {
            ArtifactPreviewPanel(
                artifact = artifact,
                onConsoleLog = onConsoleLog,
                onFixRequest = onFixRequest,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top overlay bar
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isStreaming) {
                    // Streaming indicator
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Generating...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Preview status
                    Icon(
                        imageVector = AutoDevComposeIcons.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Reload button - always visible, but more prominent after streaming completes
                IconButton(
                    onClick = {
                        reloadKey++
                        onConsoleLog("info", "Preview reloaded")
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = AutoDevComposeIcons.Refresh,
                        contentDescription = "Reload preview",
                        modifier = Modifier.size(16.dp),
                        tint = if (hasCompletedStreaming && !isStreaming) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Export artifact to file with conversation history
 */
private fun onExportArtifact(
    artifact: ArtifactAgent.Artifact,
    viewModel: ArtifactAgentViewModel,
    onNotification: (String, String) -> Unit
) {
    // Create bundle with conversation history
    val bundle = viewModel.createBundleForExport(artifact)
    // Platform-specific export will be handled by expect/actual
    exportArtifactBundle(bundle, onNotification)
}

/**
 * Platform-specific export function for raw artifact
 */
expect fun exportArtifact(
    artifact: ArtifactAgent.Artifact,
    onNotification: (String, String) -> Unit
)

/**
 * Platform-specific export function for artifact bundle (with conversation history)
 */
expect fun exportArtifactBundle(
    bundle: cc.unitmesh.agent.artifact.ArtifactBundle,
    onNotification: (String, String) -> Unit
)

@Composable
private fun ArtifactTopBar(
    onBack: () -> Unit,
    showPreview: Boolean,
    onTogglePreview: () -> Unit,
    hasArtifact: Boolean,
    isStreaming: Boolean,
    onClear: () -> Unit,
    onExport: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Text(
                    text = "Artifact",
                    style = MaterialTheme.typography.titleMedium
                )
                // Streaming indicator in title bar
                if (isStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Clear button
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear"
                    )
                }

                if (hasArtifact) {
                    // Toggle preview
                    IconButton(onClick = onTogglePreview) {
                        Icon(
                            imageVector = if (showPreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPreview) "Hide Preview" else "Show Preview"
                        )
                    }
                    // Export (only when not streaming)
                    if (!isStreaming) {
                        onExport?.let { export ->
                            IconButton(onClick = export) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Export"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Artifact preview panel - shows WebView with generated HTML
 * This is an expect/actual pattern - JVM implementation uses KCEF
 * 
 * @param artifact The artifact to preview
 * @param onConsoleLog Callback for console log messages
 * @param onFixRequest Callback when user requests to fix a failed artifact (artifact, errorMessage)
 * @param modifier Modifier for the panel
 */
@Composable
expect fun ArtifactPreviewPanel(
    artifact: ArtifactAgent.Artifact,
    onConsoleLog: (String, String) -> Unit,
    onFixRequest: ((ArtifactAgent.Artifact, String) -> Unit)? = null,
    modifier: Modifier = Modifier
)

@Composable
private fun ConsolePanel(
    logs: List<ConsoleLogItem>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(AutoDevColors.Void.surface2)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AutoDevColors.Void.surface1)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = AutoDevColors.Text.secondary
                )
                Text(
                    text = "Console",
                    style = MaterialTheme.typography.labelMedium,
                    color = AutoDevColors.Text.primary
                )
                if (logs.isNotEmpty()) {
                    Text(
                        text = "(${logs.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = AutoDevColors.Text.secondary
                    )
                }
            }

            IconButton(
                onClick = onClear,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear",
                    modifier = Modifier.size(16.dp),
                    tint = AutoDevColors.Text.secondary
                )
            }
        }

        // Log entries
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(logs) { log ->
                ConsoleLogRow(log)
            }
        }
    }
}

@Composable
private fun ConsoleLogRow(log: ConsoleLogItem) {
    val color = when (log.level.lowercase()) {
        "error" -> AutoDevColors.Signal.error
        "warn" -> AutoDevColors.Signal.warn
        "info" -> AutoDevColors.Signal.info
        else -> AutoDevColors.Text.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(AutoDevColors.Void.surface1.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Colored dot like browser console
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )

        // Level badge
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = AutoDevColors.Void.surface2,
            contentColor = color
        ) {
            Text(
                text = log.level.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                ),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Message
        Text(
            text = log.message,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = AutoDevColors.Text.primary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Repeat counter like browser console "×2"
        if (log.count > 1) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = AutoDevColors.Void.surface2,
                contentColor = AutoDevColors.Text.secondary
            ) {
                Text(
                    text = "×${log.count}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

data class ConsoleLogItem(
    val level: String,
    val message: String,
    val timestamp: Long,
    val count: Int = 1
)

/**
 * Append a console log entry and merge consecutive identical entries into one row with a repeat counter.
 */
private fun appendConsoleLog(
    logs: List<ConsoleLogItem>,
    level: String,
    message: String,
    timestamp: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
): List<ConsoleLogItem> {
    val normalizedLevel = level.lowercase()
    val normalizedMessage = message.trim()
    if (normalizedMessage.isBlank()) return logs

    val last = logs.lastOrNull()
    return if (last != null && last.level.lowercase() == normalizedLevel && last.message == normalizedMessage) {
        logs.dropLast(1) + last.copy(count = last.count + 1, timestamp = timestamp)
    } else {
        logs + ConsoleLogItem(
            level = normalizedLevel,
            message = normalizedMessage,
            timestamp = timestamp,
            count = 1
        )
    }
}
