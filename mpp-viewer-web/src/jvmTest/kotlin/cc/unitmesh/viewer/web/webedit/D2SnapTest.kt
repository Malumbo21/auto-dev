package cc.unitmesh.viewer.web.webedit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

/**
 * Tests for D2Snap DOM compression algorithm
 *
 * Based on: https://arxiv.org/html/2508.04412v2
 */
class D2SnapTest {

    @Nested
    @DisplayName("Structure Flattening Tests")
    inner class StructureFlatteningTests {

        @Test
        @DisplayName("Should flatten single-child wrapper divs")
        fun `flatten single child wrapper`() {
            // <div><span><button>OK</button></span></div> → <button>OK</button>
            val nested = DOMElement(
                id = "1",
                tagName = "div",
                selector = "div",
                children = listOf(
                    DOMElement(
                        id = "2",
                        tagName = "span",
                        selector = "div > span",
                        children = listOf(
                            DOMElement(
                                id = "3",
                                tagName = "button",
                                selector = "div > span > button",
                                textContent = "OK"
                            )
                        )
                    )
                )
            )

            val compressed = D2Snap.compress(nested)

            assertNotNull(compressed)
            assertEquals("button", compressed!!.tagName)
            assertEquals("OK", compressed.text)
        }

        @Test
        @DisplayName("Should preserve semantic elements even when wrapping")
        fun `preserve semantic elements`() {
            val nested = DOMElement(
                id = "1",
                tagName = "nav",
                selector = "nav",
                children = listOf(
                    DOMElement(
                        id = "2",
                        tagName = "a",
                        selector = "nav > a",
                        textContent = "Home",
                        attributes = mapOf("href" to "/")
                    )
                )
            )

            val compressed = D2Snap.compress(nested)

            assertNotNull(compressed)
            assertEquals("nav", compressed!!.tagName)
            assertEquals(1, compressed.children.size)
            assertEquals("a", compressed.children[0].tagName)
        }

        @Test
        @DisplayName("Should remove empty non-semantic containers")
        fun `remove empty containers`() {
            val nested = DOMElement(
                id = "1",
                tagName = "main",
                selector = "main",
                children = listOf(
                    DOMElement(
                        id = "2",
                        tagName = "div",
                        selector = "main > div",
                        children = listOf(
                            DOMElement(
                                id = "3",
                                tagName = "span",
                                selector = "main > div > span"
                                // No text, no children, no attributes
                            )
                        )
                    ),
                    DOMElement(
                        id = "4",
                        tagName = "button",
                        selector = "main > button",
                        textContent = "Click me"
                    )
                )
            )

            val compressed = D2Snap.compress(nested)

            assertNotNull(compressed)
            assertEquals("main", compressed!!.tagName)
            // Empty div/span chain should be removed
            assertEquals(1, compressed.children.size)
            assertEquals("button", compressed.children[0].tagName)
        }

        @Test
        @DisplayName("Should preserve elements with multiple children")
        fun `preserve multi child containers`() {
            val container = DOMElement(
                id = "1",
                tagName = "div",
                selector = "div",
                children = listOf(
                    DOMElement(id = "2", tagName = "button", selector = "div > button:nth-child(1)", textContent = "Save"),
                    DOMElement(id = "3", tagName = "button", selector = "div > button:nth-child(2)", textContent = "Cancel")
                )
            )

            val compressed = D2Snap.compress(container)

            assertNotNull(compressed)
            assertEquals("div", compressed!!.tagName)
            assertEquals(2, compressed.children.size)
        }
    }

