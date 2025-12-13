package cc.unitmesh.viewer.web.webedit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Tests for Accessibility Tree extraction
 *
 * Based on: https://arxiv.org/html/2508.04412v2
 */
class AccessibilityTreeTest {

    @Nested
    @DisplayName("Role Computation Tests")
    inner class RoleComputationTests {

        @Test
        @DisplayName("Should compute button role")
        fun `compute button role`() {
            val button = DOMElement(
                id = "1",
                tagName = "button",
                selector = "button",
                textContent = "Submit"
            )

            val node = AccessibilityTree.fromDOM(button)

            assertNotNull(node)
            assertEquals("button", node!!.role)
            assertEquals("Submit", node.name)
        }

        @Test
        @DisplayName("Should compute link role")
        fun `compute link role`() {
            val link = DOMElement(
                id = "1",
                tagName = "a",
                selector = "a",
                textContent = "Click here",
                attributes = mapOf("href" to "/page")
            )

            val node = AccessibilityTree.fromDOM(link)

            assertNotNull(node)
            assertEquals("link", node!!.role)
            assertEquals("Click here", node.name)
        }

        @Test
        @DisplayName("Should compute input roles based on type")
        fun `compute input roles`() {
            val inputs = listOf(
                DOMElement(id = "1", tagName = "input", selector = "input", attributes = mapOf("type" to "text")) to "textbox",
                DOMElement(id = "2", tagName = "input", selector = "input", attributes = mapOf("type" to "checkbox")) to "checkbox",
                DOMElement(id = "3", tagName = "input", selector = "input", attributes = mapOf("type" to "radio")) to "radio",
                DOMElement(id = "4", tagName = "input", selector = "input", attributes = mapOf("type" to "submit")) to "button",
                DOMElement(id = "5", tagName = "input", selector = "input", attributes = mapOf("type" to "range")) to "slider",
                DOMElement(id = "6", tagName = "input", selector = "input", attributes = mapOf("type" to "search")) to "searchbox"
            )

            inputs.forEach { (element, expectedRole) ->
                val node = AccessibilityTree.fromDOM(element)
                assertNotNull(node, "Node for ${element.attributes["type"]} should not be null")
                assertEquals(expectedRole, node!!.role, "Role for type=${element.attributes["type"]} should be $expectedRole")
            }
        }

        @Test
        @DisplayName("Should use explicit role attribute")
        fun `use explicit role`() {
            val element = DOMElement(
                id = "1",
                tagName = "div",
                selector = "div",
                textContent = "Tab content",
                attributes = mapOf("role" to "tabpanel")
            )

            val node = AccessibilityTree.fromDOM(element)

            assertNotNull(node)
            assertEquals("tabpanel", node!!.role)
        }

        @Test
        @DisplayName("Should compute semantic element roles")
        fun `compute semantic roles`() {
            val semanticElements = listOf(
                DOMElement(id = "1", tagName = "nav", selector = "nav") to "navigation",
                DOMElement(id = "2", tagName = "main", selector = "main") to "main",
                DOMElement(id = "3", tagName = "header", selector = "header") to "banner",
                DOMElement(id = "4", tagName = "footer", selector = "footer") to "contentinfo",
                DOMElement(id = "5", tagName = "article", selector = "article") to "article",
                DOMElement(id = "6", tagName = "form", selector = "form") to "form"
            )

            semanticElements.forEach { (element, expectedRole) ->
                val node = AccessibilityTree.fromDOM(element)
                assertNotNull(node, "Node for ${element.tagName} should not be null")
                assertEquals(expectedRole, node!!.role, "Role for ${element.tagName} should be $expectedRole")
            }
        }

        @Test
        @DisplayName("Should compute heading role")
        fun `compute heading role`() {
            listOf("h1", "h2", "h3", "h4", "h5", "h6").forEach { tag ->
                val heading = DOMElement(
                    id = "1",
                    tagName = tag,
                    selector = tag,
                    textContent = "Heading Text"
                )

                val node = AccessibilityTree.fromDOM(heading)

                assertNotNull(node)
                assertEquals("heading", node!!.role)
            }
        }
    }

