package cc.unitmesh.xuiper.props

/**
 * Utility for parsing aspect ratio values from NanoDSL props.
 *
 * Supports multiple formats:
 * - Fraction format: "16/9", "4/3", "1/1"
 * - Decimal format: "1.77", "1.33", "1.0"
 * - Named presets: "square", "video", "portrait", "landscape"
 *
 * ## Usage
 *
 * ```kotlin
 * val ratio = NanoAspectRatioParser.parse("16/9") // returns 1.777...
 * val ratio2 = NanoAspectRatioParser.parse("1.77") // returns 1.77
 * val ratio3 = NanoAspectRatioParser.parse("square") // returns 1.0
 * ```
 */
object NanoAspectRatioParser {

    /**
     * Parse aspect ratio from string.
     *
     * @param value Aspect ratio string (e.g., "16/9", "1.77", "square")
     * @return Aspect ratio as Float, or null if invalid
     */
    fun parse(value: String?): Float? {
        if (value.isNullOrBlank()) return null

        val trimmed = value.trim()

        // Try named presets first
        when (trimmed.lowercase()) {
            "square" -> return 1.0f
            "video", "16:9", "16/9" -> return 16f / 9f
            "4:3", "4/3" -> return 4f / 3f
            "portrait", "9:16", "9/16" -> return 9f / 16f
            "landscape", "21:9", "21/9" -> return 21f / 9f
        }

        // Try fraction format (16/9 or 16:9)
        if (trimmed.contains('/') || trimmed.contains(':')) {
            val separator = if (trimmed.contains('/')) '/' else ':'
            val parts = trimmed.split(separator, limit = 2)
            val numerator = parts.getOrNull(0)?.trim()?.toFloatOrNull()
            val denominator = parts.getOrNull(1)?.trim()?.toFloatOrNull()

            if (numerator != null && denominator != null && denominator != 0f) {
                return numerator / denominator
            }
        }

        // Try decimal format (1.77)
        return trimmed.toFloatOrNull()
    }
}

