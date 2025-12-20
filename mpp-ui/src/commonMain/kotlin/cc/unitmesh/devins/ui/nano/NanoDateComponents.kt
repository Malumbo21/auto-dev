package cc.unitmesh.devins.ui.nano

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.agent.Platform
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Date picker components for NanoUI Compose renderer.
 * Includes: DatePicker, DateRangePicker
 */
object NanoDateComponents {

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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RenderDatePicker(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: "Select date"
        val statePath = resolveStatePathFromBinding(ir, "value", "bind")
        val valueFromState = statePath?.let { state[it]?.toString() }
        val valueProp = ir.props["value"]?.jsonPrimitive?.contentOrNull
        var uncontrolledValue by remember(statePath, valueProp) { mutableStateOf(valueProp ?: "") }
        val currentValue = valueFromState ?: uncontrolledValue
        val onChange = ir.actions?.get("onChange")

        var showDialog by remember { mutableStateOf(false) }
        val datePickerState = rememberDatePickerState()

        // Display field - use widthIn to allow flexible sizing in HStack while having a reasonable default
        Box(
            modifier = modifier
                .widthIn(min = 120.dp)
                .clickable { showDialog = true }
        ) {
            OutlinedTextField(
                value = currentValue,
                onValueChange = { }, // Read-only, click to open dialog
                placeholder = { Text(placeholder) },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Date") },
                modifier = Modifier.fillMaxWidth(),
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
        }

        // On Desktop/other non-Android targets, Material3 DatePickerDialog can throw
        // "layouts are not part of the same hierarchy" due to popup positioning.
        // Use an inline panel as a safe fallback.
        if (showDialog) {
            val onConfirm: () -> Unit = {
                val selectedDate = datePickerState.selectedDateMillis
                if (selectedDate != null) {
                    val dateStr = NanoRenderUtils.formatDateFromMillis(selectedDate)
                    if (statePath != null) {
                        onAction(
                            NanoActionIR(
                                type = "stateMutation",
                                payload = mapOf(
                                    "path" to JsonPrimitive(statePath),
                                    "operation" to JsonPrimitive("SET"),
                                    "value" to JsonPrimitive(dateStr)
                                )
                            )
                        )
                    } else {
                        uncontrolledValue = dateStr
                    }
                    onChange?.let { onAction(it) }
                }
                showDialog = false
            }

            if (Platform.isAndroid) {
                DatePickerDialog(
                    onDismissRequest = { showDialog = false },
                    confirmButton = { TextButton(onClick = onConfirm) { Text("OK") } },
                    dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
                ) {
                    DatePicker(state = datePickerState)
                }
            } else {
                Surface(
                    modifier = modifier
                        .widthIn(min = 120.dp)
                        .padding(top = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        DatePicker(state = datePickerState)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                            TextButton(onClick = onConfirm) { Text("OK") }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RenderDateRangePicker(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        // NanoSpec uses bindings.bind for DateRangePicker
        val statePath = resolveStatePathFromBinding(ir, "bind", "value")
        val current = state[statePath]
        val onChange = ir.actions?.get("onChange")

        fun parseTwoDates(value: Any?): Pair<String, String> {
            return when (value) {
                is List<*> -> {
                    val start = value.getOrNull(0)?.toString().orEmpty()
                    val end = value.getOrNull(1)?.toString().orEmpty()
                    start to end
                }
                is Map<*, *> -> {
                    val start = value["start"]?.toString().orEmpty()
                    val end = value["end"]?.toString().orEmpty()
                    start to end
                }
                is String -> {
                    val parts = value.split("..", "—", " to ", limit = 2)
                    if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to ""
                }
                else -> "" to ""
            }
        }

        val (startStr, endStr) = remember(current) { parseTwoDates(current) }
        val displayValue = remember(startStr, endStr) {
            when {
                startStr.isNotBlank() && endStr.isNotBlank() -> "$startStr .. $endStr"
                startStr.isNotBlank() -> startStr
                endStr.isNotBlank() -> endStr
                else -> ""
            }
        }

        var showDialog by remember { mutableStateOf(false) }
        val dateRangeState = rememberDateRangePickerState()

        // Use widthIn to allow flexible sizing in HStack while having a reasonable default
        Box(
            modifier = modifier
                .widthIn(min = 180.dp)
                .clickable { showDialog = true }
        ) {
            OutlinedTextField(
                value = displayValue,
                onValueChange = { },
                placeholder = { Text("Select date range") },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
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
        }

        if (showDialog) {
            val onConfirm: () -> Unit = {
                val startMillis = dateRangeState.selectedStartDateMillis
                val endMillis = dateRangeState.selectedEndDateMillis

                if (statePath != null && startMillis != null && endMillis != null) {
                    val start = NanoRenderUtils.formatDateFromMillis(startMillis)
                    val end = NanoRenderUtils.formatDateFromMillis(endMillis)

                    val encodedValue = when (current) {
                        is List<*> -> "[\"$start\", \"$end\"]"
                        is Map<*, *> -> "{\"start\": \"$start\", \"end\": \"$end\"}"
                        else -> "$start..$end"
                    }

                    onAction(
                        NanoActionIR(
                            type = "stateMutation",
                            payload = mapOf(
                                "path" to JsonPrimitive(statePath),
                                "operation" to JsonPrimitive("SET"),
                                "value" to JsonPrimitive(encodedValue)
                            )
                        )
                    )

                    onChange?.let { onAction(it) }
                }

                showDialog = false
            }

            if (Platform.isAndroid) {
                DatePickerDialog(
                    onDismissRequest = { showDialog = false },
                    confirmButton = { TextButton(onClick = onConfirm) { Text("OK") } },
                    dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
                ) {
                    DateRangePicker(state = dateRangeState)
                }
            } else {
                Surface(
                    modifier = modifier
                        .widthIn(min = 180.dp)
                        .padding(top = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        DateRangePicker(state = dateRangeState)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                            TextButton(onClick = onConfirm) { Text("OK") }
                        }
                    }
                }
            }
        }
    }
}

