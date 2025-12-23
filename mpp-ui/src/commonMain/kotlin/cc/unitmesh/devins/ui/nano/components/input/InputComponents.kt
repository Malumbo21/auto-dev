package cc.unitmesh.devins.ui.nano.components.input

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.nano.ComposeNodeContext
import cc.unitmesh.devins.ui.nano.NanoPropsResolver
import cc.unitmesh.xuiper.action.NanoActionFactory
import cc.unitmesh.xuiper.ir.booleanProp
import cc.unitmesh.xuiper.ir.doubleProp
import cc.unitmesh.xuiper.ir.intProp
import cc.unitmesh.xuiper.ir.stringProp
import cc.unitmesh.xuiper.props.NanoBindingResolver
import cc.unitmesh.xuiper.render.stateful.NanoNodeRenderer
import kotlin.math.round

/**
 * Input components for NanoUI Compose renderer.
 * Includes: Input, TextArea, Switch, NumberInput, SmartTextField, Slider, Checkbox
 *
 * All components use the unified NanoNodeContext interface.
 */
object InputComponents {

    val inputRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderInput(ctx) }
    }

    val textAreaRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderTextArea(ctx) }
    }

    val switchRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderSwitch(ctx) }
    }

    val numberInputRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderNumberInput(ctx) }
    }

    val smartTextFieldRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderSmartTextField(ctx) }
    }

    val sliderRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderSlider(ctx) }
    }

    val checkboxRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderCheckbox(ctx) }
    }

    @Composable
    fun RenderInput(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val placeholder = ir.stringProp("placeholder") ?: ""
        val statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind")
        val onChange = ir.actions?.get("onChange")

        var value by remember(statePath, ctx.state[statePath]) {
            mutableStateOf(ctx.state[statePath]?.toString() ?: "")
        }

        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                value = newValue
                statePath?.let { ctx.onAction(NanoActionFactory.set(it, newValue)) }
                onChange?.let { ctx.onAction(it) }
            },
            placeholder = { Text(placeholder) },
            modifier = ctx.payload.widthIn(min = 120.dp, max = 300.dp),
            singleLine = true
        )
    }

    @Composable
    fun RenderTextArea(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val placeholder = ir.stringProp("placeholder") ?: ""
        val rows = ir.intProp("rows") ?: 4
        val statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind")
        val onChange = ir.actions?.get("onChange")

        var value by remember(statePath, ctx.state[statePath]) {
            mutableStateOf(ctx.state[statePath]?.toString() ?: "")
        }

        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                value = newValue
                statePath?.let { ctx.onAction(NanoActionFactory.set(it, newValue)) }
                onChange?.let { ctx.onAction(it) }
            },
            placeholder = { Text(placeholder) },
            modifier = ctx.payload.widthIn(min = 200.dp).height((rows * 24).dp),
            minLines = rows
        )
    }

    @Composable
    fun RenderSwitch(ctx: ComposeNodeContext) {
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
                Text(label, modifier = Modifier.weight(1f))
            }
            Switch(
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

    @Composable
    fun RenderNumberInput(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val placeholder = ir.stringProp("placeholder") ?: ""
        val statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind")
        val valueFromState = statePath?.let { (ctx.state[it] as? Number)?.toString() ?: ctx.state[it]?.toString() }
        val valueProp = ir.stringProp("value")
        var uncontrolledValue by remember(statePath, valueProp) { mutableStateOf(valueProp ?: "") }
        val currentValue = valueFromState ?: uncontrolledValue
        val onChange = ir.actions?.get("onChange")

        OutlinedTextField(
            value = currentValue,
            onValueChange = { newValue ->
                if (!newValue.matches(Regex("-?\\d*\\.?\\d*"))) return@OutlinedTextField

                if (statePath != null) {
                    ctx.onAction(NanoActionFactory.set(statePath, newValue))
                } else {
                    uncontrolledValue = newValue
                }
                onChange?.let { ctx.onAction(it) }
            },
            placeholder = { Text(placeholder) },
            modifier = ctx.payload.widthIn(min = 100.dp, max = 200.dp),
            singleLine = true
        )
    }

    @Composable
    fun RenderSmartTextField(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val label = ir.stringProp("label")
        val placeholder = ir.stringProp("placeholder") ?: ""
        val statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind")
        val currentValue = ctx.state[statePath]?.toString() ?: ""
        val onChange = ir.actions?.get("onChange")

        OutlinedTextField(
            value = currentValue,
            onValueChange = { newValue ->
                statePath?.let { ctx.onAction(NanoActionFactory.set(it, newValue)) }
                onChange?.let { ctx.onAction(it) }
            },
            label = label?.let { { Text(it) } },
            placeholder = { Text(placeholder) },
            modifier = ctx.payload.widthIn(min = 200.dp)
        )
    }

    @Composable
    fun RenderSlider(ctx: ComposeNodeContext) {
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
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = displayValue,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
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

    @Composable
    fun RenderCheckbox(ctx: ComposeNodeContext) {
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
        val rawLabel = NanoPropsResolver.resolveStringRaw(ir, "label", ctx.state)
        val label = rawLabel.takeIf { it.isNotBlank() }?.let { NanoPropsResolver.resolveString(ir, "label", ctx.state) }
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
                    style = MaterialTheme.typography.bodyMedium,
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
