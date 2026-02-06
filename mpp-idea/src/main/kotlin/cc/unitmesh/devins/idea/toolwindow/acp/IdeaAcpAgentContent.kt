package cc.unitmesh.devins.idea.toolwindow.acp

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * Features:
 * - Agent preset selector (Codex, Kimi, Gemini, Claude, Copilot)
 * - Quick connect from config.yaml agents
 * - Manual configuration panel (collapsible)
 * - Connection status with error display
 * - Reuses existing timeline renderer for streaming output
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
    var availableAgents by remember { mutableStateOf<Map<String, cc.unitmesh.config.AcpAgentConfig>>(emptyMap()) }
    var selectedAgentKey by remember { mutableStateOf<String?>(null) }
    var installedPresets by remember { mutableStateOf<List<IdeaAcpAgentPreset>>(emptyList()) }

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
    IdeaLaunchedEffect(viewModel) {
        viewModel.availableAgents.collect { availableAgents = it }
    }
    IdeaLaunchedEffect(viewModel) {
        viewModel.selectedAgentKey.collect { selectedAgentKey = it }
    }
    IdeaLaunchedEffect(viewModel) {
        viewModel.installedPresets.collect { installedPresets = it }
    }

    // Show advanced config panel
    var showAdvancedConfig by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Agent selector and connection controls
        AcpAgentSelector(
            availableAgents = availableAgents,
            selectedAgentKey = selectedAgentKey,
            installedPresets = installedPresets,
            isConnected = isConnected,
            connectionError = connectionError,
            onSelectAgent = { viewModel.selectAgent(it) },
            onAddPreset = { viewModel.addPresetAgent(it) },
            onConnect = { viewModel.connectSelectedAgent() },
            onDisconnect = { viewModel.disconnect() },
            showAdvanced = showAdvancedConfig,
            onToggleAdvanced = { showAdvancedConfig = !showAdvancedConfig },
            modifier = Modifier.fillMaxWidth()
        )

        // Advanced manual config (collapsible)
        if (showAdvancedConfig) {
            AcpManualConfigPanel(
                viewModel = viewModel,
                isConnected = isConnected,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Stderr tail (only when disconnected and has output)
        if (stderrTail.isNotEmpty() && !isConnected) {
            val tailText = remember(stderrTail) { stderrTail.takeLast(10).joinToString("\n") }
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

        // Timeline content
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

/**
 * Agent selector with preset dropdown and connection controls.
 */
@Composable
private fun AcpAgentSelector(
    availableAgents: Map<String, cc.unitmesh.config.AcpAgentConfig>,
    selectedAgentKey: String?,
    installedPresets: List<IdeaAcpAgentPreset>,
    isConnected: Boolean,
    connectionError: String?,
    onSelectAgent: (String) -> Unit,
    onAddPreset: (IdeaAcpAgentPreset) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(JewelTheme.globalColors.panelBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Agent dropdown + Connect/Stop button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Agent:", style = JewelTheme.defaultTextStyle, modifier = Modifier.width(50.dp))

            // Agent selector dropdown
            Box(modifier = Modifier.weight(1f)) {
                AgentDropdown(
                    availableAgents = availableAgents,
                    selectedKey = selectedAgentKey,
                    installedPresets = installedPresets,
                    onSelectAgent = onSelectAgent,
                    onAddPreset = onAddPreset,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Connect/Stop button
            if (isConnected) {
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.height(32.dp)) { Text("Stop") }
            } else {
                DefaultButton(
                    onClick = onConnect,
                    enabled = selectedAgentKey != null,
                    modifier = Modifier.height(32.dp)
                ) { Text("Start") }
            }
        }

        // Row 2: Connection status + Advanced toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConnectionStatusBar(
                isConnected = isConnected,
                connectionError = connectionError,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = if (showAdvanced) "Hide Config" else "Manual Config",
                style = JewelTheme.defaultTextStyle.copy(
                    color = JewelTheme.globalColors.text.info
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onToggleAdvanced)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Agent dropdown showing configured agents and installable presets.
 */
@Composable
private fun AgentDropdown(
    availableAgents: Map<String, cc.unitmesh.config.AcpAgentConfig>,
    selectedKey: String?,
    installedPresets: List<IdeaAcpAgentPreset>,
    onSelectAgent: (String) -> Unit,
    onAddPreset: (IdeaAcpAgentPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedLabel = if (selectedKey != null) {
        val config = availableAgents[selectedKey]
        config?.name?.ifBlank { selectedKey } ?: selectedKey
    } else {
        "Select an agent..."
    }

    Box(modifier = modifier) {
        // Display selected agent as clickable text
        Text(
            text = selectedLabel,
            style = JewelTheme.defaultTextStyle,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )

        // Dropdown popup
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(JewelTheme.globalColors.panelBackground)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    // Configured agents
                    if (availableAgents.isNotEmpty()) {
                        availableAgents.forEach { (key, config) ->
                            val isSelected = key == selectedKey
                            val bgColor = if (isSelected) {
                                JewelTheme.globalColors.text.normal.copy(alpha = 0.08f)
                            } else {
                                JewelTheme.globalColors.panelBackground
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(bgColor)
                                    .clickable {
                                        onSelectAgent(key)
                                        expanded = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            if (isSelected) IdeaAutoDevColors.Green.c400
                                            else JewelTheme.globalColors.text.normal.copy(alpha = 0.3f),
                                            CircleShape
                                        )
                                )
                                Column {
                                    Text(
                                        text = config.name.ifBlank { key },
                                        style = JewelTheme.defaultTextStyle
                                    )
                                    Text(
                                        text = "${config.command} ${config.args}".trim(),
                                        style = JewelTheme.defaultTextStyle.copy(
                                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Add from presets (only show presets not already configured)
                    val unconfiguredPresets = installedPresets.filter { preset ->
                        !availableAgents.containsKey(preset.id)
                    }
                    if (unconfiguredPresets.isNotEmpty()) {
                        if (availableAgents.isNotEmpty()) {
                            // Separator
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .padding(vertical = 4.dp)
                                    .background(JewelTheme.globalColors.text.normal.copy(alpha = 0.1f))
                            )
                        }

                        Text(
                            text = "Add from detected:",
                            style = JewelTheme.defaultTextStyle.copy(
                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )

                        unconfiguredPresets.forEach { preset ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        onAddPreset(preset)
                                        expanded = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "+ ${preset.name}",
                                    style = JewelTheme.defaultTextStyle.copy(
                                        color = IdeaAutoDevColors.Blue.c400
                                    )
                                )
                                Text(
                                    text = "(${preset.command} ${preset.args})",
                                    style = JewelTheme.defaultTextStyle.copy(
                                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }

                    // Close dropdown area
                    if (availableAgents.isEmpty() && unconfiguredPresets.isEmpty()) {
                        Text(
                            text = "No agents found. Install codex, kimi, or gemini CLI.",
                            style = JewelTheme.defaultTextStyle.copy(
                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Manual configuration panel (for advanced users or custom agents).
 */
@Composable
private fun AcpManualConfigPanel(
    viewModel: IdeaAcpAgentViewModel,
    isConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    var command by remember { mutableStateOf("") }
    var args by remember { mutableStateOf("") }
    var envText by remember { mutableStateOf("") }
    var cwd by remember { mutableStateOf(viewModel.project.basePath ?: "") }

    val commandState = rememberTextFieldState(command)
    val argsState = rememberTextFieldState(args)
    val cwdState = rememberTextFieldState(cwd)
    var envValue by remember { mutableStateOf(TextFieldValue(envText)) }

    // Sync textfield changes -> state
    IdeaLaunchedEffect(Unit) {
        snapshotFlow { commandState.text.toString() }
            .distinctUntilChanged()
            .collect { command = it }
    }
    IdeaLaunchedEffect(Unit) {
        snapshotFlow { argsState.text.toString() }
            .distinctUntilChanged()
            .collect { args = it }
    }
    IdeaLaunchedEffect(Unit) {
        snapshotFlow { cwdState.text.toString() }
            .distinctUntilChanged()
            .collect { cwd = it }
    }
    IdeaLaunchedEffect(envValue.text) {
        envText = envValue.text
    }

    Column(
        modifier = modifier
            .background(JewelTheme.globalColors.panelBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cmd:", style = JewelTheme.defaultTextStyle, modifier = Modifier.width(40.dp))
            TextField(state = commandState, placeholder = { Text("e.g., codex") }, modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Args:", style = JewelTheme.defaultTextStyle, modifier = Modifier.width(40.dp))
            TextField(state = argsState, placeholder = { Text("e.g., --acp") }, modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cwd:", style = JewelTheme.defaultTextStyle, modifier = Modifier.width(40.dp))
            TextField(state = cwdState, placeholder = { Text("working directory") }, modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Env:", style = JewelTheme.defaultTextStyle, modifier = Modifier.width(40.dp))
            TextArea(
                value = envValue,
                onValueChange = { envValue = it },
                modifier = Modifier.weight(1f).height(60.dp),
                placeholder = { Text("KEY=VALUE (one per line)") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            DefaultButton(
                onClick = {
                    val key = command.trim().substringAfterLast('/').substringAfterLast('\\')
                        .ifBlank { "custom" }
                    viewModel.saveCustomAgent(
                        key = key,
                        config = cc.unitmesh.config.AcpAgentConfig(
                            name = command.trim(),
                            command = command.trim(),
                            args = args.trim(),
                            env = envText.trim()
                        )
                    )
                },
                enabled = command.isNotBlank(),
                modifier = Modifier.height(28.dp)
            ) {
                Text("Save & Select")
            }
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
