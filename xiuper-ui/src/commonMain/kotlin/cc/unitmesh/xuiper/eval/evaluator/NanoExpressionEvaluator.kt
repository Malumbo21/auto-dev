package cc.unitmesh.xuiper.eval.evaluator

import cc.unitmesh.xuiper.ir.NanoIR
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*
import kotlin.math.abs

/**
 * Utility functions for NanoUI rendering.
 * Contains text interpolation, expression evaluation, and styling helpers.
 */
object NanoExpressionEvaluator {

    private val lenientJson = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowTrailingComma = true
        allowStructuredMapKeys = true
    }

    /**
     * Convert a JsonElement to a Kotlin runtime value.
     *
     * - JsonPrimitive -> Boolean, Int, Double, or String
     * - JsonArray -> List<Any?>
     * - JsonObject -> Map<String, Any?>
     *
     * This is useful for converting JSON data from NanoDSL props to Kotlin collections.
     */
    fun jsonElementToRuntimeValue(el: JsonElement?): Any? {
        return when (el) {
            null -> null
            is JsonPrimitive -> el.booleanOrNull ?: el.intOrNull ?: el.content.toDoubleOrNull() ?: el.content
            is JsonArray -> el.map { jsonElementToRuntimeValue(it) }
            is JsonObject -> el.entries.associate { (k, v) -> k to jsonElementToRuntimeValue(v) }
            else -> el.toString()
        }
    }

    private fun parseJsonLiteralToRuntimeValueOrNull(text: String): Any? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (!(trimmed.startsWith('[') || trimmed.startsWith('{'))) return null

        return try {
            val parsed = lenientJson.parseToJsonElement(trimmed)
            jsonElementToRuntimeValue(parsed)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Resolve an expression to a runtime value (not stringified).
     *
     * Supports:
     * - `state.xxx`
     * - `xxx`
     * - dotted map access: `day_plan.title`
     * - bracket access: `items[0]`, `map["key"]`
     */
    fun resolveAny(expression: String, state: Map<String, Any>): Any? {
        return resolveIdentifierAny(expression, state)
    }

    /**
     * Resolve a string prop value from IR.
     *
     * If a binding exists for [propKey], it will read from [state]. Otherwise it reads from [ir.props].
     * This returns the raw string value (no interpolation applied).
     */
    fun resolveStringProp(ir: NanoIR, propKey: String, state: Map<String, Any>): String {
        val binding = ir.bindings?.get(propKey)
        return if (binding != null) {
            // Allow expressions beyond simple state paths.
            evaluateExpression(binding.expression, state)
        } else {
            val literal = ir.props[propKey]?.jsonPrimitive?.content ?: return ""

            // If author wrote an unquoted expression (e.g. `label=item.name`, `Text(state.destination)`),
            // NanoIR often carries it as a plain string without `{}` templates. In that case, attempt
            // to resolve it as a safe path expression.
            //
            // IMPORTANT: avoid evaluating arbitrary strings that might look like arithmetic
            // (e.g. dates: "2024-06-15"). We only evaluate simple identifiers / dotted paths /
            // bracket access, optionally prefixed with `state.`.
            val normalized = literal.trim().let { raw ->
                when {
                    raw.startsWith("<<") -> raw.removePrefix("<<").trim()
                    raw.startsWith(":=") -> raw.removePrefix(":=").trim()
                    else -> raw
                }
            }

            val isPathExpression = normalized.startsWith("state.") ||
                normalized.matches(Regex("[A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*|\\[[^\\]]+\\])*(?:\\.[A-Za-z_]\\w*)*"))

            if (isPathExpression) {
                val evaluated = evaluateExpression(normalized, state)
                if (evaluated.isNotBlank()) return evaluated
            }

            literal
        }
    }

    /**
     * Built-in variables that are always available for text interpolation
     */
    fun getBuiltInVariables(): Map<String, Any> {
        val now = Clock.System.now()
        val localDateTime = now.toLocalDateTime(TimeZone.Companion.currentSystemDefault())
        return mapOf(
            "currentYear" to localDateTime.year,
            "currentMonth" to localDateTime.monthNumber,
            "currentDay" to localDateTime.dayOfMonth,
            "currentHour" to localDateTime.hour,
            "currentMinute" to localDateTime.minute,
            "today" to "${localDateTime.year}-${
                localDateTime.monthNumber.toString().padStart(2, '0')
            }-${localDateTime.dayOfMonth.toString().padStart(2, '0')}",
            "now" to localDateTime.toString()
        )
    }

    /**
     * Interpolate text with {expr} or ${expr} templates.
     *
     * Supported expression forms:
     * - Variable: `{state.count}` or `{count}`
     * - Arithmetic: `{state.count * 5}`, `{count + price}`, `{(a + b) * 2}`
     * - Limited string methods: `{state.name.title()}`, `{state.x.replace('_', ' ')}`
     *
     * Note: this is intentionally a small, safe evaluator. It only supports a tiny whitelist
     * of string methods and simple arithmetic.
     * Also supports built-in variables and provides default values for missing variables.
     */
    fun interpolateText(text: String, state: Map<String, Any>): String {
        // Regex to match {expr} and ${expr} patterns.
        val pattern = Regex("""\$\{([^}]+)\}|\{([^}]+)\}""")

        // Merge built-in variables with state (state takes precedence)
        val mergedState = getBuiltInVariables() + state

        return pattern.replace(text) { matchResult ->
            val expr = (matchResult.groups[1]?.value ?: matchResult.groups[2]?.value ?: "").trim()
            evaluateExpression(expr, mergedState)
        }
    }

    /**
     * Evaluate an expression used in templates.
     * Returns a string suitable for display.
     */
    fun evaluateExpression(expr: String, state: Map<String, Any>): String {
        val trimmed = expr.trim()
        if (trimmed.isBlank()) return ""

        // Built-in function: len(x)
        Regex("^len\\((.+)\\)$").matchEntire(trimmed)?.let { match ->
            val inner = match.groupValues[1].trim()
            val value = resolveIdentifierAny(inner, state)
            val length = when (value) {
                null -> 0
                is CharSequence -> value.length
                is Map<*, *> -> value.size
                is Collection<*> -> value.size
                is Array<*> -> value.size
                is IntArray -> value.size
                is LongArray -> value.size
                is ShortArray -> value.size
                is ByteArray -> value.size
                is DoubleArray -> value.size
                is FloatArray -> value.size
                is BooleanArray -> value.size
                is Iterable<*> -> value.count()
                else -> return ""
            }
            return length.toString()
        }

        // Fast path: plain variable name
        resolveIdentifierAny(trimmed, state)?.let { return it.toString() }

        // Numeric literal
        trimmed.toDoubleOrNull()?.let { return normalizeNumberString(it) }

        // Arithmetic expression
        val numeric = evaluateNumericExpressionOrNull(trimmed, state)
        if (numeric != null) return normalizeNumberString(numeric)

        // Limited string method chain: state.x.replace('a','b').title()
        val stringValue = evaluateStringExpressionOrNull(trimmed, state)
        if (stringValue != null) return stringValue

        // Unknown/unsupported expression: return empty to avoid showing template syntax in UI.
        return ""
    }

    private fun evaluateStringExpressionOrNull(expr: String, state: Map<String, Any>): String? {
        val trimmed = expr.trim()
        if (trimmed.isBlank()) return null

        // Detect method-call chain like: base.method(...).method(...)
        val firstCall = Regex("\\.[A-Za-z_]\\w*\\(").find(trimmed) ?: return null
        val baseExpr = trimmed.substring(0, firstCall.range.first)
        var value = resolveIdentifierAny(baseExpr, state)?.toString() ?: return null

        var i = firstCall.range.first
        while (i < trimmed.length) {
            if (trimmed[i] != '.') return null
            i++

            val nameStart = i
            while (i < trimmed.length && (trimmed[i] == '_' || trimmed[i].isLetterOrDigit())) i++
            if (i <= nameStart) return null
            val method = trimmed.substring(nameStart, i)

            while (i < trimmed.length && trimmed[i].isWhitespace()) i++
            if (i >= trimmed.length || trimmed[i] != '(') return null
            i++ // '('

            val (args, nextIndex) = parseStringCallArgs(trimmed, i) ?: return null
            i = nextIndex

            value = when (method) {
                "replace" -> {
                    if (args.size != 2) return null
                    value.replace(args[0], args[1])
                }

                "title" -> {
                    if (args.isNotEmpty()) return null
                    titleCasePreservingAcronyms(value)
                }

                else -> return null
            }

            while (i < trimmed.length && trimmed[i].isWhitespace()) i++
            if (i < trimmed.length && trimmed[i] == '.') {
                continue
            }
            break
        }

        return value
    }

    private fun parseStringCallArgs(input: String, startIndex: Int): Pair<List<String>, Int>? {
        var i = startIndex
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var inSingle = false
        var inDouble = false

        fun flushArg() {
            val raw = current.toString().trim()
            if (raw.isNotEmpty()) {
                args += raw.removeSurrounding("\"", "\"").removeSurrounding("'", "'")
            } else if (args.isNotEmpty()) {
                args += ""
            }
            current.clear()
        }

        while (i < input.length) {
            val c = input[i]
            when {
                c == '\\' && (inSingle || inDouble) -> {
                    // Skip escapes in quoted strings
                    if (i + 1 < input.length) {
                        current.append(input[i + 1])
                        i += 2
                        continue
                    }
                }

                c == '\'' && !inDouble -> {
                    inSingle = !inSingle
                    i++
                    continue
                }

                c == '"' && !inSingle -> {
                    inDouble = !inDouble
                    i++
                    continue
                }

                !inSingle && !inDouble && c == ',' -> {
                    flushArg()
                    i++
                    continue
                }

                !inSingle && !inDouble && c == ')' -> {
                    flushArg()
                    return args.filter { true } to (i + 1)
                }
            }
            current.append(c)
            i++
        }
        return null
    }

    private fun titleCasePreservingAcronyms(input: String): String {
        val parts = input.split(Regex("\\s+"))
        return parts.joinToString(" ") { word ->
            val w = word.trim()
            if (w.isEmpty()) ""
            else if (w.length <= 4 && w.all { it.isLetter() && it.isUpperCase() }) w
            else w.lowercase().replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
        }.trim()
    }

    /**
     * Evaluate an expression and return a number when possible.
     * Accepts optional binding prefixes like `<<` and `:=`.
     */
    fun evaluateNumberOrNull(expr: String?, state: Map<String, Any>): Double? {
        if (expr.isNullOrBlank()) return null
        val trimmed = expr.trim()
        val normalized = when {
            trimmed.startsWith("<<") -> trimmed.removePrefix("<<").trim()
            trimmed.startsWith(":=") -> trimmed.removePrefix(":=").trim()
            else -> trimmed
        }

        // Direct variable
        val any = resolveIdentifierAny(normalized, state)
        if (any is Number) return any.toDouble()
        if (any is String) return any.toDoubleOrNull()

        // Numeric literal
        normalized.toDoubleOrNull()?.let { return it }

        return evaluateNumericExpressionOrNull(normalized, state)
    }

    private fun normalizeNumberString(value: Double): String {
        val asLong = value.toLong()
        return if (abs(value - asLong.toDouble()) < 1e-9) asLong.toString() else value.toString()
    }

    private fun resolveIdentifierAny(identifier: String, state: Map<String, Any>): Any? {
        val trimmed = identifier.trim()
        if (trimmed.isBlank()) return null

        // Support JSON literals in expressions, primarily for control-flow like:
        // `for item in [ {"name": "A"}, {"name": "B"} ]:`
        // This returns runtime Kotlin List/Map values so dot/bracket access works.
        parseJsonLiteralToRuntimeValueOrNull(trimmed)?.let { return it }

        return resolvePathAny(trimmed, state)
    }

    private fun resolvePathAny(expression: String, state: Map<String, Any>): Any? {
        var expr = expression.trim()
        if (expr.isBlank()) return null

        // Drop optional state. prefix.
        if (expr.startsWith("state.")) {
            expr = expr.removePrefix("state.")
        }

        fun isIdentStart(c: Char): Boolean = c == '_' || c.isLetter()
        fun isIdentPart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()

        var i = 0
        if (i >= expr.length || !isIdentStart(expr[i])) return null
        val rootStart = i
        i++
        while (i < expr.length && isIdentPart(expr[i])) i++
        val rootKey = expr.substring(rootStart, i)

        var current: Any? = state[rootKey] ?: state[expression] // fallback to raw key if provided

        fun mapGet(map: Map<*, *>, key: String): Any? {
            return map[key] ?: map.entries.firstOrNull { it.key?.toString() == key }?.value
        }

        fun jsonObjectGet(obj: JsonObject, key: String): Any? {
            val el = obj[key] ?: return null
            return jsonElementToRuntimeValue(el)
        }

        while (i < expr.length) {
            while (i < expr.length && expr[i].isWhitespace()) i++
            if (i >= expr.length) break

            when (expr[i]) {
                '.' -> {
                    i++
                    while (i < expr.length && expr[i].isWhitespace()) i++
                    val segStart = i
                    if (i >= expr.length || !isIdentStart(expr[i])) return null
                    i++
                    while (i < expr.length && isIdentPart(expr[i])) i++
                    val seg = expr.substring(segStart, i)
                    current = when (val c = current) {
                        is Map<*, *> -> mapGet(c, seg)
                        is JsonObject -> jsonObjectGet(c, seg)
                        else -> null
                    }
                }

                '[' -> {
                    i++
                    while (i < expr.length && expr[i].isWhitespace()) i++
                    if (i >= expr.length) return null

                    val key: Any? = when (expr[i]) {
                        '\'', '"' -> {
                            val quote = expr[i]
                            i++
                            val start = i
                            var escaped = false
                            val sb = StringBuilder()
                            while (i < expr.length) {
                                val ch = expr[i]
                                if (escaped) {
                                    sb.append(ch)
                                    escaped = false
                                } else if (ch == '\\') {
                                    escaped = true
                                } else if (ch == quote) {
                                    break
                                } else {
                                    sb.append(ch)
                                }
                                i++
                            }
                            // Skip closing quote
                            if (i < expr.length && expr[i] == quote) i++
                            sb.toString()
                        }

                        else -> {
                            val start = i
                            while (i < expr.length && expr[i] != ']') i++
                            expr.substring(start, i).trim().toIntOrNull() ?: expr.substring(start, i).trim()
                        }
                    }

                    while (i < expr.length && expr[i].isWhitespace()) i++
                    if (i < expr.length && expr[i] == ']') i++ else return null

                    current = when (val c = current) {
                        is Map<*, *> -> mapGet(c, key?.toString().orEmpty())
                        is List<*> -> (key as? Int)?.let { idx -> c.getOrNull(idx) }
                        is JsonObject -> jsonObjectGet(c, key?.toString().orEmpty())
                        is JsonArray -> (key as? Int)?.let { idx -> c.getOrNull(idx) }?.let { jsonElementToRuntimeValue(it) }
                        else -> null
                    }
                }

                else -> {
                    // Unsupported syntax
                    return null
                }
            }
        }

        return current
    }

    private fun evaluateNumericExpressionOrNull(expr: String, state: Map<String, Any>): Double? {
        val parser = NumericExprParser(expr, state)
        return try {
            val result = parser.parseExpression()
            if (!parser.isAtEnd()) null else result
        } catch (_: Throwable) {
            null
        }
    }

    private class NumericExprParser(
        private val input: String,
        private val state: Map<String, Any>
    ) {
        private var index: Int = 0

        fun isAtEnd(): Boolean {
            skipWhitespace()
            return index >= input.length
        }

        fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipWhitespace()
                if (match('+')) {
                    value += parseTerm()
                } else if (match('-')) {
                    value -= parseTerm()
                } else {
                    break
                }
            }
            return value
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                skipWhitespace()
                if (match('*')) {
                    value *= parseFactor()
                } else if (match('/')) {
                    val rhs = parseFactor()
                    value = if (abs(rhs) < 1e-12) 0.0 else value / rhs
                } else {
                    break
                }
            }
            return value
        }

        private fun parseFactor(): Double {
            skipWhitespace()
            if (match('+')) return parseFactor()
            if (match('-')) return -parseFactor()

            if (match('(')) {
                val inner = parseExpression()
                skipWhitespace()
                match(')')
                return inner
            }

            val c = peek() ?: return 0.0
            return when {
                c.isDigit() || c == '.' -> parseNumber()
                c == '_' || c.isLetter() -> parseIdentifierValue()
                else -> {
                    // Unknown token, consume and treat as 0
                    index++
                    0.0
                }
            }
        }

        private fun parseNumber(): Double {
            val start = index
            var sawDot = false
            while (index < input.length) {
                val c = input[index]
                if (c == '.') {
                    if (sawDot) break
                    sawDot = true
                    index++
                } else if (c.isDigit()) {
                    index++
                } else {
                    break
                }
            }
            return input.substring(start, index).toDoubleOrNull() ?: 0.0
        }

        private fun parseIdentifierValue(): Double {
            val raw = parsePathToken()

            // Important: arithmetic expressions often reference nested values like
            // `state.budget.transport` where `budget` is a Map. Direct lookup in [state]
            // would fail, so we resolve via the same safe path resolver used elsewhere.
            val any = resolveIdentifierAny(raw, state)
            return when (any) {
                is Number -> any.toDouble()
                is String -> any.toDoubleOrNull() ?: 0.0
                is Boolean -> if (any) 1.0 else 0.0
                else -> 0.0
            }
        }

        /**
         * Parse a path-like token that may include dotted access and bracket access, e.g.:
         * - state.budget.transport
         * - flight["price"]
         * - items[0].price
         */
        private fun parsePathToken(): String {
            val start = index

            fun isIdentPart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()

            // root identifier
            while (index < input.length && isIdentPart(input[index])) index++

            while (index < input.length) {
                when (input[index]) {
                    '.' -> {
                        index++
                        if (index >= input.length) break
                        while (index < input.length && isIdentPart(input[index])) index++
                    }

                    '[' -> {
                        // Scan until matching ']'. Support quoted strings inside brackets.
                        index++
                        var inString: Char? = null
                        var escaped = false
                        while (index < input.length) {
                            val c = input[index]
                            if (inString != null) {
                                if (escaped) {
                                    escaped = false
                                } else if (c == '\\') {
                                    escaped = true
                                } else if (c == inString) {
                                    inString = null
                                }
                                index++
                                continue
                            }

                            when (c) {
                                '\'', '"' -> {
                                    inString = c
                                    index++
                                }

                                ']' -> {
                                    index++
                                    break
                                }

                                else -> index++
                            }
                        }
                    }

                    else -> break
                }
            }

            return input.substring(start, index)
        }

        private fun skipWhitespace() {
            while (index < input.length && input[index].isWhitespace()) index++
        }

        private fun match(ch: Char): Boolean {
            if (index < input.length && input[index] == ch) {
                index++
                return true
            }
            return false
        }

        private fun peek(): Char? = if (index < input.length) input[index] else null
    }

    /**
     * Get default value for a variable based on its name.
     * Provides sensible defaults when variables are not found.
     */
    fun getDefaultForVariable(varName: String): Int {
        return when {
            varName.contains("day", ignoreCase = true) -> 1
            varName.contains("month", ignoreCase = true) -> 1
            varName.contains("year", ignoreCase = true) -> 2024
            varName.contains("count", ignoreCase = true) -> 0
            varName.contains("index", ignoreCase = true) -> 0
            varName.contains("page", ignoreCase = true) -> 1
            varName.contains("step", ignoreCase = true) -> 1
            varName.contains("quantity", ignoreCase = true) -> 1
            varName.contains("amount", ignoreCase = true) -> 0
            varName.contains("price", ignoreCase = true) -> 0
            varName.contains("total", ignoreCase = true) -> 0
            else -> 0
        }
    }

    /**
     * Resolve a binding value from state
     */
    fun resolveBindingValue(value: String?, state: Map<String, Any>): String? {
        if (value == null) return null

        val trimmed = value.trim()
        val normalized = if (trimmed.startsWith("<<")) trimmed.removePrefix("<<").trim() else trimmed
        if (normalized.startsWith("state.")) {
            val path = normalized.removePrefix("state.")
            return state[path]?.toString()
        }

        return value
    }

    /**
     * Resolve a binding value from state, returning the underlying value (List/Map/Number/etc).
     * Supports both `state.xxx` and subscribe form `<< state.xxx`.
     */
    fun resolveBindingAny(value: String?, state: Map<String, Any>): Any? {
        if (value == null) return null

        val trimmed = value.trim()
        val normalized = if (trimmed.startsWith("<<")) trimmed.removePrefix("<<").trim() else trimmed
        if (normalized.startsWith("state.")) {
            val path = normalized.removePrefix("state.")
            return state[path]
        }

        return value
    }

    /**
     * Evaluate a condition expression
     */
    fun evaluateCondition(condition: String?, state: Map<String, Any>): Boolean {
        if (condition.isNullOrBlank()) return true

        fun unquote(raw: String): String = raw
            .trim()
            .removeSurrounding("\"", "\"")
            .removeSurrounding("'", "'")

        fun stripOuterParens(raw: String): String {
            var s = raw.trim()
            while (s.startsWith('(') && s.endsWith(')')) {
                var depth = 0
                var inString: Char? = null
                var escaped = false
                var enclosesAll = true
                for (i in s.indices) {
                    val c = s[i]
                    if (inString != null) {
                        if (escaped) {
                            escaped = false
                        } else if (c == '\\') {
                            escaped = true
                        } else if (c == inString) {
                            inString = null
                        }
                        continue
                    }
                    when (c) {
                        '\'', '"' -> inString = c
                        '(' -> depth++
                        ')' -> {
                            depth--
                            // If we reach depth 0 before the last char, outer parens don't enclose all.
                            if (depth == 0 && i != s.lastIndex) {
                                enclosesAll = false
                                break
                            }
                        }
                    }
                }
                if (!enclosesAll || depth != 0) break
                s = s.substring(1, s.length - 1).trim()
            }
            return s
        }

        fun splitTopLevel(expr: String, keyword: String): List<String> {
            val parts = mutableListOf<String>()
            var depth = 0
            var inString: Char? = null
            var escaped = false
            var start = 0

            fun isBoundary(idx: Int): Boolean {
                if (idx < 0 || idx >= expr.length) return true
                return expr[idx].isWhitespace()
            }

            var i = 0
            while (i < expr.length) {
                val c = expr[i]
                if (inString != null) {
                    if (escaped) {
                        escaped = false
                    } else if (c == '\\') {
                        escaped = true
                    } else if (c == inString) {
                        inString = null
                    }
                    i++
                    continue
                }

                when (c) {
                    '\'', '"' -> inString = c
                    '(' -> depth++
                    ')' -> if (depth > 0) depth--
                }

                if (depth == 0) {
                    if (expr.regionMatches(i, keyword, 0, keyword.length, ignoreCase = false)) {
                        val beforeOk = isBoundary(i - 1)
                        val afterOk = isBoundary(i + keyword.length)
                        if (beforeOk && afterOk) {
                            val part = expr.substring(start, i).trim()
                            if (part.isNotEmpty()) parts.add(part)
                            start = i + keyword.length
                            i = start
                            continue
                        }
                    }
                }
                i++
            }

            val tail = expr.substring(start).trim()
            if (tail.isNotEmpty()) parts.add(tail)
            return parts
        }

        fun resolveValue(expr: String): Any? = resolveIdentifierAny(expr.trim(), state)

        fun parseOperand(raw: String): Any? {
            val r = raw.trim()
            if (r.equals("true", ignoreCase = true)) return true
            if (r.equals("false", ignoreCase = true)) return false
            if ((r.startsWith("\"") && r.endsWith("\"")) || (r.startsWith("'") && r.endsWith("'"))) return unquote(r)
            r.toDoubleOrNull()?.let { return it }

            // Try resolving identifier/path (supports dot and brackets).
            val resolved = resolveValue(r)
            return resolved ?: r
        }

        fun asNumber(v: Any?): Double? = when (v) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }

        fun equalsLoosely(left: Any?, right: Any?): Boolean {
            // Prefer numeric equality if both sides are numeric-ish.
            val ln = asNumber(left)
            val rn = asNumber(right)
            if (ln != null && rn != null) return ln == rn

            // Prefer boolean equality if both sides are booleans.
            if (left is Boolean && right is Boolean) return left == right

            return left?.toString() == right?.toString()
        }

        fun findTopLevelComparison(expr: String): Pair<Int, String>? {
            var depth = 0
            var inString: Char? = null
            var escaped = false
            var i = 0

            while (i < expr.length) {
                val c = expr[i]
                if (inString != null) {
                    if (escaped) {
                        escaped = false
                    } else if (c == '\\') {
                        escaped = true
                    } else if (c == inString) {
                        inString = null
                    }
                    i++
                    continue
                }

                when (c) {
                    '\'', '"' -> inString = c
                    '(' -> depth++
                    ')' -> if (depth > 0) depth--
                }

                if (depth == 0) {
                    val two = if (i + 1 < expr.length) expr.substring(i, i + 2) else ""
                    when (two) {
                        "==", "!=", ">=", "<=" -> return i to two
                    }
                    if (c == '>' || c == '<') return i to c.toString()
                }
                i++
            }
            return null
        }

        fun truthy(value: Any?): Boolean {
            return when (value) {
                is Boolean -> value
                is String -> value.isNotBlank()
                is Number -> value.toDouble() != 0.0
                is Map<*, *> -> value.isNotEmpty()
                is Collection<*> -> value.isNotEmpty()
                null -> false
                else -> true
            }
        }

        fun eval(exprRaw: String): Boolean {
            var expr = stripOuterParens(exprRaw).trim()
            if (expr.isBlank()) return false

            // Unary operators
            if (expr.startsWith("!")) return !eval(expr.removePrefix("!").trim())
            if (expr.startsWith("not ")) return !eval(expr.removePrefix("not ").trim())

            if (expr == "true") return true
            if (expr == "false") return false

            // Boolean operators with precedence: and > or
            val orParts = splitTopLevel(expr, "or")
            if (orParts.size > 1) return orParts.any { eval(it) }

            val andParts = splitTopLevel(expr, "and")
            if (andParts.size > 1) return andParts.all { eval(it) }

            // Comparisons at top-level
            val cmp = findTopLevelComparison(expr)
            if (cmp != null) {
                val (idx, op) = cmp
                val leftRaw = expr.substring(0, idx).trim()
                val rightRaw = expr.substring(idx + op.length).trim()

                val leftValue = parseOperand(leftRaw)
                val rightValue = parseOperand(rightRaw)

                return when (op) {
                    "==" -> equalsLoosely(leftValue, rightValue)
                    "!=" -> !equalsLoosely(leftValue, rightValue)
                    ">" -> (asNumber(leftValue) ?: return false) > (asNumber(rightValue) ?: return false)
                    ">=" -> (asNumber(leftValue) ?: return false) >= (asNumber(rightValue) ?: return false)
                    "<" -> (asNumber(leftValue) ?: return false) < (asNumber(rightValue) ?: return false)
                    "<=" -> (asNumber(leftValue) ?: return false) <= (asNumber(rightValue) ?: return false)
                    else -> false
                }
            }

            // Simple evaluation: treat it as a state path and check if it is truthy.
            val value = resolveValue(expr) ?: parseOperand(expr)
            return truthy(value)
        }

        return eval(condition)
    }

    /**
     * Format date from epoch milliseconds to YYYY-MM-DD string
     * Material3 DatePicker returns UTC millis at midnight
     */
    fun formatDateFromMillis(millis: Long): String {
        val instant = Instant.Companion.fromEpochMilliseconds(millis)
        val localDate = instant.toLocalDateTime(TimeZone.Companion.UTC).date
        return buildString {
            append(localDate.year.toString().padStart(4, '0'))
            append('-')
            append(localDate.monthNumber.toString().padStart(2, '0'))
            append('-')
            append(localDate.dayOfMonth.toString().padStart(2, '0'))
        }
    }

    /**
     * Extract a meaningful prompt from the image src URL or path.
     */
    fun extractImagePrompt(src: String): String {
        // Clean up the URL
        val cleaned = src
            .replace(Regex("https?://[^/]+/"), "") // Remove domain
            .replace(Regex("[?#].*"), "") // Remove query params
            .replace(Regex("[-_/.]"), " ") // Replace separators with spaces
            .replace(Regex("\\d+"), "") // Remove numbers
            .trim()

        // If we got a meaningful string, use it
        if (cleaned.length > 3 && cleaned.any { it.isLetter() }) {
            return cleaned.take(100) // Limit length
        }

        // Fallback
        return "high quality image"
    }
}