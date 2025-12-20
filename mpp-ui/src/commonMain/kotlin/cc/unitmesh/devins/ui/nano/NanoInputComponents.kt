package cc.unitmesh.devins.ui.nano

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
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

    private fun resolveStatePathFromBinding(ir: NanoIR, vararg keys: String): String? {
        val binding = keys.firstNotNullOfOrNull { ir.bindings?.get(it) }
        val exprFromBinding = binding?.expression?.trim()
        val exprFromProp = keys.firstNotNullOfOrNull { key ->
            ir.props[key]?.jsonPrimitive?.contentOrNull?.trim()
        }

        val rawExpr = (exprFromBinding ?: exprFromProp)?.trim() ?: return null
        val withoutMode = when {
            rawExpr.startsWith(":=") -> rawExpr.removePrefix(":=").trim()
            rawExpr.startsWith("<<") -> rawExpr.removePrefix("<<").trim()
            else -> rawExpr
        }

        val normalized = if (withoutMode.startsWith("state.")) withoutMode.removePrefix("state.") else withoutMode
        // Only treat simple identifiers or dotted paths as a writable state path.
        // This prevents expressions like '"x" in state.items' from being treated as a path.
        return normalized.takeIf { it.matches(Regex("[A-Za-z_]\\w*(\\.[A-Za-z_]\\w*)*")) }
    }

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
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: ""
        val statePath = resolveStatePathFromBinding(ir, "value", "bind")
        val onChange = ir.actions?.get("onChange")

        var value by remember(statePath, state[statePath]) {
            mutableStateOf(state[statePath]?.toString() ?: "")
        }

        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                value = newValue
                if (statePath != null) {
                    onAction(NanoActionIR(
                        type = "stateMutation",
                        payload = mapOf(
                            "path" to JsonPrimitive(statePath),
                            "operation" to JsonPrimitive("SET"),
                            "value" to JsonPrimitive(newValue)
                        )
                    ))
                }
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

        val statePath = resolveStatePathFromBinding(ir, "checked", "bind", "value")
        val checkedFromState = statePath?.let { state[it] as? Boolean }

        val checkedFromInList = inList?.let { parsed ->
            val list = state[parsed.listPath] as? List<*>
            list?.contains(parsed.item) == true
        }

        val checkedProp = ir.props["checked"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
        var uncontrolledChecked by remember(statePath, checkedProp) { mutableStateOf(checkedProp ?: false) }

        val checked = checkedFromInList ?: checkedFromState ?: uncontrolledChecked
        val rawLabel = NanoRenderUtils.resolveStringProp(ir, "label", state)
        val label = rawLabel.takeIf { it.isNotBlank() }?.let { NanoRenderUtils.interpolateText(it, state) }
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
                            onAction(
                                NanoActionIR(
                                    type = "stateMutation",
                                    payload = mapOf(
                                        "path" to JsonPrimitive(inList.listPath),
                                        "operation" to JsonPrimitive(if (newValue) "APPEND" else "REMOVE"),
                                        "value" to JsonPrimitive(inList.item)
                                    )
                                )
                            )
                        }
                        statePath != null -> {
                            onAction(
                                NanoActionIR(
                                    type = "stateMutation",
                                    payload = mapOf(
                                        "path" to JsonPrimitive(statePath),
                                        "operation" to JsonPrimitive("SET"),
                                        "value" to JsonPrimitive(newValue.toString())
                                    )
                                )
                            )
                        }
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
                                onAction(
                                    NanoActionIR(
                                        type = "stateMutation",
                                        payload = mapOf(
                                            "path" to JsonPrimitive(inList.listPath),
                                            "operation" to JsonPrimitive(if (toggled) "APPEND" else "REMOVE"),
                                            "value" to JsonPrimitive(inList.item)
                                        )
                                    )
                                )
                            }
                            statePath != null -> {
                                onAction(
                                    NanoActionIR(
                                        type = "stateMutation",
                                        payload = mapOf(
                                            "path" to JsonPrimitive(statePath),
                                            "operation" to JsonPrimitive("SET"),
                                            "value" to JsonPrimitive(toggled.toString())
                                        )
                                    )
                                )
                            }
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
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: ""
        val rows = ir.props["rows"]?.jsonPrimitive?.intOrNull ?: 4
        val statePath = resolveStatePathFromBinding(ir, "value", "bind")
        val onChange = ir.actions?.get("onChange")

        var value by remember(statePath, state[statePath]) {
            mutableStateOf(state[statePath]?.toString() ?: "")
        }

        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                value = newValue
                if (statePath != null) {
                    onAction(NanoActionIR(
                        type = "stateMutation",
                        payload = mapOf(
                            "path" to JsonPrimitive(statePath),
                            "operation" to JsonPrimitive("SET"),
                            "value" to JsonPrimitive(newValue)
                        )
                    ))
                }
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
        val label = ir.props["label"]?.jsonPrimitive?.content
        val statePath = resolveStatePathFromBinding(ir, "checked", "value", "bind")
        val checkedFromState = statePath?.let { state[it] as? Boolean }
        val checkedProp = (
            ir.props["checked"]?.jsonPrimitive?.contentOrNull
                ?: ir.props["value"]?.jsonPrimitive?.contentOrNull
        )?.toBooleanStrictOrNull()
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
                        onAction(NanoActionIR(
                            type = "stateMutation",
                            payload = mapOf(
                                "path" to JsonPrimitive(statePath),
                                "operation" to JsonPrimitive("SET"),
                                "value" to JsonPrimitive(newValue.toString())
                            )
                        ))
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
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: ""
        val statePath = resolveStatePathFromBinding(ir, "value", "bind")
        val valueFromState = statePath?.let { (state[it] as? Number)?.toString() ?: state[it]?.toString() }
        val valueProp = ir.props["value"]?.jsonPrimitive?.contentOrNull
        var uncontrolledValue by remember(statePath, valueProp) { mutableStateOf(valueProp ?: "") }
        val currentValue = valueFromState ?: uncontrolledValue
        val onChange = ir.actions?.get("onChange")

        OutlinedTextField(
            value = currentValue,
            onValueChange = { newValue ->
                if (!newValue.matches(Regex("-?\\d*\\.?\\d*"))) return@OutlinedTextField

                if (statePath != null) {
                    onAction(
                        NanoActionIR(
                            type = "stateMutation",
                            payload = mapOf(
                                "path" to JsonPrimitive(statePath),
                                "operation" to JsonPrimitive("SET"),
                                "value" to JsonPrimitive(newValue)
                            )
                        )
                    )
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
        val label = ir.props["label"]?.jsonPrimitive?.content
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: ""
        // NanoSpec uses bindings.bind for SmartTextField (keep compatibility with older value)
        val statePath = resolveStatePathFromBinding(ir, "value", "bind")
        val currentValue = state[statePath]?.toString() ?: ""
        val onChange = ir.actions?.get("onChange")

        OutlinedTextField(
            value = currentValue,
            onValueChange = { newValue ->
                if (statePath != null) {
                    onAction(NanoActionIR(
                        type = "stateMutation",
                        payload = mapOf(
                            "path" to JsonPrimitive(statePath),
                            "operation" to JsonPrimitive("SET"),
                            "value" to JsonPrimitive(newValue)
                        )
                    ))
                }
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
        val label = ir.props["label"]?.jsonPrimitive?.content
        val min = ir.props["min"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
        val max = ir.props["max"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 100f
        val step = ir.props["step"]?.jsonPrimitive?.content?.toFloatOrNull()
        // NanoSpec uses bindings.bind for Slider (keep compatibility with older value)
        val statePath = resolveStatePathFromBinding(ir, "value", "bind")
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
                        onAction(NanoActionIR(
                            type = "stateMutation",
                            payload = mapOf(
                                "path" to JsonPrimitive(statePath),
                                "operation" to JsonPrimitive("SET"),
                                "value" to JsonPrimitive(encoded)
                            )
                        ))
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
