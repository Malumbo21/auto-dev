package cc.unitmesh.devins.ui.compose.editor.multimodal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons

/**
 * Available image generation models for GLM CogView.
 */
enum class ImageGenerationModel(val modelId: String, val displayName: String) {
    COGVIEW_3_FLASH("cogview-3-flash", "CogView-3-Flash"),
    COGVIEW_4("cogview-4", "CogView-4");

    companion object {
        fun fromModelId(modelId: String): ImageGenerationModel {
            return entries.find { it.modelId == modelId } ?: COGVIEW_3_FLASH
        }
    }
}

/**
 * Compact image generation model selector.
 * Shows current model and allows switching between available image generation models.
 * Only visible when GLM provider is selected.
 *
 * @param currentModel The currently selected image generation model
 * @param onModelChange Callback when user selects a different model
 * @param modifier Modifier for the component
 */
@Composable
fun ImageGenerationModelSelector(
    currentModel: ImageGenerationModel,
    onModelChange: (ImageGenerationModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = AutoDevComposeIcons.Image,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = "Image: ${currentModel.displayName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Icon(
                imageVector = AutoDevComposeIcons.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ImageGenerationModel.entries.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = model.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = model.modelId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    },
                    onClick = {
                        onModelChange(model)
                        expanded = false
                    },
                    trailingIcon = {
                        if (model == currentModel) {
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

