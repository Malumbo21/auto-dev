package cc.unitmesh.devins.idea.renderer.sketch.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Chart renderer for IntelliJ IDEA using Jewel theme and Compose Canvas API.
 * Note: ComposeCharts library cannot be used due to ClassLoader conflicts with IntelliJ's Compose runtime.
 */
@Composable
fun IdeaChartRenderer(
    chartCode: String,
    modifier: Modifier = Modifier
) {
    val chartConfig = remember(chartCode) { IdeaChartParser.parse(chartCode) }

    Column(
        modifier = modifier
            .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
            .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        // Title row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = chartConfig?.title ?: "Chart",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp),
                color = JewelTheme.globalColors.text.normal
            )
            Text(
                text = chartConfig?.type?.name ?: "Unknown",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp),
                color = JewelTheme.globalColors.text.info
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (chartConfig != null) {
            RenderChart(chartConfig)
            // Legend
            RenderLegend(chartConfig)
        } else {
            RenderFallback(chartCode)
        }
    }
}

@Composable
private fun RenderChart(config: ChartConfig) {
    val chartHeight = (config.height ?: 180).dp

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
    val total = data.items.sumOf { it.value }
    val colors = data.items.mapIndexed { index, item ->
        parseColor(item.color) ?: getDefaultColor(index)
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val diameter = min(size.width, size.height) * 0.8f
        val radius = diameter / 2
        val center = Offset(size.width / 2, size.height / 2)
        var startAngle = -90f

        data.items.forEachIndexed { index, item ->
            val sweepAngle = (item.value / total * 360).toFloat()
            if (data.style == PieStyle.STROKE) {
                drawArc(
                    color = colors[index],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(diameter, diameter),
                    style = Stroke(width = 30f)
                )
            } else {
                drawArc(
                    color = colors[index],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(diameter, diameter)
                )
            }
            startAngle += sweepAngle
        }
    }
}

@Composable
private fun RenderLineChart(data: ChartDataContent.LineData) {
    val allValues = data.lines.flatMap { it.values }
    val minVal = data.minValue ?: (allValues.minOrNull() ?: 0.0)
    val maxVal = data.maxValue ?: (allValues.maxOrNull() ?: 100.0)
    val range = if (maxVal > minVal) maxVal - minVal else 1.0

    Canvas(modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 24.dp)) {
        val chartWidth = size.width
        val chartHeight = size.height

        // Draw grid lines
        drawGridLines(chartWidth, chartHeight)

        // Draw each line
        data.lines.forEachIndexed { lineIndex, lineItem ->
            val color = parseColor(lineItem.color) ?: getDefaultColor(lineIndex)
            val points = lineItem.values.mapIndexed { index, value ->
                val x = if (lineItem.values.size > 1) {
                    index * chartWidth / (lineItem.values.size - 1)
                } else chartWidth / 2
                val y = chartHeight - ((value - minVal) / range * chartHeight).toFloat()
                Offset(x, y)
            }

            // Draw line path
            if (points.size >= 2) {
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
                drawPath(path, color, style = Stroke(width = 3f))
            }

            // Draw dots
            if (data.showDots) {
                points.forEach { point ->
                    drawCircle(color, radius = 5f, center = point)
                }
            }
        }
    }
}

@Composable
private fun RenderColumnChart(data: ChartDataContent.ColumnData) {
    val allValues = data.bars.flatMap { it.values.map { v -> v.value } }
    val minVal = data.minValue ?: 0.0
    val maxVal = data.maxValue ?: (allValues.maxOrNull() ?: 100.0)
    val range = if (maxVal > minVal) maxVal - minVal else 1.0

    Canvas(modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 24.dp)) {
        val chartWidth = size.width
        val chartHeight = size.height
        val barCount = data.bars.size
        if (barCount == 0) return@Canvas

        drawGridLines(chartWidth, chartHeight)

        val groupWidth = chartWidth / barCount
        val barPadding = groupWidth * 0.2f
        val maxValuesPerGroup = data.bars.maxOfOrNull { it.values.size } ?: 1
        val barWidth = (groupWidth - barPadding * 2) / maxValuesPerGroup.coerceAtLeast(1)

        data.bars.forEachIndexed { groupIndex, group ->
            val groupX = groupIndex * groupWidth + barPadding
            group.values.forEachIndexed { valueIndex, barValue ->
                val color = parseColor(barValue.color) ?: getDefaultColor(valueIndex)
                val barHeight = ((barValue.value - minVal) / range * chartHeight).toFloat()
                val x = groupX + valueIndex * barWidth
                val y = chartHeight - barHeight
                drawRect(color, topLeft = Offset(x, y), size = Size(barWidth * 0.8f, barHeight))
            }
        }
    }
}

