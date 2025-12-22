package cc.unitmesh.xuiper.render

/**
 * Builder for generating chart code in YAML format.
 *
 * This utility converts data from NanoUI state into YAML chart configurations
 * that can be rendered by chart libraries. It supports multiple chart types
 * and can infer field mappings from data structure.
 *
 * ## Supported Chart Types
 *
 * - `pie` - Pie charts with label/value pairs
 * - `line` - Line charts with series data
 * - `column`/`bar` - Bar/column charts
 *
 * ## Usage
 *
 * ```kotlin
 * val chartCode = NanoChartCodeBuilder.buildFromDataList(
 *     chartType = "pie",
 *     title = "Sales Distribution",
 *     dataList = listOf(mapOf("name" to "A", "value" to 100))
 * )
 * ```
 */
object NanoChartCodeBuilder {

    fun buildFromDataList(
        chartType: String,
        title: String,
        dataList: List<*>,
        xField: String? = null,
        yField: String? = null
    ): String {
        val inferred = inferXYFields(dataList)
        val effectiveXField = xField ?: inferred.first
        val effectiveYField = yField ?: inferred.second

        return when (chartType.lowercase()) {
            "pie" -> buildPieChart(title, dataList, effectiveXField, effectiveYField)
            "line" -> buildLineChart(title, dataList, effectiveXField, effectiveYField)
            "column", "bar" -> buildColumnChart(title, dataList, effectiveXField, effectiveYField)
            else -> buildDefaultChart(chartType, title)
        }
    }

    fun buildDefaultChart(chartType: String, title: String): String = when (chartType.lowercase()) {
        "pie" -> "type: pie\ntitle: $title\ndata:\n  items:\n    - label: \"Item 1\"\n      value: 30\n    - label: \"Item 2\"\n      value: 50"
        "line" -> "type: line\ntitle: $title\ndata:\n  lines:\n    - label: \"Series 1\"\n      values: [10, 20, 15, 25, 30]"
        "column", "bar" -> "type: column\ntitle: $title\ndata:\n  bars:\n    - label: \"Group 1\"\n      values:\n        - label: \"A\"\n          value: 10"
        else -> "type: line\ntitle: $title\ndata:\n  lines:\n    - label: \"Data\"\n      values: [0, 0, 0]"
    }

    fun inferXYFields(dataList: List<*>): Pair<String, String> {
        val first = dataList.firstOrNull() as? Map<*, *> ?: return "x" to "y"
        val keys = first.keys.mapNotNull { it?.toString() }.toSet()

        return when {
            "name" in keys && "value" in keys -> "name" to "value"
            "label" in keys && "value" in keys -> "label" to "value"
            "month" in keys && "sales" in keys -> "month" to "sales"
            "date" in keys && "sales" in keys -> "date" to "sales"
            "category" in keys && "count" in keys -> "category" to "count"
            "x" in keys && "y" in keys -> "x" to "y"
            else -> inferFieldsByType(first, keys)
        }
    }

    private fun inferFieldsByType(first: Map<*, *>, keys: Set<String>): Pair<String, String> {
        val stringField = keys.firstOrNull { key ->
            val value = first[key]
            value is String || (value != null && value.toString().toDoubleOrNull() == null)
        }
        val numberField = keys.firstOrNull { key ->
            val value = first[key]
            value is Number || value.toString().toDoubleOrNull() != null
        }
        return (stringField ?: "x") to (numberField ?: "y")
    }

    private fun buildPieChart(title: String, dataList: List<*>, xField: String, yField: String): String {
        val items = dataList.mapNotNull { item ->
            if (item is Map<*, *>) {
                val label = item[xField]?.toString() ?: item["label"]?.toString() ?: "Unknown"
                val value = (item[yField] ?: item["value"])?.toString()?.toDoubleOrNull() ?: 0.0
                "    - label: \"$label\"\n      value: $value"
            } else null
        }.joinToString("\n")
        return "type: pie\ntitle: $title\ndata:\n  items:\n$items"
    }

    private fun buildLineChart(title: String, dataList: List<*>, xField: String, yField: String): String {
        val values = dataList.mapNotNull { item ->
            if (item is Map<*, *>) (item[yField])?.toString()?.toDoubleOrNull() else null
        }.joinToString(", ")
        return "type: line\ntitle: $title\ndata:\n  lines:\n    - label: \"Data\"\n      values: [$values]"
    }

    private fun buildColumnChart(title: String, dataList: List<*>, xField: String, yField: String): String {
        val bars = dataList.mapNotNull { item ->
            if (item is Map<*, *>) {
                val label = item[xField]?.toString() ?: "Unknown"
                val value = (item[yField])?.toString()?.toDoubleOrNull() ?: 0.0
                "        - label: \"$label\"\n          value: $value"
            } else null
        }.joinToString("\n")
        return "type: column\ntitle: $title\ndata:\n  bars:\n    - label: \"Data\"\n      values:\n$bars"
    }
}

