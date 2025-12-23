package cc.unitmesh.xuiper.render.theme

import cc.unitmesh.yaml.YamlUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Nano theme configuration model.
 *
 * This is designed for YAML-driven theming.
 * Supports:
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
        YamlUtils.loadAs(yaml, NanoThemeConfig.serializer())
    }.getOrNull()
}
