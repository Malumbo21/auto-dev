package cc.unitmesh.devins.ui.nano.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal fun parseHexColorOrNull(input: String): Color? {
    val s = input.trim()
        .removePrefix("#")
        .removePrefix("0x")
        .removePrefix("0X")

    if (s.length != 6 && s.length != 8) return null

    return runCatching {
        val value = s.toLong(16)
        val a = if (s.length == 8) ((value shr 24) and 0xFF).toInt() else 0xFF
        val r = ((value shr 16) and 0xFF).toInt()
        val g = ((value shr 8) and 0xFF).toInt()
        val b = (value and 0xFF).toInt()
        Color((a shl 24) or (r shl 16) or (g shl 8) or b)
    }.getOrNull()
}

internal fun nanoColorSchemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    // A lightweight, cross-platform seed scheme builder.
    // We intentionally keep it simple and deterministic across KMP targets.

    val primary = seed
    val secondary = shiftHue(seed, degrees = 30f)
    val tertiary = shiftHue(seed, degrees = 60f)

    val background = if (dark) Color(0xFF0B0E14) else Color(0xFFFFFFFF)
    val surface = if (dark) Color(0xFF151922) else Color(0xFFFFFFFF)
    val surfaceVariant = if (dark) Color(0xFF1F2430) else Color(0xFFF6F7F9)
    val outline = if (dark) Color(0xFF2A3040) else Color(0xFFE2E8F0)

    val onPrimary = if (relativeLuminance(primary) > 0.55f) Color(0xFF0B0E14) else Color(0xFFFFFFFF)
    val onSecondary = if (relativeLuminance(secondary) > 0.55f) Color(0xFF0B0E14) else Color(0xFFFFFFFF)
    val onTertiary = if (relativeLuminance(tertiary) > 0.55f) Color(0xFF0B0E14) else Color(0xFFFFFFFF)

    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            tertiary = tertiary,
            onTertiary = onTertiary,
            background = background,
            onBackground = Color(0xFFF5F5F5),
            surface = surface,
            onSurface = Color(0xFFF5F5F5),
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = Color(0xFFB0BEC5),
            outline = outline,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            tertiary = tertiary,
            onTertiary = onTertiary,
            background = background,
            onBackground = Color(0xFF0F172A),
            surface = surface,
            onSurface = Color(0xFF0F172A),
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = Color(0xFF475569),
            outline = outline,
        )
    }
}

private fun shiftHue(color: Color, degrees: Float): Color {
    val (h, s, l) = rgbToHsl(color)
    val newH = (h + degrees) % 360f
    return hslToRgb(newH, s, l, alpha = color.alpha)
}

private data class Hsl(val h: Float, val s: Float, val l: Float)

private fun rgbToHsl(color: Color): Hsl {
    val r = color.red
    val g = color.green
    val b = color.blue

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

private fun hslToRgb(h: Float, s: Float, l: Float, alpha: Float): Color {
    if (s == 0f) {
        return Color(l, l, l, alpha)
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
    return Color(
        red = clamp01(r1 + m),
        green = clamp01(g1 + m),
        blue = clamp01(b1 + m),
        alpha = clamp01(alpha)
    )
}

private fun clamp01(v: Float): Float = min(1f, max(0f, v))

private fun relativeLuminance(color: Color): Float {
    // WCAG relative luminance for sRGB
    fun channel(c: Float): Float {
        return if (c <= 0.03928f) {
            c / 12.92f
        } else {
            ((c + 0.055f) / 1.055f).pow(2.4f)
        }
    }

    val r = channel(color.red)
    val g = channel(color.green)
    val b = channel(color.blue)
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}
