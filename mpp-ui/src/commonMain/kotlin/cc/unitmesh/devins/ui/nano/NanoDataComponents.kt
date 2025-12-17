package cc.unitmesh.devins.ui.nano

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.compose.sketch.chart.ChartBlockRenderer
import cc.unitmesh.devins.ui.compose.sketch.chart.ChartParser
import cc.unitmesh.devins.ui.compose.sketch.chart.isChartAvailable
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.yaml.YamlUtils
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.pow
import kotlin.math.round

/**
 * Data visualization components for NanoUI Compose renderer.
 * Includes: DataChart, DataTable
 */
object NanoDataComponents {

    private fun formatFixed(num: Double, decimals: Int): String {
        if (decimals <= 0) return round(num).toLong().toString()

        val factor = 10.0.pow(decimals.toDouble())
        val rounded = round(num * factor) / factor
        val parts = rounded.toString().split('.', limit = 2)
        val intPart = parts[0]
        val fracPart = (parts.getOrNull(1) ?: "")
            .padEnd(decimals, '0')
            .take(decimals)

        return "$intPart.$fracPart"
    }

    /**
     * Render a data chart using the ChartBlockRenderer.
     * Supports YAML/JSON chart configuration format.
     */
    @Composable
    fun RenderDataChart(ir: NanoIR, state: Map<String, Any>, modifier: Modifier) {
        val chartType = ir.props["type"]?.jsonPrimitive?.content ?: "line"
        val dataStr = ir.props["data"]?.jsonPrimitive?.content
        val xField = ir.props["xField"]?.jsonPrimitive?.content
            ?: ir.props["x_axis"]?.jsonPrimitive?.content
        val yField = ir.props["yField"]?.jsonPrimitive?.content
            ?: ir.props["y_axis"]?.jsonPrimitive?.content

        // Try to resolve data from state if it's a binding
        val resolvedData = NanoRenderUtils.resolveBindingAny(dataStr, state).toString()

        // Build chart code in YAML format
        val chartCode = buildChartCode(chartType, resolvedData, dataStr, xField, yField, ir)

        // Use existing ChartBlockRenderer if available
        if (isChartAvailable()) {
            ChartBlockRenderer(
                chartCode = chartCode,
                modifier = modifier.fillMaxWidth().height(240.dp)
            )
        } else {
            // Fallback for platforms without chart support
            RenderChartFallback(chartType, resolvedData, modifier)
        }
    }

    /**
     * Build chart code in YAML format for ChartBlockRenderer
     */
    private fun buildChartCode(
        chartType: String,
        resolvedData: Any?,
        dataStr: String?,
        xField: String?,
        yField: String?,
        ir: NanoIR
    ): String {
        val title = ir.props["title"]?.jsonPrimitive?.content ?: "Data Chart"

        // If resolvedData is a list of objects, build chart from it
        if (resolvedData is List<*>) {
            val inferred = inferXYFields(resolvedData)
            return buildChartCodeFromObjectList(
                chartType = chartType,
                title = title,
                dataList = resolvedData,
                xField = xField ?: inferred.first,
                yField = yField ?: inferred.second
            )
        }

        // If data is already in YAML/JSON format, try to use it directly
        if (dataStr != null && (dataStr.trim().startsWith("{") || dataStr.contains(":"))) {
            return try {
                // Try parsing as chart config
                val config = ChartParser.parse(dataStr)
                if (config != null) {
                    return dataStr
                }
                // Otherwise build from scratch
                buildDefaultChartCode(chartType, title, dataStr)
            } catch (e: Exception) {
                buildDefaultChartCode(chartType, title, dataStr)
            }
        }

        return buildDefaultChartCode(chartType, title, dataStr ?: "")
    }

    /**
     * Build chart code from a list of objects (e.g., from state)
     */
    private fun buildChartCodeFromObjectList(
        chartType: String,
        title: String,
        dataList: List<*>,
        xField: String,
        yField: String
    ): String {
        return when (chartType.lowercase()) {
            "pie" -> {
                val items = dataList.mapNotNull { item ->
                    if (item is Map<*, *>) {
                        val label = item[xField]?.toString() ?: item["label"]?.toString() ?: "Unknown"
                        val value = (item[yField] ?: item["value"])?.toString()?.toDoubleOrNull() ?: 0.0
                        "    - label: \"$label\"\n      value: $value"
                    } else null
                }.joinToString("\n")

                """
                type: pie
                title: $title
                data:
                  items:
$items
                """.trimIndent()
            }

            "line" -> {
                val labels = dataList.mapNotNull { item ->
                    if (item is Map<*, *>) item[xField]?.toString() else null
                }.joinToString(", ") { "\"$it\"" }

                val values = dataList.mapNotNull { item ->
                    if (item is Map<*, *>) {
                        (item[yField])?.toString()?.toDoubleOrNull()
                    } else null
                }.joinToString(", ")

                """
                type: line
                title: $title
                data:
                  lines:
                    - label: "Data"
                      values: [$values]
                """.trimIndent()
            }

            "column", "bar" -> {
                val bars = dataList.mapNotNull { item ->
                    if (item is Map<*, *>) {
                        val label = item[xField]?.toString() ?: "Unknown"
                        val value = (item[yField])?.toString()?.toDoubleOrNull() ?: 0.0
                        "        - label: \"$label\"\n          value: $value"
                    } else null
                }.joinToString("\n")

                """
                type: column
                title: $title
                data:
                  bars:
                    - label: "Data"
                      values:
$bars
                """.trimIndent()
            }

            else -> buildDefaultChartCode(chartType, title, "")
        }
    }

