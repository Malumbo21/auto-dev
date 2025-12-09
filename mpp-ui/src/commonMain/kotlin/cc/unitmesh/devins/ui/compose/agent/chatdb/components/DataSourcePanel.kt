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
 */
@Composable
fun DataSourcePanel(
    dataSources: List<DataSourceConfig>,
    selectedDataSourceId: String?,
    connectionStatus: ConnectionStatus,
    filterQuery: String,
    onFilterChange: (String) -> Unit,
    onSelectDataSource: (String) -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (DataSourceConfig) -> Unit,
    onDeleteClick: (String) -> Unit,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        // Header with Add button
        DataSourceHeader(onAddClick = onAddClick)

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
                    isSelected = dataSource.id == selectedDataSourceId,
                    connectionStatus = if (dataSource.id == selectedDataSourceId) connectionStatus else ConnectionStatus.Disconnected,
                    onClick = { onSelectDataSource(dataSource.id) },
                    onEditClick = { onEditClick(dataSource) },
                    onDeleteClick = { onDeleteClick(dataSource.id) }
                )
            }
        }

        // Connection controls
        if (selectedDataSourceId != null) {
            HorizontalDivider()
            ConnectionControls(
                connectionStatus = connectionStatus,
                onConnect = onConnectClick,
                onDisconnect = onDisconnectClick
            )
        }
    }
}

@Composable
private fun DataSourceHeader(onAddClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Data Sources",
            style = MaterialTheme.typography.titleMedium
        )
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
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dataSource.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dataSource.getDisplayUrl(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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

@Composable
private fun ConnectionControls(
    connectionStatus: ConnectionStatus,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (connectionStatus) {
            is ConnectionStatus.Connected -> {
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Disconnect")
                }
            }
            is ConnectionStatus.Connecting -> {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connecting...")
                }
            }
            else -> {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect")
                }
            }
        }
    }

    if (connectionStatus is ConnectionStatus.Error) {
        Text(
            text = connectionStatus.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

