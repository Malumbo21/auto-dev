package cc.unitmesh.devins.ui.compose.sketch.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * JS fallback implementation of ChartBlockRenderer.
 *
 * ComposeCharts is not available on JS, so this displays the chart code
 * as a fallback using HTML elements.
 */
@Composable
actual fun ChartBlockRenderer(
    chartCode: String,
    modifier: Modifier
) {
    val chartConfig = ChartParser.parse(chartCode)

    Div(attrs = {
        style {
            property("border", "1px solid rgba(128, 128, 128, 0.3)")
            property("background", "#1e1e1e")
            property("padding", "12px")
            property("border-radius", "4px")
            property("font-family", "monospace")
        }
    }) {
        // Title row
        Div(attrs = {
            style {
                property("display", "flex")
                property("justify-content", "space-between")
                property("margin-bottom", "8px")
            }
        }) {
            Span(attrs = {
                style {
                    property("color", "#ffffff")
                    property("font-weight", "500")
                }
            }) {
                Text(chartConfig?.title ?: "Chart (Preview Only)")
            }
            Span(attrs = {
                style {
                    property("color", "#888888")
                    property("font-size", "12px")
                }
            }) {
                Text("JS - Code View")
            }
        }

        // Info message
        Div(attrs = {
            style {
                property("color", "#888888")
                property("font-size", "12px")
                property("margin-bottom", "8px")
            }
        }) {
            Text("Charts are not available in JS. Showing chart code.")
        }

        // Code display
        Pre(attrs = {
            style {
                property("background", "#0d0d0d")
                property("padding", "12px")
                property("border-radius", "4px")
                property("overflow-x", "auto")
                property("margin", "0")
                property("color", "#d4d4d4")
                property("font-size", "13px")
            }
        }) {
            Text(chartCode)
        }
    }
}

/**
 * JS does not support ComposeCharts
 */
actual fun isChartAvailable(): Boolean = false

