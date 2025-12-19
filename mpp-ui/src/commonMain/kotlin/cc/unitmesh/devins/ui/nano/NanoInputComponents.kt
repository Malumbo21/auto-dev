package cc.unitmesh.devins.ui.nano

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.unitmesh.agent.Platform
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.round

/**
 * Input components for NanoUI Compose renderer.
 * Includes: Button, Input, Checkbox, TextArea, Select, DatePicker, Radio, RadioGroup,
 * Switch, NumberInput, SmartTextField, Slider, DateRangePicker
 */
object NanoInputComponents {

    private val lenientJson = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private data class RadioOption(val value: String, val label: String)

    private fun unquoteString(raw: String): String {
        val t = raw.trim()
        return t.removeSurrounding("\"", "\"")
            .removeSurrounding("'", "'")
            .trim()
    }

    private fun splitTopLevelCommaSeparated(input: String): List<String> {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        var braceDepth = 0
        var bracketDepth = 0
        var inSingle = false
        var inDouble = false
        var escaped = false

        fun flush() {
            val s = sb.toString().trim()
            if (s.isNotEmpty()) parts += s
            sb.clear()
        }

        input.forEach { c ->
            if (escaped) {
                sb.append(c)
                escaped = false
                return@forEach
            }
            when (c) {
                '\\' -> {
                    if (inSingle || inDouble) {
                        sb.append(c)
                        escaped = true
                    } else {
                        sb.append(c)
                    }
                }
                '\'' -> {
                    sb.append(c)
                    if (!inDouble) inSingle = !inSingle
                }
                '"' -> {
                    sb.append(c)
                    if (!inSingle) inDouble = !inDouble
                }
                '{' -> {
                    sb.append(c)
                    if (!inSingle && !inDouble) braceDepth++
                }
                '}' -> {
                    sb.append(c)
                    if (!inSingle && !inDouble && braceDepth > 0) braceDepth--
                }
                '[' -> {
                    sb.append(c)
                    if (!inSingle && !inDouble) bracketDepth++
                }
                ']' -> {
                    sb.append(c)
                    if (!inSingle && !inDouble && bracketDepth > 0) bracketDepth--
                }
                ',' -> {
                    if (!inSingle && !inDouble && braceDepth == 0 && bracketDepth == 0) {
                        flush()
                    } else {
                        sb.append(c)
                    }
                }
                else -> sb.append(c)
            }
        }
        flush()
        return parts
    }

    private fun parseRadioOptionsFromDslLiteral(raw: String): List<RadioOption> {
        // Some upstream IR generators store strings with escaped quotes (e.g. \"train\")
        // inside a Kotlin raw string / JSON primitive. Normalize them so the lightweight
        // DSL parser can still recognize quoted values.
        val trimmed = raw.trim()
            .replace("\\\"", "\"")
            .replace("\\'", "'")
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()

        val inner = trimmed.removePrefix("[").removeSuffix("]").trim()
        if (inner.isBlank()) return emptyList()

        val items = splitTopLevelCommaSeparated(inner)

        fun extractField(item: String, field: String): String? {
            val pattern = Regex(
                """(?is)(?:\"$field\"|$field)\s*:\s*(\"(?:\\\\.|[^\"])*\"|'(?:\\\\.|[^'])*'|[^,}\]]+)"""
            )
            val m = pattern.find(item) ?: return null
            return unquoteString(m.groupValues[1])
        }

        return items.mapNotNull { itemRaw ->
            val item = itemRaw.trim()
            when {
                item.startsWith("{") && item.endsWith("}") -> {
                    val value = extractField(item, "value") ?: return@mapNotNull null
                    val label = extractField(item, "label") ?: value
                    RadioOption(value = value, label = label)
                }
                item.startsWith("\"") || item.startsWith("'") -> {
                    val v = unquoteString(item)
                    if (v.isBlank()) null else RadioOption(value = v, label = v)
                }
                else -> {
                    val v = item.trim()
                    if (v.isBlank()) null else RadioOption(value = v, label = v)
                }
            }
        }
    }

