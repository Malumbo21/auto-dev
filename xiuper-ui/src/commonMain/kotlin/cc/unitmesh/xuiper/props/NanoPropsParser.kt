package cc.unitmesh.xuiper.props

import kotlinx.serialization.json.*

/**
 * Generic interface for parsing props from JsonElement.
 * 
 * Implementations can parse different formats:
 * - JsonArray of strings: ["A", "B", "C"]
 * - JsonArray of objects: [{"value": "a", "label": "Option A"}]
 * - JsonPrimitive (string) containing JSON or DSL format
 * - Comma-separated strings: "a,b,c"
 */
interface NanoPropsParser<T> {
    /**
     * Parse a JsonElement into a list of items.
     */
    fun parse(element: JsonElement?): List<T>
}

/**
 * Parser for NanoOption - handles value/label pairs.
 * 
 * Supports:
 * - JsonArray of strings: ["A", "B", "C"] -> value=label
 * - JsonArray of objects: [{"value": "a", "label": "Option A"}]
 * - JsonPrimitive string containing JSON array
 * - DSL literal format: [A, B, C] or [{value: "a", label: "A"}]
 */
object NanoOptionParser : NanoPropsParser<NanoOption> {
    
    private val lenientJson = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }
    
    override fun parse(element: JsonElement?): List<NanoOption> {
        if (element == null) return emptyList()
        
        return when (element) {
            is JsonArray -> parseJsonArray(element)
            is JsonPrimitive -> parseStringContent(element.contentOrNull)
            else -> emptyList()
        }
    }
    
    /**
     * Parse from a raw string (for convenience).
     */
    fun parseString(content: String?): List<NanoOption> {
        return parseStringContent(content)
    }
    
    private fun parseJsonArray(arr: JsonArray): List<NanoOption> {
        return arr.mapNotNull { el ->
            when (el) {
                is JsonPrimitive -> {
                    val v = el.contentOrNull ?: return@mapNotNull null
                    NanoOption(value = v, label = v)
                }
                is JsonObject -> parseJsonObject(el)
                else -> null
            }
        }
    }
    
    private fun parseJsonObject(obj: JsonObject): NanoOption? {
        val value = obj["value"]?.jsonPrimitive?.contentOrNull ?: return null
        val label = obj["label"]?.jsonPrimitive?.contentOrNull ?: value
        return NanoOption(value = value, label = label)
    }
    
    private fun parseStringContent(content: String?): List<NanoOption> {
        if (content.isNullOrBlank()) return emptyList()
        
        val trimmed = content.trim()
        if (!trimmed.startsWith("[")) return emptyList()
        
        return try {
            val parsed = lenientJson.parseToJsonElement(trimmed)
            (parsed as? JsonArray)?.let { parseJsonArray(it) } ?: parseDslLiteral(trimmed)
        } catch (_: Exception) {
            parseDslLiteral(trimmed)
        }
    }
    
    private fun parseDslLiteral(raw: String): List<NanoOption> {
        val trimmed = raw.trim()
            .replace("\\\"", "\"")
            .replace("\\'", "'")
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        
        val inner = trimmed.removePrefix("[").removeSuffix("]").trim()
        if (inner.isBlank()) return emptyList()
        
        val items = splitTopLevel(inner)
        return items.mapNotNull { parseOptionItem(it.trim()) }
    }
    
    private fun parseOptionItem(item: String): NanoOption? {
        return when {
            item.startsWith("{") && item.endsWith("}") -> {
                val value = extractField(item, "value") ?: return null
                val label = extractField(item, "label") ?: value
                NanoOption(value = value, label = label)
            }
            item.startsWith("\"") || item.startsWith("'") -> {
                val v = unquote(item)
                if (v.isBlank()) null else NanoOption(value = v, label = v)
            }
            else -> {
                val v = item.trim()
                if (v.isBlank()) null else NanoOption(value = v, label = v)
            }
        }
    }
    
    private fun extractField(item: String, field: String): String? {
        val pattern = Regex(
            """(?is)(?:\"$field\"|$field)\s*:\s*(\"(?:\\\\.|[^\"])*\"|'(?:\\\\.|[^'])*'|[^,}\]]+)"""
        )
        val m = pattern.find(item) ?: return null
        return unquote(m.groupValues[1])
    }
    
    private fun unquote(raw: String): String {
        val t = raw.trim()
        return t.removeSurrounding("\"", "\"")
            .removeSurrounding("'", "'")
            .trim()
    }
    
    internal fun splitTopLevel(input: String): List<String> {
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
}

