package cc.unitmesh.xuiper.components.input

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Represents a selectable option with a value and display label.
 * Used by Select, Radio, RadioGroup components.
 */
data class SelectOption(val value: String, val label: String)

/**
 * Utility object for parsing selection options from various formats.
 * Supports:
 * - JsonArray of strings: ["A", "B", "C"]
 * - JsonArray of objects: [{"value": "a", "label": "Option A"}]
 * - JsonPrimitive (string) containing JSON or DSL format
 */
object SelectionUtils {

    private val lenientJson = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    /**
     * Parse options from JsonElement.
     */
    fun parseOptions(optionsElement: JsonElement?): List<SelectOption> {
        if (optionsElement == null) return emptyList()

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

    private fun fromJsonArray(arr: JsonArray): List<SelectOption> {
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
        val trimmed = raw.trim()
            .replace("\\\"", "\"")
            .replace("\\'", "'")
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()

        val inner = trimmed.removePrefix("[").removeSuffix("]").trim()
        if (inner.isBlank()) return emptyList()

        val items = splitTopLevelCommaSeparated(inner)

        return items.mapNotNull { itemRaw ->
            val item = itemRaw.trim()
            parseOptionItem(item)
        }
    }

    private fun parseOptionItem(item: String): SelectOption? {
        return when {
            item.startsWith("{") && item.endsWith("}") -> {
                val value = extractField(item, "value") ?: return null
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

    private fun extractField(item: String, field: String): String? {
        val pattern = Regex(
            """(?is)(?:\"$field\"|$field)\s*:\s*(\"(?:\\\\.|[^\"])*\"|'(?:\\\\.|[^'])*'|[^,}\]]+)"""
        )
        val m = pattern.find(item) ?: return null
        return unquoteString(m.groupValues[1])
    }
}

