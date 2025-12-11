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
import kotlinx.coroutines.launch

/**
 * Vision model configuration for multimodal analysis.
 * Contains the model name and the API key from the config.
 */
data class VisionModelConfig(
    val name: String,
    val displayName: String,
    val apiKey: String,
    val provider: String
) {
    companion object {
        /**
         * Known vision-capable models.
         * These models support image understanding.
         */
        val VISION_MODEL_PATTERNS = listOf(
            "glm-4.6v", "glm-4.5v", "glm-4.1v-thinking", "glm-4v",
            "gpt-4-vision", "gpt-4o", "gpt-4o-mini",
            "claude-3", "claude-3.5",
            "gemini-pro-vision", "gemini-1.5", "gemini-2",
            "qwen-vl", "qwen2-vl"
        )

        /**
         * Check if a model name indicates vision capability.
         */
        fun isVisionModel(modelName: String): Boolean {
            val lowerName = modelName.lowercase()
            return VISION_MODEL_PATTERNS.any { pattern ->
                lowerName.contains(pattern.lowercase())
            } || lowerName.contains("vision") || lowerName.contains("-vl")
        }

        /**
         * Create VisionModelConfig from NamedModelConfig.
         */
        fun fromNamedConfig(config: NamedModelConfig): VisionModelConfig {
            return VisionModelConfig(
                name = config.model,
                displayName = "${config.provider}/${config.model}",
                apiKey = config.apiKey,
                provider = config.provider
            )
        }
    }
}

/**
 * Compact vision model selector for the ImageAttachmentBar.
 * Shows current model and allows switching between available vision models.
 */
@Composable
fun VisionModelSelector(
    currentModel: String,
    onModelChange: (VisionModelConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<VisionModelConfig>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // Load vision-capable models from config
    LaunchedEffect(Unit) {
        try {
            val wrapper = ConfigManager.load()
            val allConfigs = wrapper.getAllConfigs()
            
            // Filter to vision-capable models
            availableModels = allConfigs
                .filter { VisionModelConfig.isVisionModel(it.model) && it.apiKey.isNotBlank() }
                .map { VisionModelConfig.fromNamedConfig(it) }
            
            // If no vision models found, add default GLM config if available
            if (availableModels.isEmpty()) {
                val glmConfig = allConfigs.find { 
                    it.provider.equals("glm", ignoreCase = true) && it.apiKey.isNotBlank() 
                }
                if (glmConfig != null) {
                    availableModels = listOf(
                        VisionModelConfig(
                            name = "glm-4.6v",
                            displayName = "GLM/glm-4.6v",
                            apiKey = glmConfig.apiKey,
                            provider = "glm"
                        )
                    )
                }
            }
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
                            text = model.displayName,
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

