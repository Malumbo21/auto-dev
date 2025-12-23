package cc.unitmesh.devins.ui.nano.components.data

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
import cc.unitmesh.devins.ui.nano.ComposeNodeContext
import cc.unitmesh.xuiper.eval.evaluator.NanoExpressionEvaluator
import cc.unitmesh.xuiper.ir.stringProp
import cc.unitmesh.xuiper.props.NanoFormatUtils
import cc.unitmesh.xuiper.props.NanoOptionWithMeta
import cc.unitmesh.xuiper.props.NanoOptionWithMetaParser
import cc.unitmesh.xuiper.render.NanoChartCodeBuilder
import cc.unitmesh.xuiper.render.stateful.NanoNodeRenderer
import cc.unitmesh.yaml.YamlUtils
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/**
 * Data visualization components for NanoUI Compose renderer.
 * Includes: DataChart, DataTable
 *
 * All components use the unified NanoNodeContext interface.
 */
object DataComponents {

    val dataChartRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderDataChart(ctx) }
    }

    val dataTableRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderDataTable(ctx) }
    }

    /**
     * Convert a JsonElement to a Kotlin runtime value.
     */
    private fun jsonElementToValue(element: JsonElement?): Any? =
        NanoExpressionEvaluator.jsonElementToRuntimeValue(element)

    /**
     * Convert a JsonArray to a Kotlin List.
     */
    @Suppress("UNCHECKED_CAST")
    private fun jsonArrayToList(jsonArray: JsonArray): List<Any?> =
        jsonElementToValue(jsonArray) as? List<Any?> ?: emptyList()

    @Composable
    fun RenderDataChart(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val chartType = ir.stringProp("type") ?: "line"
        val dataStr = ir.stringProp("data")
        val xField = ir.stringProp("xField") ?: ir.stringProp("x_axis")
        val yField = ir.stringProp("yField") ?: ir.stringProp("y_axis")

        // Try to resolve data from state if it's a binding
        val resolvedData = NanoExpressionEvaluator.resolveBindingAny(dataStr, ctx.state).toString()

        // Build chart code in YAML format
        val chartCode = buildChartCode(chartType, resolvedData, dataStr, xField, yField, ir.stringProp("title"))

        // Use existing ChartBlockRenderer if available
        if (isChartAvailable()) {
            ChartBlockRenderer(
                chartCode = chartCode,
                modifier = ctx.payload.fillMaxWidth().height(240.dp)
            )
        } else {
            RenderChartFallback(chartType, resolvedData, ctx.payload)
        }
    }


    private fun buildChartCode(
        chartType: String,
        resolvedData: Any?,
        dataStr: String?,
        xField: String?,
        yField: String?,
        title: String?
    ): String {
        val chartTitle = title ?: "Data Chart"

        // If resolvedData is a list of objects, build chart from it
        if (resolvedData is List<*>) {
            return NanoChartCodeBuilder.buildFromDataList(
                chartType = chartType,
                title = chartTitle,
                dataList = resolvedData,
                xField = xField,
                yField = yField
            )
        }

        // If data is already in YAML/JSON format, try to use it directly
        if (dataStr != null && (dataStr.trim().startsWith("{") || dataStr.contains(":"))) {
            return try {
                val config = ChartParser.parse(dataStr)
                if (config != null) dataStr else NanoChartCodeBuilder.buildDefaultChart(chartType, chartTitle)
            } catch (e: Exception) {
                NanoChartCodeBuilder.buildDefaultChart(chartType, chartTitle)
            }
        }

        return NanoChartCodeBuilder.buildDefaultChart(chartType, chartTitle)
    }

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

    @Composable
    fun RenderDataTable(ctx: ComposeNodeContext) {
        val ir = ctx.node

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

        // For JsonArray, convert directly to List
        val resolvedColumnsFromArray = if (columnsElement is JsonArray) {
            jsonArrayToList(columnsElement)
        } else null
        val resolvedDataFromArray = if (dataElement is JsonArray) {
            jsonArrayToList(dataElement)
        } else null

        // Resolve bindings (only for string values that might be state references)
        val resolvedColumns = resolvedColumnsFromArray ?: NanoExpressionEvaluator.resolveBindingAny(columnsStr, ctx.state)
        val resolvedData = resolvedDataFromArray ?: NanoExpressionEvaluator.resolveBindingAny(dataStr, ctx.state)

        // Parse columns and data
        val columnDefs = parseColumnDefs(resolvedColumns ?: columnsStr)
        val effectiveColumnDefs = columnDefs.ifEmpty { inferColumnsFromData(resolvedData) }
        val rows = parseRowsFromData(resolvedData, dataStr, effectiveColumnDefs)

        if (effectiveColumnDefs.isEmpty() || rows.isEmpty()) {
            RenderTableFallback(columnsStr, dataStr, ctx.payload)
            return
        }

        // Render actual table
        Surface(
            modifier = ctx.payload
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
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
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
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }


    private fun inferColumnsFromData(resolvedData: Any?): List<NanoOptionWithMeta> {
        val first = (resolvedData as? List<*>)?.firstOrNull() as? Map<*, *> ?: return emptyList()
        return first.keys
            .mapNotNull { it?.toString() }
            .filter { it.isNotBlank() }
            .map { key -> NanoOptionWithMeta.simple(key) }
    }

    private fun parseColumnDefs(columnsData: Any?): List<NanoOptionWithMeta> {
        return when (columnsData) {
            is JsonElement -> NanoOptionWithMetaParser.parse(columnsData)
            is String -> NanoOptionWithMetaParser.parseString(columnsData)
            is List<*> -> {
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
                        val value = row[colDef.value]
                        val format = colDef.getMeta<String>("format")
                        NanoFormatUtils.formatCellValue(value, format)
                    }
                } else null
            }
        }

        // Fall back to parsing from string
        return parseRows(dataStr, columnDefs.size)
    }

    @Suppress("DEPRECATION")
    private fun parseRows(dataStr: String?, columnCount: Int): List<List<String>> {
        if (dataStr.isNullOrBlank()) return emptyList()

        return try {
            if (dataStr.trim().startsWith("[")) {
                val parsed = YamlUtils.load(dataStr) as? List<*>
                parsed?.mapNotNull { row ->
                    when (row) {
                        is List<*> -> row.mapNotNull { it?.toString() }
                        else -> null
                    }
                }?.filter { it.size == columnCount } ?: emptyList()
            } else {
                dataStr.split(";").map { row ->
                    row.split(",").map { it.trim() }
                }.filter { it.size == columnCount }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

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
