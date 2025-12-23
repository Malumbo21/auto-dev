package cc.unitmesh.devins.ui.nano

import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.props.NanoOptionParser
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NanoRadioGroupOptionsTest {

    @Test
    fun `should parse RadioGroup options from string literal`() {
        // xiuper-ui IR converter stores RadioGroup.options as a JsonPrimitive(String)
        // because NanoNode.RadioGroup.options is modeled as String.
        val ir = NanoIR(
            type = "RadioGroup",
            props = mapOf(
                "options" to JsonPrimitive(
                    """[
                    {value: \"train\", label: \"高铁 (4.5小时)\"},
                    {value: \"plane\", label: \"飞机 (2小时)\"},
                    {value: \"car\", label: \"自驾 (12小时)\"}
                    ]""".trimIndent()
                )
            )
        )

        val result = NanoOptionParser.parse(ir.props["options"])
        assertEquals(3, result.size)

        val first = result.first()!!
        assertEquals("train", first.value)
        assertTrue(first.label.contains("高铁"))
    }
}
