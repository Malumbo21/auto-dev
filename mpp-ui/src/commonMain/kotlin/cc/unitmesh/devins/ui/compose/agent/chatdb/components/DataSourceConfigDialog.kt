package cc.unitmesh.devins.ui.compose.agent.chatdb.components

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
import cc.unitmesh.devins.ui.compose.agent.chatdb.model.*
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons

/**
 * Dialog for adding/editing data source configuration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSourceConfigDialog(
    existingConfig: DataSourceConfig?,
    onDismiss: () -> Unit,
    onSave: (DataSourceConfig) -> Unit
) {
    var name by remember { mutableStateOf(existingConfig?.name ?: "") }
    var dialect by remember { mutableStateOf(existingConfig?.dialect ?: DatabaseDialect.MYSQL) }
    var host by remember { mutableStateOf(existingConfig?.host ?: "localhost") }
    var port by remember { mutableStateOf(existingConfig?.port?.toString() ?: "3306") }
    var database by remember { mutableStateOf(existingConfig?.database ?: "") }
    var username by remember { mutableStateOf(existingConfig?.username ?: "") }
    var password by remember { mutableStateOf(existingConfig?.password ?: "") }
    var description by remember { mutableStateOf(existingConfig?.description ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    var dialectExpanded by remember { mutableStateOf(false) }

    val isEditing = existingConfig != null
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = if (isEditing) "Edit Data Source" else "Add Data Source",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

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

                Spacer(modifier = Modifier.height(16.dp))

                // Host and Port
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
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Database name
                OutlinedTextField(
                    value = database,
                    onValueChange = { database = it },
                    label = { Text(if (dialect == DatabaseDialect.SQLITE) "File Path *" else "Database *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (dialect != DatabaseDialect.SQLITE) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Username
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password
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

                Spacer(modifier = Modifier.height(16.dp))

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )

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
                            val config = DataSourceConfig(
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
                            onSave(config)
                        },
                        enabled = name.isNotBlank() && database.isNotBlank() &&
                            (dialect == DatabaseDialect.SQLITE || host.isNotBlank())
                    ) {
                        Text(if (isEditing) "Save" else "Add")
                    }
                }
            }
        }
    }
}

