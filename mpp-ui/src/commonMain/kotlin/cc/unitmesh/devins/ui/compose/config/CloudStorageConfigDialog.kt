package cc.unitmesh.devins.ui.compose.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cc.unitmesh.config.CloudStorageConfig
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons
import kotlinx.coroutines.launch

/**
 * Dialog for configuring cloud storage (Tencent COS) for multimodal image upload.
 */
@Composable
fun CloudStorageConfigDialog(
    onDismiss: () -> Unit,
    onSave: (CloudStorageConfig) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    
    // Form state
    var secretId by remember { mutableStateOf("") }
    var secretKey by remember { mutableStateOf("") }
    var bucket by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("ap-beijing") }
    var enabled by remember { mutableStateOf(true) }
    
    // Password visibility
    var showSecretKey by remember { mutableStateOf(false) }
    
    // Error state
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Load existing config
    LaunchedEffect(Unit) {
        try {
            val configWrapper = ConfigManager.load()
            val cloudStorage = configWrapper.getCloudStorage()
            secretId = cloudStorage.secretId
            secretKey = cloudStorage.secretKey
            bucket = cloudStorage.bucket
            region = cloudStorage.region
            enabled = cloudStorage.enabled
        } catch (e: Exception) {
            errorMessage = "Failed to load config: ${e.message}"
        } finally {
            isLoading = false
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = AutoDevComposeIcons.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Cloud Storage Configuration",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Configure Tencent COS for multimodal image upload. Images will be uploaded to your COS bucket before being analyzed by the vision model.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    // Secret ID
                    OutlinedTextField(
                        value = secretId,
                        onValueChange = { secretId = it },
                        label = { Text("Secret ID") },
                        placeholder = { Text("AKIDxxxxxxxxxx") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Secret Key
                    OutlinedTextField(
                        value = secretKey,
                        onValueChange = { secretKey = it },
                        label = { Text("Secret Key") },
                        placeholder = { Text("Enter your secret key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showSecretKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showSecretKey = !showSecretKey }) {
                                Icon(
                                    imageVector = if (showSecretKey) {
                                        AutoDevComposeIcons.VisibilityOff
                                    } else {
                                        AutoDevComposeIcons.Visibility
                                    },
                                    contentDescription = if (showSecretKey) "Hide" else "Show"
                                )
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Bucket
                    OutlinedTextField(
                        value = bucket,
                        onValueChange = { bucket = it },
                        label = { Text("Bucket Name") },
                        placeholder = { Text("bucket-name-appid") },
                        supportingText = { Text("Format: bucket-appid (e.g., autodev-1251908290)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Region
                    OutlinedTextField(
                        value = region,
                        onValueChange = { region = it },
                        label = { Text("Region") },
                        placeholder = { Text("ap-beijing") },
                        supportingText = { Text("e.g., ap-beijing, ap-guangzhou, ap-shanghai") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Enabled toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Cloud Storage",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it }
                        )
                    }
                    
                    // Error message
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    isSaving = true
                                    errorMessage = null
                                    try {
                                        val newConfig = CloudStorageConfig(
                                            provider = "tencent-cos",
                                            secretId = secretId.trim(),
                                            secretKey = secretKey.trim(),
                                            bucket = bucket.trim(),
                                            region = region.trim(),
                                            enabled = enabled
                                        )
                                        
                                        // Save to config file
                                        val currentConfig = ConfigManager.load()
                                        val updatedConfig = currentConfig.configFile.copy(
                                            cloudStorage = newConfig
                                        )
                                        ConfigManager.save(updatedConfig)
                                        
                                        onSave(newConfig)
                                        onDismiss()
                                    } catch (e: Exception) {
                                        errorMessage = "Failed to save: ${e.message}"
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            enabled = !isSaving && secretId.isNotBlank() && secretKey.isNotBlank() && bucket.isNotBlank()
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }
}

