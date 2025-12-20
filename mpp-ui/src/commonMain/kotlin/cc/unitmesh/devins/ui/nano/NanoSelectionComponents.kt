package cc.unitmesh.devins.ui.nano

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Selection components for NanoUI Compose renderer.
 * Includes: Select, Radio, RadioGroup with shared option parsing logic
 */
object NanoSelectionComponents {

    private val lenientJson = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    /**
     * Represents a selectable option with a value and display label.
     */
    internal data class SelectOption(val value: String, val label: String)

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

    private fun parseRadioOptionsFromDslLiteral(raw: String): List<SelectOption> {
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
                    SelectOption(value = value, label = label)
                }
                item.startsWith("\"") || item.startsWith("'") -> {
                    val v = unquoteString(item)
                    if (v.isBlank()) null else SelectOption(value = v, label = v)
                }
                else -> {
                    val v = item.trim()
                    if (v.isBlank()) null else SelectOption(value = v, label = v)
                }
            }
        }
    }

    /**
     * Parse options from JsonElement. Supports:
     * - JsonArray of strings: ["A", "B", "C"]
     * - JsonArray of objects: [{"value": "a", "label": "Option A"}]
     * - JsonPrimitive (string) containing JSON or DSL format
     */
    internal fun parseOptions(optionsElement: JsonElement?): List<SelectOption> {
        if (optionsElement == null) return emptyList()

        fun fromJsonArray(arr: JsonArray): List<SelectOption> {
            return arr.mapNotNull { el ->
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
        val options: List<SelectOption> = parseOptions(ir.props["options"])

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

        val options = parseOptions(ir.props["options"])
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
}

