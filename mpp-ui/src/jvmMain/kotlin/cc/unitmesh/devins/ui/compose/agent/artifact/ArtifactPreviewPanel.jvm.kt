package cc.unitmesh.devins.ui.compose.agent.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.agent.ArtifactAgent
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File

/**
 * JVM implementation of ArtifactPreviewPanel
 * 
 * Current implementation shows HTML source with an "Open in Browser" button.
 * WebView integration via KCEF can be added when the desktop app properly initializes KCEF.
 */
@Composable
actual fun ArtifactPreviewPanel(
    artifact: ArtifactAgent.Artifact,
    onConsoleLog: (String, String) -> Unit,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    var tempFile by remember { mutableStateOf<File?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }

    // Create temp file for browser preview
    LaunchedEffect(artifact.content) {
        try {
            val file = File.createTempFile("artifact-${artifact.identifier}-", ".html")
            file.writeText(artifact.content)
            file.deleteOnExit()
            tempFile = file
            onConsoleLog("info", "Artifact loaded: ${artifact.title} (${artifact.content.length} bytes)")
        } catch (e: Exception) {
            openError = "Failed to create preview: ${e.message}"
            onConsoleLog("error", "Failed to create temp file: ${e.message}")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AutoDevColors.Void.surface1)
    ) {
        // Header with title and actions
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AutoDevColors.Void.surface2,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = AutoDevColors.Energy.xiu
                    )
                    Text(
                        text = artifact.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = AutoDevColors.Text.primary
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = AutoDevColors.Energy.xiuDim
                    ) {
                        Text(
                            text = artifact.type.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = AutoDevColors.Energy.xiu,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Open in browser button
                    FilledTonalIconButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    tempFile?.let { file ->
                                        if (Desktop.isDesktopSupported()) {
                                            Desktop.getDesktop().browse(file.toURI())
                                            onConsoleLog("info", "Opened in browser: ${file.name}")
                                        } else {
                                            openError = "Desktop not supported on this system"
                                        }
                                    }
                                } catch (e: Exception) {
                                    openError = "Failed to open browser: ${e.message}"
                                    onConsoleLog("error", openError!!)
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Open in Browser",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Save file button
                    FilledTonalIconButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val saveFile = File("${artifact.identifier}.html")
                                    saveFile.writeText(artifact.content)
                                    onConsoleLog("info", "Saved to: ${saveFile.absolutePath}")
                                } catch (e: Exception) {
                                    onConsoleLog("error", "Failed to save: ${e.message}")
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Error message if any
        if (openError != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                color = AutoDevColors.Signal.errorBg,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = openError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = AutoDevColors.Signal.error,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        // Info banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = AutoDevColors.Signal.infoBg,
            shape = RoundedCornerShape(4.dp)
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = AutoDevColors.Signal.info
                )
                Text(
                    text = "Click 'Open in Browser' to preview the artifact interactively",
                    style = MaterialTheme.typography.bodySmall,
                    color = AutoDevColors.Signal.info
                )
            }
        }

        // HTML source code viewer
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .background(AutoDevColors.Void.surface2, RoundedCornerShape(8.dp))
        ) {
            val verticalScroll = rememberScrollState()
            val horizontalScroll = rememberScrollState()
            
            SelectionContainer {
                Text(
                    text = artifact.content,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    color = AutoDevColors.Text.primary,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScroll)
                        .horizontalScroll(horizontalScroll)
                        .padding(12.dp)
                )
            }
        }

        // Stats footer
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AutoDevColors.Void.surface2
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ID: ${artifact.identifier}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AutoDevColors.Text.tertiary
                )
                Text(
                    text = "${artifact.content.length} chars | ${artifact.content.lines().size} lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = AutoDevColors.Text.tertiary
                )
            }
        }
    }
}
