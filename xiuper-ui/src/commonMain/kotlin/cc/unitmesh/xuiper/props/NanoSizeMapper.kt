package cc.unitmesh.xuiper.props

/**
 * Utility for mapping size tokens to numeric values.
 *
 * NanoDSL uses semantic size tokens (xs, sm, md, lg, xl) that map to
 * numeric values. This utility provides platform-agnostic parsing.
 *
 * ## Size Scale
 *
 * | Token | Icon Size | Radius | Shadow |
 * |-------|-----------|--------|--------|
 * | xs    | 12        | 2      | 1      |
 * | sm    | 16        | 4      | 2      |
 * | md    | 24        | 8      | 4      |
 * | lg    | 32        | 12     | 8      |
 * | xl    | 48        | 16     | 12     |
 *
 * ## Usage
 *
 * ```kotlin
 * val iconSize = NanoSizeMapper.parseIconSize("md") // returns 24
 * val radius = NanoSizeMapper.parseRadius("lg") // returns 12
 * ```
 */
object NanoSizeMapper {

    const val DEFAULT_ICON_SIZE = 24
    const val DEFAULT_RADIUS = 8
    const val DEFAULT_SHADOW = 4

    /**
     * Parse icon size token to pixel value.
     *
     * @param token Size token (xs, sm, md, lg, xl)
     * @param default Default value if token is not recognized
     * @return Icon size in pixels
     */
    fun parseIconSize(token: String?, default: Int = DEFAULT_ICON_SIZE): Int = when (token?.lowercase()) {
        "xs" -> 12
        "sm", "small" -> 16
        "md", "medium" -> 24
        "lg", "large" -> 32
        "xl" -> 48
        else -> default
    }

    /**
     * Parse radius token to pixel value.
     *
     * @param token Radius token (xs, sm, md, lg, xl, none, full)
     * @param default Default value if token is not recognized
     * @return Border radius in pixels
     */
    fun parseRadius(token: String?, default: Int = DEFAULT_RADIUS): Int = when (token?.lowercase()) {
        "none" -> 0
        "xs" -> 2
        "sm" -> 4
        "md" -> 8
        "lg" -> 12
        "xl" -> 16
        "full" -> 9999
        else -> default
    }

    /**
     * Parse shadow/elevation token to pixel value.
     *
     * @param token Shadow token (xs, sm, md, lg, xl, none)
     * @param default Default value if token is not recognized
     * @return Elevation in pixels
     */
    fun parseShadow(token: String?, default: Int = DEFAULT_SHADOW): Int = when (token?.lowercase()) {
        "none" -> 0
        "xs" -> 1
        "sm" -> 2
        "md" -> 4
        "lg" -> 8
        "xl" -> 12
        else -> default
    }
}

