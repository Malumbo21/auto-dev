package cc.unitmesh.devins.ui.compose.runconfig

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import cc.unitmesh.agent.runconfig.RunConfig
import cc.unitmesh.agent.runconfig.RunConfigState
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors

/**
 * FloatingRunButton - IDE-like floating Run entry.
 *
 * Behavior:
 * - NOT_CONFIGURED: shows a "Settings" FAB for configure
 * - ANALYZING: shows spinning refresh with AI log tooltip on hover
 * - CONFIGURED: shows Run/Stop FAB; tap opens menu with actions
 * - ERROR: shows "Config Error" FAB
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingRunButton(
    state: RunConfigState,
    configs: List<RunConfig>,
    defaultConfig: RunConfig?,
    isRunning: Boolean,
    runningConfigId: String?,
    analysisLog: String = "",
    onConfigure: () -> Unit,
    onRunConfig: (RunConfig) -> Unit,
    onStopRunning: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        when (state) {
            RunConfigState.NOT_CONFIGURED -> {
                SmallFloatingActionButton(
                    onClick = onConfigure,
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = AutoDevComposeIcons.Settings,
                        contentDescription = "Configure Run",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            RunConfigState.ANALYZING -> {
                // Spinning animation
                val infiniteTransition = rememberInfiniteTransition(label = "spin")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1000, easing = LinearEasing)
                    ),
                    label = "rotation"
                )
                
                // Tooltip showing AI analysis log
                TooltipArea(
                    tooltip = {
                        if (analysisLog.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 320.dp)
                                    .heightIn(max = 200.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.inverseSurface,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = analysisLog,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.inverseOnSurface
                                )
                            }
                        }
                    },
                    tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
                    delayMillis = 300
                ) {
                    SmallFloatingActionButton(
                        onClick = {},
                        containerColor = AutoDevColors.Signal.info.copy(alpha = 0.15f)
                    ) {
                        Icon(
                            imageVector = AutoDevComposeIcons.Sync,
                            contentDescription = "Analyzing with AI...",
                            tint = AutoDevColors.Signal.info,
                            modifier = Modifier.rotate(rotation)
                        )
                    }
                }
            }

            RunConfigState.ERROR -> {
                SmallFloatingActionButton(
                    onClick = onConfigure,
                    containerColor = AutoDevColors.Signal.error.copy(alpha = 0.12f)
                ) {
                    Icon(
                        imageVector = AutoDevComposeIcons.Warning,
                        contentDescription = "Run config error",
                        tint = AutoDevColors.Signal.error
                    )
                }
            }

            RunConfigState.CONFIGURED -> {
                val isDefaultRunning = isRunning && runningConfigId != null && runningConfigId == defaultConfig?.id
                val fabTint = if (isDefaultRunning) AutoDevColors.Signal.error else AutoDevColors.Signal.success

                SmallFloatingActionButton(
                    onClick = { menuExpanded = true },
                    containerColor = fabTint.copy(alpha = 0.12f)
                ) {
                    Icon(
                        imageVector = if (isDefaultRunning) AutoDevComposeIcons.Stop else AutoDevComposeIcons.PlayArrow,
                        contentDescription = if (isDefaultRunning) "Stop" else "Run",
                        tint = fabTint
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    // Show all configs (avoid duplicates)
                    configs.forEach { cfg ->
                        val running = isRunning && runningConfigId == cfg.id
                        val isDefault = cfg.id == defaultConfig?.id
                        val tint = when {
                            running -> AutoDevColors.Signal.error
                            isDefault -> AutoDevColors.Signal.success
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    text = if (isDefault) "${cfg.name} (default)" else cfg.name,
                                    style = if (isDefault) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall
                                ) 
                            },
                            onClick = {
                                menuExpanded = false
                                println("[FloatingRunButton] Running config: ${cfg.name}")
                                if (running) onStopRunning() else onRunConfig(cfg)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (running) AutoDevComposeIcons.Stop else AutoDevComposeIcons.PlayArrow,
                                    contentDescription = null,
                                    tint = tint
                                )
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text("Re-analyze Project") },
                        onClick = {
                            menuExpanded = false
                            onConfigure()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = AutoDevComposeIcons.Refresh,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    }
}


