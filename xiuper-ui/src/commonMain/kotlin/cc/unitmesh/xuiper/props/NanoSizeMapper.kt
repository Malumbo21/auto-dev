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

    /**
     * Parse icon size token to pixel value.
     *
     * @param token Size token (xs, sm, md, lg, xl)
     * @return Icon size in pixels (default: 24 for md)
     */
    fun parseIconSize(token: String?): Int = when (token?.lowercase()) {
        "xs" -> 12
        "sm", "small" -> 16
        "md", "medium" -> 24
        "lg", "large" -> 32
        "xl" -> 48
        else -> 24
    }

    /**
     * Parse radius token to pixel value.
     *
     * @param token Radius token (xs, sm, md, lg, xl, none, full)
     * @return Border radius in pixels (default: 8 for md)
     */
    fun parseRadius(token: String?): Int = when (token?.lowercase()) {
        "none" -> 0
        "xs" -> 2
        "sm" -> 4
        "md" -> 8
        "lg" -> 12
        "xl" -> 16
        "full" -> 9999
        else -> 8
    }

    /**
     * Parse shadow/elevation token to pixel value.
     *
     * @param token Shadow token (xs, sm, md, lg, xl, none)
     * @return Elevation in pixels (default: 4 for md)
     */
    fun parseShadow(token: String?): Int = when (token?.lowercase()) {
        "none" -> 0
        "xs" -> 1
        "sm" -> 2
        "md" -> 4
        "lg" -> 8
        "xl" -> 12
        else -> 4
    }
}

