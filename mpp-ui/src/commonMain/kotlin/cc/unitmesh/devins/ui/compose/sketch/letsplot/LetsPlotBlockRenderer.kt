package cc.unitmesh.devins.ui.compose.sketch.letsplot

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * LetsPlot Block Renderer - Cross-platform component for rendering PlotDSL code blocks.
 *
 * Platform support:
 * - JVM Desktop (macOS, Windows, Linux): Full Lets-Plot Compose rendering
 * - Android: Full Lets-Plot Compose rendering
 * - iOS/JS/WASM: Fallback to syntax-highlighted code display
 *
 * This component renders statistical plots using the ggplot2-inspired PlotDSL syntax.
 * It supports various chart types including scatter plots, line charts, bar charts,
 * histograms, box plots, and more.
 *
 * Usage in SketchRenderer:
 * ```kotlin
 * "plotdsl", "letsplot", "ggplot" -> {
 *     LetsPlotBlockRenderer(
 *         plotCode = fence.text,
 *         isComplete = blockIsComplete,
 *         modifier = Modifier.fillMaxWidth()
 *     )
 * }
 * ```
 *
 * Example PlotDSL:
 * ```plotdsl
 * plot:
 *   title: "Sales by Region"
 *   data:
 *     region: [North, South, East, West]
 *     sales: [120, 98, 150, 87]
 *   geom: bar
 *   aes:
 *     x: region
 *     y: sales
 *     fill: region
 *   theme: minimal
 * ```
 *
 * @param plotCode The PlotDSL source code to render
 * @param isComplete Whether the code block streaming is complete
 * @param modifier Compose modifier for the component
 */
@Composable
expect fun LetsPlotBlockRenderer(
    plotCode: String,
    isComplete: Boolean = true,
    modifier: Modifier = Modifier
)

/**
 * Check if Lets-Plot is available on this platform.
 * Returns true for JVM Desktop and Android, false otherwise.
 */
expect fun isLetsPlotAvailable(): Boolean

