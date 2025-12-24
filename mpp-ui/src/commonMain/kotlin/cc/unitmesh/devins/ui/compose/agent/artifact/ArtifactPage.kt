package cc.unitmesh.devins.ui.compose.agent.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import cc.unitmesh.devins.ui.compose.agent.AgentMessageList
import cc.unitmesh.devins.ui.compose.editor.DevInEditorInput
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
 */
@Composable
fun ArtifactPage(
    llmService: KoogLLMService?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNotification: (String, String) -> Unit = { _, _ -> },
    chatHistoryManager: ChatHistoryManager? = null
) {
    val scope = rememberCoroutineScope()
    val currentWorkspace by WorkspaceManager.workspaceFlow.collectAsState()

    // Create ViewModel following CodingAgentViewModel pattern
    val viewModel = remember(llmService, chatHistoryManager) {
        ArtifactAgentViewModel(
            llmService = llmService,
            chatHistoryManager = chatHistoryManager
        )
    }

    // State for artifact preview
    var currentArtifact by remember { mutableStateOf<ArtifactAgent.Artifact?>(null) }
    var consoleLogs by remember { mutableStateOf<List<ConsoleLogItem>>(emptyList()) }
    var showPreview by remember { mutableStateOf(false) }
    var lastStreamingTitle by remember { mutableStateOf<String?>(null) }

    // Track streaming artifact for real-time preview (read as state to trigger recomposition)
    val streamingArtifact = viewModel.streamingArtifact

    // Derive the artifact to display (completed has priority over streaming)
    // Use derivedStateOf to correctly track state changes
    val displayArtifact by remember {
        derivedStateOf {
            currentArtifact ?: viewModel.streamingArtifact?.toArtifact()
        }
    }

    // Check if currently streaming
    val isStreaming by remember {
        derivedStateOf {
            val streaming = viewModel.streamingArtifact
            streaming != null && !streaming.isComplete
        }
    }

    // Show preview when streaming starts (real-time preview)
    LaunchedEffect(streamingArtifact?.identifier) {
        val streaming = streamingArtifact
        if (streaming != null && !showPreview) {
            showPreview = true
        }
        // Log when a new artifact starts generating (by title change)
        if (streaming != null && streaming.title != lastStreamingTitle) {
            lastStreamingTitle = streaming.title
            consoleLogs = consoleLogs + ConsoleLogItem(
                level = "info",
                message = "Generating: ${streaming.title}",
                timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    // Listen for completed artifact
    LaunchedEffect(viewModel.lastArtifact) {
        viewModel.lastArtifact?.let { artifact ->
            currentArtifact = artifact
            showPreview = true
            onNotification("success", "Artifact generated: ${artifact.title}")
            consoleLogs = consoleLogs + ConsoleLogItem(
                level = "info",
                message = "Artifact completed: ${artifact.title}",
                timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    // Create callbacks for DevInEditorInput
    val callbacks = remember(viewModel) {
        object : EditorCallbacks {
            override fun onSubmit(text: String) {
                viewModel.executeTask(text)
            }
        }
    }

    val isDesktop = Platform.isJvm && !Platform.isAndroid

    // Local variable for artifact to allow smart cast
    val artifact = displayArtifact

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
                hasArtifact = artifact != null,
                isStreaming = isStreaming,
                onClear = {
                    viewModel.clearMessages()
                    currentArtifact = null
                    showPreview = false
                    consoleLogs = emptyList()
                },
                onExport = currentArtifact?.let { artifact ->
                    { onExportArtifact(artifact, onNotification) }
                }
            )

            // Chat message list - reuses AgentMessageList from CodingAgentPage
            AgentMessageList(
                renderer = viewModel.renderer,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onOpenFileViewer = null // Artifact mode doesn't need file viewer
            )

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
        if (showPreview && artifact != null) {
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
                            artifact = artifact,
                            isStreaming = isStreaming,
                            onConsoleLog = { level, message ->
                                consoleLogs = consoleLogs + ConsoleLogItem(
                                    level = level,
                                    message = message,
                                    timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                                )
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
                        artifact = artifact,
                        isStreaming = isStreaming,
                        onConsoleLog = { level, message ->
                            consoleLogs = consoleLogs + ConsoleLogItem(
                                level = level,
                                message = message,
                                timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                            )
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
 * Wrapper component that shows streaming code view during generation,
 * then switches to preview when complete.
 */
@Composable
private fun ArtifactPreviewPanelWithStreaming(
    artifact: ArtifactAgent.Artifact,
    isStreaming: Boolean,
    onConsoleLog: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isStreaming) {
        // During streaming: Show live source code with auto-scroll
        StreamingCodeView(
            content = artifact.content,
            title = artifact.title,
            modifier = modifier
        )
    } else {
        // After completion: Show full preview panel
        ArtifactPreviewPanel(
            artifact = artifact,
            onConsoleLog = onConsoleLog,
            modifier = modifier
        )
    }
}

/**
 * Streaming code view with live updates and auto-scroll
 * Similar to ThinkingBlockRenderer - shows content as it's generated
 */
@Composable
private fun StreamingCodeView(
    content: String,
    title: String,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var userHasScrolled by remember { mutableStateOf(false) }

    // Track if user manually scrolled away from bottom
    LaunchedEffect(scrollState.value, scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            val isAtBottom = scrollState.value >= scrollState.maxValue - 10
            if (!isAtBottom && scrollState.isScrollInProgress) {
                userHasScrolled = true
            } else if (isAtBottom) {
                userHasScrolled = false
            }
        }
    }

    // Auto-scroll to bottom during streaming
    LaunchedEffect(content) {
        if (!userHasScrolled && content.isNotBlank()) {
            kotlinx.coroutines.delay(16)
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Column(modifier = modifier) {
        // Header with streaming indicator
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Generating...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Streaming code content with auto-scroll
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = AutoDevColors.Void.surface2
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(scrollState)
                    .padding(12.dp)
            ) {
                Text(
                    text = content.ifEmpty { "Waiting for content..." },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    color = if (content.isEmpty())
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else
                        AutoDevColors.Text.primary
                )
            }
        }
    }
}

/**
 * Export artifact to file
 */
private fun onExportArtifact(
    artifact: ArtifactAgent.Artifact,
    onNotification: (String, String) -> Unit
) {
    // Platform-specific export will be handled by expect/actual
    exportArtifact(artifact, onNotification)
}

/**
 * Platform-specific export function
 */
expect fun exportArtifact(
    artifact: ArtifactAgent.Artifact,
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
 */
@Composable
expect fun ArtifactPreviewPanel(
    artifact: ArtifactAgent.Artifact,
    onConsoleLog: (String, String) -> Unit,
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
    val color = when (log.level) {
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "[${log.level.uppercase()}]",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            ),
            color = color
        )
        Text(
            text = log.message,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = AutoDevColors.Text.primary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

data class ConsoleLogItem(
    val level: String,
    val message: String,
    val timestamp: Long
)
