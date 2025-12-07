package cc.unitmesh.devins.ui.compose.omnibar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Omnibar - A wide floating command palette for quick actions.
 *
 * Features:
 * - Wide layout (800dp) with semi-transparent dark backdrop
 * - Fuzzy search with weighted ranking
 * - Keyboard navigation (Up/Down, Enter, Escape)
 * - Category grouping
 * - Preview panel (optional)
 *
 * Design inspired by Linear's command palette and Raycast.
 */
@Composable
fun Omnibar(
    visible: Boolean,
    onDismiss: () -> Unit,
    dataProvider: OmnibarDataProvider = DefaultOmnibarDataProvider,
    onActionResult: (OmnibarActionResult) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!visible) return

    var searchQuery by remember { mutableStateOf("") }
    var allItems by remember { mutableStateOf<List<OmnibarItem>>(emptyList()) }
    var filteredItems by remember { mutableStateOf<List<OmnibarItem>>(emptyList()) }
    var selectedIndex by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    val searchEngine = remember { OmnibarSearchEngine() }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Load items when dialog opens
    LaunchedEffect(visible) {
        if (visible) {
            isLoading = true
            try {
                val items = dataProvider.getItems()
                val recentItems = dataProvider.getRecentItems()
                allItems = (recentItems + items).distinctBy { it.id }
                filteredItems = searchEngine.search(allItems, "")
            } finally {
                isLoading = false
            }
            delay(100)
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        } else {
            searchQuery = ""
            selectedIndex = 0
        }
    }

    // Update filtered items when query changes
    LaunchedEffect(searchQuery, allItems) {
        delay(50) // Small debounce
        filteredItems = searchEngine.search(allItems, searchQuery)
        selectedIndex = 0
    }

    // Auto-scroll to selected item
    LaunchedEffect(selectedIndex) {
        if (filteredItems.isNotEmpty() && selectedIndex in filteredItems.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Semi-transparent backdrop overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AutoDevColors.Void.overlay)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            // Main Omnibar container
            Surface(
                modifier = modifier
                    .padding(top = 100.dp)
                    .width(800.dp)
                    .heightIn(max = 500.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Prevent click-through
                    ),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 16.dp,
                shadowElevation = 24.dp,
                color = AutoDevColors.Void.surfaceElevated
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Search input area
                    OmnibarSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        focusRequester = focusRequester,
                        onKeyEvent = { event ->
                            handleKeyEvent(
                                event = event,
                                filteredItems = filteredItems,
                                selectedIndex = selectedIndex,
                                onSelectedIndexChange = { selectedIndex = it },
                                onSelect = { item ->
                                    scope.launch {
                                        dataProvider.recordUsage(item)
                                        val result = dataProvider.executeAction(item)
                                        onActionResult(result)
                                        onDismiss()
                                    }
                                },
                                onDismiss = onDismiss
                            )
                        }
                    )

                    HorizontalDivider(color = AutoDevColors.Void.border)

                    // Results list
                    OmnibarResultsList(
                        items = filteredItems,
                        selectedIndex = selectedIndex,
                        isLoading = isLoading,
                        listState = listState,
                        onItemClick = { item ->
                            scope.launch {
                                dataProvider.recordUsage(item)
                                val result = dataProvider.executeAction(item)
                                onActionResult(result)
                                onDismiss()
                            }
                        },
                        onItemHover = { index -> selectedIndex = index }
                    )
                }
            }
        }
    }
}

/** Search input field for Omnibar */
@Composable
private fun OmnibarSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onKeyEvent: (KeyEvent) -> Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = AutoDevComposeIcons.Search,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = AutoDevColors.Text.secondary
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onKeyEvent(onKeyEvent),
            textStyle = TextStyle(
                fontSize = 16.sp,
                color = AutoDevColors.Text.primary
            ),
            cursorBrush = SolidColor(AutoDevColors.Energy.primary),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            "Type a command or search...",
                            style = TextStyle(fontSize = 16.sp),
                            color = AutoDevColors.Text.tertiary
                        )
                    }
                    innerTextField()
                }
            }
        )
        // Shortcut hint
        Text(
            text = "ESC to close",
            style = MaterialTheme.typography.labelSmall,
            color = AutoDevColors.Text.tertiary
        )
    }
}

