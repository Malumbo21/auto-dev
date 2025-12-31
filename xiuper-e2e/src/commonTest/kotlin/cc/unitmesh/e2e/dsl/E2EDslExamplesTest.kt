package cc.unitmesh.e2e.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test that validates the DSL examples can be parsed correctly
 */
class E2EDslExamplesTest {

    private val parser = E2EDslParser()

    @Test
    fun testParseUserLoginFlow() {
        val dsl = """
            scenario "User Login Flow" {
                description "Test standard user login with valid credentials"
                url "https://example.com/login"
                tags ["login", "auth", "smoke"]
                priority high

                step "Enter username" {
                    type #1 "testuser@example.com"
                    expect "Username field should be filled"
                }

                step "Enter password" {
                    type #2 "SecurePassword123" clearFirst
                    expect "Password field should be filled"
                }

                step "Click login button" {
                    click #3
                    expect "Login form should be submitted"
                    timeout 10000
                }

                step "Wait for dashboard" {
                    wait urlContains "/dashboard"
                    timeout 15000
                }

                step "Verify welcome message" {
                    assert #4 textContains "Welcome"
                    expect "User should see welcome message"
                }
            }
        """.trimIndent()

        val result = parser.parse(dsl)

        assertTrue(result.success, "Parse should succeed: ${result.errors}")
        val scenario = result.scenario!!
        assertEquals("User Login Flow", scenario.name)
        assertEquals("Test standard user login with valid credentials", scenario.description)
        assertEquals("https://example.com/login", scenario.startUrl)
        assertEquals(listOf("login", "auth", "smoke"), scenario.tags)
        assertEquals(5, scenario.steps.size)
    }

    @Test
    fun testParseProductSearchAndFilter() {
        val dsl = """
            scenario "Product Search and Filter" {
                description "Test product search functionality with filters"
                url "https://example.com/products"
                tags ["search", "products", "e2e"]
                priority medium

                step "Enter search term" {
                    type #1 "laptop" pressEnter
                }

                step "Wait for results" {
                    wait visible #2
                    timeout 5000
                }

                step "Apply price filter" {
                    click #3
                }

                step "Select price range" {
                    select #4 value "500-1000"
                }

                step "Apply filter" {
                    click #5
                }

                step "Verify filtered results" {
                    assert #6 visible
                }
            }
        """.trimIndent()

        val result = parser.parse(dsl)

        assertTrue(result.success, "Parse should succeed: ${result.errors}")
        val scenario = result.scenario!!
        assertEquals("Product Search and Filter", scenario.name)
        assertEquals(6, scenario.steps.size)
    }

    @Test
    fun testParseKeyboardShortcuts() {
        val dsl = """
            scenario "Keyboard Shortcuts" {
                description "Test keyboard shortcuts functionality"
                url "https://example.com/editor"
                tags ["keyboard", "shortcuts", "accessibility"]
                priority low

                step "Focus editor" {
                    click #1
                }

                step "Type content" {
                    type #1 "Hello World"
                }

                step "Select all with Ctrl+A" {
                    pressKey "a" ctrl
                }

                step "Copy with Ctrl+C" {
                    pressKey "c" ctrl
                }

                step "Paste with Ctrl+V" {
                    pressKey "v" ctrl
                }

                step "Save with Ctrl+S" {
                    pressKey "s" ctrl
                }
            }
        """.trimIndent()

        val result = parser.parse(dsl)

        assertTrue(result.success, "Parse should succeed: ${result.errors}")
        val scenario = result.scenario!!
        assertEquals("Keyboard Shortcuts", scenario.name)
        assertEquals(6, scenario.steps.size)
    }
}

