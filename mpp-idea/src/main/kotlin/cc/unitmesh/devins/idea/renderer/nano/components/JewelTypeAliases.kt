package cc.unitmesh.devins.idea.renderer.nano.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cc.unitmesh.xuiper.render.stateful.NanoNodeContext
import cc.unitmesh.xuiper.render.stateful.NanoNodeRenderer

/**
 * Shared type aliases for Jewel nano components.
 */
typealias JewelContext = NanoNodeContext<Modifier, @Composable () -> Unit>
typealias JewelRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit>
