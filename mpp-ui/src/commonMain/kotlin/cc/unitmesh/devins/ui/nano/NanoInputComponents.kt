package cc.unitmesh.devins.ui.nano

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.unitmesh.xuiper.action.NanoActionFactory
import cc.unitmesh.xuiper.eval.evaluator.NanoExpressionEvaluator
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.ir.booleanProp
import cc.unitmesh.xuiper.ir.doubleProp
import cc.unitmesh.xuiper.ir.intProp
import cc.unitmesh.xuiper.ir.stringProp
import cc.unitmesh.xuiper.props.NanoBindingResolver
import kotlin.math.round

/**
 * Input components for NanoUI Compose renderer.
 * Includes: Input, Checkbox, TextArea, Switch, NumberInput, SmartTextField, Slider
 *
 * Note:
 * - Button components have been extracted to [NanoButtonComponents]
 * - Date components (DatePicker, DateRangePicker) have been extracted to [NanoDateComponents]
 * - Selection components (Select, Radio, RadioGroup) have been extracted to [NanoSelectionComponents]
 */
object NanoInputComponents {

    private data class InListExpression(val item: String, val listPath: String)

    private fun parseInListExpression(expr: String): InListExpression? {
        val trimmed = expr.trim()
        // Support: "item" in state.list
        val match = Regex("^(\"[^\"]+\"|'[^']+')\\s+in\\s+state\\.([A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)*)$")
            .matchEntire(trimmed)
            ?: return null

        val rawItem = match.groupValues[1]
        val item = rawItem.removeSurrounding("\"", "\"").removeSurrounding("'", "'")
        val listPath = match.groupValues[2]
        return InListExpression(item = item, listPath = listPath)
    }

    // ==================== Button Components (delegated to NanoButtonComponents) ====================

    /**
     * Renders a button component.
     * Delegates to [NanoButtonComponents.RenderButton]
     */
    @Composable
    fun RenderButton(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        NanoButtonComponents.RenderButton(ir, state, onAction, modifier)
    }

    // ==================== Input Components ====================

