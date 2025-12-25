package cc.unitmesh.devins.ui.compose.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset

@OptIn(ExperimentalFoundationApi::class)
@Composable
actual fun TooltipWrapper(
    tooltip: @Composable () -> Unit,
    tooltipOffset: DpOffset,
    delayMillis: Int,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    TooltipArea(
        tooltip = tooltip,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = tooltipOffset),
        delayMillis = delayMillis,
        modifier = modifier,
        content = content
    )
}
