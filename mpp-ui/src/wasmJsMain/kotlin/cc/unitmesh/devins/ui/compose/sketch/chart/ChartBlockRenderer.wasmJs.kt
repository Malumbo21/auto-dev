package cc.unitmesh.devins.ui.compose.sketch.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors
import ir.ehsannarmani.compose_charts.ColumnChart
import ir.ehsannarmani.compose_charts.LineChart
import ir.ehsannarmani.compose_charts.PieChart
import ir.ehsannarmani.compose_charts.RowChart
import ir.ehsannarmani.compose_charts.models.Bars
import ir.ehsannarmani.compose_charts.models.Line
import ir.ehsannarmani.compose_charts.models.Pie

/**
 * WasmJS implementation of ChartBlockRenderer using ComposeCharts library.
 */
@Composable
actual fun ChartBlockRenderer(
    chartCode: String,
    modifier: Modifier
) {
    val chartConfig = remember(chartCode) { ChartParser.parse(chartCode) }

    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = chartConfig?.title ?: "Chart",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = chartConfig?.type?.name ?: "Unknown",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (chartConfig != null) {
            RenderChart(chartConfig)
        } else {
            RenderFallback(chartCode)
        }
    }
}

@Composable
private fun RenderChart(config: ChartConfig) {
    val chartHeight = (config.height ?: 200).dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(chartHeight)
            .padding(8.dp)
    ) {
        when (val data = config.data) {
            is ChartDataContent.PieData -> RenderPieChart(data)
            is ChartDataContent.LineData -> RenderLineChart(data)
            is ChartDataContent.ColumnData -> RenderColumnChart(data)
            is ChartDataContent.RowData -> RenderRowChart(data)
        }
    }
}

@Composable
private fun RenderPieChart(data: ChartDataContent.PieData) {
    val pieData = remember(data) {
        data.items.mapIndexed { index, item ->
            Pie(
                label = item.label,
                data = item.value,
                color = parseColor(item.color) ?: getDefaultColor(index),
                selectedColor = parseColor(item.color)?.copy(alpha = 0.8f)
                    ?: getDefaultColor(index).copy(alpha = 0.8f)
            )
        }
    }

    PieChart(
        modifier = Modifier.fillMaxSize(),
        data = pieData,
        style = if (data.style == PieStyle.STROKE) {
            Pie.Style.Stroke(width = 40.dp)
        } else {
            Pie.Style.Fill
        }
    )
}

@Composable
private fun RenderLineChart(data: ChartDataContent.LineData) {
    val lineData = remember(data) {
        data.lines.mapIndexed { index, item ->
            val color = parseColor(item.color) ?: getDefaultColor(index)
            Line(
                label = item.label,
                values = item.values,
                color = SolidColor(color),
                curvedEdges = data.curvedEdges,
                firstGradientFillColor = color.copy(alpha = 0.5f),
                secondGradientFillColor = Color.Transparent
            )
        }
    }

    val calculatedMaxValue = data.maxValue ?: lineData.flatMap { it.values }.maxOrNull() ?: 100.0

    LineChart(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        data = lineData,
        minValue = data.minValue ?: 0.0,
        maxValue = calculatedMaxValue
    )
}

@Composable
private fun RenderColumnChart(data: ChartDataContent.ColumnData) {
    val barsData = remember(data) {
        data.bars.map { group ->
            Bars(
                label = group.label,
                values = group.values.mapIndexed { index, barValue ->
                    Bars.Data(
                        label = barValue.label,
                        value = barValue.value,
                        color = SolidColor(parseColor(barValue.color) ?: getDefaultColor(index))
                    )
                }
            )
        }
    }

    val calculatedMaxValue = data.maxValue
        ?: barsData.flatMap { it.values }.maxOfOrNull { it.value } ?: 100.0

    ColumnChart(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        data = barsData,
        minValue = data.minValue ?: 0.0,
        maxValue = calculatedMaxValue
    )
}

@Composable
private fun RenderRowChart(data: ChartDataContent.RowData) {
    val barsData = remember(data) {
        data.bars.map { group ->
            Bars(
                label = group.label,
                values = group.values.mapIndexed { index, barValue ->
                    Bars.Data(
                        label = barValue.label,
                        value = barValue.value,
                        color = SolidColor(parseColor(barValue.color) ?: getDefaultColor(index))
                    )
                }
            )
        }
    }

    val calculatedMaxValue = data.maxValue
        ?: barsData.flatMap { it.values }.maxOfOrNull { it.value } ?: 100.0

    RowChart(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        data = barsData,
        minValue = data.minValue ?: 0.0,
        maxValue = calculatedMaxValue
    )
}

@Composable
private fun RenderFallback(chartCode: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AutoDevColors.Void.bg)
            .padding(12.dp)
    ) {
        Text(
            text = chartCode,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            color = AutoDevColors.Text.primary
        )
    }
}

private fun parseColor(colorStr: String?): Color? {
    if (colorStr == null) return null

    return try {
        when {
            colorStr.startsWith("#") -> {
                val hex = colorStr.removePrefix("#")
                when (hex.length) {
                    6 -> Color(("FF$hex").toLong(16))
                    8 -> Color(hex.toLong(16))
                    else -> null
                }
            }
            else -> getNamedColor(colorStr.lowercase())
        }
    } catch (e: Exception) {
        null
    }
}

private fun getNamedColor(name: String): Color? {
    return when (name) {
        "red" -> Color(0xFFE53935)
        "green" -> Color(0xFF43A047)
        "blue" -> Color(0xFF1E88E5)
        "yellow" -> Color(0xFFFDD835)
        "orange" -> Color(0xFFFB8C00)
        "purple" -> Color(0xFF8E24AA)
        "cyan" -> Color(0xFF00ACC1)
        "pink" -> Color(0xFFD81B60)
        "gray", "grey" -> Color(0xFF757575)
        "teal" -> Color(0xFF00897B)
        "indigo" -> Color(0xFF3949AB)
        "lime" -> Color(0xFFC0CA33)
        else -> null
    }
}

private val defaultColors = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00), Color(0xFFE53935),
    Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFFFDD835), Color(0xFFD81B60),
    Color(0xFF00897B), Color(0xFF3949AB)
)

private fun getDefaultColor(index: Int): Color = defaultColors[index % defaultColors.size]

actual fun isChartAvailable(): Boolean = true

