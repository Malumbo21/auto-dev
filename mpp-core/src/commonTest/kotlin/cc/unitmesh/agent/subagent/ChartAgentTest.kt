package cc.unitmesh.agent.subagent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test for ChartAgent data classes and schema
 */
class ChartAgentTest {

    // ============= Context Tests =============

    @Test
    fun testChartContextCreation() {
        val context = ChartContext(
            description = "Create a column chart showing quarterly sales: Q1=100, Q2=150, Q3=200 with title 'Quarterly Sales Report'"
        )

        assertTrue(context.description.contains("quarterly sales"))
        assertTrue(context.description.contains("column chart"))
    }

    @Test
    fun testChartContextWithPieDescription() {
        val context = ChartContext(
            description = """
                Create a pie chart showing distribution:
                Category A: 30%
                Category B: 45%
                Category C: 25%
                Title: Distribution
            """.trimIndent()
        )

        assertTrue(context.description.contains("pie chart"))
        assertTrue(context.description.contains("Category A"))
        assertTrue(context.description.contains("30%"))
    }

    @Test
    fun testChartContextWithLineDescription() {
        val context = ChartContext(
            description = """
                Create a line chart for monthly financials:
                Month,Revenue,Expenses
                Jan,1000,800
                Feb,1200,850
                Mar,1100,900
            """.trimIndent()
        )

        assertTrue(context.description.contains("line chart"))
        assertTrue(context.description.contains("Revenue"))
        assertTrue(context.description.contains("Expenses"))
    }

    // ============= Schema Tests =============

    @Test
    fun testChartAgentSchemaExampleUsage() {
        val example = ChartAgentSchema.getExampleUsage("chart-agent")

        assertTrue(example.contains("/chart-agent"))
        assertTrue(example.contains("description="))
    }

    @Test
    fun testChartAgentSchemaToJsonSchema() {
        val jsonSchema = ChartAgentSchema.toJsonSchema()

        assertNotNull(jsonSchema)
        val schemaString = jsonSchema.toString()
        assertTrue(schemaString.contains("description"))
    }

    // ============= Validation Tests =============

    @Test
    fun testChartContextWithJsonDataDescription() {
        val context = ChartContext(
            description = """
                Create a column chart with this data:
                {
                    "labels": ["Q1", "Q2", "Q3", "Q4"],
                    "values": [100, 150, 200, 180]
                }
            """.trimIndent()
        )

        assertTrue(context.description.contains("labels"))
        assertTrue(context.description.contains("values"))
    }

    @Test
    fun testChartContextWithCsvDataDescription() {
        val context = ChartContext(
            description = """
                Create a horizontal bar chart with this CSV data:
                Name,Value,Color
                Sales,100,#1E88E5
                Marketing,80,#43A047
                Development,120,#FB8C00
            """.trimIndent()
        )

        assertTrue(context.description.contains("horizontal bar chart"))
        assertTrue(context.description.contains("Sales"))
        assertTrue(context.description.contains("#1E88E5"))
    }

    // ============= Edge Cases =============

    @Test
    fun testChartContextWithLongDescription() {
        val longData = (1..100).joinToString("\n") { "Item $it: ${it * 10}" }

        val context = ChartContext(
            description = "Create a column chart with this data:\n$longData"
        )

        assertTrue(context.description.length > 500)
        assertTrue(context.description.contains("Item 1"))
        assertTrue(context.description.contains("Item 100"))
    }
}

