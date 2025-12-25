package cc.unitmesh.devins.ui.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset

@Composable
actual fun TooltipWrapper(
    tooltip: @Composable () -> Unit,
    tooltipOffset: DpOffset,
    delayMillis: Int,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    // JS platform: just render content without tooltip
    Box(modifier = modifier) {
        content()
    }
}
