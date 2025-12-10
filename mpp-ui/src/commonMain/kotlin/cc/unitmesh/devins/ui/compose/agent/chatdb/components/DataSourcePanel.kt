package cc.unitmesh.devins.ui.compose.agent.chatdb.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.compose.agent.chatdb.model.*
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons

/**
 * Data Source Panel - Left side panel for managing database connections
 *
 * Supports multi-datasource selection: all data sources are selected by default,
 * users can toggle individual data sources on/off using checkboxes.
 */
@Composable
fun DataSourcePanel(
    dataSources: List<DataSourceConfig>,
    selectedDataSourceIds: Set<String>,
    connectionStatuses: Map<String, ConnectionStatus>,
    filterQuery: String,
    onFilterChange: (String) -> Unit,
    onToggleDataSource: (String) -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (DataSourceConfig) -> Unit,
    onDeleteClick: (String) -> Unit,
    onConnectClick: (String) -> Unit,
    onDisconnectClick: (String) -> Unit,
    onConnectAllClick: () -> Unit,
    onDisconnectAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedCount = selectedDataSourceIds.size
    val connectedCount = connectionStatuses.values.count { it is ConnectionStatus.Connected }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        // Header with Add button and selection info
        DataSourceHeader(
            selectedCount = selectedCount,
            totalCount = dataSources.size,
            onAddClick = onAddClick
        )

        HorizontalDivider()

        // Search/Filter
        SearchField(
            query = filterQuery,
            onQueryChange = onFilterChange,
            modifier = Modifier.padding(8.dp)
        )

        // Data source list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(dataSources, key = { it.id }) { dataSource ->
                DataSourceItem(
                    dataSource = dataSource,
                    isSelected = dataSource.id in selectedDataSourceIds,
                    connectionStatus = connectionStatuses[dataSource.id] ?: ConnectionStatus.Disconnected,
                    onToggle = { onToggleDataSource(dataSource.id) },
                    onEditClick = { onEditClick(dataSource) },
                    onDeleteClick = { onDeleteClick(dataSource.id) },
                    onConnectClick = { onConnectClick(dataSource.id) },
                    onDisconnectClick = { onDisconnectClick(dataSource.id) }
                )
            }
        }

        // Connection controls for all selected data sources
        if (selectedDataSourceIds.isNotEmpty()) {
            HorizontalDivider()
            MultiConnectionControls(
                selectedCount = selectedCount,
                connectedCount = connectedCount,
                onConnectAll = onConnectAllClick,
                onDisconnectAll = onDisconnectAllClick
            )
        }
    }
}

@Composable
private fun DataSourceHeader(
    selectedCount: Int,
    totalCount: Int,
    onAddClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Data Sources",
                style = MaterialTheme.typography.titleMedium
            )
            if (totalCount > 0) {
                Text(
                    text = "$selectedCount of $totalCount selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(
            onClick = onAddClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                AutoDevComposeIcons.Add,
                contentDescription = "Add data source",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search...", style = MaterialTheme.typography.bodySmall) },
        leadingIcon = {
            Icon(
                AutoDevComposeIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                    Icon(AutoDevComposeIcons.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        textStyle = MaterialTheme.typography.bodySmall,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun DataSourceItem(
    dataSource: DataSourceConfig,
    isSelected: Boolean,
    connectionStatus: ConnectionStatus,
    onToggle: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isConnected = connectionStatus is ConnectionStatus.Connected
    val isConnecting = connectionStatus is ConnectionStatus.Connecting

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox for selection
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Status indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when (connectionStatus) {
                            is ConnectionStatus.Connected -> MaterialTheme.colorScheme.primary
                            is ConnectionStatus.Connecting -> MaterialTheme.colorScheme.tertiary
                            is ConnectionStatus.Error -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        }
                    )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dataSource.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = dataSource.getDisplayUrl(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Connection status text
                    when (connectionStatus) {
                        is ConnectionStatus.Connected -> {
                            Text(
                                text = "Connected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is ConnectionStatus.Connecting -> {
                            Text(
                                text = "Connecting...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        is ConnectionStatus.Error -> {
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {}
                    }
                }
            }

            // Quick connect/disconnect button
            if (isSelected) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else if (isConnected) {
                    IconButton(
                        onClick = onDisconnectClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            AutoDevComposeIcons.CloudOff,
                            contentDescription = "Disconnect",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    IconButton(
                        onClick = onConnectClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            AutoDevComposeIcons.Cloud,
                            contentDescription = "Connect",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        AutoDevComposeIcons.MoreVert,
                        contentDescription = "More options",
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEditClick()
                        },
                        leadingIcon = {
                            Icon(AutoDevComposeIcons.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        },
                        leadingIcon = {
                            Icon(
                                AutoDevComposeIcons.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Connection controls for multi-datasource mode
 */
@Composable
private fun MultiConnectionControls(
    selectedCount: Int,
    connectedCount: Int,
    onConnectAll: () -> Unit,
    onDisconnectAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status text
        Text(
            text = "$connectedCount of $selectedCount connected",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Connect All button
            Button(
                onClick = onConnectAll,
                enabled = connectedCount < selectedCount,
                modifier = Modifier.weight(1f)
            ) {
                Text("Connect All")
            }

            // Disconnect All button
            OutlinedButton(
                onClick = onDisconnectAll,
                enabled = connectedCount > 0,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Disconnect All")
            }
        }
    }
}