/**
 * Parser for NanoOptionWithMeta - handles value/label pairs with additional metadata.
 *
 * Supports all formats of NanoOptionParser plus additional fields in objects:
 * - [{"key": "name", "title": "Name", "sortable": true, "format": "currency"}]
 *
 * For DataTable columns, uses "key" as value and "title" as label.
 */
object NanoOptionWithMetaParser : NanoPropsParser<NanoOptionWithMeta> {

    private val lenientJson = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    override fun parse(element: JsonElement?): List<NanoOptionWithMeta> {
        if (element == null) return emptyList()

        return when (element) {
            is JsonArray -> parseJsonArray(element)
            is JsonPrimitive -> parseStringContent(element.contentOrNull)
            else -> emptyList()
        }
    }

    /**
     * Parse from a raw string.
     */
    fun parseString(content: String?): List<NanoOptionWithMeta> {
        return parseStringContent(content)
    }

    private fun parseJsonArray(arr: JsonArray): List<NanoOptionWithMeta> {
        return arr.mapNotNull { el ->
            when (el) {
                is JsonPrimitive -> {
                    val v = el.contentOrNull ?: return@mapNotNull null
                    NanoOptionWithMeta(value = v, label = v)
                }
                is JsonObject -> parseJsonObject(el)
                else -> null
            }
        }
    }

    private fun parseJsonObject(obj: JsonObject): NanoOptionWithMeta? {
        // Support both "value"/"label" and "key"/"title" patterns
        val value = obj["value"]?.jsonPrimitive?.contentOrNull
            ?: obj["key"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val label = obj["label"]?.jsonPrimitive?.contentOrNull
            ?: obj["title"]?.jsonPrimitive?.contentOrNull
            ?: value

        // Extract all other fields as meta
        val meta = mutableMapOf<String, Any?>()
        obj.forEach { (key, jsonValue) ->
            if (key !in setOf("value", "label", "key", "title")) {
                meta[key] = when (jsonValue) {
                    is JsonPrimitive -> {
                        jsonValue.booleanOrNull
                            ?: jsonValue.intOrNull
                            ?: jsonValue.doubleOrNull
                            ?: jsonValue.contentOrNull
                    }
                    else -> jsonValue.toString()
                }
            }
        }

        return NanoOptionWithMeta(value = value, label = label, meta = meta)
    }

    private fun parseStringContent(content: String?): List<NanoOptionWithMeta> {
        if (content.isNullOrBlank()) return emptyList()

        val trimmed = content.trim()

        // Try JSON array format
        if (trimmed.startsWith("[")) {
            return try {
                val parsed = lenientJson.parseToJsonElement(trimmed)
                (parsed as? JsonArray)?.let { parseJsonArray(it) } ?: parseSimpleList(trimmed)
            } catch (_: Exception) {
                parseSimpleList(trimmed)
            }
        }

        // Simple comma-separated format: "col1,col2,col3"
        return trimmed.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { NanoOptionWithMeta(value = it, label = it) }
    }

    private fun parseSimpleList(raw: String): List<NanoOptionWithMeta> {
        // Delegate to NanoOptionParser and convert
        return NanoOptionParser.parseString(raw).map { NanoOptionWithMeta.from(it) }
    }
}

