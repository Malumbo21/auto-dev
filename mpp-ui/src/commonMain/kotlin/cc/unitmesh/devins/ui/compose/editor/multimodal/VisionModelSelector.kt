package cc.unitmesh.devins.ui.compose.editor.multimodal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons
import cc.unitmesh.llm.NamedModelConfig

/**
 * Compact vision model selector for the ImageAttachmentBar.
 * Shows current model and allows switching between available vision models.
 */
@Composable
fun VisionModelSelector(
    currentModel: String,
    onModelChange: (NamedModelConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<NamedModelConfig>>(emptyList()) }

    // Load vision-capable models from ConfigManager
    LaunchedEffect(Unit) {
        try {
            availableModels = ConfigManager.load().getAllConfigs()
        } catch (e: Exception) {
            println("Failed to load vision models: ${e.message}")
        }
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clickable(enabled = availableModels.size > 1) { expanded = true }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = AutoDevComposeIcons.Vision,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Vision: $currentModel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (availableModels.size > 1) {
                Icon(
                    imageVector = AutoDevComposeIcons.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableModels.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        onModelChange(model)
                        expanded = false
                    },
                    trailingIcon = {
                        if (model.name == currentModel) {
                            Icon(
                                imageVector = AutoDevComposeIcons.Check,
                                contentDescription = "Selected",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        }
    }
}

