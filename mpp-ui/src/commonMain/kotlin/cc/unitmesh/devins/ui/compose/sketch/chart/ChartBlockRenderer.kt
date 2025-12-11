package cc.unitmesh.devins.ui.compose.sketch.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Chart block renderer for displaying various chart types.
 * Uses ComposeCharts library for cross-platform chart rendering on supported platforms.
 * Falls back to code display on platforms without chart support (JS).
 */
@Composable
expect fun ChartBlockRenderer(
    chartCode: String,
    modifier: Modifier = Modifier
)

/**
 * Check if chart rendering is available on the current platform
 */
expect fun isChartAvailable(): Boolean

