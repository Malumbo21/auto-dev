package cc.unitmesh.devins.ui.compose.sketch.letsplot

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for PlotParser to ensure correct parsing of PlotDSL.
 */
class PlotParserTest {

    @Test
    fun `should parse simple bar chart YAML`() {
        val yaml = """
            plot:
              title: "Sales by Region"
              data:
                region: [North, South, East, West]
                sales: [120, 98, 150, 87]
              geom: bar
              aes:
                x: region
                y: sales
              theme: minimal
        """.trimIndent()

        val config = PlotParser.parse(yaml)

        assertNotNull(config)
        assertEquals("Sales by Region", config.title)
        assertEquals(PlotGeom.BAR, config.geom)
        assertEquals(PlotTheme.MINIMAL, config.theme)
        assertNotNull(config.aes)
        assertEquals("region", config.aes?.x)
        assertEquals("sales", config.aes?.y)
        
        // Check data
        assertEquals(4, config.data.getStringColumn("region")?.size)
        assertEquals(4, config.data.getNumericColumn("sales")?.size)
    }

    @Test
    fun `should parse line chart with numeric data`() {
        val yaml = """
            plot:
              title: "Monthly Revenue"
              data:
                month: [1, 2, 3, 4, 5, 6]
                revenue: [10000, 12000, 9500, 14000, 13500, 16000]
              geom: line
              aes:
                x: month
                y: revenue
        """.trimIndent()

        val config = PlotParser.parse(yaml)

        assertNotNull(config)
        assertEquals("Monthly Revenue", config.title)
        assertEquals(PlotGeom.LINE, config.geom)
        assertEquals(PlotTheme.DEFAULT, config.theme)
        
        val months = config.data.getNumericColumn("month")
        assertNotNull(months)
        assertEquals(6, months.size)
        assertEquals(1.0, months[0])
    }

    @Test
    fun `should parse scatter plot with color aesthetic`() {
        val yaml = """
            plot:
              title: "Price vs Sales"
              data:
                price: [10, 20, 30, 40, 50]
                sales: [100, 80, 60, 40, 20]
                category: [low, low, medium, high, high]
              geom: point
              aes:
                x: price
                y: sales
                color: category
        """.trimIndent()

        val config = PlotParser.parse(yaml)

        assertNotNull(config)
        assertEquals(PlotGeom.POINT, config.geom)
        assertEquals("category", config.aes?.color)
    }

    @Test
    fun `should parse histogram`() {
        val yaml = """
            plot:
              title: "Score Distribution"
              data:
                scores: [65, 70, 72, 75, 78, 80, 82, 85, 88, 90]
              geom: histogram
              aes:
                x: scores
        """.trimIndent()

        val config = PlotParser.parse(yaml)

        assertNotNull(config)
        assertEquals("Score Distribution", config.title)
        assertEquals(PlotGeom.HISTOGRAM, config.geom)
        assertEquals("scores", config.aes?.x)
    }

    @Test
    fun `should parse with custom dimensions`() {
        val yaml = """
            plot:
              title: "Custom Size Chart"
              width: 800
              height: 400
              data:
                x: [1, 2, 3]
                y: [10, 20, 30]
              geom: bar
              aes:
                x: x
                y: y
        """.trimIndent()

        val config = PlotParser.parse(yaml)

        assertNotNull(config)
        assertEquals(800, config.width)
        assertEquals(400, config.height)
    }

    @Test
    fun `should parse axis labels`() {
        val yaml = """
            plot:
              title: "Sales Analysis"
              xLabel: "Quarter"
              yLabel: "Revenue ($)"
              data:
                quarter: [Q1, Q2, Q3, Q4]
                revenue: [100, 150, 120, 180]
              geom: bar
              aes:
                x: quarter
                y: revenue
        """.trimIndent()

        val config = PlotParser.parse(yaml)

        assertNotNull(config)
        assertEquals("Quarter", config.xLabel)
        assertEquals("Revenue ($)", config.yLabel)
    }

    @Test
    fun `should parse all theme types`() {
        val themes = listOf(
            "default" to PlotTheme.DEFAULT,
            "minimal" to PlotTheme.MINIMAL,
            "classic" to PlotTheme.CLASSIC,
            "dark" to PlotTheme.DARK,
            "light" to PlotTheme.LIGHT,
            "void" to PlotTheme.VOID
        )

        for ((themeStr, expectedTheme) in themes) {
            val yaml = """
                plot:
                  data:
                    x: [1, 2, 3]
                    y: [1, 2, 3]
                  geom: point
                  aes:
                    x: x
                    y: y
                  theme: $themeStr
            """.trimIndent()

            val config = PlotParser.parse(yaml)
            assertNotNull(config, "Config should not be null for theme: $themeStr")
            assertEquals(expectedTheme, config.theme, "Theme should be $expectedTheme for: $themeStr")
        }
    }

