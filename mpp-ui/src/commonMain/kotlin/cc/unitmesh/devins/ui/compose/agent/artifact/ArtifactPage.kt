package cc.unitmesh.devins.ui.compose.agent.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import cc.unitmesh.devins.ui.base.VerticalResizableSplitPane
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors
import cc.unitmesh.llm.KoogLLMService
import kotlinx.coroutines.launch

/**
 * ArtifactPage - Page for generating and previewing artifacts
 *
 * Layout:
 * - Left: Chat interface for artifact generation
 * - Right (when artifact generated):
 *   - Top: WebView preview of generated HTML
 *   - Bottom: Console output panel
 */
@Composable
fun ArtifactPage(
    llmService: KoogLLMService?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNotification: (String, String) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()

    // State
    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var currentArtifact by remember { mutableStateOf<ArtifactAgent.Artifact?>(null) }
    var responseText by remember { mutableStateOf("") }
    var consoleLogs by remember { mutableStateOf<List<ConsoleLogItem>>(emptyList()) }
    var showPreview by remember { mutableStateOf(false) }

    // Agent instance
    val artifactAgent = remember(llmService) {
        llmService?.let { ArtifactAgent(it) }
    }

    fun handleGenerate() {
        if (inputText.isBlank() || isGenerating || artifactAgent == null) return

        scope.launch {
            isGenerating = true
            responseText = ""
            consoleLogs = emptyList()

            try {
                val result = artifactAgent.generate(inputText) { chunk ->
                    responseText += chunk
                }

                if (result.success && result.artifacts.isNotEmpty()) {
                    currentArtifact = result.artifacts.first()
                    showPreview = true
                    onNotification("success", "Artifact generated: ${result.artifacts.first().title}")

                    // Add initial console log
                    consoleLogs = consoleLogs + ConsoleLogItem(
                        level = "info",
                        message = "Artifact loaded: ${result.artifacts.first().title}",
                        timestamp = System.currentTimeMillis()
                    )
                } else if (result.error != null) {
                    onNotification("error", result.error)
                }
            } catch (e: Exception) {
                onNotification("error", "Generation failed: ${e.message}")
            } finally {
                isGenerating = false
            }
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        // Left panel: Chat interface
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
                hasArtifact = currentArtifact != null
            )

            // Response area
            if (responseText.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp),
                    state = rememberLazyListState()
                ) {
                    item {
                        Text(
                            text = responseText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            } else {
                // Welcome message
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "AutoDev Artifact",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Generate interactive HTML/JS artifacts",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        
                        // Quick prompts
                        Column(
                            modifier = Modifier.padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickPromptChip("Create a todo list app") {
                                inputText = "Create a todo list app with add, delete, and local storage"
                            }
                            QuickPromptChip("Build a calculator") {
                                inputText = "Create a calculator widget with basic operations"
                            }
                            QuickPromptChip("Make a dashboard") {
                                inputText = "Create an analytics dashboard with stat cards and a chart"
                            }
                        }
                    }
                }
            }

            // Input area
            ArtifactInput(
                text = inputText,
                onTextChange = { inputText = it },
                onSubmit = { handleGenerate() },
                isGenerating = isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            )
        }

        // Right panel: Preview (when artifact is generated)
        if (showPreview && currentArtifact != null) {
            VerticalResizableSplitPane(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight(),
                initialSplitRatio = 0.7f,
                minRatio = 0.3f,
                maxRatio = 0.9f,
                first = {
                    // WebView preview (expect/actual pattern - platform specific)
                    ArtifactPreviewPanel(
                        artifact = currentArtifact!!,
                        onConsoleLog = { level, message ->
                            consoleLogs = consoleLogs + ConsoleLogItem(
                                level = level,
                                message = message,
                                timestamp = System.currentTimeMillis()
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
        }
    }
}

@Composable
private fun ArtifactTopBar(
    onBack: () -> Unit,
    showPreview: Boolean,
    onTogglePreview: () -> Unit,
    hasArtifact: Boolean
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
            }

            if (hasArtifact) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onTogglePreview) {
                        Icon(
                            imageVector = if (showPreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPreview) "Hide Preview" else "Show Preview"
                        )
                    }
                    IconButton(onClick = { /* TODO: Export */ }) {
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

@Composable
private fun ArtifactInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isGenerating: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Describe what you want to create...") },
                maxLines = 4,
                enabled = !isGenerating,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )

            FilledIconButton(
                onClick = onSubmit,
                enabled = text.isNotBlank() && !isGenerating
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Generate"
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPromptChip(
    text: String,
    onClick: () -> Unit
) {
    SuggestionChip(
        onClick = onClick,
        label = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    )
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
            .background(AutoDevColors.Dark.codeBackground)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AutoDevColors.Dark.surface)
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
                    tint = AutoDevColors.Dark.textSecondary
                )
                Text(
                    text = "Console",
                    style = MaterialTheme.typography.labelMedium,
                    color = AutoDevColors.Dark.textPrimary
                )
                if (logs.isNotEmpty()) {
                    Text(
                        text = "(${logs.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = AutoDevColors.Dark.textSecondary
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
                    tint = AutoDevColors.Dark.textSecondary
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
        "error" -> AutoDevColors.Dark.error
        "warn" -> AutoDevColors.Dark.warning
        "info" -> AutoDevColors.Dark.info
        else -> AutoDevColors.Dark.textPrimary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(AutoDevColors.Dark.surface.copy(alpha = 0.5f))
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
            color = AutoDevColors.Dark.textPrimary,
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

