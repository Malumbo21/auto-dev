package cc.unitmesh.xuiper.render.theme

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Platform-independent theme family identifier.
 *
 * Used to select between built-in themes or custom seed-based themes.
 * Platform implementations (Compose, Jewel, etc.) map these to their specific color systems.
 */
@Serializable
enum class NanoThemeFamily {
    @SerialName("bank_black_gold")
    BANK_BLACK_GOLD,

    @SerialName("travel_airbnb")
    TRAVEL_AIRBNB,

    @SerialName("custom")
    CUSTOM
}
