package cc.unitmesh.devins.idea.renderer.nano.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.xuiper.action.NanoActionFactory
import cc.unitmesh.xuiper.eval.evaluator.NanoExpressionEvaluator
import cc.unitmesh.xuiper.ir.booleanProp
import cc.unitmesh.xuiper.ir.doubleProp
import cc.unitmesh.xuiper.ir.intProp
import cc.unitmesh.xuiper.ir.stringProp
import cc.unitmesh.xuiper.props.NanoBindingResolver
import cc.unitmesh.xuiper.render.stateful.NanoNodeRenderer
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Slider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import kotlin.math.round

/**
 * Jewel input components for NanoUI IntelliJ IDEA renderer.
 * Includes: Input, TextArea, Switch, NumberInput, SmartTextField, Slider, Checkbox
 */
object JewelInputComponents {

    private fun resolveText(ctx: JewelContext, propKey: String): String {
        val raw = NanoExpressionEvaluator.resolveStringProp(ctx.node, propKey, ctx.state)
        return NanoExpressionEvaluator.interpolateText(raw, ctx.state)
    }

    val inputRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val ir = ctx.node
            val placeholder = ir.stringProp("placeholder") ?: ""
            val statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind")
            val onChange = ir.actions?.get("onChange")
            val initialValue = ctx.state[statePath]?.toString() ?: ""

            val textFieldState = rememberTextFieldState(initialValue)

            // Sync state changes back to nano state
            LaunchedEffect(textFieldState.text) {
                val newValue = textFieldState.text.toString()
                if (newValue != initialValue) {
                    statePath?.let { ctx.onAction(NanoActionFactory.set(it, newValue)) }
                    onChange?.let { ctx.onAction(it) }
                }
            }

            // Sync external state changes to text field
            LaunchedEffect(initialValue) {
                if (textFieldState.text.toString() != initialValue) {
                    textFieldState.setTextAndPlaceCursorAtEnd(initialValue)
                }
            }

