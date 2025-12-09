package cc.unitmesh.devins.ui.compose.sketch

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * JS implementation of PlatformMermaidFullscreenDialog.
 * No-op for JS platform as it uses TypeScript/React for rendering.
 */
@Composable
actual fun PlatformMermaidFullscreenDialog(
    mermaidCode: String,
    isDarkTheme: Boolean,
    backgroundColor: Color,
    onDismiss: () -> Unit
) {
    // No-op for JS platform
    // The JS/TypeScript implementation handles Mermaid rendering separately
}

