package cc.unitmesh.devins.ui.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset

/**
 * Cross-platform tooltip wrapper.
 * On Desktop: Uses TooltipArea with full tooltip support
 * On other platforms: Just renders the content without tooltip
 */
@Composable
expect fun TooltipWrapper(
    tooltip: @Composable () -> Unit,
    tooltipOffset: DpOffset,
    delayMillis: Int,
    modifier: Modifier,
    content: @Composable () -> Unit
)
