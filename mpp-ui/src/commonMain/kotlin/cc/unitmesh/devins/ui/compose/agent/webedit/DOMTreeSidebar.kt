package cc.unitmesh.devins.ui.compose.agent.webedit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import cc.unitmesh.viewer.web.webedit.DOMElement
import kotlinx.coroutines.launch

/**
 * DOM Tree Sidebar - displays the DOM structure of the current page
 */
@Composable
fun DOMTreeSidebar(
    domTree: DOMElement? = null,
    selectedElement: DOMElement? = null,
    modifier: Modifier = Modifier,
    onElementClick: (String) -> Unit = {},
    onElementHover: (String?) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Convert DOMElement tree to flat list with depth
    val flattenedTree = remember(domTree) {
        domTree?.let { flattenDOMTree(it, 0) } ?: emptyList()
    }

    // Auto-scroll to selected element when it changes
    LaunchedEffect(selectedElement?.selector, flattenedTree) {
        if (selectedElement != null && flattenedTree.isNotEmpty()) {
            val index = flattenedTree.indexOfFirst { it.selector == selectedElement.selector }
            if (index >= 0) {
                // Scroll to make the item visible (with some padding)
                listState.animateScrollToItem(maxOf(0, index - 2))
            }
        }
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
        if (flattenedTree.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Load a page to see DOM tree",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(flattenedTree, key = { index, item -> item.id }) { _, item ->
                    DOMTreeItemRow(
                        item = item,
                        isSelected = selectedElement?.selector == item.selector,
                        onClick = { onElementClick(item.selector) },
                        onHover = { onElementHover(if (it) item.selector else null) }
                    )
                }
            }
        }
    }
}

/**
 * Data class for flattened DOM tree items with depth
 */
data class DOMTreeItem(
    val id: String,
    val tagName: String,
    val selector: String,
    val depth: Int,
    val attributes: List<String>,
    val textContent: String? = null
)

/**
 * Flatten a DOMElement tree into a list with depth information
 */
private fun flattenDOMTree(element: DOMElement, depth: Int): List<DOMTreeItem> {
    val result = mutableListOf<DOMTreeItem>()
    result.add(
        DOMTreeItem(
            id = element.id,
            tagName = element.tagName,
            selector = element.selector,
            depth = depth,
            attributes = element.attributes.map { "${it.key}=${it.value}" },
            textContent = element.textContent
        )
    )
    for (child in element.children) {
        result.addAll(flattenDOMTree(child, depth + 1))
    }
    return result
}

/**
 * Single row in the DOM tree
 */
@Composable
private fun DOMTreeItemRow(
    item: DOMTreeItem,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onHover: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isHovered by remember { mutableStateOf(false) }

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        isHovered -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = (item.depth * 12).dp),
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor
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
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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

            // Text content preview
            item.textContent?.takeIf { it.isNotBlank() }?.let { text ->
                Text(
                    text = "\"${text.take(20)}${if (text.length > 20) "..." else ""}\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

