package cc.unitmesh.devins.ui.compose.agent

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.compose.terminal.PlatformTerminalDisplay

/**
 * iOS implementation of LiveTerminalItem
 * Terminal functionality is limited on iOS
 */
@Composable
actual fun LiveTerminalItem(
    sessionId: String,
    command: String,
    workingDirectory: String?,
    ptyHandle: Any?,
    exitCode: Int?,
    executionTimeMs: Long?,
    output: String?
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Terminal functionality is not available on iOS")
        Text("Command: $command")
        if (workingDirectory != null) {
            Text("Working directory: $workingDirectory")
        }

        if (exitCode != null && !output.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            PlatformTerminalDisplay(
                output = output,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Output will appear after completion.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

