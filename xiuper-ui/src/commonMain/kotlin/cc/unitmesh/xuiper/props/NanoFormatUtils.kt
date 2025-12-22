package cc.unitmesh.xuiper.props

import kotlin.math.pow
import kotlin.math.round

/**
 * Utility for formatting values in NanoUI components.
 *
 * Provides common formatting functions for numbers, currencies, percentages, etc.
 * These are platform-agnostic and can be used across all NanoUI renderers.
 *
 * ## Usage
 *
 * ```kotlin
 * // Format a number with fixed decimal places
 * val formatted = NanoFormatUtils.formatFixed(123.456, 2) // "123.46"
 *
 * // Format as currency
 * val currency = NanoFormatUtils.formatCurrency(99.9) // "$99.90"
 *
 * // Format as percentage
 * val percent = NanoFormatUtils.formatPercent(75.5) // "75.5%"
 *
 * // Format cell value with format specifier
 * val cell = NanoFormatUtils.formatCellValue(1234.5, "currency") // "$1234.50"
 * ```
 */
object NanoFormatUtils {

    /**
     * Format a number with a fixed number of decimal places.
     *
     * @param num The number to format
     * @param decimals The number of decimal places (0 or negative returns integer)
     * @return Formatted string with exactly [decimals] decimal places
     */
    fun formatFixed(num: Double, decimals: Int): String {
        if (decimals <= 0) return round(num).toLong().toString()

        val factor = 10.0.pow(decimals.toDouble())
        val rounded = round(num * factor) / factor
        val parts = rounded.toString().split('.', limit = 2)
        val intPart = parts[0]
        val fracPart = (parts.getOrNull(1) ?: "")
            .padEnd(decimals, '0')
            .take(decimals)

        return "$intPart.$fracPart"
    }

    /**
     * Format a number as currency (USD format with $ prefix).
     *
     * @param num The number to format
     * @param decimals The number of decimal places (default: 2)
     * @return Formatted currency string like "$123.45"
     */
    fun formatCurrency(num: Double, decimals: Int = 2): String {
        return "$${formatFixed(num, decimals)}"
    }

    /**
     * Format a number as percentage.
     *
     * @param num The number to format (already in percentage form, e.g., 75.5 for 75.5%)
     * @param decimals The number of decimal places (default: 1)
     * @return Formatted percentage string like "75.5%"
     */
    fun formatPercent(num: Double, decimals: Int = 1): String {
        return "${formatFixed(num, decimals)}%"
    }

    /**
     * Format a cell value according to a format specifier.
     *
     * Supported formats:
     * - "currency" - Format as USD currency ($123.45)
     * - "percent" - Format as percentage (75.5%)
     * - null or other - Return value as string
     *
     * @param value The value to format (can be any type)
     * @param format The format specifier (case-insensitive)
     * @return Formatted string
     */
    fun formatCellValue(value: Any?, format: String?): String {
        if (value == null) return ""

        return when (format?.lowercase()) {
            "currency" -> {
                val num = value.toString().toDoubleOrNull()
                if (num != null) formatCurrency(num) else value.toString()
            }
            "percent" -> {
                val num = value.toString().toDoubleOrNull()
                if (num != null) formatPercent(num) else value.toString()
            }
            else -> value.toString()
        }
    }

    /**
     * Format a number with thousands separators.
     *
     * @param num The number to format
     * @param decimals The number of decimal places
     * @return Formatted string with commas as thousands separators
     */
    fun formatWithSeparators(num: Double, decimals: Int = 0): String {
        val fixed = formatFixed(num, decimals)
        val parts = fixed.split('.', limit = 2)
        val intPart = parts[0]
        val fracPart = parts.getOrNull(1)

        // Add thousands separators to integer part
        val withSeparators = intPart.reversed().chunked(3).joinToString(",").reversed()

        return if (fracPart != null) "$withSeparators.$fracPart" else withSeparators
    }
}

