package cc.unitmesh.devins.ui.compose.agent.chatdb.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.agent.Platform
import cc.unitmesh.agent.database.DatabaseSchema
import cc.unitmesh.devins.ui.compose.agent.AgentMessageList
import cc.unitmesh.devins.ui.compose.agent.ComposeRenderer
import cc.unitmesh.devins.ui.compose.agent.chatdb.model.ConnectionStatus
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons

/**
 * Chat pane for ChatDB - right side chat area for text-to-SQL
 *
 * Supports multi-datasource mode: shows connection status for multiple databases.
 */
@Composable
fun ChatDBChatPane(
    renderer: ComposeRenderer,
    connectionStatus: ConnectionStatus,
    connectedCount: Int = 0,
    selectedCount: Int = 0,
    schema: DatabaseSchema?,
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit,
    onNewSession: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hasAnyConnection = connectedCount > 0

    Column(modifier = modifier.fillMaxSize()) {
        // Connection status banner (multi-datasource aware)
        ConnectionStatusBanner(
            connectionStatus = connectionStatus,
            connectedCount = connectedCount,
            selectedCount = selectedCount,
            schema = schema,
            onNewSession = onNewSession,
            hasMessages = renderer.timeline.isNotEmpty()
        )

        // Message list
        Box(modifier = Modifier.weight(1f)) {
            AgentMessageList(
                renderer = renderer,
                modifier = Modifier.fillMaxSize()
            )

            // Welcome message when no messages and not streaming
            if (renderer.timeline.isEmpty() && renderer.currentStreamingOutput.isEmpty() && !renderer.isProcessing) {
                WelcomeMessage(
                    isConnected = hasAnyConnection,
                    connectedCount = connectedCount,
                    schema = schema,
                    onQuickQuery = onSendMessage,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        HorizontalDivider()

        // Input area
        ChatInputArea(
            isGenerating = isGenerating,
            isConnected = hasAnyConnection,
            connectedCount = connectedCount,
            onSendMessage = onSendMessage,
            onStopGeneration = onStopGeneration
        )
    }
}

@Composable
private fun ConnectionStatusBanner(
    connectionStatus: ConnectionStatus,
    connectedCount: Int,
    selectedCount: Int,
    schema: DatabaseSchema?,
    onNewSession: () -> Unit,
    hasMessages: Boolean
) {
    val hasAnyConnection = connectedCount > 0

    when {
        hasAnyConnection -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        AutoDevComposeIcons.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (connectedCount == 1) "Connected" else "$connectedCount databases connected",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val statusText = buildString {
                            if (schema != null) {
                                append("${schema.tables.size} tables available")
                            }
                            if (connectedCount < selectedCount) {
                                if (isNotEmpty()) append(" • ")
                                append("${selectedCount - connectedCount} not connected")
                            }
                        }
                        if (statusText.isNotEmpty()) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // New session button (only show if there are messages)
                    if (hasMessages) {
                        FilledTonalIconButton(
                            onClick = onNewSession,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                AutoDevComposeIcons.Add,
                                contentDescription = "New Session",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
        selectedCount > 0 -> {
            // Selected but not connected
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        AutoDevComposeIcons.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$selectedCount data source${if (selectedCount > 1) "s" else ""} selected. Click 'Connect All' to connect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        else -> {
            // No data sources selected
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        AutoDevComposeIcons.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "No data sources selected. Select data sources from the left panel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeMessage(
    isConnected: Boolean,
    connectedCount: Int = 0,
    schema: DatabaseSchema?,
    onQuickQuery: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            AutoDevComposeIcons.Database,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "ChatDB - Text to SQL",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when {
                connectedCount > 1 -> "Query across $connectedCount databases in natural language"
                isConnected -> "Ask questions about your data in natural language"
                else -> "Connect to a database to start querying"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isConnected && schema != null) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Try asking:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            val suggestions = listOf(
                "Show me all tables in the database",
                "What are the columns in the ${schema.tables.firstOrNull()?.name ?: "users"} table?",
                "Count the total number of records"
            )

            suggestions.forEach { suggestion ->
                SuggestionChip(
                    onClick = { onQuickQuery(suggestion) },
                    label = { Text(suggestion, style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatInputArea(
    isGenerating: Boolean,
    isConnected: Boolean,
    connectedCount: Int = 0,
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit
) {
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    val focusManager = LocalFocusManager.current

    fun sendMessage() {
        if (inputText.text.isNotBlank() && !isGenerating && isConnected) {
            onSendMessage(inputText.text)
            inputText = TextFieldValue("")
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                if (!event.isShiftPressed) {
                                    sendMessage()
                                    true
                                } else false
                            } else false
                        },
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    enabled = isConnected,
                    decorationBox = { innerTextField ->
                        Box {
                            if (inputText.text.isEmpty()) {
                                val placeholderText = when {
                                    connectedCount > 1 -> "Ask a question across $connectedCount databases..."
                                    isConnected -> "Ask a question about your data..."
                                    else -> "Connect to a database first"
                                }
                                Text(
                                    text = placeholderText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            if (isGenerating) {
                FilledIconButton(
                    onClick = onStopGeneration,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(AutoDevComposeIcons.Stop, contentDescription = "Stop")
                }
            } else {
                FilledIconButton(
                    onClick = { sendMessage() },
                    enabled = inputText.text.isNotBlank() && isConnected
                ) {
                    Icon(AutoDevComposeIcons.Send, contentDescription = "Send")
                }
            }
        }
    }
}