    private fun parseRadioOptions(optionsElement: JsonElement?): List<RadioOption> {
        if (optionsElement == null) return emptyList()

        fun fromJsonArray(arr: JsonArray): List<RadioOption> {
            return arr.mapNotNull { el ->
                when (el) {
                    is JsonPrimitive -> {
                        val v = el.contentOrNull ?: return@mapNotNull null
                        RadioOption(value = v, label = v)
                    }
                    is JsonObject -> {
                        val v = el["value"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val l = el["label"]?.jsonPrimitive?.contentOrNull ?: v
                        RadioOption(value = v, label = l)
                    }
                    else -> null
                }
            }
        }

        return when (optionsElement) {
            is JsonArray -> fromJsonArray(optionsElement)
            is JsonPrimitive -> {
                val raw = optionsElement.contentOrNull ?: return emptyList()
                val trimmed = raw.trim()
                if (!trimmed.startsWith("[")) return emptyList()
                try {
                    val parsed = lenientJson.parseToJsonElement(trimmed)
                    (parsed as? JsonArray)?.let { fromJsonArray(it) } ?: parseRadioOptionsFromDslLiteral(trimmed)
                } catch (_: Exception) {
                    parseRadioOptionsFromDslLiteral(trimmed)
                }
            }
            else -> emptyList()
        }
    }

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

    @Composable
    fun RenderButton(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val rawLabel = NanoRenderUtils.resolveStringProp(ir, "label", state).ifBlank { "Button" }
        val label = NanoRenderUtils.interpolateText(rawLabel, state)
        val intent = ir.props["intent"]?.jsonPrimitive?.content
        val disabledIf = ir.props["disabled_if"]?.jsonPrimitive?.content
        val isDisabled = !disabledIf.isNullOrBlank() && NanoRenderUtils.evaluateCondition(disabledIf, state)
        val onClick = ir.actions?.get("onClick")

        when (intent) {
            "secondary" -> OutlinedButton(
                onClick = { if (!isDisabled) onClick?.let { onAction(it) } },
                enabled = !isDisabled,
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
                    onClick = { if (!isDisabled) onClick?.let { onAction(it) } },
                    enabled = !isDisabled,
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

    @Composable
    fun RenderSelect(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: "Select..."
        val statePath = resolveStatePathFromBinding(ir, "value", "bind")
        val selectedFromState = statePath?.let { state[it]?.toString() }
        val selectedProp = ir.props["value"]?.jsonPrimitive?.contentOrNull
        var uncontrolledSelected by remember(statePath, selectedProp) { mutableStateOf(selectedProp ?: "") }
        val selectedValue = selectedFromState ?: uncontrolledSelected
        val onChange = ir.actions?.get("onChange")
        var expanded by remember { mutableStateOf(false) }

        // Parse options - support both string array and object array {value, label}
        data class SelectOption(val value: String, val label: String)
        val options: List<SelectOption> = ir.props["options"]?.let { optionsElement ->
            try {
                (optionsElement as? JsonArray)?.mapNotNull { el ->
                    when (el) {
                        is JsonPrimitive -> {
                            val v = el.contentOrNull ?: return@mapNotNull null
                            SelectOption(value = v, label = v)
                        }
                        is JsonObject -> {
                            val v = el["value"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                            val l = el["label"]?.jsonPrimitive?.contentOrNull ?: v
                            SelectOption(value = v, label = l)
                        }
                        else -> null
                    }
                }
            } catch (e: Exception) { null }
        } ?: emptyList()

        // Find the label to display
        val displayText = if (selectedValue.isNotEmpty()) {
            options.find { it.value == selectedValue }?.label ?: selectedValue
        } else {
            placeholder
        }

        Box(modifier = modifier.widthIn(min = 120.dp)) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(displayText)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            expanded = false
                            if (statePath != null) {
                                onAction(NanoActionIR(
                                    type = "stateMutation",
                                    payload = mapOf(
                                        "path" to JsonPrimitive(statePath),
                                        "operation" to JsonPrimitive("SET"),
                                        "value" to JsonPrimitive(option.value)
                                    )
                                ))
                            } else {
                                uncontrolledSelected = option.value
                            }
                            onChange?.let { onAction(it) }
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

    @Composable
    fun RenderRadio(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        val option = ir.props["option"]?.jsonPrimitive?.content ?: ""
        val label = ir.props["label"]?.jsonPrimitive?.content ?: option
        val statePath = resolveStatePathFromBinding(ir, "value", "bind")
        val selectedFromState = statePath?.let { state[it]?.toString() }
        val selectedProp = ir.props["value"]?.jsonPrimitive?.contentOrNull
        var uncontrolledSelected by remember(statePath, selectedProp) { mutableStateOf(selectedProp ?: "") }
        val selectedValue = selectedFromState ?: uncontrolledSelected
        val onChange = ir.actions?.get("onChange")
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
                    } else {
                        uncontrolledSelected = option
                    }
                    onChange?.let { onAction(it) }
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
        val children = ir.children.orEmpty()
        if (children.isNotEmpty()) {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                children.forEach { child -> renderNode(child, state, onAction, Modifier) }
            }
            return
        }

        val options = parseRadioOptions(ir.props["options"])
        val statePath = resolveStatePathFromBinding(ir, "value", "bind")
        val selectedFromState = statePath?.let { state[it]?.toString() }
        var uncontrolledSelected by remember(statePath) { mutableStateOf("") }
        val selectedValue = selectedFromState ?: uncontrolledSelected

        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedValue == option.value,
                        onClick = {
                            if (statePath != null) {
                                onAction(
                                    NanoActionIR(
                                        type = "stateMutation",
                                        payload = mapOf(
                                            "path" to JsonPrimitive(statePath),
                                            "operation" to JsonPrimitive("SET"),
                                            "value" to JsonPrimitive(option.value)
                                        )
                                    )
                                )
                            } else {
                                uncontrolledSelected = option.value
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(option.label)
                }
            }
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
