package cc.unitmesh.devins.ui.compose.agent.chatdb

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cc.unitmesh.devins.ui.base.ResizableSplitPane
import cc.unitmesh.devins.ui.compose.agent.chatdb.components.*
import cc.unitmesh.devins.workspace.Workspace
import cc.unitmesh.llm.KoogLLMService
import kotlinx.coroutines.flow.collectLatest

/**
 * ChatDB Page - Main page for text-to-SQL agent
 *
 * Left side: Data source management panel (resizable)
 * Right side: Config pane (when adding/editing) or Chat area for natural language to SQL queries
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

    Scaffold(modifier = modifier) { paddingValues ->
        ResizableSplitPane(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            initialSplitRatio = 0.22f,
            minRatio = 0.15f,
            maxRatio = 0.4f,
            saveKey = "chatdb_split_ratio",
            first = {
                // Left panel - Data source management
                DataSourcePanel(
                    dataSources = state.filteredDataSources,
                    selectedDataSourceId = state.selectedDataSourceId,
                    connectionStatus = state.connectionStatus,
                    filterQuery = state.filterQuery,
                    onFilterChange = viewModel::setFilterQuery,
                    onSelectDataSource = { id ->
                        viewModel.selectDataSource(id)
                        // When selecting a different data source, show its config in the pane
                        val selected = state.dataSources.find { it.id == id }
                        if (selected != null && state.isConfigPaneOpen) {
                            viewModel.openConfigPane(selected)
                        }
                    },
                    onAddClick = { viewModel.openConfigPane(null) },
                    onEditClick = { config -> viewModel.openConfigPane(config) },
                    onDeleteClick = viewModel::deleteDataSource,
                    onConnectClick = viewModel::connect,
                    onDisconnectClick = viewModel::disconnect,
                    modifier = Modifier.fillMaxSize()
                )
            },
            second = {
                // Right panel - Config pane or Chat area
                if (state.isConfigPaneOpen) {
                    DataSourceConfigPane(
                        existingConfig = state.configuringDataSource,
                        onCancel = viewModel::closeConfigPane,
                        onSave = viewModel::saveFromPane,
                        onSaveAndConnect = viewModel::saveAndConnectFromPane,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ChatDBChatPane(
                        renderer = viewModel.renderer,
                        connectionStatus = state.connectionStatus,
                        schema = viewModel.getSchema(),
                        isGenerating = viewModel.isGenerating,
                        onSendMessage = viewModel::sendMessage,
                        onStopGeneration = viewModel::stopGeneration,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        )
    }
}