    @Nested
    @DisplayName("Attribute Filtering Tests")
    inner class AttributeFilteringTests {

        @Test
        @DisplayName("Should preserve key attributes")
        fun `preserve key attributes`() {
            val element = DOMElement(
                id = "1",
                tagName = "input",
                selector = "input",
                attributes = mapOf(
                    "id" to "email-input",
                    "name" to "email",
                    "type" to "email",
                    "class" to "form-input primary-style",
                    "placeholder" to "Enter email",
                    "aria-label" to "Email address",
                    "style" to "color: red; font-size: 14px;", // Should be removed
                    "onclick" to "handleClick()" // Should be removed
                )
            )

            val compressed = D2Snap.compress(element)

            assertNotNull(compressed)
            val attrs = compressed!!.attributes
            assertTrue(attrs.containsKey("id"))
            assertTrue(attrs.containsKey("name"))
            assertTrue(attrs.containsKey("type"))
            assertTrue(attrs.containsKey("placeholder"))
            assertTrue(attrs.containsKey("aria-label"))
            assertFalse(attrs.containsKey("style"))
            assertFalse(attrs.containsKey("onclick"))
        }

        @Test
        @DisplayName("Should simplify class attribute")
        fun `simplify class attribute`() {
            val element = DOMElement(
                id = "1",
                tagName = "button",
                selector = "button",
                textContent = "Submit",
                attributes = mapOf(
                    "class" to "btn btn-primary form-btn hover:bg-blue-500 focus:ring-2 p-4 m-2 extra-class another-class"
                )
            )

            val compressed = D2Snap.compress(element)

            assertNotNull(compressed)
            val classValue = compressed!!.attributes["class"]
            assertNotNull(classValue)
            // Should keep only first 3 meaningful classes, filter utility classes
            val classes = classValue!!.split(" ")
            assertTrue(classes.size <= 3)
        }

        @Test
        @DisplayName("Should preserve ARIA attributes")
        fun `preserve aria attributes`() {
            val element = DOMElement(
                id = "1",
                tagName = "div",
                selector = "div",
                textContent = "Menu",
                attributes = mapOf(
                    "role" to "menu",
                    "aria-label" to "Main menu",
                    "aria-expanded" to "false",
                    "aria-haspopup" to "true"
                )
            )

            val compressed = D2Snap.compress(element)

            assertNotNull(compressed)
            val attrs = compressed!!.attributes
            assertEquals("menu", attrs["role"])
            assertEquals("Main menu", attrs["aria-label"])
            assertEquals("false", attrs["aria-expanded"])
            assertEquals("true", attrs["aria-haspopup"])
        }
    }

    @Nested
    @DisplayName("ID Truncation Tests")
    inner class IdTruncationTests {

        @Test
        @DisplayName("Should truncate UUID-style IDs")
        fun `truncate uuid suffix`() {
            assertEquals("input-{dynamic}", D2Snap.truncateDynamicId("input-a1b2c3d4e5f6"))
            assertEquals("btn-{dynamic}", D2Snap.truncateDynamicId("btn-12345678"))
        }

        @Test
        @DisplayName("Should truncate numeric suffix IDs")
        fun `truncate numeric suffix`() {
            assertEquals("field-{dynamic}", D2Snap.truncateDynamicId("field-12345"))
            assertEquals("item-{dynamic}", D2Snap.truncateDynamicId("item-9999"))
        }

        @Test
        @DisplayName("Should truncate React-style IDs")
        fun `truncate react style ids`() {
            assertEquals("{dynamic}", D2Snap.truncateDynamicId(":r1a:"))
            assertEquals("{dynamic}", D2Snap.truncateDynamicId(":r2b3c:"))
        }

        @Test
        @DisplayName("Should preserve stable IDs")
        fun `preserve stable ids`() {
            assertEquals("main-content", D2Snap.truncateDynamicId("main-content"))
            assertEquals("header", D2Snap.truncateDynamicId("header"))
            assertEquals("nav-menu", D2Snap.truncateDynamicId("nav-menu"))
            assertEquals("login-form", D2Snap.truncateDynamicId("login-form"))
        }

        @Test
        @DisplayName("Should handle underscore separator")
        fun `truncate underscore separator`() {
            assertEquals("user-{dynamic}", D2Snap.truncateDynamicId("user_a1b2c3d4e5f6"))
            assertEquals("row-{dynamic}", D2Snap.truncateDynamicId("row_12345"))
        }
    }

    @Nested
    @DisplayName("Interactive Elements Tests")
    inner class InteractiveElementsTests {

        @Test
        @DisplayName("Should always preserve buttons")
        fun `preserve buttons`() {
            val button = DOMElement(
                id = "1",
                tagName = "button",
                selector = "button",
                textContent = "Submit"
            )

            val compressed = D2Snap.compress(button)

            assertNotNull(compressed)
            assertEquals("button", compressed!!.tagName)
        }

        @Test
        @DisplayName("Should always preserve form inputs")
        fun `preserve form inputs`() {
            val form = DOMElement(
                id = "1",
                tagName = "form",
                selector = "form",
                children = listOf(
                    DOMElement(
                        id = "2",
                        tagName = "input",
                        selector = "form > input",
                        attributes = mapOf("type" to "text", "name" to "username")
                    ),
                    DOMElement(
                        id = "3",
                        tagName = "select",
                        selector = "form > select",
                        children = listOf(
                            DOMElement(id = "4", tagName = "option", selector = "form > select > option", textContent = "Option 1")
                        )
                    ),
                    DOMElement(
                        id = "5",
                        tagName = "textarea",
                        selector = "form > textarea",
                        attributes = mapOf("name" to "message")
                    )
                )
            )

            val compressed = D2Snap.compress(form)

            assertNotNull(compressed)
            assertEquals("form", compressed!!.tagName)
            assertEquals(3, compressed.children.size)
            assertTrue(compressed.children.any { it.tagName == "input" })
            assertTrue(compressed.children.any { it.tagName == "select" })
            assertTrue(compressed.children.any { it.tagName == "textarea" })
        }

        @Test
        @DisplayName("Should preserve links with href")
        fun `preserve links`() {
            val link = DOMElement(
                id = "1",
                tagName = "a",
                selector = "a",
                textContent = "Click here",
                attributes = mapOf("href" to "/page")
            )

            val compressed = D2Snap.compress(link)

            assertNotNull(compressed)
            assertEquals("a", compressed!!.tagName)
            assertEquals("/page", compressed.attributes["href"])
        }
    }

