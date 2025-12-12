package cc.unitmesh.devins.ui.compose.agent.webedit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.agent.AgentType
import cc.unitmesh.llm.KoogLLMService
import kotlinx.coroutines.launch

/**
 * WebEdit Page - Browse, select DOM elements, and interact with web pages
 * 
 * Layout:
 * - Top: URL bar with navigation controls
 * - Center: WebView with selection overlay
 * - Right: DOM tree sidebar (toggleable)
 * - Bottom: Chat/Q&A input area
 */
@Composable
fun WebEditPage(
    llmService: KoogLLMService?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNotification: (String, String) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()
    
    // URL and navigation state
    var currentUrl by remember { mutableStateOf("https://") }
    var inputUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    // Selection mode state
    var isSelectionMode by remember { mutableStateOf(false) }
    
    // DOM sidebar state
    var showDOMSidebar by remember { mutableStateOf(true) }
    
    // Chat input state
    var chatInput by remember { mutableStateOf("") }
    
    Column(modifier = modifier.fillMaxSize()) {
        // Top bar with URL input and controls
        WebEditToolbar(
            currentUrl = currentUrl,
            inputUrl = inputUrl,
            isLoading = isLoading,
            isSelectionMode = isSelectionMode,
            showDOMSidebar = showDOMSidebar,
            onUrlChange = { inputUrl = it },
            onNavigate = { url ->
                val normalizedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    "https://$url"
                } else url
                currentUrl = normalizedUrl
                inputUrl = normalizedUrl
                isLoading = true
            },
            onBack = onBack,
            onReload = { isLoading = true },
            onToggleSelectionMode = { isSelectionMode = !isSelectionMode },
            onToggleDOMSidebar = { showDOMSidebar = !showDOMSidebar }
        )
        
        // Main content area
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // WebView area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Placeholder for WebView - will be platform-specific
                WebEditViewPlaceholder(
                    url = currentUrl,
                    isSelectionMode = isSelectionMode,
                    onPageLoaded = { isLoading = false },
                    onElementSelected = { /* Handle element selection */ },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Loading indicator
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    )
                }
                
                // Selection mode indicator
                if (isSelectionMode) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Selection Mode",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            // DOM Tree Sidebar
            if (showDOMSidebar) {
                DOMTreeSidebar(
                    modifier = Modifier.width(280.dp).fillMaxHeight()
                )
            }
        }
        
        // Bottom chat/Q&A input area
        WebEditChatInput(
            input = chatInput,
            onInputChange = { chatInput = it },
            onSend = { message ->
                scope.launch {
                    // Handle chat message with LLM
                    chatInput = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

