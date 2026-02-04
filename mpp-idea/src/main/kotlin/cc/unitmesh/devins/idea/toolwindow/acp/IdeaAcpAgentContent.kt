package cc.unitmesh.devins.idea.toolwindow.acp

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.idea.compose.IdeaLaunchedEffect
import cc.unitmesh.devins.idea.components.timeline.IdeaTimelineContent
import cc.unitmesh.devins.idea.theme.IdeaAutoDevColors
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*

/**
 * ACP (Agent Client Protocol) content UI for IntelliJ IDEA plugin.
 *
 * It reuses the existing timeline renderer, but drives it from ACP session/update streams.
 */
@Composable
fun IdeaAcpAgentContent(
    viewModel: IdeaAcpAgentViewModel,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    var timeline by remember { mutableStateOf<List<cc.unitmesh.agent.render.TimelineItem>>(emptyList()) }
    var streamingOutput by remember { mutableStateOf("") }
    var isConnected by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var stderrTail by remember { mutableStateOf<List<String>>(emptyList()) }

    IdeaLaunchedEffect(viewModel.renderer) {
        viewModel.renderer.timeline.collect { timeline = it }
    }
    IdeaLaunchedEffect(viewModel.renderer) {
        viewModel.renderer.currentStreamingOutput.collect { streamingOutput = it }
    }
    IdeaLaunchedEffect(viewModel) {
        viewModel.isConnected.collect { isConnected = it }
    }
    IdeaLaunchedEffect(viewModel) {
        viewModel.connectionError.collect { connectionError = it }
    }
    IdeaLaunchedEffect(viewModel) {
        viewModel.stderrTail.collect { stderrTail = it }
    }

    // Load initial config from settings
    val initialConfig = remember { viewModel.loadConfigFromSettings() }
    var command by remember { mutableStateOf(initialConfig.command) }
    var args by remember { mutableStateOf(initialConfig.args) }
    var envText by remember { mutableStateOf(initialConfig.envText) }
    var cwd by remember { mutableStateOf(initialConfig.cwd) }

    Column(modifier = modifier.fillMaxSize()) {
        AcpConfigPanel(
            command = command,
            onCommandChange = { command = it },
            args = args,
            onArgsChange = { args = it },
            cwd = cwd,
            onCwdChange = { cwd = it },
            envText = envText,
            onEnvChange = { envText = it },
            isConnected = isConnected,
            connectionError = connectionError,
            onStart = {
                val cfg = AcpAgentConfig(command = command, args = args, envText = envText, cwd = cwd)
                viewModel.saveConfigToSettings(cfg)
                viewModel.connect(cfg)
            },
            onStop = { viewModel.disconnect() },
            modifier = Modifier.fillMaxWidth()
        )

        if (stderrTail.isNotEmpty() && !isConnected) {
            val tailText = remember(stderrTail) { stderrTail.takeLast(30).joinToString("\n") }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Text(
                    text = tailText,
                    style = JewelTheme.defaultTextStyle.copy(color = JewelTheme.globalColors.text.info)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            IdeaTimelineContent(
                timeline = timeline,
                streamingOutput = streamingOutput,
                listState = listState,
                project = viewModel.project
            )
        }
    }
}

@Composable
private fun AcpConfigPanel(
    command: String,
    onCommandChange: (String) -> Unit,
    args: String,
    onArgsChange: (String) -> Unit,
    cwd: String,
    onCwdChange: (String) -> Unit,
    envText: String,
    onEnvChange: (String) -> Unit,
    isConnected: Boolean,
    connectionError: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val commandState = rememberTextFieldState(command)
    val argsState = rememberTextFieldState(args)
    val cwdState = rememberTextFieldState(cwd)
    var envValue by remember { mutableStateOf(TextFieldValue(envText)) }

    // Keep envValue in sync
    IdeaLaunchedEffect(envText) {
        if (envValue.text != envText) envValue = TextFieldValue(envText)
    }

    // Sync textfield changes -> callbacks
    IdeaLaunchedEffect(Unit) {
        snapshotFlow { commandState.text.toString() }
            .distinctUntilChanged()
            .collect { onCommandChange(it) }
    }
    IdeaLaunchedEffect(Unit) {
        snapshotFlow { argsState.text.toString() }
            .distinctUntilChanged()
            .collect { onArgsChange(it) }
    }
    IdeaLaunchedEffect(Unit) {
        snapshotFlow { cwdState.text.toString() }
            .distinctUntilChanged()
            .collect { onCwdChange(it) }
    }
    IdeaLaunchedEffect(envValue.text) {
        onEnvChange(envValue.text)
    }

    // Sync external changes -> textfield states
    IdeaLaunchedEffect(command) {
        if (commandState.text.toString() != command) commandState.setTextAndPlaceCursorAtEnd(command)
    }
    IdeaLaunchedEffect(args) {
        if (argsState.text.toString() != args) argsState.setTextAndPlaceCursorAtEnd(args)
    }
    IdeaLaunchedEffect(cwd) {
        if (cwdState.text.toString() != cwd) cwdState.setTextAndPlaceCursorAtEnd(cwd)
    }

    Column(
        modifier = modifier
            .background(JewelTheme.globalColors.panelBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ACP:", style = JewelTheme.defaultTextStyle, modifier = Modifier.width(60.dp))
            ConnectionStatusBar(
                isConnected = isConnected,
                connectionError = connectionError,
                modifier = Modifier.weight(1f)
            )

            if (isConnected) {
                OutlinedButton(onClick = onStop, modifier = Modifier.height(32.dp)) { Text("Stop") }
            } else {
                DefaultButton(onClick = onStart, modifier = Modifier.height(32.dp)) { Text("Start") }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Command:", style = JewelTheme.defaultTextStyle, modifier = Modifier.width(60.dp))
            TextField(state = commandState, placeholder = { Text("agent command (e.g. node)") }, modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Args:", style = JewelTheme.defaultTextStyle, modifier = Modifier.width(60.dp))
            TextField(state = argsState, placeholder = { Text("args (e.g. path/to/agent.js --stdio)") }, modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cwd:", style = JewelTheme.defaultTextStyle, modifier = Modifier.width(60.dp))
            TextField(state = cwdState, placeholder = { Text("working directory") }, modifier = Modifier.weight(1f))
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Env (KEY=VALUE, one per line):", style = JewelTheme.defaultTextStyle)
            TextArea(
                value = envValue,
                onValueChange = { envValue = it },
                modifier = Modifier.fillMaxWidth().height(80.dp),
                placeholder = { Text("OPENAI_API_KEY=...\nHTTP_PROXY=...") }
            )
        }
    }
}

@Composable
private fun ConnectionStatusBar(
    isConnected: Boolean,
    connectionError: String?,
    modifier: Modifier = Modifier,
) {
    val statusColor by animateColorAsState(
        targetValue = if (isConnected) IdeaAutoDevColors.Green.c400 else IdeaAutoDevColors.Red.c400,
        label = "acpStatusColor"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = statusColor, shape = CircleShape)
        )
        Text(
            text = when {
                isConnected -> "Connected"
                connectionError != null -> "Error: $connectionError"
                else -> "Not connected"
            },
            style = JewelTheme.defaultTextStyle.copy(
                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
            )
        )
    }
}