    @Composable
    fun RenderInput(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val placeholder = ir.stringProp("placeholder") ?: ""
        val statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind")
        val onChange = ir.actions?.get("onChange")

        var value by remember(statePath, state[statePath]) {
            mutableStateOf(state[statePath]?.toString() ?: "")
        }

        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                value = newValue
                statePath?.let { onAction(NanoActionFactory.set(it, newValue)) }
                onChange?.let { onAction(it) }
            },
            placeholder = { Text(placeholder) },
            modifier = modifier.widthIn(min = 120.dp, max = 300.dp),
            singleLine = true
        )
    }

    @Composable
    fun RenderCheckbox(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val checkedBindingExpr = ir.bindings?.get("checked")?.expression
        val inList = checkedBindingExpr?.let { parseInListExpression(it) }

        val statePath = NanoBindingResolver.resolveStatePath(ir, "checked", "bind", "value")
        val checkedFromState = statePath?.let { state[it] as? Boolean }

        val checkedFromInList = inList?.let { parsed ->
            val list = state[parsed.listPath] as? List<*>
            list?.contains(parsed.item) == true
        }

        val checkedProp = ir.booleanProp("checked")
        var uncontrolledChecked by remember(statePath, checkedProp) { mutableStateOf(checkedProp ?: false) }

        val checked = checkedFromInList ?: checkedFromState ?: uncontrolledChecked
        val rawLabel = NanoExpressionEvaluator.resolveStringProp(ir, "label", state)
        val label = rawLabel.takeIf { it.isNotBlank() }?.let { NanoExpressionEvaluator.interpolateText(it, state) }
        val onChange = ir.actions?.get("onChange")

        Row(
            modifier = modifier,
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
                            onAction(action)
                        }
                        statePath != null -> onAction(NanoActionFactory.set(statePath, newValue))
                        else -> uncontrolledChecked = newValue
                    }
                    onChange?.let { onAction(it) }
                }
            )
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable {
                        // Allow clicking label to toggle checkbox
                        val toggled = !checked
                        when {
                            inList != null -> {
                                val action = if (toggled) {
                                    NanoActionFactory.append(inList.listPath, inList.item)
                                } else {
                                    NanoActionFactory.remove(inList.listPath, inList.item)
                                }
                                onAction(action)
                            }
                            statePath != null -> onAction(NanoActionFactory.set(statePath, toggled))
                            else -> uncontrolledChecked = toggled
                        }
                        onChange?.let { onAction(it) }
                    }
                )
            }
        }
    }

    @Composable
    fun RenderTextArea(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val placeholder = ir.stringProp("placeholder") ?: ""
        val rows = ir.intProp("rows") ?: 4
        val statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind")
        val onChange = ir.actions?.get("onChange")

        var value by remember(statePath, state[statePath]) {
            mutableStateOf(state[statePath]?.toString() ?: "")
        }

        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                value = newValue
                statePath?.let { onAction(NanoActionFactory.set(it, newValue)) }
                onChange?.let { onAction(it) }
            },
            placeholder = { Text(placeholder) },
            modifier = modifier.widthIn(min = 200.dp).height((rows * 24).dp),
            minLines = rows
        )
    }

    /**
     * Renders a select component.
     * Delegates to [NanoSelectionComponents.RenderSelect]
     */
    @Composable
    fun RenderSelect(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        NanoSelectionComponents.RenderSelect(ir, state, onAction, modifier)
    }

    /**
     * Renders a date picker component.
     * Delegates to [NanoDateComponents.RenderDatePicker]
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RenderDatePicker(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        NanoDateComponents.RenderDatePicker(ir, state, onAction, modifier)
    }

    /**
     * Renders a radio button component.
     * Delegates to [NanoSelectionComponents.RenderRadio]
     */
    @Composable
    fun RenderRadio(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        NanoSelectionComponents.RenderRadio(ir, state, onAction, modifier)
    }

    /**
     * Renders a radio group component.
     * Delegates to [NanoSelectionComponents.RenderRadioGroup]
     */
    @Composable
    fun RenderRadioGroup(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier,
        renderNode: @Composable (NanoIR, Map<String, Any>, (NanoActionIR) -> Unit, Modifier) -> Unit
    ) {
        NanoSelectionComponents.RenderRadioGroup(ir, state, onAction, modifier, renderNode)
    }

    @Composable
    fun RenderSwitch(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val label = ir.stringProp("label")
        val statePath = NanoBindingResolver.resolveStatePath(ir, "checked", "value", "bind")
        val checkedFromState = statePath?.let { state[it] as? Boolean }
        val checkedProp = ir.booleanProp("checked") ?: ir.booleanProp("value")
        var uncontrolledChecked by remember(statePath, checkedProp) { mutableStateOf(checkedProp ?: false) }
        val isChecked = checkedFromState ?: uncontrolledChecked
        val onChange = ir.actions?.get("onChange")

        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            if (label != null) {
                Text(label, modifier = Modifier.weight(1f))
            }
            Switch(
                checked = isChecked,
                onCheckedChange = { newValue ->
                    if (statePath != null) {
                        onAction(NanoActionFactory.set(statePath, newValue))
                    } else {
                        uncontrolledChecked = newValue
                    }
                    onChange?.let { onAction(it) }
                }
            )
        }
    }

    @Composable
    fun RenderNumberInput(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val placeholder = ir.stringProp("placeholder") ?: ""
        val statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind")
        val valueFromState = statePath?.let { (state[it] as? Number)?.toString() ?: state[it]?.toString() }
        val valueProp = ir.stringProp("value")
        var uncontrolledValue by remember(statePath, valueProp) { mutableStateOf(valueProp ?: "") }
        val currentValue = valueFromState ?: uncontrolledValue
        val onChange = ir.actions?.get("onChange")

        OutlinedTextField(
            value = currentValue,
            onValueChange = { newValue ->
                if (!newValue.matches(Regex("-?\\d*\\.?\\d*"))) return@OutlinedTextField

                if (statePath != null) {
                    onAction(NanoActionFactory.set(statePath, newValue))
                } else {
                    uncontrolledValue = newValue
                }
                onChange?.let { onAction(it) }
            },
            placeholder = { Text(placeholder) },
            modifier = modifier.widthIn(min = 100.dp, max = 200.dp),
            singleLine = true
        )
    }

    @Composable
    fun RenderSmartTextField(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val label = ir.stringProp("label")
        val placeholder = ir.stringProp("placeholder") ?: ""
        // NanoSpec uses bindings.bind for SmartTextField (keep compatibility with older value)
        val statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind")
        val currentValue = state[statePath]?.toString() ?: ""
        val onChange = ir.actions?.get("onChange")

        OutlinedTextField(
            value = currentValue,
            onValueChange = { newValue ->
                statePath?.let { onAction(NanoActionFactory.set(it, newValue)) }
                onChange?.let { onAction(it) }
            },
            label = label?.let { { Text(it) } },
            placeholder = { Text(placeholder) },
            modifier = modifier.widthIn(min = 200.dp)
        )
    }

    @Composable
    fun RenderSlider(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val label = ir.stringProp("label")
        val min = ir.doubleProp("min")?.toFloat() ?: 0f
        val max = ir.doubleProp("max")?.toFloat() ?: 100f
        val step = ir.doubleProp("step")?.toFloat()
        // NanoSpec uses bindings.bind for Slider (keep compatibility with older value)
        val statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind")
        val rawStateValue = statePath?.let { state[it] }
        val currentValue = ((rawStateValue as? Number)?.toFloat() ?: min).coerceIn(min, max)
        val onChange = ir.actions?.get("onChange")

        val steps = remember(min, max, step) {
            if (step == null || step <= 0f) 0
            else {
                val totalSteps = ((max - min) / step).toInt() - 1
                totalSteps.coerceAtLeast(0)
            }
        }

        // Format the display value based on the state type
        val displayValue = when (rawStateValue) {
            is Int -> currentValue.toInt().toString()
            is Float, is Double -> (round(currentValue * 10f) / 10f).toString()
            else -> currentValue.toInt().toString()
        }

        Column(modifier = modifier.widthIn(min = 150.dp)) {
            // Display label and current value in a row
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
                    // Display current value
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
                        onAction(NanoActionFactory.set(statePath, encoded))
                    }
                    onChange?.let { onAction(it) }
                },
                valueRange = min..max,
                steps = steps,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    /**
     * Renders a date range picker component.
     * Delegates to [NanoDateComponents.RenderDateRangePicker]
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RenderDateRangePicker(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        NanoDateComponents.RenderDateRangePicker(ir, state, onAction, modifier)
    }
}
