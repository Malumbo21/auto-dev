package cc.unitmesh.devins.ui.nano.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * NanoDesignSystem
 *
 * A self-contained design system for NanoUI rendering.
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
)

/**
 * Built-in Nano themes.
 *
 * Note: These palettes are intentionally defined as design tokens.
 */
object NanoBuiltInDesignSystems {

    /**
     * Bank-like black & gold style.
     */
    val BankBlackGold: NanoDesignSystem = NanoDesignSystem(
        id = "bank_black_gold",
        displayName = "Bank (Black/Gold)",
        light = lightColorScheme(
            primary = Color(0xFFB88900),
            onPrimary = Color(0xFFFFFFFF),
            secondary = Color(0xFF2E2A24),
            onSecondary = Color(0xFFFFFFFF),
            background = Color(0xFFFAFAFA),
            onBackground = Color(0xFF121212),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF121212),
            surfaceVariant = Color(0xFFF1F1F1),
            onSurfaceVariant = Color(0xFF3A3A3A),
            outline = Color(0xFFE0E0E0),
        ),
        dark = darkColorScheme(
            primary = Color(0xFFFFD166),
            onPrimary = Color(0xFF0B0E14),
            secondary = Color(0xFF3B342B),
            onSecondary = Color(0xFFF5F5F5),
            background = Color(0xFF0B0E14),
            onBackground = Color(0xFFF5F5F5),
            surface = Color(0xFF151922),
            onSurface = Color(0xFFF5F5F5),
            surfaceVariant = Color(0xFF1F2430),
            onSurfaceVariant = Color(0xFFB0BEC5),
            outline = Color(0xFF2A3040),
        )
    )

    /**
     * Travel-like Airbnb style.
     */
    val TravelAirbnb: NanoDesignSystem = NanoDesignSystem(
        id = "travel_airbnb",
        displayName = "Travel (Airbnb)",
        light = lightColorScheme(
            primary = Color(0xFFFF385C),
            onPrimary = Color(0xFFFFFFFF),
            secondary = Color(0xFF00A699),
            onSecondary = Color(0xFFFFFFFF),
            background = Color(0xFFFFFFFF),
            onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFF6F7F9),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFE2E8F0),
        ),
        dark = darkColorScheme(
            primary = Color(0xFFFF5A75),
            onPrimary = Color(0xFF0B0E14),
            secondary = Color(0xFF2DD4BF),
            onSecondary = Color(0xFF0B0E14),
            background = Color(0xFF0B0E14),
            onBackground = Color(0xFFE5E7EB),
            surface = Color(0xFF111827),
            onSurface = Color(0xFFE5E7EB),
            surfaceVariant = Color(0xFF1F2937),
            onSurfaceVariant = Color(0xFFCBD5E1),
            outline = Color(0xFF334155),
        )
    )
}
