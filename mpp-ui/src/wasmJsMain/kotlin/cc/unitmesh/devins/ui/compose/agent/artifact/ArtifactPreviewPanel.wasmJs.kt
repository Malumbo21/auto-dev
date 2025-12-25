package cc.unitmesh.devins.ui.compose.agent.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.agent.ArtifactAgent

/**
 * WASM/JS implementation of ArtifactPreviewPanel.
 * Shows source code view as WebView is not available in WASM.
 */
@Composable
actual fun ArtifactPreviewPanel(
    artifact: ArtifactAgent.Artifact,
    onConsoleLog: (String, String) -> Unit,
    modifier: Modifier
) {
    Column(modifier = modifier) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
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
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = artifact.title,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text(
                    text = "Source View",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Source code
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = artifact.content,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Export artifact implementation for WASM/JS
 */
actual fun exportArtifact(
    artifact: ArtifactAgent.Artifact,
    onNotification: (String, String) -> Unit
) {
    // WASM export is limited
    onNotification("info", "Export not available in WASM mode")
}

/**
 * Export artifact bundle implementation for WASM/JS
 */
actual fun exportArtifactBundle(
    bundle: cc.unitmesh.agent.artifact.ArtifactBundle,
    onNotification: (String, String) -> Unit
) {
    // WASM export is limited
    onNotification("info", "Bundle export not available in WASM mode")
}
