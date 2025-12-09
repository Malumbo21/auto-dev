package cc.unitmesh.devins.ui.compose.sketch.letsplot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PlotDSL - A ggplot2-inspired DSL for statistical data visualization.
 *
 * This DSL is designed to be:
 * 1. Token-efficient for LLM generation
 * 2. Compatible with Lets-Plot Compose on Desktop/Android
 * 3. Fallback-friendly for JS/WASM/iOS platforms
 *
 * Example DSL:
 * ```plotdsl
 * plot:
 *   title: "Sales by Region"
 *   data:
 *     x: [Q1, Q2, Q3, Q4]
 *     y: [100, 150, 120, 180]
 *     group: [North, North, South, South]
 *   geom: bar
 *   aes:
 *     x: x
 *     y: y
 *     fill: group
 *   theme: minimal
 * ```
 */

/**
 * Plot type enumeration - corresponds to ggplot2 geoms
 */
@Serializable
enum class PlotGeom {
    @SerialName("point")
    POINT,
    @SerialName("line")
    LINE,
    @SerialName("bar")
    BAR,
    @SerialName("histogram")
    HISTOGRAM,
    @SerialName("boxplot")
    BOXPLOT,
    @SerialName("scatter")
    SCATTER,
    @SerialName("area")
    AREA,
    @SerialName("density")
    DENSITY,
    @SerialName("heatmap")
    HEATMAP,
    @SerialName("pie")
    PIE
}

/**
 * Plot theme enumeration
 */
@Serializable
enum class PlotTheme {
    @SerialName("default")
    DEFAULT,
    @SerialName("minimal")
    MINIMAL,
    @SerialName("classic")
    CLASSIC,
    @SerialName("dark")
    DARK,
    @SerialName("light")
    LIGHT,
    @SerialName("void")
    VOID
}

/**
 * Main plot configuration
 */
@Serializable
data class PlotConfig(
    val title: String? = null,
    val subtitle: String? = null,
    val data: PlotDataFrame,
    val geom: PlotGeom = PlotGeom.POINT,
    val aes: PlotAesthetics? = null,
    val theme: PlotTheme = PlotTheme.DEFAULT,
    val width: Int? = null,
    val height: Int? = null,
    val xLabel: String? = null,
    val yLabel: String? = null,
    val colorScale: ColorScale? = null,
    val facet: PlotFacet? = null,
    val layers: List<PlotLayer>? = null
)

/**
 * Data frame representation for plots
 * Supports columnar data format
 */
@Serializable
data class PlotDataFrame(
    val columns: Map<String, List<PlotValue>> = emptyMap()
) {
    /**
     * Get column as list of doubles (for numeric data)
     */
    fun getNumericColumn(name: String): List<Double>? {
        return columns[name]?.mapNotNull { 
            when (it) {
                is PlotValue.Number -> it.value
                is PlotValue.Text -> it.value.toDoubleOrNull()
            }
        }
    }
    
    /**
     * Get column as list of strings
     */
    fun getStringColumn(name: String): List<String>? {
        return columns[name]?.map { 
            when (it) {
                is PlotValue.Number -> it.value.toString()
                is PlotValue.Text -> it.value
            }
        }
    }

    /**
     * Convert to Map<String, List<Any>> for Lets-Plot
     */
    fun toMap(): Map<String, List<Any>> {
        return columns.mapValues { (_, values) ->
            values.map { 
                when (it) {
                    is PlotValue.Number -> it.value
                    is PlotValue.Text -> it.value
                }
            }
        }
    }
}

/**
 * Plot value - can be numeric or text
 */
@Serializable
sealed class PlotValue {
    @Serializable
    @SerialName("number")
    data class Number(val value: Double) : PlotValue()
    
    @Serializable
    @SerialName("text")
    data class Text(val value: String) : PlotValue()
}

/**
 * Aesthetic mappings (aes in ggplot2)
 */
@Serializable
data class PlotAesthetics(
    val x: String? = null,
    val y: String? = null,
    val color: String? = null,
    val fill: String? = null,
    val size: String? = null,
    val shape: String? = null,
    val alpha: String? = null,
    val group: String? = null,
    val label: String? = null,
    val weight: String? = null
)

/**
 * Color scale configuration
 */
@Serializable
data class ColorScale(
    val type: ColorScaleType = ColorScaleType.DISCRETE,
    val palette: String? = null,
    val colors: List<String>? = null,
    val low: String? = null,
    val high: String? = null
)

/**
 * Color scale type
 */
@Serializable
enum class ColorScaleType {
    @SerialName("discrete")
    DISCRETE,
    @SerialName("continuous")
    CONTINUOUS,
    @SerialName("gradient")
    GRADIENT
}

/**
 * Facet configuration for multi-panel plots
 */
@Serializable
data class PlotFacet(
    val type: FacetType = FacetType.WRAP,
    val facets: List<String>,
    val ncol: Int? = null,
    val nrow: Int? = null
)

/**
 * Facet type
 */
@Serializable
enum class FacetType {
    @SerialName("wrap")
    WRAP,
    @SerialName("grid")
    GRID
}

/**
 * Additional plot layer (for layered plots)
 */
@Serializable
data class PlotLayer(
    val geom: PlotGeom,
    val aes: PlotAesthetics? = null,
    val data: PlotDataFrame? = null,
    val stat: String? = null,
    val position: String? = null,
    val params: Map<String, String>? = null
)