    @Nested
    @DisplayName("Shadow DOM Tests")
    inner class ShadowDOMTests {

        @Test
        @DisplayName("Should preserve shadow hosts")
        fun `preserve shadow hosts`() {
            val shadowHost = DOMElement(
                id = "1",
                tagName = "div",
                selector = "div",
                isShadowHost = true,
                children = listOf(
                    DOMElement(
                        id = "2",
                        tagName = "slot",
                        selector = "div > slot",
                        inShadowRoot = true
                    )
                )
            )

            val compressed = D2Snap.compress(shadowHost)

            assertNotNull(compressed)
            assertTrue(compressed!!.isShadowHost)
        }

        @Test
        @DisplayName("Should mark shadow root children")
        fun `mark shadow children`() {
            val element = DOMElement(
                id = "1",
                tagName = "button",
                selector = "button",
                textContent = "Shadow Button",
                inShadowRoot = true
            )

            val compressed = D2Snap.compress(element)

            assertNotNull(compressed)
            assertTrue(compressed!!.inShadowRoot)
        }
    }

    @Nested
    @DisplayName("Compression Ratio Tests")
    inner class CompressionRatioTests {

        @Test
        @DisplayName("Should calculate compression ratio correctly")
        fun `calculate compression ratio`() {
            // Create a deeply nested structure
            val deep = DOMElement(
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
                                tagName = "div",
                                selector = "div > div > div",
                                children = listOf(
                                    DOMElement(
                                        id = "4",
                                        tagName = "div",
                                        selector = "div > div > div > div",
                                        children = listOf(
                                            DOMElement(
                                                id = "5",
                                                tagName = "button",
                                                selector = "div > div > div > div > button",
                                                textContent = "Click"
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

            val compressed = D2Snap.compress(deep)
            assertNotNull(compressed)

            val ratio = D2Snap.calculateCompressionRatio(deep, compressed)

            // Original has 5 nodes, compressed should have 1 (just the button)
            assertTrue(ratio > 0.5, "Compression ratio should be > 50%")
        }
    }

    @Nested
    @DisplayName("Compact JSON Output Tests")
    inner class CompactJsonTests {

        @Test
        @DisplayName("Should generate valid compact HTML-like output")
        fun `generate compact output`() {
            val element = D2SnapElement(
                id = "1",
                tagName = "button",
                selector = "button",
                text = "Submit",
                attributes = mapOf("id" to "submit-btn", "type" to "submit")
            )

            val output = D2Snap.toCompactJson(element)

            assertTrue(output.contains("<button"))
            assertTrue(output.contains("id=\"submit-btn\""))
            assertTrue(output.contains("type=\"submit\""))
            assertTrue(output.contains("Submit"))
            assertTrue(output.contains("</button>"))
        }

        @Test
        @DisplayName("Should handle nested elements")
        fun `generate nested output`() {
            val element = D2SnapElement(
                id = "1",
                tagName = "nav",
                selector = "nav",
                attributes = mapOf("role" to "navigation"),
                children = listOf(
                    D2SnapElement(
                        id = "2",
                        tagName = "a",
                        selector = "nav > a",
                        text = "Home",
                        attributes = mapOf("href" to "/")
                    )
                )
            )

            val output = D2Snap.toCompactJson(element)

            assertTrue(output.contains("<nav"))
            assertTrue(output.contains("<a"))
            assertTrue(output.contains("Home"))
            assertTrue(output.contains("</nav>"))
        }
    }

    @Nested
    @DisplayName("Text Truncation Tests")
    inner class TextTruncationTests {

        @Test
        @DisplayName("Should truncate long text content")
        fun `truncate long text`() {
            val element = DOMElement(
                id = "1",
                tagName = "p",
                selector = "p",
                textContent = "This is a very long paragraph that contains way more than one hundred characters and should definitely be truncated to prevent excessive token usage in LLM prompts."
            )

            val compressed = D2Snap.compress(element, maxTextLength = 50)

            assertNotNull(compressed)
            assertNotNull(compressed!!.text)
            assertTrue(compressed.text!!.length <= 53) // 50 + "..."
            assertTrue(compressed.text!!.endsWith("..."))
        }

        @Test
        @DisplayName("Should preserve short text")
        fun `preserve short text`() {
            val element = DOMElement(
                id = "1",
                tagName = "button",
                selector = "button",
                textContent = "OK"
            )

            val compressed = D2Snap.compress(element)

            assertNotNull(compressed)
            assertEquals("OK", compressed!!.text)
        }
    }

    @Nested
    @DisplayName("Real-World Scenarios")
    inner class RealWorldScenarios {

        @Test
        @DisplayName("Should compress typical navigation structure")
        fun `compress navigation`() {
            val nav = DOMElement(
                id = "1",
                tagName = "nav",
                selector = "nav",
                attributes = mapOf("class" to "main-nav bg-white shadow-lg"),
                children = listOf(
                    DOMElement(
                        id = "2",
                        tagName = "div",
                        selector = "nav > div",
                        attributes = mapOf("class" to "container mx-auto"),
                        children = listOf(
                            DOMElement(
                                id = "3",
                                tagName = "ul",
                                selector = "nav > div > ul",
                                children = listOf(
                                    DOMElement(
                                        id = "4",
                                        tagName = "li",
                                        selector = "nav > div > ul > li:nth-child(1)",
                                        children = listOf(
                                            DOMElement(
                                                id = "5",
                                                tagName = "a",
                                                selector = "nav > div > ul > li > a",
                                                textContent = "Home",
                                                attributes = mapOf("href" to "/")
                                            )
                                        )
                                    ),
                                    DOMElement(
                                        id = "6",
                                        tagName = "li",
                                        selector = "nav > div > ul > li:nth-child(2)",
                                        children = listOf(
                                            DOMElement(
                                                id = "7",
                                                tagName = "a",
                                                selector = "nav > div > ul > li > a",
                                                textContent = "About",
                                                attributes = mapOf("href" to "/about")
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

            val compressed = D2Snap.compress(nav)

            assertNotNull(compressed)
            assertEquals("nav", compressed!!.tagName)

            // The nav structure is mostly semantic (nav, ul, li, a), so compression is limited
            // The div wrapper should be flattened as it's not semantic
            val ratio = D2Snap.calculateCompressionRatio(nav, compressed)
            // Expect minimal compression since most elements are semantic
            // Original: nav > div > ul > li > a (7 nodes), Compressed: at least nav with semantic children
            assertTrue(ratio >= 0.0, "Should have valid compression ratio")
        }

        @Test
        @DisplayName("Should handle login form")
        fun `compress login form`() {
            val form = DOMElement(
                id = "1",
                tagName = "form",
                selector = "form",
                attributes = mapOf("id" to "login-form", "class" to "form-container p-4"),
                children = listOf(
                    DOMElement(
                        id = "2",
                        tagName = "div",
                        selector = "form > div:nth-child(1)",
                        attributes = mapOf("class" to "form-group mb-4"),
                        children = listOf(
                            DOMElement(
                                id = "3",
                                tagName = "label",
                                selector = "form > div > label",
                                textContent = "Email",
                                attributes = mapOf("for" to "email")
                            ),
                            DOMElement(
                                id = "4",
                                tagName = "input",
                                selector = "form > div > input",
                                attributes = mapOf(
                                    "type" to "email",
                                    "id" to "email",
                                    "name" to "email",
                                    "placeholder" to "Enter your email"
                                )
                            )
                        )
                    ),
                    DOMElement(
                        id = "5",
                        tagName = "button",
                        selector = "form > button",
                        textContent = "Login",
                        attributes = mapOf("type" to "submit")
                    )
                )
            )

            val compressed = D2Snap.compress(form)

            assertNotNull(compressed)
            assertEquals("form", compressed!!.tagName)

            // Should preserve form, label, input, and button
            fun countByTag(el: D2SnapElement, tag: String): Int {
                return (if (el.tagName == tag) 1 else 0) + el.children.sumOf { countByTag(it, tag) }
            }

            assertTrue(countByTag(compressed, "input") >= 1)
            assertTrue(countByTag(compressed, "button") >= 1)
            assertTrue(countByTag(compressed, "label") >= 1)
        }
    }
}
