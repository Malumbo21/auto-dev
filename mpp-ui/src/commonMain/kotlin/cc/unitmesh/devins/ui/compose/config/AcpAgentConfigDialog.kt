package cc.unitmesh.devins.ui.compose.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cc.unitmesh.config.AcpAgentConfig

/**
 * Dialog for configuring ACP (Agent Client Protocol) agents.
 *
 * Allows the user to add, edit, or select an external ACP-compliant agent
 * (e.g., Kimi CLI, Claude CLI, Gemini CLI) that will be spawned as a child process
 * and communicated with via JSON-RPC over stdio.
 *
 * When a custom agent is active, **all interaction goes through the external agent**;
 * the local LLM service is not used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcpAgentConfigDialog(
    currentAgents: Map<String, AcpAgentConfig>,
    activeAgentKey: String?,
    onDismiss: () -> Unit,
    onSave: (agents: Map<String, AcpAgentConfig>, activeKey: String?) -> Unit
) {
    var agents by remember { mutableStateOf(currentAgents.toMutableMap()) }
    var selectedKey by remember { mutableStateOf(activeAgentKey) }
    var isEditing by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<String?>(null) }

    // Edit form state
    var editName by remember { mutableStateOf("") }
    var editCommand by remember { mutableStateOf("") }
    var editArgs by remember { mutableStateOf("") }
    var editEnv by remember { mutableStateOf("") }
    var editKeyName by remember { mutableStateOf("") }
    var commandError by remember { mutableStateOf<String?>(null) }

    fun startEditing(key: String?, config: AcpAgentConfig?) {
        editingKey = key
        editName = config?.name ?: ""
        editCommand = config?.command ?: ""
        editArgs = config?.args ?: ""
        editEnv = config?.env ?: ""
        editKeyName = key ?: ""
        commandError = null
        isEditing = true
    }

    fun saveEdit() {
        if (editCommand.isBlank()) {
            commandError = "Command is required"
            return
        }

        val key = if (editKeyName.isBlank()) {
            editName.lowercase().replace("\\s+".toRegex(), "-").ifBlank { "custom" }
        } else {
            editKeyName
        }

        val config = AcpAgentConfig(
            name = editName.ifBlank { editCommand },
            command = editCommand.trim(),
            args = editArgs.trim(),
            env = editEnv.trim()
        )

        val newAgents = agents.toMutableMap()
        // If renaming, remove the old key
        if (editingKey != null && editingKey != key) {
            newAgents.remove(editingKey)
            if (selectedKey == editingKey) {
                selectedKey = key
            }
        }
        newAgents[key] = config
        agents = newAgents

        if (selectedKey == null) {
            selectedKey = key
        }

        isEditing = false
        editingKey = null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(640.dp)
                .heightIn(max = 600.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Title
                Text(
                    text = "Custom Agent (ACP)",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Configure external ACP-compliant agents (e.g., Kimi CLI, Claude CLI). " +
                        "When active, all interaction goes through the external agent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isEditing) {
                    // Editing form
                    EditAgentForm(
                        editName = editName,
                        onNameChange = { editName = it },
                        editCommand = editCommand,
                        onCommandChange = {
                            editCommand = it
                            commandError = null
                        },
                        commandError = commandError,
                        editArgs = editArgs,
                        onArgsChange = { editArgs = it },
                        editEnv = editEnv,
                        onEnvChange = { editEnv = it },
                        onCancel = {
                            isEditing = false
                            editingKey = null
                        },
                        onSaveEdit = { saveEdit() }
                    )
                } else {
                    // Agent list
                    AgentListView(
                        agents = agents,
                        selectedKey = selectedKey,
                        onSelect = { selectedKey = it },
                        onEdit = { key -> startEditing(key, agents[key]) },
                        onDelete = { key ->
                            val newAgents = agents.toMutableMap()
                            newAgents.remove(key)
                            agents = newAgents
                            if (selectedKey == key) {
                                selectedKey = newAgents.keys.firstOrNull()
                            }
                        },
                        onAddNew = { startEditing(null, null) }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action buttons
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
                            onClick = { onSave(agents, selectedKey) },
                            enabled = agents.isEmpty() || selectedKey != null
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditAgentForm(
    editName: String,
    onNameChange: (String) -> Unit,
    editCommand: String,
    onCommandChange: (String) -> Unit,
    commandError: String?,
    editArgs: String,
    onArgsChange: (String) -> Unit,
    editEnv: String,
    onEnvChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSaveEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Agent Configuration",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Name
            OutlinedTextField(
                value = editName,
                onValueChange = onNameChange,
                label = { Text("Display Name") },
                placeholder = { Text("e.g., Kimi CLI") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Command
            OutlinedTextField(
                value = editCommand,
                onValueChange = onCommandChange,
                label = { Text("Command *") },
                placeholder = { Text("e.g., kimi, claude, gemini") },
                singleLine = true,
                isError = commandError != null,
                supportingText = if (commandError != null) {
                    { Text(commandError, color = MaterialTheme.colorScheme.error) }
                } else {
                    { Text("The executable to spawn (must be in PATH or absolute path)") }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Args
            OutlinedTextField(
                value = editArgs,
                onValueChange = onArgsChange,
                label = { Text("Arguments") },
                placeholder = { Text("e.g., --acp --verbose") },
                singleLine = true,
                supportingText = { Text("Space-separated command-line arguments") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Environment variables
            OutlinedTextField(
                value = editEnv,
                onValueChange = onEnvChange,
                label = { Text("Environment Variables") },
                placeholder = { Text("KEY=VALUE (one per line)") },
                minLines = 2,
                maxLines = 4,
                supportingText = { Text("Optional environment variables, one KEY=VALUE per line") },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSaveEdit) {
                    Text("Save Agent")
                }
            }
        }
    }
}

@Composable
private fun AgentListView(
    agents: Map<String, AcpAgentConfig>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAddNew: () -> Unit
) {
    if (agents.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No ACP agents configured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Add an agent to connect to external AI tools via ACP protocol.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        agents.forEach { (key, config) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (key == selectedKey)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                ),
                onClick = { onSelect(key) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = key == selectedKey,
                        onClick = { onSelect(key) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = config.name.ifBlank { key },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${config.command} ${config.args}".trim(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { onEdit(key) }) {
                        Text("Edit")
                    }
                    TextButton(
                        onClick = { onDelete(key) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Add new button
    OutlinedButton(
        onClick = onAddNew,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("+ Add Agent")
    }
}
