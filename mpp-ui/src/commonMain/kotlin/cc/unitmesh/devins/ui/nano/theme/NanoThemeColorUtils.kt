package cc.unitmesh.devins.ui.nano.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import cc.unitmesh.xuiper.render.theme.NanoColorUtils

/**
 * Compose-specific color utilities.
 *
 * Most color operations are now in [NanoColorUtils] (platform-independent).
 * This file provides Compose-specific wrappers.
 */

internal fun parseHexColorOrNull(input: String): Color? {
    return NanoColorUtils.parseHexColorOrNull(input)?.let { Color(it) }
}

internal fun nanoColorSchemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    // Convert Compose Color to Long, generate tokens, convert back
    val seedLong = (seed.value shr 32).toLong() or 0xFF000000L
    val tokens = NanoColorUtils.generateFromSeed(seedLong, dark)
    return tokens.toColorScheme(dark)
}
