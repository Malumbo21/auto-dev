package cc.unitmesh.devins.idea.toolwindow.webedit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.agent.render.TimelineItem
import cc.unitmesh.devins.idea.compose.IdeaLaunchedEffect
import cc.unitmesh.devins.idea.components.IdeaResizableSplitPane
import cc.unitmesh.devins.idea.components.IdeaVerticalResizableSplitPane
import cc.unitmesh.devins.idea.theme.IdeaAutoDevColors
import cc.unitmesh.devins.idea.toolwindow.IdeaComposeIcons
import cc.unitmesh.devins.llm.MessageRole
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*

/**
 * Main content view for WebEdit Agent in IntelliJ IDEA.
 * Provides web browsing with DOM selection and AI-powered Q&A.
 *
 * Layout:
 * - Top: URL bar with navigation controls
 * - Center: WebView (JBCefBrowser via SwingPanel)
 * - Right: DOM tree sidebar (toggleable)
 * - Bottom: AI Chat input
 */
@Composable
fun IdeaWebEditContent(
    viewModel: IdeaWebEditViewModel,
    modifier: Modifier = Modifier
) {
    // Collect state
    var state by remember { mutableStateOf(IdeaWebEditState()) }
    var timeline by remember { mutableStateOf<List<TimelineItem>>(emptyList()) }
    var streamingOutput by remember { mutableStateOf("") }

    IdeaLaunchedEffect(viewModel) {
        viewModel.state.collect { state = it }
    }
    IdeaLaunchedEffect(viewModel.renderer) {
        viewModel.renderer.timeline.collect { timeline = it }
    }
    IdeaLaunchedEffect(viewModel.renderer) {
        viewModel.renderer.currentStreamingOutput.collect { streamingOutput = it }
    }

    // Check JCEF support
    if (!viewModel.isJcefSupported) {
        JcefNotSupportedView(modifier)
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top toolbar with URL bar
        WebEditToolbar(
            currentUrl = state.currentUrl,
            isLoading = state.isLoading,
            isSelectionMode = state.isSelectionMode,
            showDOMSidebar = state.showDOMSidebar,
            onNavigate = { viewModel.navigateTo(it) },
            onReload = { viewModel.reload() },
            onGoBack = { viewModel.goBack() },
            onGoForward = { viewModel.goForward() },
            onToggleSelectionMode = { viewModel.toggleSelectionMode() },
            onToggleDOMSidebar = { viewModel.toggleDOMSidebar() }
        )

        Divider(Orientation.Horizontal, modifier = Modifier.fillMaxWidth().height(1.dp))

        // Main content with vertical split (WebView + Chat)
        IdeaVerticalResizableSplitPane(
            modifier = Modifier.fillMaxSize(),
            initialSplitRatio = 0.75f,
            minRatio = 0.4f,
            maxRatio = 0.9f,
            top = {
                // WebView + DOM Sidebar
                if (state.showDOMSidebar) {
                    IdeaResizableSplitPane(
                        modifier = Modifier.fillMaxSize(),
                        initialSplitRatio = 0.7f,
                        minRatio = 0.5f,
                        maxRatio = 0.85f,
                        first = {
                            WebViewPanel(viewModel, state, Modifier.fillMaxSize())
                        },
                        second = {
                            DOMTreeSidebar(
                                domTree = state.domTree,
                                selectedElement = state.selectedElement,
                                onElementClick = { selector ->
                                    viewModel.highlightElement(selector)
                                    viewModel.scrollToElement(selector)
                                },
                                onElementHover = { selector ->
                                    if (selector != null) {
                                        viewModel.highlightElement(selector)
                                    } else {
                                        viewModel.clearHighlights()
                                    }
                                },
                                onRefresh = { viewModel.refreshDOMTree() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    )
                } else {
                    WebViewPanel(viewModel, state, Modifier.fillMaxSize())
                }
            },
            bottom = {
                // Chat panel
                WebEditChatPanel(
                    timeline = timeline,
                    streamingOutput = streamingOutput,
                    selectedElement = state.selectedElement,
                    onSendMessage = { viewModel.sendChatMessage(it) },
                    onStopGeneration = { viewModel.stopGeneration() },
                    onClearHistory = { viewModel.clearChatHistory() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        )
    }
}

/**
 * WebView panel using SwingPanel to embed JBCefBrowser
 */
@Composable
private fun WebViewPanel(
    viewModel: IdeaWebEditViewModel,
    state: IdeaWebEditState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(JewelTheme.globalColors.panelBackground)) {
        val browserComponent = viewModel.getBrowserComponent()
        if (browserComponent != null) {
            SwingPanel(
                factory = { browserComponent },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Initializing browser...",
                    style = JewelTheme.defaultTextStyle
                )
            }
        }

        // Loading indicator
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter)
                    .background(IdeaAutoDevColors.Blue.c400)
            )
        }

        // Selection mode indicator
        if (state.isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(IdeaAutoDevColors.Blue.c400.copy(alpha = 0.9f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Selection Mode",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        color = IdeaAutoDevColors.Neutral.c50
                    )
                )
            }
        }

        // Selected element info
        state.selectedElement?.let { element ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(IdeaAutoDevColors.Green.c400.copy(alpha = 0.9f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Selected: ${element.getDisplayName()}",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        color = IdeaAutoDevColors.Neutral.c50
                    )
                )
            }
        }
    }
}

