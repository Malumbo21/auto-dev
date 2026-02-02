package cc.unitmesh.devins.idea.toolwindow.remote

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

enum class RemoteAgentMode {
    SERVER,
    ACP,
}

@Composable
fun IdeaRemoteModeSelector(
    mode: RemoteAgentMode,
    onModeChange: (RemoteAgentMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Pill(
            label = "Server",
            selected = mode == RemoteAgentMode.SERVER,
            onClick = { onModeChange(RemoteAgentMode.SERVER) }
        )
        Pill(
            label = "ACP",
            selected = mode == RemoteAgentMode.ACP,
            onClick = { onModeChange(RemoteAgentMode.ACP) }
        )
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        targetValue = if (selected) JewelTheme.globalColors.text.normal.copy(alpha = 0.12f) else Color.Transparent,
        label = "remoteModeBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.normal.copy(alpha = 0.7f),
        label = "remoteModeFg"
    )

    Text(
        text = label,
        style = JewelTheme.defaultTextStyle.copy(color = fg),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