    @Nested
    @DisplayName("Name Computation Tests")
    inner class NameComputationTests {

        @Test
        @DisplayName("Should use aria-label as name")
        fun `use aria label`() {
            val button = DOMElement(
                id = "1",
                tagName = "button",
                selector = "button",
                textContent = "X",
                attributes = mapOf("aria-label" to "Close dialog")
            )

            val node = AccessibilityTree.fromDOM(button)

            assertNotNull(node)
            assertEquals("Close dialog", node!!.name)
        }

        @Test
        @DisplayName("Should use placeholder for input")
        fun `use placeholder`() {
            val input = DOMElement(
                id = "1",
                tagName = "input",
                selector = "input",
                attributes = mapOf(
                    "type" to "email",
                    "placeholder" to "Enter your email"
                )
            )

            val node = AccessibilityTree.fromDOM(input)

            assertNotNull(node)
            assertEquals("Enter your email", node!!.name)
        }

        @Test
        @DisplayName("Should use alt text for images")
        fun `use alt text`() {
            val img = DOMElement(
                id = "1",
                tagName = "img",
                selector = "img",
                attributes = mapOf(
                    "src" to "/logo.png",
                    "alt" to "Company Logo"
                )
            )

            val node = AccessibilityTree.fromDOM(img)

            assertNotNull(node)
            assertEquals("Company Logo", node!!.name)
        }

        @Test
        @DisplayName("Should use text content for buttons")
        fun `use text content`() {
            val button = DOMElement(
                id = "1",
                tagName = "button",
                selector = "button",
                textContent = "Submit Form"
            )

            val node = AccessibilityTree.fromDOM(button)

            assertNotNull(node)
            assertEquals("Submit Form", node!!.name)
        }

        @Test
        @DisplayName("Should use title attribute as fallback")
        fun `use title fallback`() {
            val element = DOMElement(
                id = "1",
                tagName = "span",
                selector = "span",
                attributes = mapOf("title" to "Additional information")
            )

            val node = AccessibilityTree.fromDOM(element)

            assertNotNull(node)
            assertEquals("Additional information", node!!.name)
        }
    }

    @Nested
    @DisplayName("State Computation Tests")
    inner class StateComputationTests {

        @Test
        @DisplayName("Should detect disabled state")
        fun `detect disabled state`() {
            val button = DOMElement(
                id = "1",
                tagName = "button",
                selector = "button",
                textContent = "Disabled Button",
                attributes = mapOf("disabled" to "")
            )

            val node = AccessibilityTree.fromDOM(button)

            assertNotNull(node)
            assertTrue(node!!.state["disabled"] == true)
        }

        @Test
        @DisplayName("Should detect aria-disabled")
        fun `detect aria disabled`() {
            val button = DOMElement(
                id = "1",
                tagName = "button",
                selector = "button",
                textContent = "ARIA Disabled",
                attributes = mapOf("aria-disabled" to "true")
            )

            val node = AccessibilityTree.fromDOM(button)

            assertNotNull(node)
            assertTrue(node!!.state["disabled"] == true)
        }

        @Test
        @DisplayName("Should detect checked state")
        fun `detect checked state`() {
            val checkbox = DOMElement(
                id = "1",
                tagName = "input",
                selector = "input",
                attributes = mapOf("type" to "checkbox", "checked" to "")
            )

            val node = AccessibilityTree.fromDOM(checkbox)

            assertNotNull(node)
            assertTrue(node!!.state["checked"] == true)
        }

        @Test
        @DisplayName("Should detect expanded state")
        fun `detect expanded state`() {
            val accordion = DOMElement(
                id = "1",
                tagName = "button",
                selector = "button",
                textContent = "Accordion Header",
                attributes = mapOf("aria-expanded" to "true")
            )

            val node = AccessibilityTree.fromDOM(accordion)

            assertNotNull(node)
            assertTrue(node!!.state["expanded"] == true)
        }

        @Test
        @DisplayName("Should detect multiple states")
        fun `detect multiple states`() {
            val input = DOMElement(
                id = "1",
                tagName = "input",
                selector = "input",
                attributes = mapOf(
                    "type" to "text",
                    "required" to "",
                    "readonly" to "",
                    "aria-invalid" to "true"
                )
            )

            val node = AccessibilityTree.fromDOM(input)

            assertNotNull(node)
            assertTrue(node!!.state["required"] == true)
            assertTrue(node.state["readonly"] == true)
            assertTrue(node.state["invalid"] == true)
        }
    }

