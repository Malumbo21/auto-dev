package cc.unitmesh.xuiper.render.theme

/**
 * Built-in Nano theme specifications.
 *
 * These are platform-independent color definitions that can be
 * converted to platform-specific color systems (Compose, Jewel, AWT, etc.).
 */
object NanoBuiltInThemes {

    /**
     * Bank-like black & gold style.
     */
    val BankBlackGold = NanoDesignSystemSpec(
        id = "bank_black_gold",
        displayName = "Bank (Black/Gold)",
        lightTokens = NanoColorToken(
            primary = 0xFFB88900,
            onPrimary = 0xFFFFFFFF,
            secondary = 0xFF2E2A24,
            onSecondary = 0xFFFFFFFF,
            background = 0xFFFAFAFA,
            onBackground = 0xFF121212,
            surface = 0xFFFFFFFF,
            onSurface = 0xFF121212,
            surfaceVariant = 0xFFF1F1F1,
            onSurfaceVariant = 0xFF3A3A3A,
            outline = 0xFFE0E0E0
        ),
        darkTokens = NanoColorToken(
            primary = 0xFFFFD166,
            onPrimary = 0xFF0B0E14,
            secondary = 0xFF3B342B,
            onSecondary = 0xFFF5F5F5,
            background = 0xFF0B0E14,
            onBackground = 0xFFF5F5F5,
            surface = 0xFF151922,
            onSurface = 0xFFF5F5F5,
            surfaceVariant = 0xFF1F2430,
            onSurfaceVariant = 0xFFB0BEC5,
            outline = 0xFF2A3040
        )
    )

    /**
     * Travel-like Airbnb style.
     */
    val TravelAirbnb = NanoDesignSystemSpec(
        id = "travel_airbnb",
        displayName = "Travel (Airbnb)",
        lightTokens = NanoColorToken(
            primary = 0xFFFF385C,
            onPrimary = 0xFFFFFFFF,
            secondary = 0xFF00A699,
            onSecondary = 0xFFFFFFFF,
            background = 0xFFFFFFFF,
            onBackground = 0xFF0F172A,
            surface = 0xFFFFFFFF,
            onSurface = 0xFF0F172A,
            surfaceVariant = 0xFFF6F7F9,
            onSurfaceVariant = 0xFF475569,
            outline = 0xFFE2E8F0
        ),
        darkTokens = NanoColorToken(
            primary = 0xFFFF5A75,
            onPrimary = 0xFF0B0E14,
            secondary = 0xFF2DD4BF,
            onSecondary = 0xFF0B0E14,
            background = 0xFF0B0E14,
            onBackground = 0xFFE5E7EB,
            surface = 0xFF111827,
            onSurface = 0xFFE5E7EB,
            surfaceVariant = 0xFF1F2937,
            onSurfaceVariant = 0xFFCBD5E1,
            outline = 0xFF334155
        )
    )

    /**
     * Get design system spec by family.
     */
    fun getByFamily(family: NanoThemeFamily): NanoDesignSystemSpec? {
        return when (family) {
            NanoThemeFamily.BANK_BLACK_GOLD -> BankBlackGold
            NanoThemeFamily.TRAVEL_AIRBNB -> TravelAirbnb
            NanoThemeFamily.CUSTOM -> null
        }
    }

    /**
     * All built-in themes.
     */
    val all: List<NanoDesignSystemSpec> = listOf(BankBlackGold, TravelAirbnb)
}
