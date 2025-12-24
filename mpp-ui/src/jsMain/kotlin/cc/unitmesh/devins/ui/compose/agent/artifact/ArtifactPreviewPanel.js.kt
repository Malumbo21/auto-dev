package cc.unitmesh.devins.ui.compose.agent.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.agent.ArtifactAgent
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLIFrameElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

/**
 * JavaScript implementation of ArtifactPreviewPanel.
 * Uses an iframe to render HTML content in the browser.
 */
@Composable
actual fun ArtifactPreviewPanel(
    artifact: ArtifactAgent.Artifact,
    onConsoleLog: (String, String) -> Unit,
    modifier: Modifier
) {
    var showSource by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = artifact.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { showSource = !showSource },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = if (showSource) "Show Preview" else "Show Source",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { openInNewTab(artifact.content) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Open in New Tab",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Content
        if (showSource) {
            // Source view
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = artifact.content,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Preview using iframe
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Preview available in browser. Click 'Open in New Tab' to view.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Open HTML content in a new browser tab
 */
private fun openInNewTab(html: String) {
    try {
        val blob = Blob(arrayOf(html), BlobPropertyBag(type = "text/html"))
        val url = URL.createObjectURL(blob)
        window.open(url, "_blank")
    } catch (e: Exception) {
        println("Failed to open in new tab: ${e.message}")
    }
}

/**
 * Export artifact implementation for JS
 */
actual fun exportArtifact(
    artifact: ArtifactAgent.Artifact,
    onNotification: (String, String) -> Unit
) {
    try {
        val blob = Blob(arrayOf(artifact.content), BlobPropertyBag(type = "text/html"))
        val url = URL.createObjectURL(blob)

        val link = document.createElement("a") as org.w3c.dom.HTMLAnchorElement
        link.href = url
        link.download = "${artifact.title.replace(" ", "_")}.html"
        link.click()

        URL.revokeObjectURL(url)
        onNotification("success", "Artifact downloaded")
    } catch (e: Exception) {
        onNotification("error", "Failed to export: ${e.message}")
    }
}
