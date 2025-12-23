package cc.unitmesh.xuiper.render.theme

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Platform-independent color utility functions.
 *
 * These work with ARGB Long values and can be used across all platforms.
 */
object NanoColorUtils {

    /**
     * Parse hex color string to ARGB Long.
     * Supports formats: #RRGGBB, #AARRGGBB, 0xRRGGBB, 0xAARRGGBB
     *
     * @return ARGB Long value or null if parsing fails
     */
    fun parseHexColorOrNull(input: String): Long? {
        val s = input.trim()
            .removePrefix("#")
            .removePrefix("0x")
            .removePrefix("0X")

        if (s.length != 6 && s.length != 8) return null

        return runCatching {
            val value = s.toLong(16)
            if (s.length == 8) {
                value
            } else {
                // Add alpha = 0xFF for 6-digit hex
                0xFF000000L or value
            }
        }.getOrNull()
    }

    /**
     * Convert ARGB Long to hex string (with # prefix).
     */
    fun toHexString(color: Long, includeAlpha: Boolean = false): String {
        return if (includeAlpha) {
            val hex = (color.toInt().toUInt()).toString(16).uppercase().padStart(8, '0')
            "#$hex"
        } else {
            val hex = ((color and 0xFFFFFF).toInt().toUInt()).toString(16).uppercase().padStart(6, '0')
            "#$hex"
        }
    }

    /**
     * Shift hue of a color by given degrees.
     *
     * @param color ARGB Long value
     * @param degrees Hue shift in degrees (0-360)
     * @return New ARGB Long with shifted hue
     */
    fun shiftHue(color: Long, degrees: Float): Long {
        val (h, s, l) = rgbToHsl(color)
        val newH = (h + degrees) % 360f
        return hslToRgb(newH, s, l, alpha = NanoColorToken.alpha(color))
    }

    /**
     * Calculate relative luminance (WCAG formula).
     *
     * @return Luminance value between 0 and 1
     */
    fun relativeLuminance(color: Long): Float {
        fun channel(c: Int): Float {
            val normalized = c / 255f
            return if (normalized <= 0.03928f) {
                normalized / 12.92f
            } else {
                ((normalized + 0.055f) / 1.055f).pow(2.4f)
            }
        }

        val r = channel(NanoColorToken.red(color))
        val g = channel(NanoColorToken.green(color))
        val b = channel(NanoColorToken.blue(color))
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /**
     * Determine if text on this background should be light or dark.
     *
     * @return true if light text should be used (dark background)
     */
    fun shouldUseLightText(backgroundColor: Long): Boolean {
        return relativeLuminance(backgroundColor) < 0.5f
    }

    /**
     * Generate a color scheme from a seed color.
     *
     * @param seed Primary seed color (ARGB Long)
     * @param dark Whether to generate dark mode colors
     * @return NanoColorToken with generated colors
     */
    fun generateFromSeed(seed: Long, dark: Boolean): NanoColorToken {
        val primary = seed
        val secondary = shiftHue(seed, degrees = 30f)
        val tertiary = shiftHue(seed, degrees = 60f)

        val background = if (dark) 0xFF0B0E14 else 0xFFFFFFFF
        val surface = if (dark) 0xFF151922 else 0xFFFFFFFF
        val surfaceVariant = if (dark) 0xFF1F2430 else 0xFFF6F7F9
        val outline = if (dark) 0xFF2A3040 else 0xFFE2E8F0

        val onPrimary = if (relativeLuminance(primary) > 0.55f) 0xFF0B0E14 else 0xFFFFFFFF
        val onSecondary = if (relativeLuminance(secondary) > 0.55f) 0xFF0B0E14 else 0xFFFFFFFF
        val onTertiary = if (relativeLuminance(tertiary) > 0.55f) 0xFF0B0E14 else 0xFFFFFFFF

        return NanoColorToken(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            tertiary = tertiary,
            onTertiary = onTertiary,
            background = background,
            onBackground = if (dark) 0xFFF5F5F5 else 0xFF0F172A,
            surface = surface,
            onSurface = if (dark) 0xFFF5F5F5 else 0xFF0F172A,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = if (dark) 0xFFB0BEC5 else 0xFF475569,
            outline = outline
        )
    }

    // ==================== Internal HSL utilities ====================

    private data class Hsl(val h: Float, val s: Float, val l: Float)

    private fun rgbToHsl(color: Long): Hsl {
        val r = NanoColorToken.red(color) / 255f
        val g = NanoColorToken.green(color) / 255f
        val b = NanoColorToken.blue(color) / 255f

        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC

        val l = (maxC + minC) / 2f

        if (delta == 0f) {
            return Hsl(h = 0f, s = 0f, l = l)
        }

        val s = if (l < 0.5f) {
            delta / (maxC + minC)
        } else {
            delta / (2f - maxC - minC)
        }

        val h = when (maxC) {
            r -> ((g - b) / delta + if (g < b) 6f else 0f) * 60f
            g -> ((b - r) / delta + 2f) * 60f
            else -> ((r - g) / delta + 4f) * 60f
        }

        return Hsl(h = h, s = s, l = l)
    }

    private fun hslToRgb(h: Float, s: Float, l: Float, alpha: Int): Long {
        if (s == 0f) {
            val gray = (l * 255).toInt()
            return NanoColorToken.argb(alpha, gray, gray, gray)
        }

        val c = (1f - abs(2f * l - 1f)) * s
        val hh = (h % 360f) / 60f
        val x = c * (1f - abs(hh % 2f - 1f))

        val (r1, g1, b1) = when {
            hh < 1f -> Triple(c, x, 0f)
            hh < 2f -> Triple(x, c, 0f)
            hh < 3f -> Triple(0f, c, x)
            hh < 4f -> Triple(0f, x, c)
            hh < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val m = l - c / 2f
        return NanoColorToken.argb(
            alpha = alpha,
            red = (clamp01(r1 + m) * 255).toInt(),
            green = (clamp01(g1 + m) * 255).toInt(),
            blue = (clamp01(b1 + m) * 255).toInt()
        )
    }

    private fun clamp01(v: Float): Float = min(1f, max(0f, v))
}
