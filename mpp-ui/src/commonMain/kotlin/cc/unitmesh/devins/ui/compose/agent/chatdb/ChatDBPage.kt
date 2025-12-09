package cc.unitmesh.devins.ui.compose.agent.chatdb

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.compose.agent.chatdb.components.*
import cc.unitmesh.devins.ui.compose.agent.chatdb.model.ConnectionStatus
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons
import cc.unitmesh.devins.workspace.Workspace
import cc.unitmesh.llm.KoogLLMService
import kotlinx.coroutines.flow.collectLatest

/**
 * ChatDB Page - Main page for text-to-SQL agent
 *
 * Left side: Data source management panel
 * Right side: Chat area for natural language to SQL queries
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDBPage(
    workspace: Workspace? = null,
    llmService: KoogLLMService?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNotification: (String, String) -> Unit = { _, _ -> }
) {
    val viewModel = remember { ChatDBViewModel(workspace) }
    val state = viewModel.state

    // Collect notifications
    LaunchedEffect(viewModel) {
        viewModel.notificationEvent.collectLatest { (title, message) ->
            onNotification(title, message)
        }
    }

    // Cleanup on dispose
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.dispose()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ChatDB") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AutoDevComposeIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Schema info button when connected
                    if (state.connectionStatus is ConnectionStatus.Connected) {
                        var showSchemaDialog by remember { mutableStateOf(false) }
                        IconButton(onClick = { showSchemaDialog = true }) {
                            Icon(AutoDevComposeIcons.Schema, contentDescription = "View Schema")
                        }
                        if (showSchemaDialog) {
                            SchemaInfoDialog(
                                schema = viewModel.getSchema(),
                                onDismiss = { showSchemaDialog = false }
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Left panel - Data source management
            DataSourcePanel(
                dataSources = state.filteredDataSources,
                selectedDataSourceId = state.selectedDataSourceId,
                connectionStatus = state.connectionStatus,
                filterQuery = state.filterQuery,
                onFilterChange = viewModel::setFilterQuery,
                onSelectDataSource = viewModel::selectDataSource,
                onAddClick = viewModel::openAddDialog,
                onEditClick = viewModel::openEditDialog,
                onDeleteClick = viewModel::deleteDataSource,
                onConnectClick = viewModel::connect,
                onDisconnectClick = viewModel::disconnect,
                modifier = Modifier.width(280.dp)
            )

            VerticalDivider()

            // Right panel - Chat area
            ChatDBChatPane(
                renderer = viewModel.renderer,
                connectionStatus = state.connectionStatus,
                schema = viewModel.getSchema(),
                isGenerating = viewModel.isGenerating,
                onSendMessage = viewModel::sendMessage,
                onStopGeneration = viewModel::stopGeneration,
                modifier = Modifier.weight(1f)
            )
        }

        // Config dialog
        if (state.isConfigDialogOpen) {
            DataSourceConfigDialog(
                existingConfig = state.editingDataSource,
                onDismiss = viewModel::closeConfigDialog,
                onSave = { config ->
                    if (state.editingDataSource != null) {
                        viewModel.updateDataSource(config)
                    } else {
                        viewModel.addDataSource(config)
                    }
                }
            )
        }
    }
}

@Composable
private fun SchemaInfoDialog(
    schema: cc.unitmesh.agent.database.DatabaseSchema?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Database Schema") },
        text = {
            if (schema != null) {
                Column {
                    Text(
                        text = "${schema.tables.size} tables",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    schema.tables.take(10).forEach { table ->
                        Text(
                            text = "• ${table.name} (${table.columns.size} columns)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (schema.tables.size > 10) {
                        Text(
                            text = "... and ${schema.tables.size - 10} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text("No schema available")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

