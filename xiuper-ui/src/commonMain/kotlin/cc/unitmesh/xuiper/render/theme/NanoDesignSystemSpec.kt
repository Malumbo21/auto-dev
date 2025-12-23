package cc.unitmesh.xuiper.render.theme

import kotlinx.serialization.Serializable

/**
 * Platform-independent design system specification.
 *
 * Contains color tokens for both light and dark modes.
 * Platform implementations convert these to their specific color systems.
 */
@Serializable
data class NanoDesignSystemSpec(
    val id: String,
    val displayName: String,
    val lightTokens: NanoColorToken,
    val darkTokens: NanoColorToken
)