    /**
     * Infer x/y field names from a list of object rows.
     * Minimal heuristics for common NanoDSL outputs.
     */
    private fun inferXYFields(dataList: List<*>): Pair<String, String> {
        val first = dataList.firstOrNull() as? Map<*, *> ?: return "x" to "y"
        val keys = first.keys.mapNotNull { it?.toString() }.toSet()

        return when {
            "name" in keys && "value" in keys -> "name" to "value"
            "label" in keys && "value" in keys -> "label" to "value"
            "month" in keys && "sales" in keys -> "month" to "sales"
            "date" in keys && "sales" in keys -> "date" to "sales"
            else -> "x" to "y"
        }
    }

    /**
     * Build default chart code with simple data format
     */
    private fun buildDefaultChartCode(chartType: String, title: String, data: String): String {
        return when (chartType.lowercase()) {
            "pie" -> """
                type: pie
                title: $title
                data:
                  items:
                    - label: "Item 1"
                      value: 30
                    - label: "Item 2"
                      value: 50
                    - label: "Item 3"
                      value: 20
            """.trimIndent()

            "line" -> """
                type: line
                title: $title
                data:
                  lines:
                    - label: "Series 1"
                      values: [10, 20, 15, 25, 30]
            """.trimIndent()

            "column", "bar" -> """
                type: column
                title: $title
                data:
                  bars:
                    - label: "Group 1"
                      values:
                        - label: "A"
                          value: 10
                        - label: "B"
                          value: 20
            """.trimIndent()

            else -> """
                type: line
                title: $title
                data:
                  lines:
                    - label: "Data"
                      values: [0, 0, 0]
            """.trimIndent()
        }
    }

