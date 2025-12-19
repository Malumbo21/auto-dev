package cc.unitmesh.devins.ui.nano.theme

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Nano theme configuration model.
 *
 * This is designed for future YAML-driven theming.
 * For now it intentionally supports a minimal, stable subset:
 * - Built-in family selection
 * - Dark/light mode flag
 * - Custom seed color (hex)
 */
@Serializable
data class NanoThemeConfig(
    @SerialName("family")
    val family: NanoThemeFamily? = null,
    @SerialName("dark")
    val dark: Boolean? = null,
    @SerialName("customSeedHex")
    val customSeedHex: String? = null
)

/**
 * Parse YAML into [NanoThemeConfig]. Returns null when parsing fails.
 */
fun parseNanoThemeConfigYamlOrNull(yaml: String): NanoThemeConfig? {
    return runCatching {
        Yaml.default.decodeFromString(NanoThemeConfig.serializer(), yaml)
    }.getOrNull()
}

/**
 * Apply a config onto an existing [NanoThemeState].
 *
 * This keeps state objects stable (good for Compose), while allowing external config to update it.
 */
fun NanoThemeConfig.applyTo(state: NanoThemeState) {
    family?.let { state.family = it }
    dark?.let { state.dark = it }
    customSeedHex?.let { state.customSeedHex = it }
}
