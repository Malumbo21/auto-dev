package cc.unitmesh.devins.ui.compose.sketch.letsplot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors

/**
 * WASM fallback implementation of LetsPlotBlockRenderer.
 *
 * Lets-Plot is not available on WASM, so this displays the PlotDSL code
 * with syntax highlighting as a fallback.
 */
@Composable
actual fun LetsPlotBlockRenderer(
    plotCode: String,
    isComplete: Boolean,
    modifier: Modifier
) {
    val plotConfig = remember(plotCode) { PlotParser.parse(plotCode) }

    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        // Title row with platform notice
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = plotConfig?.title ?: "Plot (Preview Only)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "WASM - Code View",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Info message
        Text(
            text = "Lets-Plot is not available on WASM. Showing PlotDSL code.",
            style = MaterialTheme.typography.bodySmall,
            color = AutoDevColors.Text.secondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Code display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AutoDevColors.Void.bg)
                .padding(12.dp)
        ) {
            Text(
                text = plotCode,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                color = AutoDevColors.Text.primary
            )
        }
    }
}

/**
 * WASM does not support Lets-Plot
 */
actual fun isLetsPlotAvailable(): Boolean = false