    @Nested
    @DisplayName("Value Computation Tests")
    inner class ValueComputationTests {

        @Test
        @DisplayName("Should extract input value")
        fun `extract input value`() {
            val input = DOMElement(
                id = "1",
                tagName = "input",
                selector = "input",
                attributes = mapOf(
                    "type" to "text",
                    "value" to "John Doe"
                )
            )

            val node = AccessibilityTree.fromDOM(input)

            assertNotNull(node)
            assertEquals("John Doe", node!!.value)
        }

        @Test
        @DisplayName("Should not expose password values")
        fun `hide password value`() {
            val input = DOMElement(
                id = "1",
                tagName = "input",
                selector = "input",
                attributes = mapOf(
                    "type" to "password",
                    "value" to "secret123"
                )
            )

            val node = AccessibilityTree.fromDOM(input)

            assertNotNull(node)
            assertNull(node!!.value)
        }

        @Test
        @DisplayName("Should extract progress value")
        fun `extract progress value`() {
            val progress = DOMElement(
                id = "1",
                tagName = "progress",
                selector = "progress",
                attributes = mapOf("value" to "75")
            )

            val node = AccessibilityTree.fromDOM(progress)

            assertNotNull(node)
            assertEquals("75", node!!.value)
        }
    }

    @Nested
    @DisplayName("Tree Structure Tests")
    inner class TreeStructureTests {

        @Test
        @DisplayName("Should flatten non-semantic containers")
        fun `flatten containers`() {
            val nested = DOMElement(
                id = "1",
                tagName = "div",
                selector = "div",
                children = listOf(
                    DOMElement(
                        id = "2",
                        tagName = "div",
                        selector = "div > div",
                        children = listOf(
                            DOMElement(
                                id = "3",
                                tagName = "button",
                                selector = "div > div > button",
                                textContent = "Click me"
                            )
                        )
                    )
                )
            )

            val node = AccessibilityTree.fromDOM(nested)

            assertNotNull(node)
            // Should flatten to just the button
            assertEquals("button", node!!.role)
            assertEquals("Click me", node.name)
        }

        @Test
        @DisplayName("Should preserve semantic structure")
        fun `preserve semantic structure`() {
            val nav = DOMElement(
                id = "1",
                tagName = "nav",
                selector = "nav",
                children = listOf(
                    DOMElement(
                        id = "2",
                        tagName = "ul",
                        selector = "nav > ul",
                        children = listOf(
                            DOMElement(
                                id = "3",
                                tagName = "li",
                                selector = "nav > ul > li",
                                children = listOf(
                                    DOMElement(
                                        id = "4",
                                        tagName = "a",
                                        selector = "nav > ul > li > a",
                                        textContent = "Home",
                                        attributes = mapOf("href" to "/")
                                    )
                                )
                            )
                        )
                    )
                )
            )

            val node = AccessibilityTree.fromDOM(nav)

            assertNotNull(node)
            assertEquals("navigation", node!!.role)
            assertTrue(node.children.isNotEmpty())
        }

        @Test
        @DisplayName("Should handle multiple children")
        fun `handle multiple children`() {
            val form = DOMElement(
                id = "1",
                tagName = "form",
                selector = "form",
                children = listOf(
                    DOMElement(
                        id = "2",
                        tagName = "input",
                        selector = "form > input:nth-child(1)",
                        attributes = mapOf("type" to "text", "placeholder" to "Username")
                    ),
                    DOMElement(
                        id = "3",
                        tagName = "input",
                        selector = "form > input:nth-child(2)",
                        attributes = mapOf("type" to "password", "placeholder" to "Password")
                    ),
                    DOMElement(
                        id = "4",
                        tagName = "button",
                        selector = "form > button",
                        textContent = "Login"
                    )
                )
            )

            val node = AccessibilityTree.fromDOM(form)

            assertNotNull(node)
            assertEquals("form", node!!.role)
            assertEquals(3, node.children.size)
            assertTrue(node.children.any { it.role == "textbox" })
            assertTrue(node.children.any { it.role == "button" })
        }
    }

