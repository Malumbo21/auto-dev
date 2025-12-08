package cc.unitmesh.devins.ui.compose.sketch.chart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChartParserTest {

    @Test
    fun `should parse pie chart YAML from ChartAgent`() {
        val yamlContent = """
            type: pie
            title: "Department Distribution"
            data:
              items:
                - label: "Sales"
                  value: 30
                  color: "#1E88E5"
                - label: "Marketing"
                  value: 25
                  color: "#43A047"
                - label: "Development"
                  value: 45
                  color: "#FB8C00"
        """.trimIndent()

        val result = ChartParser.parse(yamlContent)

        assertNotNull(result)
        assertEquals(ChartType.PIE, result.type)
        assertEquals("Department Distribution", result.title)

        val pieData = result.data as ChartDataContent.PieData
        assertEquals(3, pieData.items.size)
        assertEquals("Sales", pieData.items[0].label)
        assertEquals(30.0, pieData.items[0].value)
        assertEquals("#1E88E5", pieData.items[0].color)
    }

    @Test
    fun `should parse line chart YAML from ChartAgent`() {
        val yamlContent = """
            type: line
            title: "Monthly Trends"
            data:
              lines:
                - label: "Revenue"
                  values: [100, 150, 200, 180, 220]
                  color: "#1E88E5"
                - label: "Expenses"
                  values: [80, 90, 120, 100, 130]
                  color: "#E53935"
        """.trimIndent()

        val result = ChartParser.parse(yamlContent)

        assertNotNull(result)
        assertEquals(ChartType.LINE, result.type)
        assertEquals("Monthly Trends", result.title)

        val lineData = result.data as ChartDataContent.LineData
        assertEquals(2, lineData.lines.size)
        assertEquals("Revenue", lineData.lines[0].label)
        assertEquals(listOf(100.0, 150.0, 200.0, 180.0, 220.0), lineData.lines[0].values)
        assertEquals("#1E88E5", lineData.lines[0].color)
    }

    @Test
    fun `should parse column chart YAML from ChartAgent`() {
        val yamlContent = """
            type: column
            title: "Quarterly Sales"
            data:
              bars:
                - label: "Q1"
                  values:
                    - value: 100
                      color: "#1E88E5"
                - label: "Q2"
                  values:
                    - value: 150
                      color: "#43A047"
        """.trimIndent()

        val result = ChartParser.parse(yamlContent)

        assertNotNull(result)
        assertEquals(ChartType.COLUMN, result.type)
        assertEquals("Quarterly Sales", result.title)

        val columnData = result.data as ChartDataContent.ColumnData
        assertEquals(2, columnData.bars.size)
        assertEquals("Q1", columnData.bars[0].label)
        assertEquals(100.0, columnData.bars[0].values[0].value)
    }

    @Test
    fun `should parse row chart YAML from ChartAgent`() {
        val yamlContent = """
            type: row
            title: "Rankings"
            data:
              bars:
                - label: "Item A"
                  values:
                    - value: 85
                - label: "Item B"
                  values:
                    - value: 72
        """.trimIndent()

        val result = ChartParser.parse(yamlContent)

        assertNotNull(result)
        assertEquals(ChartType.ROW, result.type)
        assertEquals("Rankings", result.title)

        val rowData = result.data as ChartDataContent.RowData
        assertEquals(2, rowData.bars.size)
        assertEquals("Item A", rowData.bars[0].label)
        assertEquals(85.0, rowData.bars[0].values[0].value)
    }

    @Test
    fun `should parse pie chart without colors`() {
        val yamlContent = """
            type: pie
            title: "Simple Pie"
            data:
              items:
                - label: "A"
                  value: 50
                - label: "B"
                  value: 50
        """.trimIndent()

        val result = ChartParser.parse(yamlContent)

        assertNotNull(result)
        assertEquals(ChartType.PIE, result.type)

        val pieData = result.data as ChartDataContent.PieData
        assertEquals(2, pieData.items.size)
        assertNull(pieData.items[0].color)
    }

    @Test
    fun `should return null for invalid YAML`() {
        val invalidYaml = "this is not valid yaml: [["

        val result = ChartParser.parse(invalidYaml)

        assertNull(result)
    }

    @Test
    fun `should return null for empty content`() {
        val result = ChartParser.parse("")

        assertNull(result)
    }
}