@Composable
private fun RenderRowChart(data: ChartDataContent.RowData) {
    val allValues = data.bars.flatMap { it.values.map { v -> v.value } }
    val minVal = data.minValue ?: 0.0
    val maxVal = data.maxValue ?: (allValues.maxOrNull() ?: 100.0)
    val range = if (maxVal > minVal) maxVal - minVal else 1.0

    Canvas(modifier = Modifier.fillMaxSize().padding(start = 60.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
        val chartWidth = size.width
        val chartHeight = size.height
        val barCount = data.bars.size
        if (barCount == 0) return@Canvas

        val groupHeight = chartHeight / barCount
        val barPadding = groupHeight * 0.2f
        val maxValuesPerGroup = data.bars.maxOfOrNull { it.values.size } ?: 1
        val barHeight = (groupHeight - barPadding * 2) / maxValuesPerGroup.coerceAtLeast(1)

        data.bars.forEachIndexed { groupIndex, group ->
            val groupY = groupIndex * groupHeight + barPadding
            group.values.forEachIndexed { valueIndex, barValue ->
                val color = parseColor(barValue.color) ?: getDefaultColor(valueIndex)
                val barWidth = ((barValue.value - minVal) / range * chartWidth).toFloat()
                val y = groupY + valueIndex * barHeight
                drawRect(color, topLeft = Offset(0f, y), size = Size(barWidth, barHeight * 0.8f))
            }
        }
    }
}

private fun DrawScope.drawGridLines(chartWidth: Float, chartHeight: Float) {
    val gridColor = Color.Gray.copy(alpha = 0.3f)
    val gridCount = 4
    for (i in 0..gridCount) {
        val y = i * chartHeight / gridCount
        drawLine(gridColor, Offset(0f, y), Offset(chartWidth, y), strokeWidth = 1f)
    }
}

@Composable
private fun RenderLegend(config: ChartConfig) {
    val items = when (val data = config.data) {
        is ChartDataContent.PieData -> data.items.mapIndexed { i, it -> it.label to (parseColor(it.color) ?: getDefaultColor(i)) }
        is ChartDataContent.LineData -> data.lines.mapIndexed { i, it -> it.label to (parseColor(it.color) ?: getDefaultColor(i)) }
        is ChartDataContent.ColumnData -> data.bars.firstOrNull()?.values?.mapIndexed { i, it -> (it.label ?: "Value ${i+1}") to (parseColor(it.color) ?: getDefaultColor(i)) } ?: emptyList()
        is ChartDataContent.RowData -> data.bars.firstOrNull()?.values?.mapIndexed { i, it -> (it.label ?: "Value ${i+1}") to (parseColor(it.color) ?: getDefaultColor(i)) } ?: emptyList()
    }

    if (items.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.take(6).forEach { (label, color) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 9.sp),
                        color = JewelTheme.globalColors.text.info
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderFallback(chartCode: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Text(
            text = chartCode,
            style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
            color = JewelTheme.globalColors.text.normal
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

private fun getNamedColor(name: String): Color? = when (name) {
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

private val defaultColors = listOf(
    Color(0xFF1E88E5), // Blue
    Color(0xFF43A047), // Green
    Color(0xFFFB8C00), // Orange
    Color(0xFFE53935), // Red
    Color(0xFF8E24AA), // Purple
    Color(0xFF00ACC1), // Cyan
    Color(0xFFFDD835), // Yellow
    Color(0xFFD81B60), // Pink
    Color(0xFF00897B), // Teal
    Color(0xFF3949AB), // Indigo
)

private fun getDefaultColor(index: Int): Color = defaultColors[index % defaultColors.size]