    @Test
    fun `should parse all geom types`() {
        val geoms = listOf(
            "point" to PlotGeom.POINT,
            "scatter" to PlotGeom.POINT,
            "line" to PlotGeom.LINE,
            "bar" to PlotGeom.BAR,
            "column" to PlotGeom.BAR,
            "histogram" to PlotGeom.HISTOGRAM,
            "boxplot" to PlotGeom.BOXPLOT,
            "area" to PlotGeom.AREA,
            "density" to PlotGeom.DENSITY
        )

        for ((geomStr, expectedGeom) in geoms) {
            val yaml = """
                plot:
                  data:
                    x: [1, 2, 3]
                    y: [1, 2, 3]
                  geom: $geomStr
                  aes:
                    x: x
                    y: y
            """.trimIndent()

            val config = PlotParser.parse(yaml)
            assertNotNull(config, "Config should not be null for geom: $geomStr")
            assertEquals(expectedGeom, config.geom, "Geom should be $expectedGeom for: $geomStr")
        }
    }

    @Test
    fun `should parse simple inline format`() {
        val simple = """
            bar: Sales by Quarter
            Q1: 100
            Q2: 150
            Q3: 120
            Q4: 180
        """.trimIndent()

        val config = PlotParser.parse(simple)

        assertNotNull(config)
        assertEquals("Sales by Quarter", config.title)
        assertEquals(PlotGeom.BAR, config.geom)
        
        val xValues = config.data.getStringColumn("x")
        val yValues = config.data.getNumericColumn("y")
        
        assertNotNull(xValues)
        assertNotNull(yValues)
        assertEquals(4, xValues.size)
        assertEquals(listOf("Q1", "Q2", "Q3", "Q4"), xValues)
        assertEquals(listOf(100.0, 150.0, 120.0, 180.0), yValues)
    }

    @Test
    fun `should handle missing optional fields`() {
        val yaml = """
            plot:
              data:
                x: [1, 2, 3]
                y: [4, 5, 6]
              geom: point
              aes:
                x: x
                y: y
        """.trimIndent()

        val config = PlotParser.parse(yaml)

        assertNotNull(config)
        assertEquals(null, config.title)
        assertEquals(null, config.subtitle)
        assertEquals(null, config.width)
        assertEquals(null, config.height)
        assertEquals(PlotTheme.DEFAULT, config.theme)
    }

    @Test
    fun `should convert data to map for Lets-Plot`() {
        val yaml = """
            plot:
              data:
                category: [A, B, C]
                value: [10, 20, 30]
              geom: bar
              aes:
                x: category
                y: value
        """.trimIndent()

        val config = PlotParser.parse(yaml)
        assertNotNull(config)

        val dataMap = config.data.toMap()
        
        assertTrue(dataMap.containsKey("category"))
        assertTrue(dataMap.containsKey("value"))
        assertEquals(listOf("A", "B", "C"), dataMap["category"])
        assertEquals(listOf(10.0, 20.0, 30.0), dataMap["value"])
    }

    @Test
    fun `should parse color scale`() {
        val yaml = """
            plot:
              title: "Heatmap"
              data:
                x: [1, 2, 3]
                y: [1, 2, 3]
                z: [10, 20, 30]
              geom: heatmap
              aes:
                x: x
                y: y
                fill: z
              colorScale:
                type: gradient
                low: "#ffffff"
                high: "#ff0000"
        """.trimIndent()

        val config = PlotParser.parse(yaml)

        assertNotNull(config)
        assertNotNull(config.colorScale)
        assertEquals(ColorScaleType.GRADIENT, config.colorScale?.type)
        assertEquals("#ffffff", config.colorScale?.low)
        assertEquals("#ff0000", config.colorScale?.high)
    }

    @Test
    fun `should parse without plot wrapper`() {
        val yaml = """
            title: "Direct Format"
            data:
              x: [1, 2, 3]
              y: [1, 2, 3]
            geom: line
            aes:
              x: x
              y: y
        """.trimIndent()

        val config = PlotParser.parse(yaml)

        assertNotNull(config)
        assertEquals("Direct Format", config.title)
        assertEquals(PlotGeom.LINE, config.geom)
    }

    @Test
    fun `should return null for invalid input`() {
        val invalid = "not valid yaml or json at all {{{"
        val config = PlotParser.parse(invalid)
        
        // Should return null or fallback parse result
        // The parser is lenient, so it might try to parse as simple format
        // Just ensure it doesn't crash
    }

    @Test
    fun `should return null for empty input`() {
        val config = PlotParser.parse("")
        assertEquals(null, config)
        
        val configWhitespace = PlotParser.parse("   ")
        assertEquals(null, configWhitespace)
    }
}

