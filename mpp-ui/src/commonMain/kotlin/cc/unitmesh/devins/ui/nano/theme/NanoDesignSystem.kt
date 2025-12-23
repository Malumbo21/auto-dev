package cc.unitmesh.devins.ui.nano.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import cc.unitmesh.xuiper.render.theme.NanoBuiltInThemes
import cc.unitmesh.xuiper.render.theme.NanoColorToken
import cc.unitmesh.xuiper.render.theme.NanoDesignSystemSpec

/**
 * NanoDesignSystem
 *
 * A Compose-specific design system for NanoUI rendering.
 * Wraps platform-independent [NanoDesignSystemSpec] with Compose ColorScheme.
 *
 * It supports:
 * - Two built-in theme families (Bank Black/Gold, Travel Airbnb)
 * - A custom theme generated from a user-selected seed color
 */
@Immutable
data class NanoDesignSystem(
    val id: String,
    val displayName: String,
    val light: ColorScheme,
    val dark: ColorScheme
) {
    companion object {
        /**
         * Create NanoDesignSystem from platform-independent spec.
         */
        fun fromSpec(spec: NanoDesignSystemSpec): NanoDesignSystem {
            return NanoDesignSystem(
                id = spec.id,
                displayName = spec.displayName,
                light = spec.lightTokens.toColorScheme(dark = false),
                dark = spec.darkTokens.toColorScheme(dark = true)
            )
        }
    }
}

/**
 * Convert NanoColorToken to Compose ColorScheme.
 */
fun NanoColorToken.toColorScheme(dark: Boolean): ColorScheme {
    return if (dark) {
        darkColorScheme(
            primary = Color(primary),
            onPrimary = Color(onPrimary),
            secondary = Color(secondary),
            onSecondary = Color(onSecondary),
            tertiary = Color(tertiary),
            onTertiary = Color(onTertiary),
            background = Color(background),
            onBackground = Color(onBackground),
            surface = Color(surface),
            onSurface = Color(onSurface),
            surfaceVariant = Color(surfaceVariant),
            onSurfaceVariant = Color(onSurfaceVariant),
            outline = Color(outline),
            error = Color(error),
            onError = Color(onError)
        )
    } else {
        lightColorScheme(
            primary = Color(primary),
            onPrimary = Color(onPrimary),
            secondary = Color(secondary),
            onSecondary = Color(onSecondary),
            tertiary = Color(tertiary),
            onTertiary = Color(onTertiary),
            background = Color(background),
            onBackground = Color(onBackground),
            surface = Color(surface),
            onSurface = Color(onSurface),
            surfaceVariant = Color(surfaceVariant),
            onSurfaceVariant = Color(onSurfaceVariant),
            outline = Color(outline),
            error = Color(error),
            onError = Color(onError)
        )
    }
}

/**
 * Built-in Nano themes for Compose.
 *
 * These are derived from platform-independent [NanoBuiltInThemes].
 */
object NanoBuiltInDesignSystems {

    /**
     * Bank-like black & gold style.
     */
    val BankBlackGold: NanoDesignSystem = NanoDesignSystem.fromSpec(NanoBuiltInThemes.BankBlackGold)

    /**
     * Travel-like Airbnb style.
     */
    val TravelAirbnb: NanoDesignSystem = NanoDesignSystem.fromSpec(NanoBuiltInThemes.TravelAirbnb)
}