    @Nested
    @DisplayName("Compact JSON Output Tests")
    inner class CompactJsonTests {

        @Test
        @DisplayName("Should generate compact JSON")
        fun `generate compact json`() {
            val node = AccessibilityNode(
                role = "button",
                name = "Submit",
                state = mapOf("disabled" to false, "focused" to true),
                selector = "button",
                children = emptyList()
            )

            val json = AccessibilityTree.toCompactJson(node)

            assertTrue(json.contains("\"button\""))
            assertTrue(json.contains("\"Submit\""))
            assertTrue(json.contains("\"focused\""))
        }

        @Test
        @DisplayName("Should handle nested structure in JSON")
        fun `generate nested json`() {
            val node = AccessibilityNode(
                role = "navigation",
                name = "Main menu",
                selector = "nav",
                children = listOf(
                    AccessibilityNode(
                        role = "link",
                        name = "Home",
                        selector = "nav > a:nth-child(1)"
                    ),
                    AccessibilityNode(
                        role = "link",
                        name = "About",
                        selector = "nav > a:nth-child(2)"
                    )
                )
            )

            val json = AccessibilityTree.toCompactJson(node)

            assertTrue(json.contains("navigation"))
            assertTrue(json.contains("link"))
            assertTrue(json.contains("Home"))
            assertTrue(json.contains("About"))
            assertTrue(json.contains("children"))
        }
    }