    /**
     * Fallback chart rendering for platforms without ChartBlockRenderer
     */
    @Composable
    private fun RenderChartFallback(chartType: String, data: String?, modifier: Modifier) {
        Surface(
            modifier = modifier.fillMaxWidth().height(200.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Chart: $chartType",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (data != null) {
                        Text(
                            "Data: $data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    /**
     * Render a data table with columns and rows.
     * Supports both simple and complex data formats.
     */
    @Composable
    fun RenderDataTable(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val columnsStr = ir.props["columns"]?.jsonPrimitive?.content
        val dataStr = ir.props["data"]?.jsonPrimitive?.content

        // Resolve bindings
        val resolvedColumns = NanoRenderUtils.resolveBindingAny(columnsStr, state)
        val resolvedData = NanoRenderUtils.resolveBindingAny(dataStr, state)

        // Parse columns and data
        val columnDefs = parseColumnDefs(resolvedColumns ?: columnsStr)

        val effectiveColumnDefs = if (columnDefs.isNotEmpty()) {
            columnDefs
        } else {
            inferColumnsFromData(resolvedData)
        }

        val rows = parseRowsFromData(resolvedData, dataStr, effectiveColumnDefs)

        if (effectiveColumnDefs.isEmpty() || rows.isEmpty()) {
            RenderTableFallback(columnsStr, dataStr, modifier)
            return
        }

        // Render actual table
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            ) {
                // Header row
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
                        .fillMaxWidth()
                ) {
                    effectiveColumnDefs.forEach { column ->
                        Text(
                            text = column.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                    }
                }

                HorizontalDivider()

                // Data rows
                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .background(
                                if (index % 2 == 0) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                            .padding(8.dp)
                            .fillMaxWidth()
                    ) {
                        row.forEach { cell ->
                            Text(
                                text = cell,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Column definition for DataTable
     */
    private data class ColumnDef(
        val key: String,
        val title: String,
        val sortable: Boolean = false,
        val format: String? = null
    )

    private fun inferColumnsFromData(resolvedData: Any?): List<ColumnDef> {
        val first = (resolvedData as? List<*>)?.firstOrNull() as? Map<*, *> ?: return emptyList()
        return first.keys
            .mapNotNull { it?.toString() }
            .filter { it.isNotBlank() }
            .map { key -> ColumnDef(key = key, title = key) }
    }

    /**
     * Parse column definitions from various formats
     * Supports:
     * - Simple string: "col1,col2,col3"
     * - JSON array of objects: [{"key": "product", "title": "Product Name"}]
     * - JSON array of strings: ["col1", "col2"]
     */
    private fun parseColumnDefs(columnsData: Any?): List<ColumnDef> {
        return when (columnsData) {
            is List<*> -> {
                // List of column definitions
                columnsData.mapNotNull { item ->
                    when (item) {
                        is Map<*, *> -> {
                            val key = item["key"]?.toString() ?: return@mapNotNull null
                            val title = item["title"]?.toString() ?: key
                            val sortable = item["sortable"] as? Boolean ?: false
                            val format = item["format"]?.toString()
                            ColumnDef(key, title, sortable, format)
                        }
                        is String -> ColumnDef(item, item)
                        else -> null
                    }
                }
            }
            is String -> {
                // Try JSON array format first
                try {
                    val parsed = YamlUtils.load(columnsData) as? List<*>
                    if (parsed != null) {
                        return parseColumnDefs(parsed)
                    }
                } catch (e: Exception) {
                    // Fall through to simple format
                }
                // Simple comma-separated format
                columnsData.split(",").map {
                    val trimmed = it.trim()
                    ColumnDef(trimmed, trimmed)
                }.filter { it.key.isNotEmpty() }
            }
            else -> emptyList()
        }
    }

    /**
     * Parse columns from string format (legacy method).
     * Supports: "col1,col2,col3" or JSON array format
     */
    @Deprecated("Use parseColumnDefs instead")
    private fun parseColumns(columnsStr: String?): List<String> {
        if (columnsStr.isNullOrBlank()) return emptyList()

        return try {
            // Try JSON array format first
            if (columnsStr.trim().startsWith("[")) {
                val parsed = YamlUtils.load(columnsStr) as? List<*>
                parsed?.mapNotNull { it?.toString() } ?: emptyList()
            } else {
                // Simple comma-separated format
                columnsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            columnsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    /**
     * Parse rows from resolved data or string format
     * Supports:
     * - List of objects (from state)
     * - JSON arrays
     * - CSV-like format
     */
    private fun parseRowsFromData(
        resolvedData: Any?,
        dataStr: String?,
        columnDefs: List<ColumnDef>
    ): List<List<String>> {
        // If resolved data is a list of objects, extract values by column keys
        if (resolvedData is List<*>) {
            return resolvedData.mapNotNull { row ->
                if (row is Map<*, *>) {
                    columnDefs.map { colDef ->
                        val value = row[colDef.key]
                        formatCellValue(value, colDef.format)
                    }
                } else null
            }
        }

        // Fall back to parsing from string
        return parseRows(dataStr, columnDefs.size)
    }

    /**
     * Format cell value according to column format
     */
    private fun formatCellValue(value: Any?, format: String?): String {
        if (value == null) return ""

        return when (format?.lowercase()) {
            "currency" -> {
                val num = value.toString().toDoubleOrNull()
                if (num != null) "$${formatFixed(num, 2)}" else value.toString()
            }
            "percent" -> {
                val num = value.toString().toDoubleOrNull()
                if (num != null) "${formatFixed(num, 1)}%" else value.toString()
            }
            else -> value.toString()
        }
    }

    /**
     * Parse rows from string format (legacy method).
     * Supports: nested arrays or CSV-like format
     */
    @Deprecated("Use parseRowsFromData instead")
    private fun parseRows(dataStr: String?, columnCount: Int): List<List<String>> {
        if (dataStr.isNullOrBlank()) return emptyList()

        return try {
            // Try JSON/YAML array format
            if (dataStr.trim().startsWith("[")) {
                val parsed = YamlUtils.load(dataStr) as? List<*>
                parsed?.mapNotNull { row ->
                    when (row) {
                        is List<*> -> row.mapNotNull { it?.toString() }
                        else -> null
                    }
                }?.filter { it.size == columnCount } ?: emptyList()
            } else {
                // Simple row-based format: "row1col1,row1col2;row2col1,row2col2"
                dataStr.split(";").map { row ->
                    row.split(",").map { it.trim() }
                }.filter { it.size == columnCount }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Fallback table rendering when data cannot be parsed
     */
    @Composable
    private fun RenderTableFallback(columns: String?, data: String?, modifier: Modifier) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "DataTable",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (columns != null) {
                    Text(
                        "Columns: $columns",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (data != null) {
                    Text(
                        "Data: $data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
