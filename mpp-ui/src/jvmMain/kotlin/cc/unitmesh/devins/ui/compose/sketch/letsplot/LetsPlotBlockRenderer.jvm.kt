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
import org.jetbrains.letsPlot.Figure
import org.jetbrains.letsPlot.Stat
import org.jetbrains.letsPlot.geom.*
import org.jetbrains.letsPlot.ggsize
import org.jetbrains.letsPlot.intern.Plot
import org.jetbrains.letsPlot.label.ggtitle
import org.jetbrains.letsPlot.label.labs
import org.jetbrains.letsPlot.letsPlot
import org.jetbrains.letsPlot.scale.*
import org.jetbrains.letsPlot.compose.PlotPanel
import org.jetbrains.letsPlot.themes.*

/**
 * JVM implementation of LetsPlotBlockRenderer using Lets-Plot Compose.
 *
 * This implementation provides full statistical plotting capabilities on:
 * - macOS
 * - Windows
 * - Linux
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
        // Title row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = plotConfig?.title ?: "Plot",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = plotConfig?.geom?.name ?: "Lets-Plot",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (plotConfig != null && isComplete) {
            RenderLetsPlot(plotConfig)
        } else if (!isComplete) {
            // Show loading state while streaming
            RenderLoading()
        } else {
            // Fallback: show raw code
            RenderFallback(plotCode)
        }
    }
}

@Composable
private fun RenderLetsPlot(config: PlotConfig) {
    val plotHeight = (config.height ?: 300).dp
    val plotWidth = config.width?.dp

    val figure = remember(config) {
        try {
            buildLetsPlotFigure(config)
        } catch (e: Exception) {
            null
        }
    }

    if (figure != null) {
        Box(
            modifier = if (plotWidth != null) {
                Modifier.width(plotWidth).height(plotHeight)
            } else {
                Modifier.fillMaxWidth().height(plotHeight)
            }
        ) {
            PlotPanel(
                figure = figure,
                modifier = Modifier.fillMaxSize()
            ) { /* computationMessagesHandler - ignore messages */ }
        }
    } else {
        RenderFallback("Failed to render plot")
    }
}

/**
 * Build Lets-Plot Figure from PlotConfig
 */
private fun buildLetsPlotFigure(config: PlotConfig): Figure {
    val data = config.data.toMap()
    val aes = config.aes

    // Start with base plot
    var plot: Plot = letsPlot(data)

    // Add geometry layer
    plot = when (config.geom) {
        PlotGeom.POINT, PlotGeom.SCATTER -> {
            plot + geomPoint {
                aes?.x?.let { x = it }
                aes?.y?.let { y = it }
                aes?.color?.let { color = it }
                aes?.size?.let { size = it }
                aes?.shape?.let { shape = it }
                aes?.alpha?.let { alpha = it }
            }
        }
        PlotGeom.LINE -> {
            plot + geomLine {
                aes?.x?.let { x = it }
                aes?.y?.let { y = it }
                aes?.color?.let { color = it }
                aes?.group?.let { group = it }
            }
        }
        PlotGeom.BAR -> {
            plot + geomBar(stat = Stat.identity) {
                aes?.x?.let { x = it }
                aes?.y?.let { y = it }
                aes?.fill?.let { fill = it }
                aes?.color?.let { color = it }
            }
        }
        PlotGeom.HISTOGRAM -> {
            plot + geomHistogram {
                aes?.x?.let { x = it }
                aes?.fill?.let { fill = it }
                aes?.color?.let { color = it }
            }
        }
        PlotGeom.BOXPLOT -> {
            plot + geomBoxplot {
                aes?.x?.let { x = it }
                aes?.y?.let { y = it }
                aes?.fill?.let { fill = it }
                aes?.color?.let { color = it }
            }
        }
        PlotGeom.AREA -> {
            plot + geomArea {
                aes?.x?.let { x = it }
                aes?.y?.let { y = it }
                aes?.fill?.let { fill = it }
                aes?.group?.let { group = it }
            }
        }
        PlotGeom.DENSITY -> {
            plot + geomDensity {
                aes?.x?.let { x = it }
                aes?.fill?.let { fill = it }
                aes?.color?.let { color = it }
            }
        }
        PlotGeom.HEATMAP -> {
            plot + geomTile {
                aes?.x?.let { x = it }
                aes?.y?.let { y = it }
                aes?.fill?.let { fill = it }
            }
        }
        PlotGeom.PIE -> {
            // Lets-Plot doesn't have native pie, use bar with coord_polar
            plot + geomBar(stat = Stat.identity) {
                aes?.x?.let { x = it }
                aes?.y?.let { y = it }
                aes?.fill?.let { fill = it }
            }
        }
    }

    // Add additional layers if present
    config.layers?.forEach { layer ->
        val layerData = layer.data?.toMap() ?: data
        val layerAes = layer.aes ?: aes

        plot = when (layer.geom) {
            PlotGeom.POINT, PlotGeom.SCATTER -> {
                plot + geomPoint(data = layerData) {
                    layerAes?.x?.let { x = it }
                    layerAes?.y?.let { y = it }
                    layerAes?.color?.let { color = it }
                }
            }
            PlotGeom.LINE -> {
                plot + geomLine(data = layerData) {
                    layerAes?.x?.let { x = it }
                    layerAes?.y?.let { y = it }
                    layerAes?.color?.let { color = it }
                }
            }
            else -> plot
        }
    }

    // Add title and labels
    config.title?.let { plot = plot + ggtitle(it, config.subtitle) }
    if (config.xLabel != null || config.yLabel != null) {
        plot = plot + labs(x = config.xLabel, y = config.yLabel)
    }

    // Add theme
    plot = plot + when (config.theme) {
        PlotTheme.MINIMAL -> themeMinimal()
        PlotTheme.CLASSIC -> themeClassic()
        PlotTheme.DARK -> themeBW() // Closest to dark
        PlotTheme.LIGHT -> themeLight()
        PlotTheme.VOID -> themeVoid()
        PlotTheme.DEFAULT -> themeGrey()
    }

    // Add size
    val width = config.width ?: 400
    val height = config.height ?: 300
    plot = plot + ggsize(width, height)

    // Add color scale if specified
    config.colorScale?.let { scale ->
        plot = when (scale.type) {
            ColorScaleType.DISCRETE -> {
                scale.colors?.let { colors ->
                    plot + scaleFillManual(values = colors)
                } ?: plot
            }
            ColorScaleType.CONTINUOUS, ColorScaleType.GRADIENT -> {
                if (scale.low != null && scale.high != null) {
                    plot + scaleFillGradient(low = scale.low, high = scale.high)
                } else {
                    plot
                }
            }
        }
    }

    return plot
}

@Composable
private fun RenderLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(AutoDevColors.Void.bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Loading plot...",
            style = MaterialTheme.typography.bodyMedium,
            color = AutoDevColors.Text.secondary
        )
    }
}

@Composable
private fun RenderFallback(plotCode: String) {
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

/**
 * JVM platforms support Lets-Plot
 */
actual fun isLetsPlotAvailable(): Boolean = true

