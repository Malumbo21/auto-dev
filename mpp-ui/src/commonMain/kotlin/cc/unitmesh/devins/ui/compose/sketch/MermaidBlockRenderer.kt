package cc.unitmesh.devins.ui.compose.sketch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors

/**
 * Platform-specific Mermaid fullscreen dialog.
 * Implemented in jvmMain and wasmJsMain using MermaidFullscreenDialog from mpp-viewer-web.
 * Other platforms show a placeholder or no-op.
 */
@Composable
expect fun PlatformMermaidFullscreenDialog(
    mermaidCode: String,
    isDarkTheme: Boolean,
    backgroundColor: Color,
    onDismiss: () -> Unit
)

/**
 * Mermaid diagram block renderer
 *
 * Displays mermaid code with a clickable "Open in Viewer" button
 * that opens a fullscreen dialog for rendering the diagram.
 */
@Composable
fun MermaidBlockRenderer(
    mermaidCode: String,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true
) {
    var showFullscreenViewer by remember { mutableStateOf(false) }

    val backgroundColor = if (isDarkTheme) {
        Color(0xFF171717)
    } else {
        Color(0xFFfafafa)
    }

    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Mermaid Diagram",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Open in Viewer",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showFullscreenViewer = true }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Display the code in a code block style
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AutoDevColors.Void.bg)
                .padding(12.dp)
        ) {
            Text(
                text = mermaidCode,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                color = AutoDevColors.Text.primary
            )
        }
    }

    // Fullscreen Viewer Dialog
    if (showFullscreenViewer) {
        PlatformMermaidFullscreenDialog(
            mermaidCode = mermaidCode,
            isDarkTheme = isDarkTheme,
            backgroundColor = backgroundColor,
            onDismiss = { showFullscreenViewer = false }
        )
    }
}