    @Nested
    @DisplayName("Actionable Filtering Tests")
    inner class ActionableFilteringTests {

        @Test
        @DisplayName("Should filter to only actionable elements")
        fun `filter actionable`() {
            val tree = AccessibilityNode(
                role = "main",
                selector = "main",
                children = listOf(
                    AccessibilityNode(
                        role = "heading",
                        name = "Title",
                        selector = "main > h1"
                    ),
                    AccessibilityNode(
                        role = "generic",
                        selector = "main > div",
                        children = listOf(
                            AccessibilityNode(
                                role = "button",
                                name = "Click me",
                                selector = "main > div > button"
                            ),
                            AccessibilityNode(
                                role = "generic",
                                selector = "main > div > span"
                            )
                        )
                    ),
                    AccessibilityNode(
                        role = "textbox",
                        name = "Search",
                        selector = "main > input"
                    )
                )
            )

            val actionable = AccessibilityTree.filterActionable(tree)

            assertNotNull(actionable)
            // Should only contain button and textbox
            fun countActionable(node: AccessibilityNode): Int {
                val actionableRoles = setOf("button", "link", "textbox", "checkbox", "radio")
                val self = if (node.role in actionableRoles) 1 else 0
                return self + node.children.sumOf { countActionable(it) }
            }
            assertEquals(2, countActionable(actionable!!))
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    inner class StatisticsTests {

        @Test
        @DisplayName("Should calculate tree statistics")
        fun `calculate statistics`() {
            val tree = AccessibilityNode(
                role = "main",
                selector = "main",
                children = listOf(
                    AccessibilityNode(role = "button", name = "Save", selector = "button:nth-child(1)"),
                    AccessibilityNode(role = "button", name = "Cancel", selector = "button:nth-child(2)"),
                    AccessibilityNode(role = "textbox", name = "Input", selector = "input"),
                    AccessibilityNode(role = "link", name = "Help", selector = "a")
                )
            )

            val stats = AccessibilityTree.getStatistics(tree)

            assertEquals(5, stats.totalNodes)
            assertEquals(4, stats.actionableNodes)
            assertEquals(2, stats.roleCounts["button"])
            assertEquals(1, stats.roleCounts["textbox"])
            assertEquals(1, stats.roleCounts["link"])
        }
    }

    @Nested
    @DisplayName("Real-World Scenarios")
    inner class RealWorldScenarios {

        @Test
        @DisplayName("Should handle login form")
        fun `handle login form`() {
            val form = DOMElement(
                id = "1",
                tagName = "form",
                selector = "form",
                attributes = mapOf("aria-label" to "Login form"),
                children = listOf(
                    DOMElement(
                        id = "2",
                        tagName = "label",
                        selector = "form > label:nth-child(1)",
                        textContent = "Email"
                    ),
                    DOMElement(
                        id = "3",
                        tagName = "input",
                        selector = "form > input:nth-child(2)",
                        attributes = mapOf(
                            "type" to "email",
                            "id" to "email",
                            "required" to "",
                            "placeholder" to "Enter email"
                        )
                    ),
                    DOMElement(
                        id = "4",
                        tagName = "label",
                        selector = "form > label:nth-child(3)",
                        textContent = "Password"
                    ),
                    DOMElement(
                        id = "5",
                        tagName = "input",
                        selector = "form > input:nth-child(4)",
                        attributes = mapOf(
                            "type" to "password",
                            "id" to "password",
                            "required" to ""
                        )
                    ),
                    DOMElement(
                        id = "6",
                        tagName = "button",
                        selector = "form > button",
                        textContent = "Sign In",
                        attributes = mapOf("type" to "submit")
                    )
                )
            )

            val node = AccessibilityTree.fromDOM(form)

            assertNotNull(node)
            assertEquals("form", node!!.role)
            assertEquals("Login form", node.name)

            // Should have form controls
            assertTrue(node.children.any { it.role == "textbox" })
            assertTrue(node.children.any { it.role == "button" })
        }

        @Test
        @DisplayName("Should handle dropdown menu")
        fun `handle dropdown menu`() {
            val menu = DOMElement(
                id = "1",
                tagName = "div",
                selector = "div",
                attributes = mapOf(
                    "role" to "menu",
                    "aria-label" to "Actions"
                ),
                children = listOf(
                    DOMElement(
                        id = "2",
                        tagName = "button",
                        selector = "div > button:nth-child(1)",
                        textContent = "Edit",
                        attributes = mapOf("role" to "menuitem")
                    ),
                    DOMElement(
                        id = "3",
                        tagName = "button",
                        selector = "div > button:nth-child(2)",
                        textContent = "Delete",
                        attributes = mapOf("role" to "menuitem")
                    )
                )
            )

            val node = AccessibilityTree.fromDOM(menu)

            assertNotNull(node)
            assertEquals("menu", node!!.role)
            assertEquals("Actions", node.name)
            assertEquals(2, node.children.size)
            assertTrue(node.children.all { it.role == "menuitem" })
        }

        @Test
        @DisplayName("Should handle tab panel")
        fun `handle tab panel`() {
            val tabs = DOMElement(
                id = "1",
                tagName = "div",
                selector = "div",
                attributes = mapOf("role" to "tablist"),
                children = listOf(
                    DOMElement(
                        id = "2",
                        tagName = "button",
                        selector = "div > button:nth-child(1)",
                        textContent = "Tab 1",
                        attributes = mapOf(
                            "role" to "tab",
                            "aria-selected" to "true"
                        )
                    ),
                    DOMElement(
                        id = "3",
                        tagName = "button",
                        selector = "div > button:nth-child(2)",
                        textContent = "Tab 2",
                        attributes = mapOf(
                            "role" to "tab",
                            "aria-selected" to "false"
                        )
                    )
                )
            )

            val node = AccessibilityTree.fromDOM(tabs)

            assertNotNull(node)
            assertEquals("tablist", node!!.role)
            
            val selectedTab = node.children.find { it.state["selected"] == true }
            assertNotNull(selectedTab)
            assertEquals("Tab 1", selectedTab!!.name)
        }
    }
}
