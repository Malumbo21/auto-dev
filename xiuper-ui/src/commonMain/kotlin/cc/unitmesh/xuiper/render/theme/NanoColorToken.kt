package cc.unitmesh.xuiper.render.theme

import kotlinx.serialization.Serializable

/**
 * Platform-independent color token using ARGB Long values.
 *
 * This allows color definitions to be shared across platforms without
 * depending on platform-specific color classes (Compose Color, AWT Color, etc.).
 *
 * Format: 0xAARRGGBB (e.g., 0xFFB88900 for opaque gold)
 */
@Serializable
data class NanoColorToken(
    val primary: Long,
    val onPrimary: Long,
    val secondary: Long,
    val onSecondary: Long,
    val tertiary: Long = 0xFF6366F1,
    val onTertiary: Long = 0xFFFFFFFF,
    val background: Long,
    val onBackground: Long,
    val surface: Long,
    val onSurface: Long,
    val surfaceVariant: Long,
    val onSurfaceVariant: Long,
    val outline: Long,
    val error: Long = 0xFFB00020,
    val onError: Long = 0xFFFFFFFF
) {
    companion object {
        /**
         * Extract alpha component (0-255)
         */
        fun alpha(color: Long): Int = ((color shr 24) and 0xFF).toInt()

        /**
         * Extract red component (0-255)
         */
        fun red(color: Long): Int = ((color shr 16) and 0xFF).toInt()

        /**
         * Extract green component (0-255)
         */
        fun green(color: Long): Int = ((color shr 8) and 0xFF).toInt()

        /**
         * Extract blue component (0-255)
         */
        fun blue(color: Long): Int = (color and 0xFF).toInt()

        /**
         * Create ARGB Long from components
         */
        fun argb(alpha: Int, red: Int, green: Int, blue: Int): Long {
            return ((alpha and 0xFF).toLong() shl 24) or
                    ((red and 0xFF).toLong() shl 16) or
                    ((green and 0xFF).toLong() shl 8) or
                    (blue and 0xFF).toLong()
        }

        /**
         * Create opaque RGB Long (alpha = 0xFF)
         */
        fun rgb(red: Int, green: Int, blue: Int): Long = argb(0xFF, red, green, blue)
    }
}
