package cc.unitmesh.devins.ui.nano

import cc.unitmesh.xuiper.ir.NanoIR
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

        val method = NanoInputComponents::class.java.getDeclaredMethod(
            "parseRadioOptions",
            kotlinx.serialization.json.JsonElement::class.java
        )
        method.isAccessible = true

        val result = method.invoke(NanoInputComponents, ir.props["options"]) as List<*>
        assertEquals(3, result.size)

        val first = result.first()!!
        val valueField = first::class.java.getDeclaredField("value").also { it.isAccessible = true }
        val labelField = first::class.java.getDeclaredField("label").also { it.isAccessible = true }

        assertEquals("train", valueField.get(first) as String)
        assertTrue((labelField.get(first) as String).contains("高铁"))
    }
}
