package cc.unitmesh.devins.ui.nano.theme

import cc.unitmesh.xuiper.render.theme.NanoThemeConfig
import cc.unitmesh.xuiper.render.theme.parseNanoThemeConfigYamlOrNull

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

/**
 * Parse YAML config and apply to state.
 */
fun applyNanoThemeConfigYaml(yaml: String, state: NanoThemeState): Boolean {
    val config = parseNanoThemeConfigYamlOrNull(yaml) ?: return false
    config.applyTo(state)
    return true
}
