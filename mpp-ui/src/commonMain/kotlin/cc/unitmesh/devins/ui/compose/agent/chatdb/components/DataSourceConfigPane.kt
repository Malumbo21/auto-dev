package cc.unitmesh.devins.ui.compose.agent.chatdb.components

import androidx.compose.foundation.background
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
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Edit Data Source" else "Add Data Source",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onCancel) {
                    Icon(AutoDevComposeIcons.Close, contentDescription = "Close")
                }
            }
        }

        HorizontalDivider()

        // Form content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Dialect dropdown
            ExposedDropdownMenuBox(
                expanded = dialectExpanded,
                onExpandedChange = { dialectExpanded = it }
            ) {
                OutlinedTextField(
                    value = dialect.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Database Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dialectExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = dialectExpanded,
                    onDismissRequest = { dialectExpanded = false }
                ) {
                    DatabaseDialect.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            onClick = {
                                dialect = option
                                port = option.defaultPort.toString()
                                dialectExpanded = false
                            }
                        )
                    }
                }
            }

            // Host and Port (not for SQLite)
            if (dialect != DatabaseDialect.SQLITE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host *") },
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text("Port *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            // Database name
            OutlinedTextField(
                value = database,
                onValueChange = { database = it },
                label = { Text(if (dialect == DatabaseDialect.SQLITE) "File Path *" else "Database *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Username and Password (not for SQLite)
            if (dialect != DatabaseDialect.SQLITE) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) AutoDevComposeIcons.VisibilityOff else AutoDevComposeIcons.Visibility,
                                contentDescription = if (showPassword) "Hide" else "Show"
                            )
                        }
                    }
                )
            }

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3
            )
        }

        HorizontalDivider()

        // Action buttons
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
                OutlinedButton(
                    onClick = { onSave(buildConfig()) },
                    enabled = isValid
                ) {
                    Text("Save")
                }
                Button(
                    onClick = { onSaveAndConnect(buildConfig()) },
                    enabled = isValid
                ) {
                    Text("Save & Connect")
                }
            }
        }
    }
}

