package cc.unitmesh.devins.ui.compose.agent.webedit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A chip/tag component for displaying a selected DOM element.
 * Supports double-click to remove.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ElementTagChip(
    tag: ElementTag,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTooltip by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .combinedClickable(
                onClick = { showTooltip = !showTooltip },
                onDoubleClick = { onRemove() }
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Tag icon based on element type
            Text(
                text = getTagIcon(tag.tagName),
                style = MaterialTheme.typography.labelSmall
            )

            // Tag label
            Text(
                text = tag.getShortLabel(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Close button
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove element",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }

    // Tooltip with detailed info
    if (showTooltip) {
        ElementTagTooltip(tag = tag, onDismiss = { showTooltip = false })
    }
}

/**
 * Get an icon emoji based on the HTML tag type
 */
private fun getTagIcon(tagName: String): String {
    return when (tagName.lowercase()) {
        "button" -> "B"
        "input" -> "I"
        "a" -> "L"
        "img" -> "P"
        "div" -> "D"
        "span" -> "S"
        "form" -> "F"
        "table" -> "T"
        "ul", "ol", "li" -> "*"
        "h1", "h2", "h3", "h4", "h5", "h6" -> "H"
        "p" -> "P"
        "nav" -> "N"
        "header" -> "H"
        "footer" -> "F"
        "section" -> "S"
        "article" -> "A"
        else -> "E"
    }
}

/**
 * Tooltip showing detailed element information
 */
@Composable
fun ElementTagTooltip(
    tag: ElementTag,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Element Details",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow("Tag", "<${tag.tagName}>")
                DetailRow("Selector", tag.selector)

                if (tag.attributes.isNotEmpty()) {
                    Text(
                        text = "Attributes:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    tag.attributes.forEach { (key, value) ->
                        Text(
                            text = "  $key: $value",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                tag.textContent?.let {
                    DetailRow("Text", "\"$it\"")
                }

                tag.sourceHint?.let {
                    DetailRow("Source Hint", it)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Row of element tag chips
 */
@Composable
fun ElementTagRow(
    tags: ElementTagCollection,
    onRemoveTag: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (tags.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label
        Text(
            text = "Selected:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Element chips
        tags.tags.forEach { tag ->
            ElementTagChip(
                tag = tag,
                onRemove = { onRemoveTag(tag.id) }
            )
        }

        // Clear all button
        if (tags.tags.size > 1) {
            TextButton(
                onClick = onClearAll,
                modifier = Modifier.height(24.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "Clear all",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/**
 * Chat input area for WebEdit Q&A functionality.
 * Now supports element tags for selected DOM elements.
 */
@Composable
fun WebEditChatInput(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask about the page or selected element...",
    enabled: Boolean = true,
    isProcessing: Boolean = false,
    elementTags: ElementTagCollection = ElementTagCollection(),
    onRemoveTag: (String) -> Unit = {},
    onClearTags: () -> Unit = {},
    onSendWithContext: ((String, ElementTagCollection) -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        tonalElevation = 2.dp
    ) {
        Column {
            // Processing indicator
            if (isProcessing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Element tags row (shown when elements are selected)
            if (elementTags.isNotEmpty()) {
                ElementTagRow(
                    tags = elementTags,
                    onRemoveTag = onRemoveTag,
                    onClearAll = onClearTags,
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Input field
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp, max = 120.dp),
                    placeholder = {
                        Text(
                            text = if (elementTags.isNotEmpty()) {
                                "Ask about selected elements..."
                            } else {
                                placeholder
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    enabled = enabled,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (input.isNotBlank() || elementTags.isNotEmpty()) {
                                if (onSendWithContext != null && elementTags.isNotEmpty()) {
                                    onSendWithContext(input, elementTags)
                                } else {
                                    onSend(input)
                                }
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                // Send button
                FilledIconButton(
                    onClick = {
                        if (input.isNotBlank() || elementTags.isNotEmpty()) {
                            if (onSendWithContext != null && elementTags.isNotEmpty()) {
                                onSendWithContext(input, elementTags)
                            } else if (input.isNotBlank()) {
                                onSend(input)
                            }
                        }
                    },
                    enabled = enabled && (input.isNotBlank() || elementTags.isNotEmpty()) && !isProcessing,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

