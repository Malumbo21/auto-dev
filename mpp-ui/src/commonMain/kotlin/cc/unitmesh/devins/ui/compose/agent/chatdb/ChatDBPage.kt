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
                // Left panel - Data source management (multi-selection mode)
                DataSourcePanel(
                    dataSources = state.filteredDataSources,
                    selectedDataSourceIds = state.selectedDataSourceIds,
                    connectionStatuses = state.connectionStatuses,
                    filterQuery = state.filterQuery,
                    onFilterChange = viewModel::setFilterQuery,
                    onToggleDataSource = { id ->
                        viewModel.toggleDataSource(id)
                        // When toggling a data source, optionally show its config in the pane
                        val selected = state.dataSources.find { it.id == id }
                        if (selected != null && state.isConfigPaneOpen) {
                            viewModel.openConfigPane(selected)
                        }
                    },
                    onAddClick = { viewModel.openConfigPane(null) },
                    onEditClick = { config -> viewModel.openConfigPane(config) },
                    onDeleteClick = viewModel::deleteDataSource,
                    onConnectClick = viewModel::connectDataSource,
                    onDisconnectClick = viewModel::disconnectDataSource,
                    onConnectAllClick = viewModel::connectAll,
                    onDisconnectAllClick = viewModel::disconnectAll,
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
                        connectedCount = state.connectedCount,
                        selectedCount = state.selectedCount,
                        schema = viewModel.getSchema(),
                        isGenerating = viewModel.isGenerating,
                        onSendMessage = viewModel::sendMessage,
                        onStopGeneration = viewModel::stopGeneration,
                        onNewSession = viewModel::newSession,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        )
    }
}