/** Results list for Omnibar */
@Composable
private fun OmnibarResultsList(
    items: List<OmnibarItem>,
    selectedIndex: Int,
    isLoading: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onItemClick: (OmnibarItem) -> Unit,
    onItemHover: (Int) -> Unit
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = AutoDevColors.Energy.primary
                )
            }
        }
        items.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = AutoDevComposeIcons.Search,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = AutoDevColors.Text.tertiary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No results found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AutoDevColors.Text.secondary
                    )
                }
            }
        }
        else -> {
            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 400.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(items.size) { index ->
                    val item = items[index]
                    OmnibarResultItem(
                        item = item,
                        isSelected = index == selectedIndex,
                        onClick = { onItemClick(item) },
                        onHover = { onItemHover(index) }
                    )
                }
            }
        }
    }
}

/** Single result item in the Omnibar */
@Composable
private fun OmnibarResultItem(
    item: OmnibarItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onHover: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    LaunchedEffect(isHovered) {
        if (isHovered) onHover()
    }

    val backgroundColor = when {
        isSelected -> AutoDevColors.Energy.primary.copy(alpha = 0.15f)
        isHovered -> AutoDevColors.Void.surfaceHover
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource = interactionSource)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon
        Icon(
            imageVector = item.icon ?: getDefaultIcon(item.type),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isSelected) AutoDevColors.Energy.primary else AutoDevColors.Text.secondary
        )

        // Title and description
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = AutoDevColors.Text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AutoDevColors.Text.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Shortcut hint
        if (item.shortcutHint.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = AutoDevColors.Void.surfaceElevated,
                tonalElevation = 4.dp
            ) {
                Text(
                    text = item.shortcutHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = AutoDevColors.Text.tertiary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Category tag
        if (item.category.isNotBlank()) {
            Text(
                text = item.category,
                style = MaterialTheme.typography.labelSmall,
                color = AutoDevColors.Text.quaternary
            )
        }
    }
}

/** Get default icon for item type */
@Composable
private fun getDefaultIcon(type: OmnibarItemType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (type) {
        OmnibarItemType.COMMAND -> AutoDevComposeIcons.Terminal
        OmnibarItemType.CUSTOM_COMMAND -> AutoDevComposeIcons.Code
        OmnibarItemType.FILE -> AutoDevComposeIcons.InsertDriveFile
        OmnibarItemType.SYMBOL -> AutoDevComposeIcons.Functions
        OmnibarItemType.AGENT -> AutoDevComposeIcons.SmartToy
        OmnibarItemType.RECENT -> AutoDevComposeIcons.History
        OmnibarItemType.SETTING -> AutoDevComposeIcons.Settings
    }
}

/** Handle keyboard events for navigation */
private fun handleKeyEvent(
    event: KeyEvent,
    filteredItems: List<OmnibarItem>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    onSelect: (OmnibarItem) -> Unit,
    onDismiss: () -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    return when (event.key) {
        Key.DirectionDown -> {
            if (filteredItems.isNotEmpty()) {
                onSelectedIndexChange((selectedIndex + 1) % filteredItems.size)
            }
            true
        }
        Key.DirectionUp -> {
            if (filteredItems.isNotEmpty()) {
                onSelectedIndexChange(
                    if (selectedIndex > 0) selectedIndex - 1 else filteredItems.lastIndex
                )
            }
            true
        }
        Key.Enter -> {
            if (filteredItems.isNotEmpty() && selectedIndex in filteredItems.indices) {
                onSelect(filteredItems[selectedIndex])
            }
            true
        }
        Key.Escape -> {
            onDismiss()
            true
        }
        else -> false
    }
}

