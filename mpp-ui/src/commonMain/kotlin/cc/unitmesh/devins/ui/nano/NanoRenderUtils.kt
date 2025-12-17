package cc.unitmesh.devins.ui.nano

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Utility functions for NanoUI rendering.
 * Contains text interpolation, expression evaluation, and styling helpers.
 */
object NanoRenderUtils {

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
     * Interpolate text with {state.xxx} or {state.xxx + 1} expressions.
     * Also supports built-in variables and provides default values for missing variables.
     */
    fun interpolateText(text: String, state: Map<String, Any>): String {
        // Regex to match {expr} patterns, including expressions like {state.currentDay - 1}
        val pattern = Regex("""\{([^}]+)\}""")

        // Merge built-in variables with state (state takes precedence)
        val mergedState = getBuiltInVariables() + state

        return pattern.replace(text) { matchResult ->
            val expr = matchResult.groupValues[1].trim()
            evaluateExpression(expr, mergedState)
        }
    }

    /**
     * Evaluate a simple expression like "state.currentDay" or "state.currentDay - 1"
     */
    fun evaluateExpression(expr: String, state: Map<String, Any>): String {
        // Handle arithmetic expressions: state.xxx + N, state.xxx - N
        val arithmeticPattern = Regex("""state\.(\w+)\s*([+\-*/])\s*(\d+)""")
        val arithmeticMatch = arithmeticPattern.matchEntire(expr)
        if (arithmeticMatch != null) {
            val varName = arithmeticMatch.groupValues[1]
            val operator = arithmeticMatch.groupValues[2]
            val operand = arithmeticMatch.groupValues[3].toIntOrNull() ?: 0

            val value = state[varName]
            val numValue = when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull() ?: getDefaultForVariable(varName)
                else -> getDefaultForVariable(varName)
            }

            val result = when (operator) {
                "+" -> numValue + operand
                "-" -> numValue - operand
                "*" -> numValue * operand
                "/" -> if (operand != 0) numValue / operand else numValue
                else -> numValue
            }
            return result.toString()
        }

        // Handle simple state reference: state.xxx
        val simplePattern = Regex("""state\.(\w+)""")
        val simpleMatch = simplePattern.matchEntire(expr)
        if (simpleMatch != null) {
            val varName = simpleMatch.groupValues[1]
            return state[varName]?.toString() ?: getDefaultForVariable(varName).toString()
        }

        // Return original expression if it doesn't match
        return "{$expr}"
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
        // Simple evaluation: check if state path exists and is truthy
        val path = condition.removePrefix("state.")
        val value = state[path]
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
        // Simple conversion: millis / 86400000 = days since epoch
        val days = millis / 86400000L
        // Approximate year (will be off by a few days due to leap years, but good enough for display)
        val year = 1970 + (days / 365)
        val remainingDays = days % 365
        val month = (remainingDays / 30).coerceIn(0, 11) + 1
        val day = (remainingDays % 30).coerceIn(0, 30) + 1

        return buildString {
            append(year.toString().padStart(4, '0'))
            append('-')
            append(month.toString().padStart(2, '0'))
            append('-')
            append(day.toString().padStart(2, '0'))
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
