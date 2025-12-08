package cc.unitmesh.devins.idea.omnibar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import cc.unitmesh.devins.idea.omnibar.model.OmnibarActionResult
import cc.unitmesh.devins.idea.omnibar.model.OmnibarItem
import cc.unitmesh.devins.idea.omnibar.model.OmnibarItemType
import cc.unitmesh.devins.idea.omnibar.model.OmnibarSearchEngine
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import java.awt.Dimension
import javax.swing.JComponent

/**
 * DialogWrapper for Omnibar in IntelliJ IDEA.
 * Uses JewelComposePanel for native IDE integration.
 */
class IdeaOmnibarDialogWrapper(
    private val project: Project,
    private val onActionResult: (OmnibarActionResult) -> Unit = {}
) : DialogWrapper(project, true) {
    
    init {
        title = ""
        isModal = true
        init()
        contentPanel.border = null
        rootPane.border = null
    }
    
    override fun createSouthPanel(): JComponent? = null
    override fun createNorthPanel(): JComponent? = null
    
    override fun createCenterPanel(): JComponent {
        val dialogPanel = JewelComposePanel {
            IdeaOmnibarContent(
                project = project,
                onDismiss = { close(CANCEL_EXIT_CODE) },
                onActionResult = { result ->
                    onActionResult(result)
                    close(OK_EXIT_CODE)
                }
            )
        }
        dialogPanel.preferredSize = Dimension(800, 500)
        dialogPanel.background = java.awt.Color(0x0B, 0x0E, 0x14, 230) // Semi-transparent
        return dialogPanel
    }
    
    companion object {
        fun show(project: Project, onActionResult: (OmnibarActionResult) -> Unit = {}) {
            IdeaOmnibarDialogWrapper(project, onActionResult).show()
        }
    }
}

/**
 * Compose content for IDEA Omnibar using Jewel components.
 */
@Composable
fun IdeaOmnibarContent(
    project: Project,
    onDismiss: () -> Unit,
    onActionResult: (OmnibarActionResult) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var allItems by remember { mutableStateOf<List<OmnibarItem>>(emptyList()) }
    var filteredItems by remember { mutableStateOf<List<OmnibarItem>>(emptyList()) }
    var selectedIndex by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    
    val dataProvider = remember { IdeaOmnibarDataProvider.getInstance(project) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // Load items
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val items = dataProvider.getItems()
            val recentItems = dataProvider.getRecentItems()
            allItems = (recentItems + items).distinctBy { it.id }
            filteredItems = OmnibarSearchEngine.search(allItems, "")
        } finally {
            isLoading = false
        }
        delay(100)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }
    
    // Update filtered items on query change
    LaunchedEffect(searchQuery, allItems) {
        delay(50)
        filteredItems = OmnibarSearchEngine.search(allItems, searchQuery)
        selectedIndex = 0
    }
    
    // Auto-scroll
    LaunchedEffect(selectedIndex) {
        if (filteredItems.isNotEmpty() && selectedIndex in filteredItems.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    
    // Key event handler
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionDown -> {
                if (filteredItems.isNotEmpty()) {
                    selectedIndex = (selectedIndex + 1) % filteredItems.size
                }
                true
            }
            Key.DirectionUp -> {
                if (filteredItems.isNotEmpty()) {
                    selectedIndex = if (selectedIndex > 0) selectedIndex - 1 else filteredItems.lastIndex
                }
                true
            }
            Key.Enter -> {
                if (filteredItems.isNotEmpty() && selectedIndex in filteredItems.indices) {
                    val item = filteredItems[selectedIndex]
                    scope.launch {
                        dataProvider.recordUsage(item)
                        val result = dataProvider.executeAction(item)
                        onActionResult(result)
                    }
                }
                true
            }
            Key.Escape -> { onDismiss(); true }
            else -> false
        }
    }
    
    Box(
        modifier = Modifier
            .width(800.dp)
            .heightIn(max = 500.dp)
            .background(JewelTheme.globalColors.panelBackground, RoundedCornerShape(12.dp))
    ) {
        Column {
            // Search field
            IdeaOmnibarSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                focusRequester = focusRequester,
                onKeyEvent = ::handleKeyEvent
            )

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(JewelTheme.globalColors.borders.normal)
            )

            // Results
            IdeaOmnibarResults(
                items = filteredItems,
                selectedIndex = selectedIndex,
                isLoading = isLoading,
                listState = listState,
                onItemClick = { item ->
                    scope.launch {
                        dataProvider.recordUsage(item)
                        val result = dataProvider.executeAction(item)
                        onActionResult(result)
                    }
                },
                onItemHover = { index -> selectedIndex = index }
            )
        }
    }
}

/** Search field for IDEA Omnibar */
@Composable
private fun IdeaOmnibarSearchField(
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
        Text(
            text = "\uD83D\uDD0D", // 🔍
            style = TextStyle(fontSize = 16.sp)
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
                color = JewelTheme.globalColors.text.normal
            ),
            cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            "Type a command or search...",
                            style = TextStyle(fontSize = 16.sp),
                            color = JewelTheme.globalColors.text.disabled
                        )
                    }
                    innerTextField()
                }
            }
        )
        Text(
            text = "ESC",
            style = TextStyle(fontSize = 11.sp),
            color = JewelTheme.globalColors.text.disabled
        )
    }
}

/** Results list for IDEA Omnibar */
@Composable
private fun IdeaOmnibarResults(
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
                CircularProgressIndicator()
            }
        }
        items.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No results found",
                    color = JewelTheme.globalColors.text.disabled
                )
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
                    IdeaOmnibarResultItem(
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

/** Single result item */
@Composable
private fun IdeaOmnibarResultItem(
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
        isSelected -> JewelTheme.globalColors.outlines.focused.copy(alpha = 0.15f)
        isHovered -> JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource = interactionSource)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon placeholder
        Text(
            text = when (item.type) {
                OmnibarItemType.COMMAND -> "/"
                OmnibarItemType.CUSTOM_COMMAND -> "⚡"
                OmnibarItemType.FILE -> "\uD83D\uDCC4" // 📄
                OmnibarItemType.SYMBOL -> "λ"
                OmnibarItemType.AGENT -> "\uD83E\uDD16" // 🤖
                OmnibarItemType.RECENT -> "⏱"
                OmnibarItemType.SETTING -> "⚙"
            },
            style = TextStyle(fontSize = 14.sp),
            color = if (isSelected) JewelTheme.globalColors.outlines.focused
                    else JewelTheme.globalColors.text.info
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = JewelTheme.globalColors.text.normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = TextStyle(fontSize = 12.sp),
                    color = JewelTheme.globalColors.text.disabled,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (item.category.isNotBlank()) {
            Text(
                text = item.category,
                style = TextStyle(fontSize = 11.sp),
                color = JewelTheme.globalColors.text.disabled
            )
        }
    }
}

