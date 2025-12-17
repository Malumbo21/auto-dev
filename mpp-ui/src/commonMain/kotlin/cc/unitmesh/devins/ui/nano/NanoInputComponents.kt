package cc.unitmesh.devins.ui.nano

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Input components for NanoUI Compose renderer.
 * Includes: Button, Input, Checkbox, TextArea, Select, DatePicker, Radio, RadioGroup,
 * Switch, NumberInput, SmartTextField, Slider, DateRangePicker
 */
object NanoInputComponents {

    @Composable
    fun RenderButton(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val rawLabel = ir.props["label"]?.jsonPrimitive?.content ?: "Button"
        // Interpolate {state.xxx} expressions in button label
        val label = NanoRenderUtils.interpolateText(rawLabel, state)
        val intent = ir.props["intent"]?.jsonPrimitive?.content
        val onClick = ir.actions?.get("onClick")

        when (intent) {
            "secondary" -> OutlinedButton(
                onClick = { onClick?.let { onAction(it) } },
                modifier = modifier
            ) {
                Text(label)
            }
            else -> {
                val colors = when (intent) {
                    "danger" -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    else -> ButtonDefaults.buttonColors()
                }
                Button(
                    onClick = { onClick?.let { onAction(it) } },
                    colors = colors,
                    modifier = modifier
                ) {
                    Text(label)
                }
            }
        }
    }

    @Composable
    fun RenderInput(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: ""
        val binding = ir.bindings?.get("value")
        val statePath = binding?.expression?.removePrefix("state.")

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
        val binding = ir.bindings?.get("checked")
        val statePath = binding?.expression?.removePrefix("state.")
        val checked = (state[statePath] as? Boolean) ?: false
        val label = ir.props["label"]?.jsonPrimitive?.content

        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = checked,
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
                    }
                }
            )
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable {
                        // Allow clicking label to toggle checkbox
                        if (statePath != null) {
                            onAction(NanoActionIR(
                                type = "stateMutation",
                                payload = mapOf(
                                    "path" to JsonPrimitive(statePath),
                                    "operation" to JsonPrimitive("SET"),
                                    "value" to JsonPrimitive((!checked).toString())
                                )
                            ))
                        }
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
        val binding = ir.bindings?.get("value")
        val statePath = binding?.expression?.removePrefix("state.")

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
            },
            placeholder = { Text(placeholder) },
            modifier = modifier.fillMaxWidth().height((rows * 24).dp),
            minLines = rows
        )
    }

    @Composable
    fun RenderSelect(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: "Select..."
        val binding = ir.bindings?.get("value")
        val statePath = binding?.expression?.removePrefix("state.")
        val selectedValue = state[statePath]?.toString() ?: ""
        var expanded by remember { mutableStateOf(false) }

        // Read options from IR props
        val options: List<String> = ir.props["options"]?.let { optionsElement ->
            try {
                (optionsElement as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            } catch (e: Exception) { null }
        } ?: emptyList()

        Box(modifier = modifier) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (selectedValue.isNotEmpty()) selectedValue else placeholder)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            if (statePath != null) {
                                onAction(NanoActionIR(
                                    type = "stateMutation",
                                    payload = mapOf(
                                        "path" to JsonPrimitive(statePath),
                                        "operation" to JsonPrimitive("SET"),
                                        "value" to JsonPrimitive(option)
                                    )
                                ))
                            }
                        }
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RenderDatePicker(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: "Select date"
        val binding = ir.bindings?.get("value")
        val statePath = binding?.expression?.removePrefix("state.")
        val currentValue = state[statePath]?.toString() ?: ""

        var showDialog by remember { mutableStateOf(false) }
        val datePickerState = rememberDatePickerState()

        // Display field
        OutlinedTextField(
            value = currentValue,
            onValueChange = { }, // Read-only, click to open dialog
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Date") },
            modifier = modifier
                .fillMaxWidth()
                .clickable { showDialog = true },
            singleLine = true,
            readOnly = true,
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        // DatePicker Dialog
        if (showDialog) {
            DatePickerDialog(
                onDismissRequest = { showDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        val selectedDate = datePickerState.selectedDateMillis
                        if (selectedDate != null && statePath != null) {
                            val dateStr = NanoRenderUtils.formatDateFromMillis(selectedDate)

                            onAction(NanoActionIR(
                                type = "stateMutation",
                                payload = mapOf(
                                    "path" to JsonPrimitive(statePath),
                                    "operation" to JsonPrimitive("SET"),
                                    "value" to JsonPrimitive(dateStr)
                                )
                            ))
                        }
                        showDialog = false
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }

    @Composable
    fun RenderRadio(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val option = ir.props["option"]?.jsonPrimitive?.content ?: ""
        val label = ir.props["label"]?.jsonPrimitive?.content ?: option
        val binding = ir.bindings?.get("value")
        val statePath = binding?.expression?.removePrefix("state.")
        val selectedValue = state[statePath]?.toString() ?: ""
        val isSelected = selectedValue == option

        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = isSelected,
                onClick = {
                    if (statePath != null) {
                        onAction(NanoActionIR(
                            type = "stateMutation",
                            payload = mapOf(
                                "path" to JsonPrimitive(statePath),
                                "operation" to JsonPrimitive("SET"),
                                "value" to JsonPrimitive(option)
                            )
                        ))
                    }
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label)
        }
    }

    @Composable
    fun RenderRadioGroup(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier,
        renderNode: @Composable (NanoIR, Map<String, Any>, (NanoActionIR) -> Unit, Modifier) -> Unit
    ) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ir.children?.forEach { child -> renderNode(child, state, onAction, Modifier) }
        }
    }

    @Composable
    fun RenderSwitch(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val label = ir.props["label"]?.jsonPrimitive?.content
        val binding = ir.bindings?.get("value")
        val statePath = binding?.expression?.removePrefix("state.")
        val isChecked = state[statePath] as? Boolean ?: false

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
                                "value" to JsonPrimitive(newValue)
                            )
                        ))
                    }
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
        val binding = ir.bindings?.get("value")
        val statePath = binding?.expression?.removePrefix("state.")
        val currentValue = (state[statePath] as? Number)?.toString() ?: state[statePath]?.toString() ?: ""

        OutlinedTextField(
            value = currentValue,
            onValueChange = { newValue ->
                if (statePath != null && newValue.matches(Regex("-?\\d*\\.?\\d*"))) {
                    onAction(NanoActionIR(
                        type = "stateMutation",
                        payload = mapOf(
                            "path" to JsonPrimitive(statePath),
                            "operation" to JsonPrimitive("SET"),
                            "value" to JsonPrimitive(newValue.toDoubleOrNull() ?: 0)
                        )
                    ))
                }
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
        val binding = ir.bindings?.get("value")
        val statePath = binding?.expression?.removePrefix("state.")
        val currentValue = state[statePath]?.toString() ?: ""

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
            },
            label = label?.let { { Text(it) } },
            placeholder = { Text(placeholder) },
            modifier = modifier.fillMaxWidth()
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
        val binding = ir.bindings?.get("value")
        val statePath = binding?.expression?.removePrefix("state.")
        val currentValue = (state[statePath] as? Number)?.toFloat() ?: min

        Column(modifier = modifier.fillMaxWidth()) {
            if (label != null) {
                Text(label, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(4.dp))
            }
            Slider(
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
                },
                valueRange = min..max,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable
    fun RenderDateRangePicker(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                placeholder = { Text("Start date") },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = "",
                onValueChange = {},
                placeholder = { Text("End date") },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
    }
}
