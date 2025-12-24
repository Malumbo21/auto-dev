package cc.unitmesh.devins.ui.compose.agent.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cc.unitmesh.agent.ArtifactAgent

/**
 * WASM implementation of ArtifactPreviewPanel
 * Uses iframe for preview in browser environment
 */
@Composable
actual fun ArtifactPreviewPanel(
    artifact: ArtifactAgent.Artifact,
    onConsoleLog: (String, String) -> Unit,
    modifier: Modifier
) {
    // In WASM browser environment, we could use an iframe
    // For now, show HTML source with a note
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text(
            text = "Preview: ${artifact.title}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "WASM WebView preview coming soon. View HTML source below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // HTML source view
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = artifact.content,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

