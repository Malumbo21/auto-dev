package cc.unitmesh.devins.ui.compose.agent.chatdb.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import cc.unitmesh.devins.ui.compose.agent.chatdb.model.*
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons

/**
 * Inline configuration pane for adding/editing data source
 * Displayed on the right side instead of a dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSourceConfigPane(
    existingConfig: DataSourceConfig?,
    onCancel: () -> Unit,
    onSave: (DataSourceConfig) -> Unit,
    onSaveAndConnect: (DataSourceConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember(existingConfig) { mutableStateOf(existingConfig?.name ?: "") }
    var dialect by remember(existingConfig) { mutableStateOf(existingConfig?.dialect ?: DatabaseDialect.MYSQL) }
    var host by remember(existingConfig) { mutableStateOf(existingConfig?.host ?: "localhost") }
    var port by remember(existingConfig) { mutableStateOf(existingConfig?.port?.toString() ?: "3306") }
    var database by remember(existingConfig) { mutableStateOf(existingConfig?.database ?: "") }
    var username by remember(existingConfig) { mutableStateOf(existingConfig?.username ?: "") }
    var password by remember(existingConfig) { mutableStateOf(existingConfig?.password ?: "") }
    var description by remember(existingConfig) { mutableStateOf(existingConfig?.description ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    var dialectExpanded by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(existingConfig?.description?.isNotBlank() == true) }

    val isEditing = existingConfig != null
    val scrollState = rememberScrollState()

    val isValid = name.isNotBlank() && database.isNotBlank() &&
        (dialect == DatabaseDialect.SQLITE || host.isNotBlank())

    fun buildConfig(): DataSourceConfig = DataSourceConfig(
        id = existingConfig?.id ?: "",
        name = name.trim(),
        dialect = dialect,
        host = host.trim(),
        port = port.toIntOrNull() ?: dialect.defaultPort,
        database = database.trim(),
        username = username.trim(),
        password = password,
        description = description.trim()
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Edit Data Source" else "Add Data Source",
                    style = MaterialTheme.typography.titleSmall
                )
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(AutoDevComposeIcons.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }
        }

        HorizontalDivider()

        // Form content - more compact
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Name and Database Type in one row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )
                ExposedDropdownMenuBox(
                    expanded = dialectExpanded,
                    onExpandedChange = { dialectExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = dialect.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dialectExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    ExposedDropdownMenu(
                        expanded = dialectExpanded,
                        onDismissRequest = { dialectExpanded = false }
                    ) {
                        DatabaseDialect.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName, style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    dialect = option
                                    port = option.defaultPort.toString()
                                    dialectExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Host and Port (not for SQLite)
            if (dialect != DatabaseDialect.SQLITE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host *") },
                        modifier = Modifier.weight(2f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Database name
            OutlinedTextField(
                value = database,
                onValueChange = { database = it },
                label = { Text(if (dialect == DatabaseDialect.SQLITE) "File Path *" else "Database *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall
            )

            // Username and Password in one row (not for SQLite)
            if (dialect != DatabaseDialect.SQLITE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    if (showPassword) AutoDevComposeIcons.VisibilityOff else AutoDevComposeIcons.Visibility,
                                    contentDescription = if (showPassword) "Hide" else "Show",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Advanced Settings - collapsible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { advancedExpanded = !advancedExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    if (advancedExpanded) AutoDevComposeIcons.ExpandLess else AutoDevComposeIcons.ExpandMore,
                    contentDescription = if (advancedExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Advanced Settings",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = advancedExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        HorizontalDivider()

        // Action buttons - more compact
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel, modifier = Modifier.height(36.dp)) {
                Text("Cancel", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { onSave(buildConfig()) },
                enabled = isValid,
                modifier = Modifier.height(36.dp)
            ) {
                Text("Save", style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = { onSaveAndConnect(buildConfig()) },
                enabled = isValid,
                modifier = Modifier.height(36.dp)
            ) {
                Text("Save & Connect", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

