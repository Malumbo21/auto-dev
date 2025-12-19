package cc.unitmesh.devins.ui.nano.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

internal val LocalNanoThemeApplied = staticCompositionLocalOf { false }

@Immutable
enum class NanoThemeFamily {
    BANK_BLACK_GOLD,
    TRAVEL_AIRBNB,
    CUSTOM
}

@Stable
class NanoThemeState internal constructor(
    family: NanoThemeFamily,
    dark: Boolean,
    customSeedHex: String
) {
    var family by mutableStateOf(family)
    var dark by mutableStateOf(dark)

    /**
     * Store user input to avoid losing formatting while typing.
     * Use [customSeedColorOrNull] for actual parsing.
     */
    var customSeedHex by mutableStateOf(customSeedHex)

    val customSeedColorOrNull: Color?
        get() = parseHexColorOrNull(customSeedHex)
}

@Composable
fun rememberNanoThemeState(
    family: NanoThemeFamily = NanoThemeFamily.BANK_BLACK_GOLD,
    dark: Boolean = isSystemInDarkTheme(),
    customSeedHex: String = "#FF385C"
): NanoThemeState {
    return remember { NanoThemeState(family = family, dark = dark, customSeedHex = customSeedHex) }
}

@Composable
fun ProvideNanoTheme(
    state: NanoThemeState,
    content: @Composable () -> Unit
) {
    val isDark = state.dark

    val scheme = when (state.family) {
        NanoThemeFamily.BANK_BLACK_GOLD -> if (isDark) NanoBuiltInDesignSystems.BankBlackGold.dark else NanoBuiltInDesignSystems.BankBlackGold.light
        NanoThemeFamily.TRAVEL_AIRBNB -> if (isDark) NanoBuiltInDesignSystems.TravelAirbnb.dark else NanoBuiltInDesignSystems.TravelAirbnb.light
        NanoThemeFamily.CUSTOM -> {
            val seed = state.customSeedColorOrNull ?: Color(0xFF6366F1)
            nanoColorSchemeFromSeed(seed, dark = isDark)
        }
    }

    CompositionLocalProvider(LocalNanoThemeApplied provides true) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(),
            content = content
        )
    }
}

@Composable
fun ProvideNanoTheme(
    designSystem: NanoDesignSystem,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (dark) designSystem.dark else designSystem.light
    CompositionLocalProvider(LocalNanoThemeApplied provides true) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(),
            content = content
        )
    }
}
