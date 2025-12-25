package cc.unitmesh.devins.ui.compose.runconfig

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.agent.runconfig.RunConfig
import cc.unitmesh.agent.runconfig.RunConfigState
import cc.unitmesh.agent.runconfig.RunConfigType
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors

/**
 * RunButton - A dropdown button for project run configurations.
 * 
 * Displays in the top-right area of the TopBar:
 * - "Configure" button if no run configs exist
 * - Green play button with dropdown if configs available
 * 
 * Similar to IDE run buttons (IntelliJ, VS Code).
 */
@Composable
fun RunButton(
    state: RunConfigState,
    configs: List<RunConfig>,
    defaultConfig: RunConfig?,
    isRunning: Boolean,
    runningConfigId: String?,
    onConfigure: () -> Unit,
    onRunConfig: (RunConfig) -> Unit,
    onStopRunning: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        when (state) {
            RunConfigState.NOT_CONFIGURED -> {
                // Configure button
                ConfigureButton(onClick = onConfigure)
            }
            
            RunConfigState.ANALYZING -> {
                // Loading state
                AnalyzingButton()
            }
            
            RunConfigState.CONFIGURED -> {
                // Run button with dropdown
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Main run button
                    RunPlayButton(
                        config = defaultConfig,
                        isRunning = isRunning && runningConfigId == defaultConfig?.id,
                        onClick = {
                            if (isRunning && runningConfigId == defaultConfig?.id) {
                                onStopRunning()
                            } else {
                                defaultConfig?.let { onRunConfig(it) }
                            }
                        }
                    )
                    
                    // Dropdown arrow
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = AutoDevComposeIcons.ArrowDropDown,
                            contentDescription = "More run options",
                            tint = AutoDevColors.Signal.success,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                // Dropdown menu
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    // Group by type
                    val grouped = configs.groupBy { it.type }
                    
                    grouped.forEach { (type, typeConfigs) ->
                        // Type header
                        Text(
                            text = type.name.replace("_", " "),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        typeConfigs.forEach { config ->
                            RunConfigMenuItem(
                                config = config,
                                isRunning = isRunning && runningConfigId == config.id,
                                isDefault = config.id == defaultConfig?.id,
                                onClick = {
                                    menuExpanded = false
                                    if (isRunning && runningConfigId == config.id) {
                                        onStopRunning()
                                    } else {
                                        onRunConfig(config)
                                    }
                                }
                            )
                        }
                        
                        if (type != grouped.keys.last()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    // Re-analyze option
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = AutoDevComposeIcons.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("Re-analyze Project")
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            onConfigure()
                        }
                    )
                }
            }
            
            RunConfigState.ERROR -> {
                // Error state - show configure button with error indicator
                ConfigureButton(
                    onClick = onConfigure,
                    hasError = true
                )
            }
        }
    }
}

/**
 * Configure button when no run configs exist
 */
@Composable
private fun ConfigureButton(
    onClick: () -> Unit,
    hasError: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (hasError) {
            AutoDevColors.Signal.error.copy(alpha = 0.1f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.height(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (hasError) AutoDevComposeIcons.Warning else AutoDevComposeIcons.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (hasError) {
                    AutoDevColors.Signal.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = if (hasError) "Config Error" else "Configure",
                style = MaterialTheme.typography.labelMedium,
                color = if (hasError) {
                    AutoDevColors.Signal.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * Analyzing/loading button
 */
@Composable
private fun AnalyzingButton() {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.height(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = AutoDevComposeIcons.Refresh,
                contentDescription = null,
                modifier = Modifier.size(14.dp).rotate(rotation),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Analyzing...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Main run/play button
 */
@Composable
private fun RunPlayButton(
    config: RunConfig?,
    isRunning: Boolean,
    onClick: () -> Unit
) {
    val buttonColor = if (isRunning) {
        AutoDevColors.Signal.error
    } else {
        AutoDevColors.Signal.success
    }
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = buttonColor.copy(alpha = 0.15f),
        modifier = Modifier.height(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isRunning) AutoDevComposeIcons.Stop else AutoDevComposeIcons.PlayArrow,
                contentDescription = if (isRunning) "Stop" else "Run",
                modifier = Modifier.size(16.dp),
                tint = buttonColor
            )
            Text(
                text = if (isRunning) "Stop" else (config?.name ?: "Run"),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = buttonColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 100.dp)
            )
        }
    }
}

/**
 * Menu item for a run configuration
 */
@Composable
private fun RunConfigMenuItem(
    config: RunConfig,
    isRunning: Boolean,
    isDefault: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Type icon
                Icon(
                    imageVector = getTypeIcon(config.type),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isRunning) {
                        AutoDevColors.Signal.error
                    } else {
                        getTypeColor(config.type)
                    }
                )
                
                // Config name
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = config.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isDefault) FontWeight.Medium else FontWeight.Normal
                        )
                        if (isDefault) {
                            Text(
                                text = "(default)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (config.description.isNotBlank()) {
                        Text(
                            text = config.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Running indicator
                if (isRunning) {
                    Icon(
                        imageVector = AutoDevComposeIcons.Stop,
                        contentDescription = "Running",
                        modifier = Modifier.size(14.dp),
                        tint = AutoDevColors.Signal.error
                    )
                }
            }
        },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Get icon for run config type
 */
@Composable
private fun getTypeIcon(type: RunConfigType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (type) {
        RunConfigType.RUN -> AutoDevComposeIcons.PlayArrow
        RunConfigType.DEV -> AutoDevComposeIcons.Code
        RunConfigType.TEST -> AutoDevComposeIcons.Science
        RunConfigType.BUILD -> AutoDevComposeIcons.Build
        RunConfigType.LINT -> AutoDevComposeIcons.Check
        RunConfigType.DEPLOY -> AutoDevComposeIcons.RocketLaunch
        RunConfigType.CLEAN -> AutoDevComposeIcons.Delete
        RunConfigType.INSTALL -> AutoDevComposeIcons.CloudDownload
        RunConfigType.CUSTOM -> AutoDevComposeIcons.Terminal
    }
}

/**
 * Get color for run config type
 */
@Composable
private fun getTypeColor(type: RunConfigType): Color {
    return when (type) {
        RunConfigType.RUN -> AutoDevColors.Signal.success
        RunConfigType.DEV -> AutoDevColors.Energy.xiu
        RunConfigType.TEST -> AutoDevColors.Signal.info
        RunConfigType.BUILD -> AutoDevColors.Signal.warn
        RunConfigType.LINT -> AutoDevColors.Energy.ai
        RunConfigType.DEPLOY -> AutoDevColors.Energy.ai
        RunConfigType.CLEAN -> MaterialTheme.colorScheme.outline
        RunConfigType.INSTALL -> AutoDevColors.Signal.info
        RunConfigType.CUSTOM -> MaterialTheme.colorScheme.onSurface
    }
}