/**
 * Toolbar with URL input and navigation controls
 */
@Composable
private fun WebEditToolbar(
    currentUrl: String,
    isLoading: Boolean,
    isSelectionMode: Boolean,
    showDOMSidebar: Boolean,
    onNavigate: (String) -> Unit,
    onReload: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onToggleDOMSidebar: () -> Unit
) {
    val urlTextFieldState = rememberTextFieldState(currentUrl)

    // Sync external URL changes
    IdeaLaunchedEffect(currentUrl) {
        if (urlTextFieldState.text.toString() != currentUrl) {
            urlTextFieldState.setTextAndPlaceCursorAtEnd(currentUrl)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(JewelTheme.globalColors.panelBackground)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Navigation buttons
        IconButton(onClick = onGoBack) {
            Icon(
                imageVector = IdeaComposeIcons.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(18.dp),
                tint = JewelTheme.globalColors.text.normal
            )
        }

        IconButton(onClick = onGoForward) {
            Icon(
                imageVector = IdeaComposeIcons.ArrowForward,
                contentDescription = "Forward",
                modifier = Modifier.size(18.dp),
                tint = JewelTheme.globalColors.text.normal
            )
        }

        IconButton(onClick = onReload) {
            Icon(
                imageVector = IdeaComposeIcons.Refresh,
                contentDescription = "Reload",
                modifier = Modifier.size(18.dp),
                tint = JewelTheme.globalColors.text.normal
            )
        }

        // URL input
        TextField(
            state = urlTextFieldState,
            placeholder = { Text("Enter URL...") },
            modifier = Modifier.weight(1f),
            onKeyboardAction = {
                onNavigate(urlTextFieldState.text.toString())
            }
        )

        // Go button
        DefaultButton(
            onClick = { onNavigate(urlTextFieldState.text.toString()) }
        ) {
            Text("Go")
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Selection mode toggle
        OutlinedButton(
            onClick = onToggleSelectionMode
        ) {
            Icon(
                imageVector = if (isSelectionMode) IdeaComposeIcons.Check else IdeaComposeIcons.TouchApp,
                contentDescription = "Toggle Selection Mode",
                modifier = Modifier.size(16.dp),
                tint = if (isSelectionMode) IdeaAutoDevColors.Green.c400 else JewelTheme.globalColors.text.normal
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (isSelectionMode) "Select: ON" else "Select")
        }

        // DOM sidebar toggle
        IconButton(onClick = onToggleDOMSidebar) {
            Icon(
                imageVector = IdeaComposeIcons.AccountTree,
                contentDescription = "Toggle DOM Sidebar",
                modifier = Modifier.size(18.dp),
                tint = if (showDOMSidebar) IdeaAutoDevColors.Blue.c400 else JewelTheme.globalColors.text.normal
            )
        }
    }
}

/**
 * DOM Tree sidebar
 */
@Composable
private fun DOMTreeSidebar(
    domTree: IdeaDOMElement?,
    selectedElement: IdeaDOMElement?,
    onElementClick: (String) -> Unit,
    onElementHover: (String?) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(JewelTheme.globalColors.panelBackground)
            .padding(8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DOM Tree",
                style = JewelTheme.defaultTextStyle.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = IdeaComposeIcons.Refresh,
                    contentDescription = "Refresh DOM",
                    modifier = Modifier.size(16.dp),
                    tint = JewelTheme.globalColors.text.normal
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (domTree == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No DOM tree available.\nLoad a page first.",
                    style = JewelTheme.defaultTextStyle.copy(
                        color = JewelTheme.globalColors.text.info
                    )
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    DOMTreeNode(
                        element = domTree,
                        selectedSelector = selectedElement?.selector,
                        depth = 0,
                        onElementClick = onElementClick,
                        onElementHover = onElementHover
                    )
                }
            }
        }
    }
}

/**
 * Single DOM tree node (recursive)
 */
@Composable
private fun DOMTreeNode(
    element: IdeaDOMElement,
    selectedSelector: String?,
    depth: Int,
    onElementClick: (String) -> Unit,
    onElementHover: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(depth < 2) }
    val isSelected = element.selector == selectedSelector
    val hasChildren = element.children.isNotEmpty()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) IdeaAutoDevColors.Blue.c400.copy(alpha = 0.2f)
                    else JewelTheme.globalColors.panelBackground
                )
                .clickable { onElementClick(element.selector) }
                .padding(start = (depth * 12).dp, top = 2.dp, bottom = 2.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expand/collapse icon
            if (hasChildren) {
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(16.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) IdeaComposeIcons.ExpandMore else IdeaComposeIcons.ChevronRight,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(12.dp),
                        tint = JewelTheme.globalColors.text.info
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(16.dp))
            }

            // Element display
            Text(
                text = element.getDisplayName(),
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                ),
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }

        // Children
        if (expanded && hasChildren) {
            element.children.forEach { child ->
                DOMTreeNode(
                    element = child,
                    selectedSelector = selectedSelector,
                    depth = depth + 1,
                    onElementClick = onElementClick,
                    onElementHover = onElementHover
                )
            }
        }
    }
}

