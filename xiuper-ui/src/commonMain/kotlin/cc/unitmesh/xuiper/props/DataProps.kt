package cc.unitmesh.xuiper.props

import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.props.PropExtractors.boolProp
import cc.unitmesh.xuiper.props.PropExtractors.intProp
import cc.unitmesh.xuiper.props.PropExtractors.stringProp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Properties for DataChart component.
 */
data class ChartProps(
    /** Chart type (line, bar, pie, column, area) */
    val type: String,
    /** Data source (JSON string or state binding) */
    val dataStr: String?,
    /** X-axis field name */
    val xField: String?,
    /** Y-axis field name */
    val yField: String?,
    /** Chart title */
    val title: String?,
    /** Whether to show legend */
    val showLegend: Boolean,
    /** Chart height in dp */
    val height: Int?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): ChartProps = ChartProps(
            type = ir.stringProp("type", "line"),
            dataStr = ir.props["data"]?.let { 
                when (it) {
                    is JsonPrimitive -> it.content
                    is JsonArray -> it.toString()
                    else -> null
                }
            },
            xField = ir.stringProp("xField") ?: ir.stringProp("x_axis"),
            yField = ir.stringProp("yField") ?: ir.stringProp("y_axis"),
            title = ir.stringProp("title"),
            showLegend = ir.boolProp("showLegend", true),
            height = ir.intProp("height")
        )
    }
}

/**
 * Properties for DataTable component.
 */
data class DataTableProps(
    /** Column definitions */
    val columns: List<NanoOptionWithMeta>,
    /** Data source (JSON string or state binding) */
    val dataStr: String?,
    /** Whether rows are selectable */
    val selectable: Boolean,
    /** Whether to show row numbers */
    val showRowNumbers: Boolean,
    /** Whether columns are sortable */
    val sortable: Boolean,
    /** Page size for pagination (0 = no pagination) */
    val pageSize: Int,
    /** Row click action */
    val onRowClick: NanoActionIR?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): DataTableProps {
            val columnsElement = ir.props["columns"]
            val dataElement = ir.props["data"]
            
            val columnsStr = when (columnsElement) {
                is JsonPrimitive -> columnsElement.content
                is JsonArray -> columnsElement.toString()
                else -> null
            }
            val dataStr = when (dataElement) {
                is JsonPrimitive -> dataElement.content
                is JsonArray -> dataElement.toString()
                else -> null
            }
            
            return DataTableProps(
                columns = NanoOptionWithMetaParser.parseString(columnsStr),
                dataStr = dataStr,
                selectable = ir.boolProp("selectable", false),
                showRowNumbers = ir.boolProp("showRowNumbers", false),
                sortable = ir.boolProp("sortable", false),
                pageSize = ir.intProp("pageSize", 0),
                onRowClick = ir.actions?.get("onRowClick")
            )
        }
    }
}

/**
 * Properties for List component.
 */
data class ListProps(
    /** Data source (JSON string or state binding) */
    val dataStr: String?,
    /** Whether items are selectable */
    val selectable: Boolean,
    /** Divider style (none, full, inset) */
    val divider: String,
    /** Item click action */
    val onItemClick: NanoActionIR?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): ListProps {
            val dataElement = ir.props["data"]
            val dataStr = when (dataElement) {
                is JsonPrimitive -> dataElement.content
                is JsonArray -> dataElement.toString()
                else -> null
            }
            
            return ListProps(
                dataStr = dataStr,
                selectable = ir.boolProp("selectable", false),
                divider = ir.stringProp("divider", "full"),
                onItemClick = ir.actions?.get("onItemClick")
            )
        }
    }
}

/**
 * Properties for Tree component.
 */
data class TreeProps(
    /** Data source (JSON string or state binding) */
    val dataStr: String?,
    /** Whether nodes are selectable */
    val selectable: Boolean,
    /** Whether to show checkboxes */
    val checkable: Boolean,
    /** Default expanded keys */
    val defaultExpandedKeys: List<String>,
    /** Node click action */
    val onNodeClick: NanoActionIR?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): TreeProps {
            val dataElement = ir.props["data"]
            val dataStr = when (dataElement) {
                is JsonPrimitive -> dataElement.content
                is JsonArray -> dataElement.toString()
                else -> null
            }
            
            return TreeProps(
                dataStr = dataStr,
                selectable = ir.boolProp("selectable", false),
                checkable = ir.boolProp("checkable", false),
                defaultExpandedKeys = emptyList(), // TODO: parse from props
                onNodeClick = ir.actions?.get("onNodeClick")
            )
        }
    }
}

