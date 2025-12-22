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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.compose.sketch.chart.ChartBlockRenderer
import cc.unitmesh.devins.ui.compose.sketch.chart.ChartParser
import cc.unitmesh.devins.ui.compose.sketch.chart.isChartAvailable
import cc.unitmesh.xuiper.eval.evaluator.NanoExpressionEvaluator
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.props.NanoFormatUtils
import cc.unitmesh.xuiper.props.NanoOptionWithMeta
import cc.unitmesh.xuiper.props.NanoOptionWithMetaParser
import cc.unitmesh.xuiper.render.NanoChartCodeBuilder
import cc.unitmesh.yaml.YamlUtils
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Data visualization components for NanoUI Compose renderer.
 * Includes: DataChart, DataTable
 */
object NanoDataComponents {

    /**
     * Convert a JsonElement to a Kotlin runtime value.
     * Delegates to [NanoExpressionEvaluator.jsonElementToRuntimeValue].
     */
    private fun jsonElementToValue(element: JsonElement?): Any? =
        NanoExpressionEvaluator.jsonElementToRuntimeValue(element)

    /**
     * Convert a JsonArray to a Kotlin List.
     * Delegates to [NanoExpressionEvaluator.jsonElementToRuntimeValue].
     */
    @Suppress("UNCHECKED_CAST")
    private fun jsonArrayToList(jsonArray: JsonArray): List<Any?> =
        jsonElementToValue(jsonArray) as? List<Any?> ?: emptyList()

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
        val resolvedData = NanoExpressionEvaluator.resolveBindingAny(dataStr, state).toString()

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
     * Build chart code in YAML format for ChartBlockRenderer.
     * Delegates to [NanoChartCodeBuilder] for chart generation.
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
            return NanoChartCodeBuilder.buildFromDataList(
                chartType = chartType,
                title = title,
                dataList = resolvedData,
                xField = xField,
                yField = yField
            )
        }

        // If data is already in YAML/JSON format, try to use it directly
        if (dataStr != null && (dataStr.trim().startsWith("{") || dataStr.contains(":"))) {
            return try {
                val config = ChartParser.parse(dataStr)
                if (config != null) dataStr else NanoChartCodeBuilder.buildDefaultChart(chartType, title)
            } catch (e: Exception) {
                NanoChartCodeBuilder.buildDefaultChart(chartType, title)
            }
        }

        return NanoChartCodeBuilder.buildDefaultChart(chartType, title)
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
        // Handle both JsonPrimitive (string) and JsonArray for columns and data
        val columnsElement = ir.props["columns"]
        val dataElement = ir.props["data"]

        val columnsStr = when (columnsElement) {
            is kotlinx.serialization.json.JsonPrimitive -> columnsElement.content
            is kotlinx.serialization.json.JsonArray -> columnsElement.toString()
            else -> null
        }
        val dataStr = when (dataElement) {
            is kotlinx.serialization.json.JsonPrimitive -> dataElement.content
            is kotlinx.serialization.json.JsonArray -> dataElement.toString()
            else -> null
        }

        // For JsonArray, convert directly to List for resolved values
        val resolvedColumnsFromArray = if (columnsElement is kotlinx.serialization.json.JsonArray) {
            jsonArrayToList(columnsElement)
        } else null
        val resolvedDataFromArray = if (dataElement is kotlinx.serialization.json.JsonArray) {
            jsonArrayToList(dataElement)
        } else null

        // Resolve bindings (only for string values that might be state references)
        val resolvedColumns = resolvedColumnsFromArray ?: NanoExpressionEvaluator.resolveBindingAny(columnsStr, state)
        val resolvedData = resolvedDataFromArray ?: NanoExpressionEvaluator.resolveBindingAny(dataStr, state)

        // Parse columns and data
        val columnDefs = parseColumnDefs(resolvedColumns ?: columnsStr)

        val effectiveColumnDefs = columnDefs.ifEmpty {
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
                            text = column.label,
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
     * Infer column definitions from data when columns are not explicitly specified.
     * Uses [NanoOptionWithMeta] where value=key and label=title.
     */
    private fun inferColumnsFromData(resolvedData: Any?): List<NanoOptionWithMeta> {
        val first = (resolvedData as? List<*>)?.firstOrNull() as? Map<*, *> ?: return emptyList()
        return first.keys
            .mapNotNull { it?.toString() }
            .filter { it.isNotBlank() }
            .map { key -> NanoOptionWithMeta.simple(key) }
    }

    /**
     * Parse column definitions from various formats using [NanoOptionWithMetaParser].
     *
     * Supports:
     * - Simple string: "col1,col2,col3"
     * - JSON array of objects: [{"key": "product", "title": "Product Name", "sortable": true}]
     * - JSON array of strings: ["col1", "col2"]
     *
     * Note: Uses value=key and label=title mapping.
     */
    private fun parseColumnDefs(columnsData: Any?): List<NanoOptionWithMeta> {
        return when (columnsData) {
            is kotlinx.serialization.json.JsonElement -> NanoOptionWithMetaParser.parse(columnsData)
            is String -> NanoOptionWithMetaParser.parseString(columnsData)
            is List<*> -> {
                // Convert List to NanoOptionWithMeta manually for non-JSON lists
                columnsData.mapNotNull { item ->
                    when (item) {
                        is Map<*, *> -> {
                            val key = item["key"]?.toString()
                                ?: item["value"]?.toString()
                                ?: return@mapNotNull null
                            val title = item["title"]?.toString()
                                ?: item["label"]?.toString()
                                ?: key
                            val meta = mutableMapOf<String, Any?>()
                            item.forEach { (k, v) ->
                                val keyStr = k?.toString() ?: return@forEach
                                if (keyStr !in setOf("key", "value", "title", "label")) {
                                    meta[keyStr] = v
                                }
                            }
                            NanoOptionWithMeta(value = key, label = title, meta = meta)
                        }
                        is String -> NanoOptionWithMeta.simple(item)
                        else -> null
                    }
                }
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
     * Parse rows from resolved data or string format.
     * Uses [NanoOptionWithMeta] where value=key and meta["format"] for formatting.
     *
     * Supports:
     * - List of objects (from state)
     * - JSON arrays
     * - CSV-like format
     */
    private fun parseRowsFromData(
        resolvedData: Any?,
        dataStr: String?,
        columnDefs: List<NanoOptionWithMeta>
    ): List<List<String>> {
        // If resolved data is a list of objects, extract values by column keys
        if (resolvedData is List<*>) {
            return resolvedData.mapNotNull { row ->
                if (row is Map<*, *>) {
                    columnDefs.map { colDef ->
                        val value = row[colDef.value]  // value = key
                        val format = colDef.getMeta<String>("format")
                        formatCellValue(value, format)
                    }
                } else null
            }
        }

        // Fall back to parsing from string
        return parseRows(dataStr, columnDefs.size)
    }

    /**
     * Format cell value according to column format.
     * Delegates to [NanoFormatUtils.formatCellValue].
     */
    private fun formatCellValue(value: Any?, format: String?): String =
        NanoFormatUtils.formatCellValue(value, format)

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
