package cc.unitmesh.devins.idea.renderer.nano.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.xuiper.eval.evaluator.NanoExpressionEvaluator
import cc.unitmesh.xuiper.ir.stringProp
import cc.unitmesh.xuiper.render.stateful.NanoNodeRenderer
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * Jewel data components for NanoUI IntelliJ IDEA renderer.
 * Includes: DataChart, DataTable
 */
object JewelDataComponents {

    val dataChartRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val ir = ctx.node
            val chartType = ir.stringProp("type") ?: "bar"
            val dataExpr = ir.stringProp("data") ?: ""
            val title = ir.stringProp("title") ?: ""

            val resolvedData = if (dataExpr.isNotBlank()) {
                NanoExpressionEvaluator.resolveAny(dataExpr, ctx.state)
            } else null

            val dataList = when (resolvedData) {
                is List<*> -> resolvedData.filterIsInstance<Number>().map { it.toFloat() }
                else -> emptyList()
            }

            Column(
                modifier = ctx.payload
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
                    .background(JewelTheme.globalColors.panelBackground)
                    .padding(16.dp)
            ) {
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = JewelTheme.globalColors.text.normal
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (dataList.isEmpty()) {
                    Text(
                        text = "No data available",
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                    )
                } else {
                    // Simple bar chart visualization
                    val maxValue = dataList.maxOrNull() ?: 1f
                    Row(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        dataList.forEach { value ->
                            val heightFraction = (value / maxValue).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(heightFraction)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(Color(0xFF64B5F6))
                            )
                        }
                    }
                }

                Text(
                    text = "Chart type: $chartType",
                    fontSize = 11.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    val dataTableRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val ir = ctx.node
            val columnsExpr = ir.stringProp("columns") ?: ""
            val dataExpr = ir.stringProp("data") ?: ""

            val columns = if (columnsExpr.isNotBlank()) {
                NanoExpressionEvaluator.resolveAny(columnsExpr, ctx.state) as? List<*>
            } else null

            val data = if (dataExpr.isNotBlank()) {
                NanoExpressionEvaluator.resolveAny(dataExpr, ctx.state) as? List<*>
            } else null

            val columnList = columns?.mapNotNull { col ->
                when (col) {
                    is Map<*, *> -> col["key"]?.toString() to (col["label"]?.toString() ?: col["key"]?.toString() ?: "")
                    is String -> col to col
                    else -> null
                }
            } ?: emptyList()

            val rowList = data?.filterIsInstance<Map<*, *>>() ?: emptyList()

            Column(
                modifier = ctx.payload
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
                    .background(JewelTheme.globalColors.panelBackground)
            ) {
                if (columnList.isEmpty() && rowList.isEmpty()) {
                    Text(
                        text = "No data available",
                        modifier = Modifier.padding(16.dp),
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                    )
                } else {
                    // Header row
                    if (columnList.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            columnList.forEach { (_, label) ->
                                Text(
                                    text = label ?: "",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = JewelTheme.globalColors.text.normal,
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(JewelTheme.globalColors.borders.normal)
                        )
                    }

                    // Data rows
                    rowList.forEachIndexed { index, row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (index % 2 == 0) Color.Transparent
                                    else JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f)
                                )
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            columnList.forEach { (key, _) ->
                                val cellValue = row[key]?.toString() ?: ""
                                Text(
                                    text = cellValue,
                                    fontSize = 13.sp,
                                    color = JewelTheme.globalColors.text.normal,
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