/**
 * Chat panel for WebEdit Q&A
 */
@Composable
private fun WebEditChatPanel(
    timeline: List<TimelineItem>,
    streamingOutput: String,
    selectedElement: IdeaDOMElement?,
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inputTextFieldState = rememberTextFieldState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isGenerating = streamingOutput.isNotEmpty()

    // Sync text field state
    IdeaLaunchedEffect(Unit) {
        snapshotFlow { inputTextFieldState.text.toString() }
            .distinctUntilChanged()
            .collect { inputText = it }
    }

    // Auto-scroll
    IdeaLaunchedEffect(timeline.size, streamingOutput) {
        if (timeline.isNotEmpty() || streamingOutput.isNotEmpty()) {
            val targetIndex = if (streamingOutput.isNotEmpty()) timeline.size else timeline.lastIndex.coerceAtLeast(0)
            if (targetIndex >= 0) {
                listState.scrollToItem(targetIndex)
            }
        }
    }

    Column(
        modifier = modifier
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WebEdit Assistant",
                style = JewelTheme.defaultTextStyle.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            )
            IconButton(onClick = onClearHistory) {
                Icon(
                    imageVector = IdeaComposeIcons.Delete,
                    contentDescription = "Clear history",
                    modifier = Modifier.size(16.dp),
                    tint = JewelTheme.globalColors.text.normal
                )
            }
        }

        Divider(Orientation.Horizontal, modifier = Modifier.fillMaxWidth().height(1.dp))

        // Selected element context
        selectedElement?.let { element ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IdeaAutoDevColors.Blue.c400.copy(alpha = 0.1f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = IdeaComposeIcons.Code,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = IdeaAutoDevColors.Blue.c400
                )
                Text(
                    text = "Context: ${element.getDisplayName()}",
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp),
                    maxLines = 1
                )
            }
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (timeline.isEmpty() && streamingOutput.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ask questions about the web page or selected elements",
                            style = JewelTheme.defaultTextStyle.copy(
                                color = JewelTheme.globalColors.text.info
                            )
                        )
                    }
                }
            } else {
                items(timeline, key = { it.id }) { item ->
                    SimpleChatMessageItem(item)
                }

                if (streamingOutput.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = streamingOutput,
                                style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp)
                            )
                        }
                    }
                }
            }
        }

        Divider(Orientation.Horizontal, modifier = Modifier.fillMaxWidth().height(1.dp))

        // Input area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                state = inputTextFieldState,
                placeholder = { Text("Ask about the page...") },
                modifier = Modifier.weight(1f),
                enabled = !isGenerating
            )

            if (isGenerating) {
                OutlinedButton(onClick = onStopGeneration) {
                    Text("Stop")
                }
            } else {
                DefaultButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            inputTextFieldState.edit { replace(0, length, "") }
                        }
                    },
                    enabled = inputText.isNotBlank()
                ) {
                    Text("Send")
                }
            }
        }
    }
}

/**
 * Simple chat message item
 */
@Composable
private fun SimpleChatMessageItem(item: TimelineItem) {
    when (item) {
        is TimelineItem.MessageItem -> {
            val isUser = item.role == MessageRole.USER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .background(
                            if (isUser) IdeaAutoDevColors.Blue.c400.copy(alpha = 0.2f)
                            else JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f)
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = item.content,
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp)
                    )
                }
            }
        }

        is TimelineItem.ErrorItem -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IdeaAutoDevColors.Red.c400.copy(alpha = 0.2f))
                    .padding(8.dp)
            ) {
                Text(
                    text = item.message,
                    style = JewelTheme.defaultTextStyle.copy(
                        color = IdeaAutoDevColors.Red.c400,
                        fontSize = 12.sp
                    )
                )
            }
        }

        else -> {
            // Other item types not shown in WebEdit chat
        }
    }
}

/**
 * View shown when JCEF is not supported
 */
@Composable
private fun JcefNotSupportedView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = IdeaComposeIcons.Error,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = IdeaAutoDevColors.Red.c400
            )
            Text(
                text = "WebView Not Available",
                style = JewelTheme.defaultTextStyle.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
            Text(
                text = "JCEF (Java Chromium Embedded Framework) is not supported\non this platform or IDE configuration.",
                style = JewelTheme.defaultTextStyle.copy(
                    color = JewelTheme.globalColors.text.info
                )
            )
        }
    }
}