            TextField(
                state = textFieldState,
                placeholder = { Text(placeholder) },
                modifier = ctx.payload.widthIn(min = 120.dp, max = 300.dp)
            )
        }
    }

    val textAreaRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val ir = ctx.node
            val placeholder = ir.stringProp("placeholder") ?: ""
            val rows = ir.intProp("rows") ?: 4
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

            TextField(
                state = textFieldState,
                placeholder = { Text(placeholder) },
                modifier = ctx.payload.widthIn(min = 200.dp).height((rows * 24).dp)
            )
        }
    }

    val switchRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val ir = ctx.node
            val label = ir.stringProp("label")
            val statePath = NanoBindingResolver.resolveStatePath(ir, "checked", "value", "bind")
            val checkedFromState = statePath?.let { ctx.state[it] as? Boolean }
            val checkedProp = ir.booleanProp("checked") ?: ir.booleanProp("value")
            var uncontrolledChecked by remember(statePath, checkedProp) { mutableStateOf(checkedProp ?: false) }
            val isChecked = checkedFromState ?: uncontrolledChecked
            val onChange = ir.actions?.get("onChange")

            Row(modifier = ctx.payload, verticalAlignment = Alignment.CenterVertically) {
                if (label != null) {
                    Text(label, modifier = Modifier.weight(1f), color = JewelTheme.globalColors.text.normal)
                }
                // Jewel doesn't have Switch, use Checkbox as fallback
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { newValue ->
                        if (statePath != null) {
                            ctx.onAction(NanoActionFactory.set(statePath, newValue))
                        } else {
                            uncontrolledChecked = newValue
                        }
                        onChange?.let { ctx.onAction(it) }
                    }
                )
            }
        }
    }

    val numberInputRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val ir = ctx.node
            val placeholder = ir.stringProp("placeholder") ?: ""
            val statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind")
            val valueFromState = statePath?.let { (ctx.state[it] as? Number)?.toString() ?: ctx.state[it]?.toString() }
            val valueProp = ir.stringProp("value")
            val initialValue = valueFromState ?: valueProp ?: ""
            val onChange = ir.actions?.get("onChange")

            val textFieldState = rememberTextFieldState(initialValue)

            LaunchedEffect(textFieldState.text) {
                val newValue = textFieldState.text.toString()
                if (newValue.matches(Regex("-?\\d*\\.?\\d*")) && newValue != initialValue) {
                    statePath?.let { ctx.onAction(NanoActionFactory.set(it, newValue)) }
                    onChange?.let { ctx.onAction(it) }
                }
            }

            LaunchedEffect(initialValue) {
                if (textFieldState.text.toString() != initialValue) {
                    textFieldState.setTextAndPlaceCursorAtEnd(initialValue)
                }
            }

            TextField(
                state = textFieldState,
                placeholder = { Text(placeholder) },
                modifier = ctx.payload.widthIn(min = 100.dp, max = 200.dp)
            )
        }
    }

    val smartTextFieldRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val ir = ctx.node
            val label = ir.stringProp("label")
            val placeholder = ir.stringProp("placeholder") ?: ""
            val statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind")
            val initialValue = ctx.state[statePath]?.toString() ?: ""
            val onChange = ir.actions?.get("onChange")

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
                    modifier = Modifier.widthIn(min = 200.dp)
                )
            }
        }
    }

    val sliderRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val ir = ctx.node
            val label = ir.stringProp("label")
            val min = ir.doubleProp("min")?.toFloat() ?: 0f
            val max = ir.doubleProp("max")?.toFloat() ?: 100f
            val step = ir.doubleProp("step")?.toFloat()
            val statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind")
            val rawStateValue = statePath?.let { ctx.state[it] }
            val currentValue = ((rawStateValue as? Number)?.toFloat() ?: min).coerceIn(min, max)
            val onChange = ir.actions?.get("onChange")

            val steps = remember(min, max, step) {
                if (step == null || step <= 0f) 0
                else {
                    val totalSteps = ((max - min) / step).toInt() - 1
                    totalSteps.coerceAtLeast(0)
                }
            }

            val displayValue = when (rawStateValue) {
                is Int -> currentValue.toInt().toString()
                is Float, is Double -> (round(currentValue * 10f) / 10f).toString()
                else -> currentValue.toInt().toString()
            }

            Column(modifier = ctx.payload.widthIn(min = 150.dp)) {
                if (label != null || statePath != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (label != null) {
                            Text(text = label, color = JewelTheme.globalColors.text.normal)
                        }
                        Text(text = displayValue, color = JewelTheme.globalColors.text.info)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Slider(
                    value = currentValue,
                    onValueChange = { newValue ->
                        if (statePath != null) {
                            val encoded = when (rawStateValue) {
                                is Int -> newValue.toInt().toString()
                                else -> newValue.toString()
                            }
                            ctx.onAction(NanoActionFactory.set(statePath, encoded))
                        }
                        onChange?.let { ctx.onAction(it) }
                    },
                    valueRange = min..max,
                    steps = steps,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    val checkboxRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val ir = ctx.node
            val checkedBindingExpr = ir.bindings?.get("checked")?.expression
            val inList = checkedBindingExpr?.let { parseInListExpression(it) }

            val statePath = NanoBindingResolver.resolveStatePath(ir, "checked", "bind", "value")
            val checkedFromState = statePath?.let { ctx.state[it] as? Boolean }

            val checkedFromInList = inList?.let { parsed ->
                val list = ctx.state[parsed.listPath] as? List<*>
                list?.contains(parsed.item) == true
            }

            val checkedProp = ir.booleanProp("checked")
            var uncontrolledChecked by remember(statePath, checkedProp) { mutableStateOf(checkedProp ?: false) }

            val checked = checkedFromInList ?: checkedFromState ?: uncontrolledChecked
            val label = resolveText(ctx, "label").takeIf { it.isNotBlank() }
            val onChange = ir.actions?.get("onChange")

            Row(
                modifier = ctx.payload,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { newValue ->
                        when {
                            inList != null -> {
                                val action = if (newValue) {
                                    NanoActionFactory.append(inList.listPath, inList.item)
                                } else {
                                    NanoActionFactory.remove(inList.listPath, inList.item)
                                }
                                ctx.onAction(action)
                            }
                            statePath != null -> ctx.onAction(NanoActionFactory.set(statePath, newValue))
                            else -> uncontrolledChecked = newValue
                        }
                        onChange?.let { ctx.onAction(it) }
                    }
                )
                if (label != null) {
                    Text(
                        text = label,
                        color = JewelTheme.globalColors.text.normal,
                        modifier = Modifier.clickable {
                            val toggled = !checked
                            when {
                                inList != null -> {
                                    val action = if (toggled) {
                                        NanoActionFactory.append(inList.listPath, inList.item)
                                    } else {
                                        NanoActionFactory.remove(inList.listPath, inList.item)
                                    }
                                    ctx.onAction(action)
                                }
                                statePath != null -> ctx.onAction(NanoActionFactory.set(statePath, toggled))
                                else -> uncontrolledChecked = toggled
                            }
                            onChange?.let { ctx.onAction(it) }
                        }
                    )
                }
            }
        }
    }

    private data class InListExpression(val item: String, val listPath: String)

    private fun parseInListExpression(expr: String): InListExpression? {
        val trimmed = expr.trim()
        val match = Regex("^(\"[^\"]+\"|'[^']+')\\s+in\\s+state\\.([A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)*)$")
            .matchEntire(trimmed) ?: return null

        val rawItem = match.groupValues[1]
        val item = rawItem.removeSurrounding("\"", "\"").removeSurrounding("'", "'")
        val listPath = match.groupValues[2]
        return InListExpression(item = item, listPath = listPath)
    }
}
