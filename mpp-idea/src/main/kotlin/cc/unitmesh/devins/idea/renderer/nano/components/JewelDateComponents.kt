package cc.unitmesh.devins.idea.renderer.nano.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.xuiper.action.NanoActionFactory
import cc.unitmesh.xuiper.ir.stringProp
import cc.unitmesh.xuiper.props.NanoBindingResolver
import cc.unitmesh.xuiper.render.stateful.NanoNodeRenderer
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

/**
 * Jewel date components for NanoUI IntelliJ IDEA renderer.
 * Includes: DatePicker, DateRangePicker
 *
 * Note: Jewel doesn't have native date picker components, so we use TextField as fallback.
 */
object JewelDateComponents {

    val datePickerRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val ir = ctx.node
            val label = ir.stringProp("label")
            val placeholder = ir.stringProp("placeholder") ?: "YYYY-MM-DD"
            val statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind")
            val onChange = ir.actions?.get("onChange")
            val initialValue = ctx.state[statePath]?.toString() ?: ""

            val textFieldState = rememberTextFieldState(initialValue)

            LaunchedEffect(textFieldState.text) {
                val newValue = textFieldState.text.toString()
                if (newValue != initialValue) {
                    statePath?.let { ctx.onAction(NanoActionFactory.set(it, newValue)) }
                    onChange?.let { ctx.onAction(it) }
                }
            }

            LaunchedEffect(initialValue) {
                if (textFieldState.text.toString() != initialValue) {
                    textFieldState.setTextAndPlaceCursorAtEnd(initialValue)
                }
            }

            Column(modifier = ctx.payload) {
                if (label != null) {
                    Text(label, color = JewelTheme.globalColors.text.normal)
                    Spacer(Modifier.height(4.dp))
                }
                TextField(
                    state = textFieldState,
                    placeholder = { Text(placeholder) },
                    modifier = Modifier.widthIn(min = 150.dp)
                )
            }
        }
    }

    val dateRangePickerRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val ir = ctx.node
            val label = ir.stringProp("label")
            val startPlaceholder = ir.stringProp("startPlaceholder") ?: "Start date"
            val endPlaceholder = ir.stringProp("endPlaceholder") ?: "End date"
            val startPath = NanoBindingResolver.resolveStatePath(ir, "startValue", "startBind")
            val endPath = NanoBindingResolver.resolveStatePath(ir, "endValue", "endBind")
            val onChange = ir.actions?.get("onChange")

            val startInitialValue = ctx.state[startPath]?.toString() ?: ""
            val endInitialValue = ctx.state[endPath]?.toString() ?: ""

            val startTextFieldState = rememberTextFieldState(startInitialValue)
            val endTextFieldState = rememberTextFieldState(endInitialValue)

            LaunchedEffect(startTextFieldState.text) {
                val newValue = startTextFieldState.text.toString()
                if (newValue != startInitialValue) {
                    startPath?.let { ctx.onAction(NanoActionFactory.set(it, newValue)) }
                    onChange?.let { ctx.onAction(it) }
                }
            }

            LaunchedEffect(endTextFieldState.text) {
                val newValue = endTextFieldState.text.toString()
                if (newValue != endInitialValue) {
                    endPath?.let { ctx.onAction(NanoActionFactory.set(it, newValue)) }
                    onChange?.let { ctx.onAction(it) }
                }
            }

            LaunchedEffect(startInitialValue) {
                if (startTextFieldState.text.toString() != startInitialValue) {
                    startTextFieldState.setTextAndPlaceCursorAtEnd(startInitialValue)
                }
            }

            LaunchedEffect(endInitialValue) {
                if (endTextFieldState.text.toString() != endInitialValue) {
                    endTextFieldState.setTextAndPlaceCursorAtEnd(endInitialValue)
                }
            }

            Column(modifier = ctx.payload) {
                if (label != null) {
                    Text(label, color = JewelTheme.globalColors.text.normal)
                    Spacer(Modifier.height(4.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        state = startTextFieldState,
                        placeholder = { Text(startPlaceholder) },
                        modifier = Modifier.weight(1f)
                    )
                    Text("-", color = JewelTheme.globalColors.text.normal)
                    TextField(
                        state = endTextFieldState,
                        placeholder = { Text(endPlaceholder) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
