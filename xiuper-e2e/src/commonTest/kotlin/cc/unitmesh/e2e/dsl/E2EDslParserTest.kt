package cc.unitmesh.e2e.dsl

import cc.unitmesh.agent.e2etest.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class E2EDslParserTest {

    private val parser = E2EDslParser()

    @Test
    fun testParseSimpleScenario() {
        val dsl = """
            scenario "Login Test" {
                description "Test user login"
                url "https://example.com/login"
                priority high
                
                step "Enter username" {
                    type #1 "testuser"
                }
                
                step "Click login" {
                    click #2
                }
            }
        """.trimIndent()

        val result = parser.parse(dsl)

        assertTrue(result.success, "Parse should succeed: ${result.errors}")
        assertNotNull(result.scenario)
        assertEquals("Login Test", result.scenario?.name)
        assertEquals("Test user login", result.scenario?.description)
        assertEquals("https://example.com/login", result.scenario?.startUrl)
        assertEquals(TestPriority.HIGH, result.scenario?.priority)
        assertEquals(2, result.scenario?.steps?.size)
    }

    @Test
    fun testParseClickAction() {
        val dsl = """
            scenario "Click Test" {
                description "Test click actions"
                url "https://example.com"
                
                step "Single click" {
                    click #1
                }
                
                step "Double click" {
                    click #2 double
                }
                
                step "Right click" {
                    click #3 right
                }
            }
        """.trimIndent()

        val result = parser.parse(dsl)

        assertTrue(result.success)
        val steps = result.scenario?.steps ?: emptyList()
        assertEquals(3, steps.size)

        val click1 = steps[0].action as TestAction.Click
        assertEquals(1, click1.targetId)
        assertEquals(MouseButton.LEFT, click1.button)
        assertEquals(1, click1.clickCount)

        val click2 = steps[1].action as TestAction.Click
        assertEquals(2, click2.targetId)
        assertEquals(2, click2.clickCount)

        val click3 = steps[2].action as TestAction.Click
        assertEquals(3, click3.targetId)
        assertEquals(MouseButton.RIGHT, click3.button)
    }

    @Test
    fun testParseTypeAction() {
        val dsl = """
            scenario "Type Test" {
                description "Test type actions"
                url "https://example.com"
                
                step "Simple type" {
                    type #1 "hello world"
                }
                
                step "Type with options" {
                    type #2 "password" clearFirst pressEnter
                }
            }
        """.trimIndent()

        val result = parser.parse(dsl)

        assertTrue(result.success)
        val steps = result.scenario?.steps ?: emptyList()

        val type1 = steps[0].action as TestAction.Type
        assertEquals(1, type1.targetId)
        assertEquals("hello world", type1.text)
        assertEquals(false, type1.clearFirst)
        assertEquals(false, type1.pressEnter)

        val type2 = steps[1].action as TestAction.Type
        assertEquals(2, type2.targetId)
        assertEquals("password", type2.text)
        assertEquals(true, type2.clearFirst)
        assertEquals(true, type2.pressEnter)
    }

    @Test
    fun testParseScrollAction() {
        val dsl = """
            scenario "Scroll Test" {
                description "Test scroll actions"
                url "https://example.com"
                
                step "Scroll down" {
                    scroll down
                }
                
                step "Scroll up with amount" {
                    scroll up 500
                }
            }
        """.trimIndent()

        val result = parser.parse(dsl)

        assertTrue(result.success)
        val steps = result.scenario?.steps ?: emptyList()

        val scroll1 = steps[0].action as TestAction.Scroll
        assertEquals(ScrollDirection.DOWN, scroll1.direction)

        val scroll2 = steps[1].action as TestAction.Scroll
        assertEquals(ScrollDirection.UP, scroll2.direction)
        assertEquals(500, scroll2.amount)
    }

    @Test
    fun testParseWaitAction() {
        val dsl = """
            scenario "Wait Test" {
                description "Test wait actions"
                url "https://example.com"
                
                step "Wait duration" {
                    wait duration 2000
                }
                
                step "Wait for element" {
                    wait visible #5
                }
                
                step "Wait for text" {
                    wait textPresent "Loading complete"
                }
            }
        """.trimIndent()

        val result = parser.parse(dsl)

        assertTrue(result.success)
        val steps = result.scenario?.steps ?: emptyList()

        val wait1 = steps[0].action as TestAction.Wait
        assertTrue(wait1.condition is WaitCondition.Duration)
        assertEquals(2000L, (wait1.condition as WaitCondition.Duration).ms)

        val wait2 = steps[1].action as TestAction.Wait
        assertTrue(wait2.condition is WaitCondition.ElementVisible)
        assertEquals(5, (wait2.condition as WaitCondition.ElementVisible).targetId)

        val wait3 = steps[2].action as TestAction.Wait
        assertTrue(wait3.condition is WaitCondition.TextPresent)
        assertEquals("Loading complete", (wait3.condition as WaitCondition.TextPresent).text)
    }

    @Test
    fun testParseAssertAction() {
        val dsl = """
            scenario "Assert Test" {
                description "Test assert actions"
                url "https://example.com"

                step "Assert visible" {
                    assert #1 visible
                }

                step "Assert text contains" {
                    assert #2 textContains "Welcome"
                }

                step "Assert has class" {
                    assert #3 hasClass "active"
                }
            }
        """.trimIndent()

        val result = parser.parse(dsl)

        assertTrue(result.success)
        val steps = result.scenario?.steps ?: emptyList()

        val assert1 = steps[0].action as TestAction.Assert
        assertEquals(1, assert1.targetId)
        assertTrue(assert1.assertion is AssertionType.Visible)

        val assert2 = steps[1].action as TestAction.Assert
        assertEquals(2, assert2.targetId)
        assertTrue(assert2.assertion is AssertionType.TextContains)
        assertEquals("Welcome", (assert2.assertion as AssertionType.TextContains).text)

        val assert3 = steps[2].action as TestAction.Assert
        assertEquals(3, assert3.targetId)
        assertTrue(assert3.assertion is AssertionType.HasClass)
        assertEquals("active", (assert3.assertion as AssertionType.HasClass).className)
    }

    @Test
    fun testParseNavigationActions() {
        val dsl = """
            scenario "Navigation Test" {
                description "Test navigation actions"
                url "https://example.com"

                step "Navigate to page" {
                    navigate "https://example.com/page2"
                }

                step "Go back" {
                    goBack
                }

                step "Go forward" {
                    goForward
                }

                step "Refresh" {
                    refresh
                }
            }
        """.trimIndent()

        val result = parser.parse(dsl)

        assertTrue(result.success)
        val steps = result.scenario?.steps ?: emptyList()
        assertEquals(4, steps.size)

        assertTrue(steps[0].action is TestAction.Navigate)
        assertEquals("https://example.com/page2", (steps[0].action as TestAction.Navigate).url)

        assertTrue(steps[1].action is TestAction.GoBack)
        assertTrue(steps[2].action is TestAction.GoForward)
        assertTrue(steps[3].action is TestAction.Refresh)
    }

    @Test
    fun testParsePressKeyAction() {
        val dsl = """
            scenario "Key Press Test" {
                description "Test key press actions"
                url "https://example.com"

                step "Press Enter" {
                    pressKey "Enter"
                }

                step "Press Ctrl+S" {
                    pressKey "s" ctrl
                }

                step "Press Ctrl+Shift+P" {
                    pressKey "p" ctrl shift
                }
            }
        """.trimIndent()

        val result = parser.parse(dsl)

        assertTrue(result.success)
        val steps = result.scenario?.steps ?: emptyList()

        val key1 = steps[0].action as TestAction.PressKey
        assertEquals("Enter", key1.key)
        assertTrue(key1.modifiers.isEmpty())

        val key2 = steps[1].action as TestAction.PressKey
        assertEquals("s", key2.key)
        assertTrue(key2.modifiers.contains(KeyModifier.CTRL))

        val key3 = steps[2].action as TestAction.PressKey
        assertEquals("p", key3.key)
        assertTrue(key3.modifiers.contains(KeyModifier.CTRL))
        assertTrue(key3.modifiers.contains(KeyModifier.SHIFT))
    }

    @Test
    fun testParseSelectAction() {
        val dsl = """
            scenario "Select Test" {
                description "Test select actions"
                url "https://example.com"

                step "Select by value" {
                    select #1 value "option1"
                }

                step "Select by label" {
                    select #2 label "Option Two"
                }

                step "Select by index" {
                    select #3 index 2
                }
            }
        """.trimIndent()

        val result = parser.parse(dsl)

        assertTrue(result.success)
        val steps = result.scenario?.steps ?: emptyList()

        val select1 = steps[0].action as TestAction.Select
        assertEquals(1, select1.targetId)
        assertEquals("option1", select1.value)

        val select2 = steps[1].action as TestAction.Select
        assertEquals(2, select2.targetId)
        assertEquals("Option Two", select2.label)

        val select3 = steps[2].action as TestAction.Select
        assertEquals(3, select3.targetId)
        assertEquals(2, select3.index)
    }

    @Test
    fun testParseStepWithExpectAndTimeout() {
        val dsl = """
            scenario "Step Options Test" {
                description "Test step options"
                url "https://example.com"

                step "Click with expect" {
                    click #1
                    expect "Button should be clicked"
                    timeout 10000
                    retry 3
                }
            }
        """.trimIndent()

        val result = parser.parse(dsl)

        assertTrue(result.success)
        val step = result.scenario?.steps?.first()
        assertNotNull(step)
        assertEquals("Button should be clicked", step.expectedOutcome)
        assertEquals(10000L, step.timeoutMs)
        assertEquals(3, step.retryCount)
    }

    @Test
    fun testParseTags() {
        val dsl = """
            scenario "Tags Test" {
                description "Test tags parsing"
                url "https://example.com"
                tags ["login", "auth", "smoke"]

                step "Click" {
                    click #1
                }
            }
        """.trimIndent()

        val result = parser.parse(dsl)

        assertTrue(result.success)
        assertEquals(listOf("login", "auth", "smoke"), result.scenario?.tags)
    }
}

