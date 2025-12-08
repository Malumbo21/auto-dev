package cc.unitmesh.devins.idea.renderer.sketch.chart

import cc.unitmesh.yaml.YamlUtils

/**
 * Parser for chart configuration from YAML format.
 * Uses YamlUtils for YAML parsing to handle ChartAgent generated formats.
 */
object IdeaChartParser {

    /**
     * Parse chart configuration from content string
     * Supports YAML format
     */
    fun parse(content: String): ChartConfig? {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return null

        return try {
            parseYamlWithUtils(trimmed)
        } catch (e: Exception) {
            parseSimpleFormat(trimmed)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseYamlWithUtils(content: String): ChartConfig? {
        val yamlMap = try {
            YamlUtils.load(content)
        } catch (e: Exception) {
            return parseSimpleFormat(content)
        } ?: return parseSimpleFormat(content)

        val typeStr = (yamlMap["type"] as? String)?.lowercase() ?: return parseSimpleFormat(content)
        val title = yamlMap["title"] as? String
        val dataMap = yamlMap["data"] as? Map<String, Any?> ?: return parseSimpleFormat(content)

        return when (typeStr) {
            "pie" -> parsePieFromMap(dataMap, title)
            "line" -> parseLineFromMap(dataMap, title)
            "column", "bar" -> parseColumnFromMap(dataMap, title)
            "row" -> parseRowFromMap(dataMap, title)
            else -> parseSimpleFormat(content)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parsePieFromMap(dataMap: Map<String, Any?>, title: String?): ChartConfig? {
        val itemsList = dataMap["items"] as? List<Map<String, Any?>> ?: return null
        val items = itemsList.mapNotNull { itemMap ->
            val label = itemMap["label"] as? String ?: return@mapNotNull null
            val value = (itemMap["value"] as? Number)?.toDouble() ?: return@mapNotNull null
            val color = itemMap["color"] as? String
            PieItem(label = label, value = value, color = color)
        }
        if (items.isEmpty()) return null
        return ChartConfig(type = ChartType.PIE, title = title, data = ChartDataContent.PieData(items = items))
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseLineFromMap(dataMap: Map<String, Any?>, title: String?): ChartConfig? {
        val linesList = dataMap["lines"] as? List<Map<String, Any?>> ?: return null
        val lines = linesList.mapNotNull { lineMap ->
            val label = lineMap["label"] as? String ?: return@mapNotNull null
            val valuesList = lineMap["values"] as? List<*> ?: return@mapNotNull null
            val values = valuesList.mapNotNull { (it as? Number)?.toDouble() }
            val color = lineMap["color"] as? String
            if (values.isEmpty()) return@mapNotNull null
            LineItem(label = label, values = values, color = color)
        }
        if (lines.isEmpty()) return null
        return ChartConfig(type = ChartType.LINE, title = title, data = ChartDataContent.LineData(lines = lines))
    }

    private fun parseColumnFromMap(dataMap: Map<String, Any?>, title: String?): ChartConfig? {
        val bars = parseBarsFromMap(dataMap)
        if (bars.isEmpty()) return null
        return ChartConfig(type = ChartType.COLUMN, title = title, data = ChartDataContent.ColumnData(bars = bars))
    }

    private fun parseRowFromMap(dataMap: Map<String, Any?>, title: String?): ChartConfig? {
        val bars = parseBarsFromMap(dataMap)
        if (bars.isEmpty()) return null
        return ChartConfig(type = ChartType.ROW, title = title, data = ChartDataContent.RowData(bars = bars))
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseBarsFromMap(dataMap: Map<String, Any?>): List<BarGroup> {
        val barsList = dataMap["bars"] as? List<Map<String, Any?>> ?: return emptyList()
        return barsList.mapNotNull { barMap ->
            val label = barMap["label"] as? String ?: return@mapNotNull null
            val valuesList = barMap["values"] as? List<Map<String, Any?>> ?: return@mapNotNull null
            val values = valuesList.mapNotNull { valueMap ->
                val value = (valueMap["value"] as? Number)?.toDouble() ?: return@mapNotNull null
                val color = valueMap["color"] as? String
                val valueLabel = valueMap["label"] as? String
                BarValue(label = valueLabel, value = value, color = color)
            }
            if (values.isEmpty()) return@mapNotNull null
            BarGroup(label = label, values = values)
        }
    }

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
        return ChartConfig(type = ChartType.PIE, data = ChartDataContent.PieData(items = items))
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
        return ChartConfig(type = ChartType.LINE, data = ChartDataContent.LineData(lines = lineItems))
    }

    private fun parseSimpleColumn(lines: List<String>) = parseSimpleBars(lines, ChartType.COLUMN)
    private fun parseSimpleRow(lines: List<String>) = parseSimpleBars(lines, ChartType.ROW)

    private fun parseSimpleBars(lines: List<String>, type: ChartType): ChartConfig? {
        val bars = lines.mapNotNull { line ->
            val parts = line.removePrefix("-").trim().split(":")
            if (parts.size >= 2) {
                val label = parts[0].trim()
                val value = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
                BarGroup(label = label, values = listOf(BarValue(value = value)))
            } else null
        }
        if (bars.isEmpty()) return null
        val data = if (type == ChartType.COLUMN) ChartDataContent.ColumnData(bars = bars)
                   else ChartDataContent.RowData(bars = bars)
        return ChartConfig(type = type, data = data)
    }
}

