package cc.unitmesh.devins.ui.nano

import cc.unitmesh.xuiper.action.MutationOp
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.state.NanoState
import cc.unitmesh.yaml.YamlUtils
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.round

/**
 * NanoStateRuntime
 *
 * A small runtime that bridges NanoIR state + NanoActionIR mutations into a reactive NanoState.
 *
 * Design goals:
 * - Deterministic: state defaults are applied synchronously
 * - Cross-platform: commonMain implementation
 * - Testable: no Compose dependency
 * - Compatible: accepts both `state.xxx` and `xxx` mutation paths
 */
class NanoStateRuntime(
    private val ir: NanoIR
) {
    private val declaredTypes: Map<String, String> = ir.state?.variables?.mapValues { it.value.type } ?: emptyMap()

    val state: NanoState = NanoState(buildInitialState(ir))

    val declaredKeys: Set<String> = declaredTypes.keys

    fun snapshot(): Map<String, Any> {
        val raw = state.snapshot()
        // We keep the renderer contract (Map<String, Any>) by ensuring declared variables are non-null.
        // If a declared variable is unexpectedly null, we omit it (treated as missing).
        return raw
            .filterKeys { it in declaredKeys }
            .mapNotNull { (k, v) -> v?.let { k to it } }
            .toMap()
    }

    fun apply(action: NanoActionIR) {
        when (action.type) {
            "sequence" -> applySequence(action)
            "stateMutation" -> applyStateMutation(action)
        }
    }

    private fun normalizeStatePath(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("state.")) trimmed.removePrefix("state.") else trimmed
    }

    private fun buildInitialState(ir: NanoIR): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        ir.state?.variables?.forEach { (name, varDef) ->
            val defaultValue = varDef.defaultValue
            val raw = defaultValue?.jsonPrimitive?.content
            result[name] = when (varDef.type) {
                "int" -> defaultValue?.jsonPrimitive?.intOrNull
                    ?: raw?.toIntOrNull()
                    ?: raw?.toDoubleOrNull()?.let { round(it).toInt() }
                    ?: 0
                "float" -> raw?.toFloatOrNull() ?: 0f
                "bool" -> defaultValue?.jsonPrimitive?.booleanOrNull
                    ?: raw?.let { parseBoolLoose(it) }
                    ?: false
                "str" -> raw ?: ""
                "list" -> {
                    val raw = defaultValue?.jsonPrimitive?.content ?: "[]"
                    val parsed = parseStructuredDefault(raw)
                    if (parsed is List<*>) parsed else emptyList<Any>()
                }
                "dict", "map", "object" -> {
                    val raw = defaultValue?.jsonPrimitive?.content ?: "{}"
                    val parsed = parseStructuredDefault(raw)
                    if (parsed is Map<*, *>) parsed else emptyMap<String, Any>()
                }
                else -> defaultValue?.jsonPrimitive?.content ?: ""
            }
        }
        return result
    }

    private fun applySequence(action: NanoActionIR) {
        val payload = action.payload ?: return
        val actions = payload["actions"] as? JsonArray ?: return
        actions.forEach { element ->
            decodeActionIR(element)?.let { apply(it) }
        }
    }

    private fun applyStateMutation(action: NanoActionIR) {
        val payload = action.payload ?: return
        val rawPath = payload["path"]?.jsonPrimitive?.content ?: return
        val path = normalizeStatePath(rawPath)

        val opRaw = payload["operation"]?.jsonPrimitive?.content ?: "SET"
        val operation = runCatching { MutationOp.valueOf(opRaw) }.getOrNull() ?: MutationOp.SET

        val rawValueStr = payload["value"]?.jsonPrimitive?.content ?: ""

        val currentValue = state[path]
        val declaredType = declaredTypes[path]

        val newValue = when (operation) {
            MutationOp.ADD -> when (currentValue) {
                is Int -> currentValue + (rawValueStr.toIntOrNull() ?: 1)
                is Float -> currentValue + (rawValueStr.toFloatOrNull() ?: 1f)
                is Double -> currentValue + (rawValueStr.toDoubleOrNull() ?: 1.0)
                else -> currentValue
            }

            MutationOp.SUBTRACT -> when (currentValue) {
                is Int -> currentValue - (rawValueStr.toIntOrNull() ?: 1)
                is Float -> currentValue - (rawValueStr.toFloatOrNull() ?: 1f)
                is Double -> currentValue - (rawValueStr.toDoubleOrNull() ?: 1.0)
                else -> currentValue
            }

            MutationOp.APPEND -> when (currentValue) {
                is List<*> -> {
                    val trimmed = rawValueStr.trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'")
                    if (currentValue.contains(trimmed)) currentValue else currentValue + trimmed
                }
                null -> {
                    val trimmed = rawValueStr.trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'")
                    listOf(trimmed)
                }
                else -> currentValue
            }

            MutationOp.REMOVE -> when (currentValue) {
                is List<*> -> {
                    val trimmed = rawValueStr.trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'")
                    currentValue.filterNot { it == trimmed }
                }
                else -> currentValue
            }

            MutationOp.SET -> coerceSetValue(currentValue, declaredType, rawValueStr)
        }

        state[path] = newValue
    }

    private fun coerceSetValue(currentValue: Any?, declaredType: String?, rawValueStr: String): Any? {
        // Prefer current runtime type, then declared type, then best-effort parsing.
        return when (currentValue) {
            is Int -> rawValueStr.toIntOrNull()
                ?: rawValueStr.toDoubleOrNull()?.let { round(it).toInt() }
                ?: 0

            is Float -> rawValueStr.toFloatOrNull() ?: 0f
            is Double -> rawValueStr.toDoubleOrNull() ?: 0.0
            is Boolean -> parseBoolLoose(rawValueStr) ?: false

            is List<*> -> {
                val parsed = parseStructuredDefault(rawValueStr)
                if (parsed is List<*>) parsed else currentValue
            }

            is Map<*, *> -> {
                val parsed = parseStructuredDefault(rawValueStr)
                if (parsed is Map<*, *>) parsed else currentValue
            }

            else -> when (declaredType) {
                "int" -> rawValueStr.toIntOrNull() ?: 0
                "float" -> rawValueStr.toFloatOrNull() ?: 0f
                "bool" -> parseBoolLoose(rawValueStr) ?: false
                "list" -> {
                    val parsed = parseStructuredDefault(rawValueStr)
                    if (parsed is List<*>) parsed else emptyList<Any>()
                }
                "dict", "map", "object" -> {
                    val parsed = parseStructuredDefault(rawValueStr)
                    if (parsed is Map<*, *>) parsed else emptyMap<String, Any>()
                }
                "str" -> rawValueStr
                else -> bestEffortParse(rawValueStr)
            }
        }
    }

    private fun bestEffortParse(raw: String): Any {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        // Try structured values first.
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            val parsed = parseStructuredDefault(trimmed)
            if (parsed is List<*> || parsed is Map<*, *>) return parsed
        }

        // Try booleans.
        parseBoolLoose(trimmed)?.let { return it }

        // Try numbers.
        val asInt = trimmed.toIntOrNull()
        if (asInt != null) return asInt

        val asDouble = trimmed.toDoubleOrNull()
        if (asDouble != null) return asDouble

        // Fallback string (strip quotes).
        return trimmed
            .removeSurrounding("\"", "\"")
            .removeSurrounding("'", "'")
    }

    private fun parseStructuredDefault(raw: String): Any {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        return try {
            // JSON is valid YAML 1.2, so this handles both JSON/YAML list/map literals.
            YamlUtils.load(trimmed) ?: raw
        } catch (_: Exception) {
            raw
        }
    }

    private fun decodeActionIR(el: JsonElement): NanoActionIR? {
        val obj = el as? JsonObject ?: return null
        val type = obj["type"]?.jsonPrimitive?.content ?: return null
        val payload = obj["payload"] as? JsonObject
        return NanoActionIR(
            type = type,
            payload = payload?.toMap() ?: emptyMap()
        )
    }

    private fun JsonObject.toMap(): Map<String, JsonElement> = this.entries.associate { it.key to it.value }

    private fun parseBoolLoose(raw: String): Boolean? {
        return when (raw.trim()) {
            "true", "True", "TRUE" -> true
            "false", "False", "FALSE" -> false
            else -> null
        }
    }
}
