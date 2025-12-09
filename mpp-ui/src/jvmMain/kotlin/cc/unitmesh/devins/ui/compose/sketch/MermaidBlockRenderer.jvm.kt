package cc.unitmesh.devins.ui.compose.sketch

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import cc.unitmesh.viewer.web.MermaidFullscreenDialog

/**
 * JVM implementation of PlatformMermaidFullscreenDialog.
 * Uses MermaidFullscreenDialog from mpp-viewer-web for rendering.
 */
@Composable
actual fun PlatformMermaidFullscreenDialog(
    mermaidCode: String,
    isDarkTheme: Boolean,
    backgroundColor: Color,
    onDismiss: () -> Unit
) {
    MermaidFullscreenDialog(
        mermaidCode = mermaidCode,
        isDarkTheme = isDarkTheme,
        backgroundColor = backgroundColor,
        onDismiss = onDismiss
    )
}

