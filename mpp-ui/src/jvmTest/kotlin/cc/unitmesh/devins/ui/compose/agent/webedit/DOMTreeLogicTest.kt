package cc.unitmesh.devins.ui.compose.agent.webedit

import cc.unitmesh.viewer.web.webedit.DOMElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for DOM tree logic functions
 */
class DOMTreeLogicTest {

    private fun createSimpleDOMTree(): DOMElement {
        return DOMElement(
            id = "1",
            tagName = "div",
            selector = "#root",
            attributes = mapOf("id" to "root"),
            children = listOf(
                DOMElement(
                    id = "2",
                    tagName = "header",
                    selector = "#root > header",
                    children = listOf(
                        DOMElement(
                            id = "3",
                            tagName = "h1",
                            selector = "#root > header > h1",
                            textContent = "Title"
                        )
                    )
                ),
                DOMElement(
                    id = "4",
                    tagName = "main",
                    selector = "#root > main",
                    children = listOf(
                        DOMElement(
                            id = "5",
                            tagName = "p",
                            selector = "#root > main > p",
                            textContent = "Content"
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `buildDOMTreeNode creates tree with parent references`() {
        val domElement = createSimpleDOMTree()
        val treeNode = buildDOMTreeNode(domElement, null, 0)

        assertEquals("div", treeNode.tagName)
        assertEquals("#root", treeNode.selector)
        assertEquals(0, treeNode.depth)
        assertEquals(2, treeNode.children.size)
        
        // Check first child
        val header = treeNode.children[0]
        assertEquals("header", header.tagName)
        assertEquals(1, header.depth)
        assertEquals(treeNode.selector, header.parent?.selector)
        
        // Check nested child  
        val h1 = header.children[0]
        assertEquals("h1", h1.tagName)
        assertEquals(2, h1.depth)
        assertEquals(header.selector, h1.parent?.selector)
    }

    @Test
    fun `findPathToNode returns correct path`() {
        val domElement = createSimpleDOMTree()
        val treeNode = buildDOMTreeNode(domElement, null, 0)
        
        val path = findPathToNode(treeNode, "#root > header > h1")
        
        assertEquals(2, path.size)
        assertEquals("#root", path[0])
        assertEquals("#root > header", path[1])
    }

    @Test
    fun `findPathToNode returns empty for non-existent selector`() {
        val domElement = createSimpleDOMTree()
        val treeNode = buildDOMTreeNode(domElement, null, 0)
        
        val path = findPathToNode(treeNode, "#non-existent")
        
        assertTrue(path.isEmpty())
    }

    @Test
    fun `collectAllSelectors returns all parent selectors`() {
        val domElement = createSimpleDOMTree()
        val treeNode = buildDOMTreeNode(domElement, null, 0)
        
        val selectors = collectAllSelectors(treeNode)
        
        assertEquals(3, selectors.size)
        assertTrue(selectors.contains("#root"))
        assertTrue(selectors.contains("#root > header"))
        assertTrue(selectors.contains("#root > main"))
    }

    @Test
    fun `flattenVisibleTree shows all nodes when nothing collapsed`() {
        val domElement = createSimpleDOMTree()
        val treeNode = buildDOMTreeNode(domElement, null, 0)
        
        val flattened = flattenVisibleTree(treeNode, emptySet())
        
        assertEquals(5, flattened.size)
        assertEquals("div", flattened[0].tagName)
        assertEquals("header", flattened[1].tagName)
        assertEquals("h1", flattened[2].tagName)
        assertEquals("main", flattened[3].tagName)
        assertEquals("p", flattened[4].tagName)
    }

    @Test
    fun `flattenVisibleTree hides children when parent collapsed`() {
        val domElement = createSimpleDOMTree()
        val treeNode = buildDOMTreeNode(domElement, null, 0)
        
        val collapsed = setOf("#root > header")
        val flattened = flattenVisibleTree(treeNode, collapsed)
        
        assertEquals(4, flattened.size)
        assertEquals("div", flattened[0].tagName)
        assertEquals("header", flattened[1].tagName)
        assertEquals("main", flattened[2].tagName)
        assertEquals("p", flattened[3].tagName)
    }

    @Test
    fun `flattenVisibleTree preserves depth information`() {
        val domElement = createSimpleDOMTree()
        val treeNode = buildDOMTreeNode(domElement, null, 0)
        
        val flattened = flattenVisibleTree(treeNode, emptySet())
        
        assertEquals(0, flattened[0].depth) // div
        assertEquals(1, flattened[1].depth) // header
        assertEquals(2, flattened[2].depth) // h1
        assertEquals(1, flattened[3].depth) // main
        assertEquals(2, flattened[4].depth) // p
    }

    @Test
    fun `flattenVisibleTree includes hasChildren flag`() {
        val domElement = createSimpleDOMTree()
        val treeNode = buildDOMTreeNode(domElement, null, 0)
        
        val flattened = flattenVisibleTree(treeNode, emptySet())
        
        assertTrue(flattened[0].hasChildren) // div has children
        assertTrue(flattened[1].hasChildren) // header has children
        assertTrue(!flattened[2].hasChildren) // h1 has no children
        assertTrue(flattened[3].hasChildren) // main has children
        assertTrue(!flattened[4].hasChildren) // p has no children
    }

    @Test
    fun `buildDOMTreeNode handles empty children`() {
        val domElement = DOMElement(
            id = "1",
            tagName = "div",
            selector = "#empty",
            children = emptyList()
        )
        
        val treeNode = buildDOMTreeNode(domElement, null, 0)
        
        assertEquals("div", treeNode.tagName)
        assertTrue(treeNode.children.isEmpty())
    }

    @Test
    fun `collectAllSelectors excludes leaf nodes`() {
        val domElement = DOMElement(
            id = "1",
            tagName = "div",
            selector = "#parent",
            children = listOf(
                DOMElement(
                    id = "2",
                    tagName = "span",
                    selector = "#parent > span",
                    children = emptyList()
                )
            )
        )
        
        val treeNode = buildDOMTreeNode(domElement, null, 0)
        val selectors = collectAllSelectors(treeNode)
        
        assertEquals(1, selectors.size)
        assertTrue(selectors.contains("#parent"))
        assertTrue(!selectors.contains("#parent > span"))
    }
}
