package cc.unitmesh.devins.ui.nano

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.unitmesh.xuiper.ir.NanoIR
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.jsonPrimitive

/**
 * Utility functions for NanoUI rendering.
 * Contains text interpolation, expression evaluation, and styling helpers.
 */
object NanoRenderUtils {

    /**
     * Resolve a string prop value from IR.
     *
     * If a binding exists for [propKey], it will read from [state]. Otherwise it reads from [ir.props].
     * This returns the raw string value (no interpolation applied).
     */
    fun resolveStringProp(ir: NanoIR, propKey: String, state: Map<String, Any>): String {
        val binding = ir.bindings?.get(propKey)
        return if (binding != null) {
            val expr = binding.expression.removePrefix("state.")
            state[expr]?.toString() ?: ""
        } else {
            ir.props[propKey]?.jsonPrimitive?.content ?: ""
        }
    }

    /**
     * Built-in variables that are always available for text interpolation
     */
    fun getBuiltInVariables(): Map<String, Any> {
        val now = Clock.System.now()
        val localDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
        return mapOf(
            "currentYear" to localDateTime.year,
            "currentMonth" to localDateTime.monthNumber,
            "currentDay" to localDateTime.dayOfMonth,
            "currentHour" to localDateTime.hour,
            "currentMinute" to localDateTime.minute,
            "today" to "${localDateTime.year}-${localDateTime.monthNumber.toString().padStart(2, '0')}-${localDateTime.dayOfMonth.toString().padStart(2, '0')}",
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
        return if (kotlin.math.abs(value - asLong.toDouble()) < 1e-9) asLong.toString() else value.toString()
    }

    private fun resolveIdentifierAny(identifier: String, state: Map<String, Any>): Any? {
        val trimmed = identifier.trim()
        if (trimmed.isBlank()) return null
        val key = if (trimmed.startsWith("state.")) trimmed.removePrefix("state.") else trimmed
        return when {
            state.containsKey(key) -> state[key]
            state.containsKey(trimmed) -> state[trimmed]
            else -> null
        }
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
                    value = if (kotlin.math.abs(rhs) < 1e-12) 0.0 else value / rhs
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
            val start = index
            while (index < input.length) {
                val c = input[index]
                if (c == '_' || c.isLetterOrDigit() || c == '.') {
                    index++
                } else {
                    break
                }
            }
            val raw = input.substring(start, index)
            val key = if (raw.startsWith("state.")) raw.removePrefix("state.") else raw
            val any = state[key] ?: state[raw]
            return when (any) {
                is Number -> any.toDouble()
                is String -> any.toDoubleOrNull() ?: 0.0
                is Boolean -> if (any) 1.0 else 0.0
                else -> 0.0
            }
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

        val trimmed = condition.trim()
        if (trimmed.startsWith("!")) return !evaluateCondition(trimmed.removePrefix("!").trim(), state)
        if (trimmed.startsWith("not ")) return !evaluateCondition(trimmed.removePrefix("not ").trim(), state)
        if (trimmed == "true") return true
        if (trimmed == "false") return false

        fun unquote(raw: String): String = raw
            .trim()
            .removeSurrounding("\"", "\"")
            .removeSurrounding("'", "'")

        fun resolveValue(expr: String): Any? {
            val normalized = expr.trim().removePrefix("state.")
            return state[normalized]
        }

        // Basic comparisons: ==, !=, >, >=, <, <=
        val comparison = Regex("^(.+?)\\s*(==|!=|>=|<=|>|<)\\s*(.+)$").matchEntire(trimmed)
        if (comparison != null) {
            val leftRaw = comparison.groupValues[1].trim()
            val op = comparison.groupValues[2]
            val rightRaw = comparison.groupValues[3].trim()

            val leftValue = resolveValue(leftRaw)

            val rightValue: Any? = when {
                rightRaw.equals("true", ignoreCase = true) -> true
                rightRaw.equals("false", ignoreCase = true) -> false
                rightRaw.startsWith("state.") || rightRaw.matches(Regex("[A-Za-z_]\\w*(\\.[A-Za-z_]\\w*)*")) -> resolveValue(rightRaw)
                rightRaw.matches(Regex("-?\\d+(\\.\\d+)?")) -> rightRaw.toDoubleOrNull()
                (rightRaw.startsWith("\"") && rightRaw.endsWith("\"")) || (rightRaw.startsWith("'") && rightRaw.endsWith("'")) -> unquote(rightRaw)
                else -> rightRaw
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

            when (op) {
                "==" -> return equalsLoosely(leftValue, rightValue)
                "!=" -> return !equalsLoosely(leftValue, rightValue)
                ">" -> return (asNumber(leftValue) ?: return false) > (asNumber(rightValue) ?: return false)
                ">=" -> return (asNumber(leftValue) ?: return false) >= (asNumber(rightValue) ?: return false)
                "<" -> return (asNumber(leftValue) ?: return false) < (asNumber(rightValue) ?: return false)
                "<=" -> return (asNumber(leftValue) ?: return false) <= (asNumber(rightValue) ?: return false)
            }
        }

        // Simple evaluation: treat it as a state path and check if it is truthy.
        val value = resolveValue(trimmed)
        return when (value) {
            is Boolean -> value
            is String -> value.isNotBlank()
            is Number -> value.toDouble() != 0.0
            null -> false
            else -> true
        }
    }

    /**
     * Format date from epoch milliseconds to YYYY-MM-DD string
     * Material3 DatePicker returns UTC millis at midnight
     */
    fun formatDateFromMillis(millis: Long): String {
        val instant = Instant.fromEpochMilliseconds(millis)
        val localDate = instant.toLocalDateTime(TimeZone.UTC).date
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

// Utility extension functions for styling

/**
 * Convert spacing string to Dp value
 */
fun String.toSpacing(): Dp = when (this) {
    "xs" -> 4.dp
    "sm" -> 8.dp
    "md" -> 16.dp
    "lg" -> 24.dp
    "xl" -> 32.dp
    "none" -> 0.dp
    else -> 8.dp
}

/**
 * Convert padding string to Dp value
 */
fun String.toPadding(): Dp = when (this) {
    "xs" -> 4.dp
    "sm" -> 8.dp
    "md" -> 16.dp
    "lg" -> 24.dp
    "xl" -> 32.dp
    "none" -> 0.dp
    else -> 16.dp
}
