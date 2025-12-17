package cc.unitmesh.xuiper.components.input

import cc.unitmesh.xuiper.action.NanoAction
import cc.unitmesh.xuiper.ast.Binding
import cc.unitmesh.xuiper.ast.NanoNode
import cc.unitmesh.xuiper.components.ComponentDefinition
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.components.ComponentConverterUtils
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoBindingIR
import cc.unitmesh.xuiper.spec.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * DatePicker Component - Single date picker
 * 
 * Example: `DatePicker(bind := state.birthday, format="YYYY-MM-DD")`
 */
object DatePickerComponent : ComponentDefinition {
    override val name = "DatePicker"
    
    override val spec = ComponentSpec(
        name = "DatePicker",
        category = ComponentCategory.INPUT,
        optionalProps = listOf(
            PropSpec("value", PropType.BINDING, description = "Selected date binding"),
            PropSpec("format", PropType.STRING, "YYYY-MM-DD", description = "Date format string"),
            PropSpec("minDate", PropType.STRING, description = "Minimum selectable date"),
            PropSpec("maxDate", PropType.STRING, description = "Maximum selectable date"),
            PropSpec("placeholder", PropType.STRING)
        ),
        allowsActions = true,
        description = "Single date picker component"
    )
    
    override fun createASTNode(props: Map<String, Any>, children: List<NanoNode>): NanoNode {
        return NanoNode.DatePicker(
            value = props["value"] as? Binding,
            format = props["format"] as? String,
            minDate = props["minDate"] as? String,
            maxDate = props["maxDate"] as? String,
            placeholder = props["placeholder"] as? String,
            onChange = props["onChange"] as? NanoAction
        )
    }
    
    override fun convertToIR(node: NanoNode): NanoIR {
        require(node is NanoNode.DatePicker)
        
        val props = mutableMapOf<String, JsonElement>()
        node.format?.let { props["format"] = JsonPrimitive(it) }
        node.minDate?.let { props["minDate"] = JsonPrimitive(it) }
        node.maxDate?.let { props["maxDate"] = JsonPrimitive(it) }
        node.placeholder?.let { props["placeholder"] = JsonPrimitive(it) }

        val bindings = node.value?.let {
            mapOf("value" to convertBinding(it))
        }

        val actions = node.onChange?.let {
            mapOf("onChange" to convertAction(it))
        }

        return NanoIR(type = "DatePicker", props = props, bindings = bindings, actions = actions)
    }
    
    private fun convertBinding(binding: Binding): NanoBindingIR {
        return ComponentConverterUtils.convertBinding(binding)
    }
    
    private fun convertAction(action: NanoAction): NanoActionIR {
        return ComponentConverterUtils.convertAction(action)
    }
}

