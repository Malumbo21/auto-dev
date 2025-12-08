package cc.unitmesh.devins.ui.compose.sketch.chart

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.json.Json

/**
 * Parser for chart configuration from YAML or JSON format
 */
object ChartParser {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false
        )
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parse chart configuration from content string
     * Supports both YAML and JSON formats
     */
    fun parse(content: String): ChartConfig? {
        val trimmed = content.trim()
        return try {
            if (trimmed.startsWith("{")) {
                // JSON format
                json.decodeFromString<ChartConfig>(trimmed)
            } else {
                // YAML format
                yaml.decodeFromString(ChartConfig.serializer(), trimmed)
            }
        } catch (e: Exception) {
            // Try to parse as simple format
            parseSimpleFormat(trimmed)
        }
    }

    /**
     * Parse simple DSL format for quick chart creation
     * Format:
     * ```
     * pie:
     *   - Label1: 30
     *   - Label2: 50
     *   - Label3: 20
     * ```
     */
    private fun parseSimpleFormat(content: String): ChartConfig? {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val firstLine = lines.first().lowercase()

        return when {
            firstLine.startsWith("pie") -> parseSimplePie(lines.drop(1))
            firstLine.startsWith("line") -> parseSimpleLine(lines.drop(1))
            firstLine.startsWith("column") || firstLine.startsWith("bar") -> parseSimpleColumn(lines.drop(1))
            firstLine.startsWith("row") -> parseSimpleRow(lines.drop(1))
            else -> null
        }
    }

    private fun parseSimplePie(lines: List<String>): ChartConfig? {
        val items = lines.mapNotNull { line ->
            val parts = line.removePrefix("-").trim().split(":")
            if (parts.size >= 2) {
                val label = parts[0].trim()
                val value = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
                PieItem(label = label, value = value)
            } else null
        }

        if (items.isEmpty()) return null

        return ChartConfig(
            type = ChartType.PIE,
            data = ChartDataContent.PieData(items = items)
        )
    }

    private fun parseSimpleLine(lines: List<String>): ChartConfig? {
        val lineItems = lines.mapNotNull { line ->
            val parts = line.removePrefix("-").trim().split(":")
            if (parts.size >= 2) {
                val label = parts[0].trim()
                val values = parts[1].split(",").mapNotNull { it.trim().toDoubleOrNull() }
                if (values.isNotEmpty()) LineItem(label = label, values = values) else null
            } else null
        }

        if (lineItems.isEmpty()) return null

        return ChartConfig(
            type = ChartType.LINE,
            data = ChartDataContent.LineData(lines = lineItems)
        )
    }

    private fun parseSimpleColumn(lines: List<String>): ChartConfig? {
        val bars = lines.mapNotNull { line ->
            val parts = line.removePrefix("-").trim().split(":")
            if (parts.size >= 2) {
                val label = parts[0].trim()
                val value = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
                BarGroup(label = label, values = listOf(BarValue(value = value)))
            } else null
        }

        if (bars.isEmpty()) return null

        return ChartConfig(
            type = ChartType.COLUMN,
            data = ChartDataContent.ColumnData(bars = bars)
        )
    }

    private fun parseSimpleRow(lines: List<String>): ChartConfig? {
        val bars = lines.mapNotNull { line ->
            val parts = line.removePrefix("-").trim().split(":")
            if (parts.size >= 2) {
                val label = parts[0].trim()
                val value = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
                BarGroup(label = label, values = listOf(BarValue(value = value)))
            } else null
        }

        if (bars.isEmpty()) return null

        return ChartConfig(
            type = ChartType.ROW,
            data = ChartDataContent.RowData(bars = bars)
        )
    }
}

