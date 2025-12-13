package cc.unitmesh.devins.ui.compose.agent.webedit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

/**
 * DOM Tree Sidebar - displays the DOM structure with collapsible nodes
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
    
    // Track collapsed state by selector
    var collapsedNodes by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Build hierarchical tree with parent tracking
    val treeRoot = remember(domTree) {
        domTree?.let { buildDOMTreeNode(it, null) }
    }

    // Auto-expand path to selected element and scroll to it
    LaunchedEffect(selectedElement?.selector, treeRoot) {
        if (selectedElement != null && treeRoot != null) {
            // Find path to selected element
            val pathToExpand = findPathToNode(treeRoot, selectedElement.selector)
            if (pathToExpand.isNotEmpty()) {
                // Expand all ancestors
                collapsedNodes = collapsedNodes - pathToExpand.toSet()
                
                // Wait a frame for the tree to expand
                kotlinx.coroutines.delay(50)
                
                // Find the index in flattened visible list
                val visibleItems = flattenVisibleTree(treeRoot, collapsedNodes)
                val index = visibleItems.indexOfFirst { it.selector == selectedElement.selector }
                if (index >= 0) {
                    listState.animateScrollToItem(maxOf(0, index - 2))
                }
            }
        }
    }

    // Flatten tree for display based on collapsed state
    val visibleItems = remember(treeRoot, collapsedNodes) {
        treeRoot?.let { flattenVisibleTree(it, collapsedNodes) } ?: emptyList()
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(8.dp)
    ) {
        // Header with collapse all button
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DOM Tree",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Collapse all button
                IconButton(
                    onClick = {
                        treeRoot?.let {
                            collapsedNodes = collectAllSelectors(it).toSet()
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.UnfoldLess,
                        contentDescription = "Collapse All",
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                // Expand all button
                IconButton(
                    onClick = { collapsedNodes = emptySet() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.UnfoldMore,
                        contentDescription = "Expand All",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

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
        if (visibleItems.isEmpty()) {
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
                itemsIndexed(visibleItems, key = { _, item -> item.id }) { _, item ->
                    DOMTreeItemRow(
                        item = item,
                        isSelected = selectedElement?.selector == item.selector,
                        isCollapsed = item.selector in collapsedNodes,
                        hasChildren = item.hasChildren,
                        onToggleCollapse = {
                            collapsedNodes = if (item.selector in collapsedNodes) {
                                collapsedNodes - item.selector
                            } else {
                                collapsedNodes + item.selector
                            }
                        },
                        onClick = { onElementClick(item.selector) },
                        onHover = { onElementHover(if (it) item.selector else null) }
                    )
                }
            }
        }
    }
}

/**
 * Hierarchical DOM tree node with parent reference
 */
data class DOMTreeNode(
    val id: String,
    val tagName: String,
    val selector: String,
    val attributes: Map<String, String>,
    val textContent: String?,
    val children: List<DOMTreeNode>,
    val parent: DOMTreeNode?,
    val depth: Int
)

/**
 * Build hierarchical tree with parent references
 */
fun buildDOMTreeNode(element: DOMElement, parent: DOMTreeNode?, depth: Int = 0): DOMTreeNode {
    val node = DOMTreeNode(
        id = element.id,
        tagName = element.tagName,
        selector = element.selector,
        attributes = element.attributes,
        textContent = element.textContent,
        children = emptyList(),
        parent = parent,
        depth = depth
    )
    
    val childNodes = element.children.map { buildDOMTreeNode(it, node, depth + 1) }
    return node.copy(children = childNodes)
}

/**
 * Find path (selectors) from root to target node
 */
fun findPathToNode(root: DOMTreeNode, targetSelector: String): List<String> {
    fun search(node: DOMTreeNode, path: List<String>): List<String>? {
        val currentPath = path + node.selector
        
        if (node.selector == targetSelector) {
            return currentPath
        }
        
        for (child in node.children) {
            val result = search(child, currentPath)
            if (result != null) return result
        }
        
        return null
    }
    
    return search(root, emptyList())?.dropLast(1) ?: emptyList() // Drop target itself, keep ancestors
}

/**
 * Collect all selectors in tree (for collapse all)
 */
fun collectAllSelectors(node: DOMTreeNode): List<String> {
    val result = mutableListOf<String>()
    if (node.children.isNotEmpty()) {
        result.add(node.selector)
    }
    node.children.forEach { result.addAll(collectAllSelectors(it)) }
    return result
}

/**
 * Flatten visible tree based on collapsed state
 */
fun flattenVisibleTree(root: DOMTreeNode, collapsedNodes: Set<String>): List<DOMTreeItem> {
    val result = mutableListOf<DOMTreeItem>()
    
    fun traverse(node: DOMTreeNode) {
        result.add(
            DOMTreeItem(
                id = node.id,
                tagName = node.tagName,
                selector = node.selector,
                depth = node.depth,
                attributes = node.attributes.map { "${it.key}=${it.value}" },
                textContent = node.textContent,
                hasChildren = node.children.isNotEmpty()
            )
        )
        
        // Only traverse children if this node is not collapsed
        if (node.selector !in collapsedNodes) {
            node.children.forEach { traverse(it) }
        }
    }
    
    traverse(root)
    return result
}

/**
 * Data class for flattened DOM tree items with depth and collapse state
 */
data class DOMTreeItem(
    val id: String,
    val tagName: String,
    val selector: String,
    val depth: Int,
    val attributes: List<String>,
    val textContent: String? = null,
    val hasChildren: Boolean = false
)

/**
 * Single row in the DOM tree with collapse/expand control
 */
@Composable
private fun DOMTreeItemRow(
    item: DOMTreeItem,
    isSelected: Boolean = false,
    isCollapsed: Boolean = false,
    hasChildren: Boolean = false,
    onToggleCollapse: () -> Unit = {},
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
            // Collapse/expand icon
            if (hasChildren) {
                IconButton(
                    onClick = { onToggleCollapse() },
                    modifier = Modifier.size(16.dp)
                ) {
                    Icon(
                        imageVector = if (isCollapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                        contentDescription = if (isCollapsed) "Expand" else "Collapse",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(16.dp))
            }
            
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

