package cc.unitmesh.devins.ui.compose.agent.webedit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * DOM Tree Sidebar - displays the DOM structure of the current page
 */
@Composable
fun DOMTreeSidebar(
    modifier: Modifier = Modifier,
    onElementClick: (String) -> Unit = {},
    onElementHover: (String?) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    
    // Sample DOM tree structure (will be populated by WebView)
    val sampleDomTree = remember {
        listOf(
            DOMTreeItem("html", "html", 0, listOf("lang=en")),
            DOMTreeItem("head", "html > head", 1, emptyList()),
            DOMTreeItem("body", "html > body", 1, listOf("class=main-body")),
            DOMTreeItem("div", "div.container", 2, listOf("class=container")),
            DOMTreeItem("header", "header.nav", 3, listOf("class=nav")),
            DOMTreeItem("main", "main.content", 3, listOf("class=content")),
            DOMTreeItem("footer", "footer", 3, emptyList())
        )
    }
    
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(8.dp)
    ) {
        // Header
        Text(
            text = "DOM Tree",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().height(36.dp),
            placeholder = { Text("Search...", style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            shape = RoundedCornerShape(6.dp),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // DOM tree list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(sampleDomTree) { item ->
                DOMTreeItemRow(
                    item = item,
                    onClick = { onElementClick(item.selector) },
                    onHover = { onElementHover(if (it) item.selector else null) }
                )
            }
        }
    }
}

/**
 * Data class for DOM tree items
 */
data class DOMTreeItem(
    val tagName: String,
    val selector: String,
    val depth: Int,
    val attributes: List<String>,
    val children: List<DOMTreeItem> = emptyList(),
    val isExpanded: Boolean = true
)

/**
 * Single row in the DOM tree
 */
@Composable
private fun DOMTreeItemRow(
    item: DOMTreeItem,
    onClick: () -> Unit,
    onHover: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isHovered by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = (item.depth * 12).dp),
        shape = RoundedCornerShape(4.dp),
        color = if (isHovered) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Tag name
            Text(
                text = "<${item.tagName}>",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            
            // Attributes preview
            if (item.attributes.isNotEmpty()) {
                Text(
                    text = item.attributes.firstOrNull() ?: "",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

