package cc.unitmesh.agent.subagent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Test for ChartAgent data classes and schema
 */
class ChartAgentTest {

    // ============= Context Tests =============

    @Test
    fun testChartContextCreation() {
        val context = ChartContext(
            data = "Sales: Q1=100, Q2=150, Q3=200",
            chartType = "column",
            description = "Show quarterly sales",
            title = "Quarterly Sales Report"
        )

        assertEquals("Sales: Q1=100, Q2=150, Q3=200", context.data)
        assertEquals("column", context.chartType)
        assertEquals("Show quarterly sales", context.description)
        assertEquals("Quarterly Sales Report", context.title)
    }

    @Test
    fun testChartContextDefaults() {
        val context = ChartContext(
            data = "Simple data"
        )

        assertEquals("Simple data", context.data)
        assertEquals(null, context.chartType)
        assertEquals(null, context.description)
        assertEquals(null, context.title)
    }

    @Test
    fun testChartContextWithPieData() {
        val context = ChartContext(
            data = """
                Category A: 30%
                Category B: 45%
                Category C: 25%
            """.trimIndent(),
            chartType = "pie",
            title = "Distribution"
        )

        assertEquals("pie", context.chartType)
        assertTrue(context.data.contains("Category A"))
        assertTrue(context.data.contains("30%"))
    }

    @Test
    fun testChartContextWithLineData() {
        val context = ChartContext(
            data = """
                Month,Revenue,Expenses
                Jan,1000,800
                Feb,1200,850
                Mar,1100,900
            """.trimIndent(),
            chartType = "line",
            title = "Monthly Financials"
        )

        assertEquals("line", context.chartType)
        assertTrue(context.data.contains("Revenue"))
        assertTrue(context.data.contains("Expenses"))
    }

    // ============= Schema Tests =============

    @Test
    fun testChartAgentSchemaExampleUsage() {
        val example = ChartAgentSchema.getExampleUsage("chart-agent")

        assertTrue(example.contains("/chart-agent"))
        assertTrue(example.contains("data="))
    }

    @Test
    fun testChartAgentSchemaToJsonSchema() {
        val jsonSchema = ChartAgentSchema.toJsonSchema()

        assertNotNull(jsonSchema)
        val schemaString = jsonSchema.toString()
        assertTrue(schemaString.contains("data"))
        assertTrue(schemaString.contains("chartType"))
    }

    // ============= Validation Tests =============

    @Test
    fun testChartContextWithJsonData() {
        val jsonData = """
            {
                "labels": ["Q1", "Q2", "Q3", "Q4"],
                "values": [100, 150, 200, 180]
            }
        """.trimIndent()
        
        val context = ChartContext(
            data = jsonData,
            chartType = "column"
        )

        assertTrue(context.data.contains("labels"))
        assertTrue(context.data.contains("values"))
    }

    @Test
    fun testChartContextWithCsvData() {
        val csvData = """
            Name,Value,Color
            Sales,100,#1E88E5
            Marketing,80,#43A047
            Development,120,#FB8C00
        """.trimIndent()
        
        val context = ChartContext(
            data = csvData,
            chartType = "row"
        )

        assertEquals("row", context.chartType)
        assertTrue(context.data.contains("Sales"))
        assertTrue(context.data.contains("#1E88E5"))
    }

    // ============= Edge Cases =============

    @Test
    fun testChartContextWithEmptyOptionalFields() {
        val context = ChartContext(
            data = "Some data",
            chartType = null,
            description = null,
            title = null
        )

        assertEquals("Some data", context.data)
        assertEquals(null, context.chartType)
    }

    @Test
    fun testChartContextWithLongData() {
        val longData = (1..100).joinToString("\n") { "Item $it: ${it * 10}" }
        
        val context = ChartContext(
            data = longData,
            chartType = "column"
        )

        assertTrue(context.data.length > 500)
        assertTrue(context.data.contains("Item 1"))
        assertTrue(context.data.contains("Item 100"))
    }
}

