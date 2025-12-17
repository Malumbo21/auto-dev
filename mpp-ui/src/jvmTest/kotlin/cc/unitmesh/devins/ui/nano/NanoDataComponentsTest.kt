package cc.unitmesh.devins.ui.nano

import cc.unitmesh.xuiper.dsl.NanoDSL
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for NanoDataComponents functionality
 */
class NanoDataComponentsTest {

    @Test
    fun `should parse sales dashboard with DataTable and DataChart`() {
        val source = """
component SalesDashboard:
    state:
        salesData: list = [
            {"product": "笔记本电脑", "sales": 45000, "quantity": 15, "date": "2024-01-15"},
            {"product": "智能手机", "sales": 32000, "quantity": 40, "date": "2024-01-16"},
            {"product": "平板电脑", "sales": 28000, "quantity": 20, "date": "2024-01-17"},
            {"product": "智能手表", "sales": 18000, "quantity": 30, "date": "2024-01-18"},
            {"product": "无线耳机", "sales": 12000, "quantity": 50, "date": "2024-01-19"}
        ]
        chartData: list = [
            {"month": "一月", "sales": 45000},
            {"month": "二月", "sales": 52000},
            {"month": "三月", "sales": 48000},
            {"month": "四月", "sales": 61000},
            {"month": "五月", "sales": 58000},
            {"month": "六月", "sales": 67000}
        ]

    VStack(spacing="lg", padding="md"):
        Text("销售数据仪表板", style="h1")
        
        Card(padding="md", shadow="sm"):
            VStack(spacing="md"):
                HStack(justify="between", align="center"):
                    Text("销售数据明细", style="h2")
                    Badge("共 {len(salesData)} 条记录", color="blue")
                
                DataTable(
                    columns=[
                        {"key": "product", "title": "产品名称", "sortable": true},
                        {"key": "sales", "title": "销售额", "sortable": true, "format": "currency"},
                        {"key": "quantity", "title": "数量", "sortable": true},
                        {"key": "date", "title": "日期", "sortable": true}
                    ],
                    data=state.salesData
                )
        
        Card(padding="md", shadow="sm"):
            VStack(spacing="md"):
                Text("销售额趋势", style="h2")
                DataChart(
                    type="bar",
                    data=state.chartData,
                    xField="month",
                    yField="sales"
                )
        """.trimIndent()

        val result = NanoDSL.parse(source)

        assertEquals("SalesDashboard", result.name)
        assertNotNull(result.state)
        
        // Check state variables
        val stateVars = result.state!!.variables
        assertTrue(stateVars.any { it.name == "salesData" && it.type == "list" })
        assertTrue(stateVars.any { it.name == "chartData" && it.type == "list" })
        
        // Check structure
        assertTrue(result.children.isNotEmpty())
    }

    @Test
    fun `should parse simple DataTable with string columns`() {
        val source = """
component SimpleTable:
    state:
        users: list = [
            {"name": "Alice", "age": "30", "city": "NYC"},
            {"name": "Bob", "age": "25", "city": "LA"}
        ]
    
    DataTable(
        columns="Name,Age,City",
        data=state.users
    )
        """.trimIndent()

        val result = NanoDSL.parse(source)
        assertEquals("SimpleTable", result.name)
        assertTrue(result.children.isNotEmpty())
    }

    @Test
    fun `should parse DataChart with binding`() {
        val source = """
component ChartDemo:
    state:
        monthlyData: list = [
            {"month": "Jan", "value": 100},
            {"month": "Feb", "value": 150},
            {"month": "Mar", "value": 130}
        ]
    
    DataChart(
        type="line",
        data=state.monthlyData,
        xField="month",
        yField="value"
    )
        """.trimIndent()

        val result = NanoDSL.parse(source)
        assertEquals("ChartDemo", result.name)
        assertNotNull(result.state)
        assertTrue(result.children.isNotEmpty())
    }

    @Test
    fun `should handle inline data for DataTable`() {
        val source = """
component InlineTable:
    DataTable(
        columns="Product,Price,Stock",
        data="Laptop,$1200,5;Phone,$800,10;Tablet,$500,8"
    )
        """.trimIndent()

        val result = NanoDSL.parse(source)
        assertEquals("InlineTable", result.name)
        assertTrue(result.children.isNotEmpty())
    }
}
