package cc.unitmesh.xuiper.props

/**
 * Utility for parsing spacing and padding values from NanoDSL props.
 *
 * NanoDSL uses semantic spacing tokens (xs, sm, md, lg, xl, none) that map to
 * numeric values. This utility provides platform-agnostic parsing.
 *
 * ## Spacing Scale
 *
 * | Token | Value |
 * |-------|-------|
 * | none  | 0     |
 * | xs    | 4     |
 * | sm    | 8     |
 * | md    | 16    |
 * | lg    | 24    |
 * | xl    | 32    |
 *
 * ## Usage
 *
 * ```kotlin
 * val spacing = NanoSpacingUtils.parseSpacing("md") // returns 16
 * val padding = NanoSpacingUtils.parsePadding("lg") // returns 24
 * ```
 */
object NanoSpacingUtils {

    /**
     * Default spacing value when token is not recognized.
     */
    const val DEFAULT_SPACING = 8

    /**
     * Default padding value when token is not recognized.
     */
    const val DEFAULT_PADDING = 16

    /**
     * Spacing scale mapping from tokens to numeric values.
     */
    private val SPACING_SCALE = mapOf(
        "none" to 0,
        "xs" to 4,
        "sm" to 8,
        "md" to 16,
        "lg" to 24,
        "xl" to 32
    )

    /**
     * Parse a spacing token to its numeric value.
     *
     * @param token The spacing token (xs, sm, md, lg, xl, none)
     * @param default The default value if token is not recognized
     * @return The numeric spacing value
     */
    fun parseSpacing(token: String?, default: Int = DEFAULT_SPACING): Int {
        if (token.isNullOrBlank()) return default
        return SPACING_SCALE[token.lowercase()] ?: default
    }

    /**
     * Parse a padding token to its numeric value.
     *
     * @param token The padding token (xs, sm, md, lg, xl, none)
     * @param default The default value if token is not recognized
     * @return The numeric padding value
     */
    fun parsePadding(token: String?, default: Int = DEFAULT_PADDING): Int {
        if (token.isNullOrBlank()) return default
        return SPACING_SCALE[token.lowercase()] ?: default
    }

    /**
     * Check if a token is a valid spacing/padding token.
     *
     * @param token The token to check
     * @return true if the token is valid
     */
    fun isValidToken(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        return token.lowercase() in SPACING_SCALE
    }

    /**
     * Get all valid spacing tokens.
     *
     * @return Set of valid token names
     */
    fun validTokens(): Set<String> = SPACING_SCALE.keys
}

/**
 * Extension function to parse spacing from a string.
 *
 * @param default The default value if parsing fails
 * @return The numeric spacing value
 */
fun String.toSpacingValue(default: Int = NanoSpacingUtils.DEFAULT_SPACING): Int {
    return NanoSpacingUtils.parseSpacing(this, default)
}

/**
 * Extension function to parse padding from a string.
 *
 * @param default The default value if parsing fails
 * @return The numeric padding value
 */
fun String.toPaddingValue(default: Int = NanoSpacingUtils.DEFAULT_PADDING): Int {
    return NanoSpacingUtils.parsePadding(this, default)
}

