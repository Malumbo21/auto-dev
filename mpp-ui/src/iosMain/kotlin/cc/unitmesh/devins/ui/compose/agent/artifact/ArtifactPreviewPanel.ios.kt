package cc.unitmesh.devins.ui.compose.agent.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cc.unitmesh.agent.ArtifactAgent

/**
 * iOS implementation of ArtifactPreviewPanel
 * Shows HTML source code since WebView integration requires UIKit
 */
@Composable
actual fun ArtifactPreviewPanel(
    artifact: ArtifactAgent.Artifact,
    onConsoleLog: (String, String) -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        // Header
        Text(
            text = "Preview: ${artifact.title}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "WebView preview not available on iOS (Compose). Use Safari to test.",
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

