package cc.unitmesh.devins.ui.compose.sketch.letsplot

import cc.unitmesh.yaml.YamlUtils
import kotlinx.serialization.json.Json

/**
 * Parser for PlotDSL configuration from YAML or JSON format.
 *
 * Supports formats:
 * 1. YAML format (preferred for LLM generation - more token efficient)
 * 2. JSON format (for programmatic use)
 * 3. Simple inline format (for quick charts)
 *
 * Example YAML:
 * ```yaml
 * plot:
 *   title: "Monthly Sales"
 *   data:
 *     month: [Jan, Feb, Mar, Apr]
 *     sales: [100, 150, 120, 180]
 *   geom: bar
 *   aes:
 *     x: month
 *     y: sales
 * ```
 */
object PlotParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parse plot configuration from content string
     */
    fun parse(content: String): PlotConfig? {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return null

        return try {
            when {
                trimmed.startsWith("{") -> parseJson(trimmed)
                trimmed.startsWith("plot:") || trimmed.contains("geom:") -> parseYaml(trimmed)
                else -> parseSimpleFormat(trimmed)
            }
        } catch (e: Exception) {
            parseSimpleFormat(trimmed)
        }
    }

    /**
     * Parse JSON format
     */
    private fun parseJson(content: String): PlotConfig? {
        return try {
            json.decodeFromString<PlotConfig>(content)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse YAML format using YamlUtils
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseYaml(content: String): PlotConfig? {
        val yamlMap = try {
            YamlUtils.load(content)
        } catch (e: Exception) {
            return null
        } ?: return null

        // Handle nested "plot:" wrapper
        val plotMap = if (yamlMap.containsKey("plot")) {
            yamlMap["plot"] as? Map<String, Any?> ?: yamlMap
        } else {
            yamlMap
        }

        return parseMapToConfig(plotMap)
    }

    /**
     * Parse Map to PlotConfig
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseMapToConfig(map: Map<String, Any?>): PlotConfig? {
        val title = map["title"] as? String
        val subtitle = map["subtitle"] as? String

        // Parse data
        val dataMap = map["data"] as? Map<String, Any?> ?: return null
        val dataFrame = parseDataFrame(dataMap) ?: return null

        // Parse geom
        val geomStr = (map["geom"] as? String)?.lowercase() ?: "point"
        val geom = parseGeom(geomStr)

        // Parse aesthetics
        val aesMap = map["aes"] as? Map<String, Any?>
        val aes = aesMap?.let { parseAesthetics(it) }

        // Parse theme
        val themeStr = (map["theme"] as? String)?.lowercase() ?: "default"
        val theme = parseTheme(themeStr)

        // Parse dimensions
        val width = (map["width"] as? Number)?.toInt()
        val height = (map["height"] as? Number)?.toInt()

        // Parse labels
        val xLabel = map["xLabel"] as? String ?: map["x_label"] as? String
        val yLabel = map["yLabel"] as? String ?: map["y_label"] as? String

        // Parse color scale
        val colorScaleMap = map["colorScale"] as? Map<String, Any?> 
            ?: map["color_scale"] as? Map<String, Any?>
        val colorScale = colorScaleMap?.let { parseColorScale(it) }

        // Parse facet
        val facetMap = map["facet"] as? Map<String, Any?>
        val facet = facetMap?.let { parseFacet(it) }

        // Parse layers
        val layersList = map["layers"] as? List<Map<String, Any?>>
        val layers = layersList?.mapNotNull { parseLayer(it) }

        return PlotConfig(
            title = title,
            subtitle = subtitle,
            data = dataFrame,
            geom = geom,
            aes = aes,
            theme = theme,
            width = width,
            height = height,
            xLabel = xLabel,
            yLabel = yLabel,
            colorScale = colorScale,
            facet = facet,
            layers = layers
        )
    }

    /**
     * Parse data frame from Map
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseDataFrame(dataMap: Map<String, Any?>): PlotDataFrame? {
        val columns = mutableMapOf<String, List<PlotValue>>()

        for ((key, value) in dataMap) {
            val values = when (value) {
                is List<*> -> value.mapNotNull { parseValue(it) }
                is String -> {
                    // Handle comma-separated or bracket-enclosed lists
                    val cleaned = value.trim().removeSurrounding("[", "]")
                    cleaned.split(",").map { it.trim() }.mapNotNull { parseValue(it) }
                }
                else -> continue
            }
            if (values.isNotEmpty()) {
                columns[key] = values
            }
        }

        return if (columns.isNotEmpty()) PlotDataFrame(columns) else null
    }

    /**
     * Parse a single value
     */
    private fun parseValue(value: Any?): PlotValue? {
        return when (value) {
            is Number -> PlotValue.Number(value.toDouble())
            is String -> {
                val trimmed = value.trim()
                trimmed.toDoubleOrNull()?.let { PlotValue.Number(it) }
                    ?: PlotValue.Text(trimmed)
            }
            else -> null
        }
    }

    /**
     * Parse geom type
     */
    private fun parseGeom(geomStr: String): PlotGeom {
        return when (geomStr) {
            "point", "scatter" -> PlotGeom.POINT
            "line" -> PlotGeom.LINE
            "bar", "column" -> PlotGeom.BAR
            "histogram", "hist" -> PlotGeom.HISTOGRAM
            "boxplot", "box" -> PlotGeom.BOXPLOT
            "area" -> PlotGeom.AREA
            "density" -> PlotGeom.DENSITY
            "heatmap", "heat" -> PlotGeom.HEATMAP
            "pie" -> PlotGeom.PIE
            else -> PlotGeom.POINT
        }
    }

    /**
     * Parse theme type
     */
    private fun parseTheme(themeStr: String): PlotTheme {
        return when (themeStr) {
            "minimal", "min" -> PlotTheme.MINIMAL
            "classic" -> PlotTheme.CLASSIC
            "dark" -> PlotTheme.DARK
            "light" -> PlotTheme.LIGHT
            "void", "none" -> PlotTheme.VOID
            else -> PlotTheme.DEFAULT
        }
    }

    /**
     * Parse aesthetics from Map
     */
    private fun parseAesthetics(aesMap: Map<String, Any?>): PlotAesthetics {
        return PlotAesthetics(
            x = aesMap["x"] as? String,
            y = aesMap["y"] as? String,
            color = aesMap["color"] as? String ?: aesMap["colour"] as? String,
            fill = aesMap["fill"] as? String,
            size = aesMap["size"] as? String,
            shape = aesMap["shape"] as? String,
            alpha = aesMap["alpha"] as? String,
            group = aesMap["group"] as? String,
            label = aesMap["label"] as? String,
            weight = aesMap["weight"] as? String
        )
    }

    /**
     * Parse color scale from Map
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseColorScale(scaleMap: Map<String, Any?>): ColorScale {
        val typeStr = (scaleMap["type"] as? String)?.lowercase() ?: "discrete"
        val type = when (typeStr) {
            "continuous" -> ColorScaleType.CONTINUOUS
            "gradient" -> ColorScaleType.GRADIENT
            else -> ColorScaleType.DISCRETE
        }

        val colors = (scaleMap["colors"] as? List<*>)?.mapNotNull { it as? String }

        return ColorScale(
            type = type,
            palette = scaleMap["palette"] as? String,
            colors = colors,
            low = scaleMap["low"] as? String,
            high = scaleMap["high"] as? String
        )
    }

    /**
     * Parse facet from Map
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseFacet(facetMap: Map<String, Any?>): PlotFacet? {
        val typeStr = (facetMap["type"] as? String)?.lowercase() ?: "wrap"
        val type = if (typeStr == "grid") FacetType.GRID else FacetType.WRAP

        val facets = when (val f = facetMap["facets"]) {
            is List<*> -> f.mapNotNull { it as? String }
            is String -> listOf(f)
            else -> return null
        }

        if (facets.isEmpty()) return null

        return PlotFacet(
            type = type,
            facets = facets,
            ncol = (facetMap["ncol"] as? Number)?.toInt(),
            nrow = (facetMap["nrow"] as? Number)?.toInt()
        )
    }

    /**
     * Parse layer from Map
     */
    private fun parseLayer(layerMap: Map<String, Any?>): PlotLayer? {
        val geomStr = (layerMap["geom"] as? String)?.lowercase() ?: return null
        val geom = parseGeom(geomStr)

        @Suppress("UNCHECKED_CAST")
        val aesMap = layerMap["aes"] as? Map<String, Any?>
        val aes = aesMap?.let { parseAesthetics(it) }

        @Suppress("UNCHECKED_CAST")
        val dataMap = layerMap["data"] as? Map<String, Any?>
        val data = dataMap?.let { parseDataFrame(it) }

        @Suppress("UNCHECKED_CAST")
        val params = (layerMap["params"] as? Map<String, Any?>)?.mapNotNull { (k, v) ->
            v?.toString()?.let { k to it }
        }?.toMap()

        return PlotLayer(
            geom = geom,
            aes = aes,
            data = data,
            stat = layerMap["stat"] as? String,
            position = layerMap["position"] as? String,
            params = params
        )
    }

    /**
     * Parse simple inline format for quick charts
     *
     * Format:
     * ```
     * bar: Sales by Month
     * Jan: 100
     * Feb: 150
     * Mar: 120
     * ```
     */
    private fun parseSimpleFormat(content: String): PlotConfig? {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val firstLine = lines.first()
        val (geomStr, title) = parseFirstLine(firstLine)
        val geom = parseGeom(geomStr)

        val dataLines = if (firstLine.contains(":")) {
            lines.drop(1)
        } else {
            lines.drop(1)
        }

        if (dataLines.isEmpty()) return null

        // Parse data lines as key: value pairs
        val labels = mutableListOf<String>()
        val values = mutableListOf<Double>()

        for (line in dataLines) {
            val parts = line.split(":").map { it.trim() }
            if (parts.size >= 2) {
                labels.add(parts[0])
                parts[1].toDoubleOrNull()?.let { values.add(it) }
            }
        }

        if (labels.isEmpty() || values.isEmpty()) return null

        val dataFrame = PlotDataFrame(
            columns = mapOf(
                "x" to labels.map { PlotValue.Text(it) },
                "y" to values.map { PlotValue.Number(it) }
            )
        )

        return PlotConfig(
            title = title,
            data = dataFrame,
            geom = geom,
            aes = PlotAesthetics(x = "x", y = "y")
        )
    }

    /**
     * Parse first line to extract geom type and optional title
     */
    private fun parseFirstLine(line: String): Pair<String, String?> {
        val parts = line.split(":").map { it.trim() }
        return when {
            parts.size >= 2 -> parts[0].lowercase() to parts[1].takeIf { it.isNotBlank() }
            else -> parts[0].lowercase() to null
        }
    }
}

