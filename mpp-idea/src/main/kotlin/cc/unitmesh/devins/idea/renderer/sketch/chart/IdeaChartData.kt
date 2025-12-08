package cc.unitmesh.devins.idea.renderer.sketch.chart

/**
 * Chart type enumeration
 */
enum class ChartType {
    PIE,
    LINE,
    COLUMN,
    ROW
}

/**
 * Base chart configuration
 */
data class ChartConfig(
    val type: ChartType,
    val title: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val data: ChartDataContent
)

/**
 * Chart data content - polymorphic based on chart type
 */
sealed class ChartDataContent {
    data class PieData(
        val items: List<PieItem>,
        val style: PieStyle = PieStyle.FILL
    ) : ChartDataContent()

    data class LineData(
        val lines: List<LineItem>,
        val showDots: Boolean = true,
        val curvedEdges: Boolean = true,
        val minValue: Double? = null,
        val maxValue: Double? = null
    ) : ChartDataContent()

    data class ColumnData(
        val bars: List<BarGroup>,
        val minValue: Double? = null,
        val maxValue: Double? = null
    ) : ChartDataContent()

    data class RowData(
        val bars: List<BarGroup>,
        val minValue: Double? = null,
        val maxValue: Double? = null
    ) : ChartDataContent()
}

data class PieItem(
    val label: String,
    val value: Double,
    val color: String? = null
)

enum class PieStyle {
    FILL,
    STROKE
}

data class LineItem(
    val label: String,
    val values: List<Double>,
    val color: String? = null
)

data class BarGroup(
    val label: String,
    val values: List<BarValue>
)

data class BarValue(
    val label: String? = null,
    val value: Double,
    val color: String? = null
)

