package cc.unitmesh.devins.ui.compose.sketch.chart

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Chart type enumeration
 */
@Serializable
enum class ChartType {
    @SerialName("pie")
    PIE,
    @SerialName("line")
    LINE,
    @SerialName("column")
    COLUMN,
    @SerialName("row")
    ROW
}

/**
 * Base chart configuration
 */
@Serializable
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
@Serializable
sealed class ChartDataContent {
    @Serializable
    @SerialName("pie")
    data class PieData(
        val items: List<PieItem>,
        val style: PieStyle = PieStyle.FILL
    ) : ChartDataContent()

    @Serializable
    @SerialName("line")
    data class LineData(
        val lines: List<LineItem>,
        val showDots: Boolean = true,
        val curvedEdges: Boolean = true,
        val minValue: Double? = null,
        val maxValue: Double? = null
    ) : ChartDataContent()

    @Serializable
    @SerialName("column")
    data class ColumnData(
        val bars: List<BarGroup>,
        val minValue: Double? = null,
        val maxValue: Double? = null
    ) : ChartDataContent()

    @Serializable
    @SerialName("row")
    data class RowData(
        val bars: List<BarGroup>,
        val minValue: Double? = null,
        val maxValue: Double? = null
    ) : ChartDataContent()
}

/**
 * Pie chart item
 */
@Serializable
data class PieItem(
    val label: String,
    val value: Double,
    val color: String? = null
)

/**
 * Pie chart style
 */
@Serializable
enum class PieStyle {
    @SerialName("fill")
    FILL,
    @SerialName("stroke")
    STROKE
}

/**
 * Line chart item (a single line)
 */
@Serializable
data class LineItem(
    val label: String,
    val values: List<Double>,
    val color: String? = null
)

/**
 * Bar group for column/row charts
 */
@Serializable
data class BarGroup(
    val label: String,
    val values: List<BarValue>
)

/**
 * Individual bar value
 */
@Serializable
data class BarValue(
    val label: String? = null,
    val value: Double,
    val color: String? = null
)

