package cc.unitmesh.xuiper.components.input

import cc.unitmesh.xuiper.action.NanoAction
import cc.unitmesh.xuiper.ast.NanoNode
import cc.unitmesh.xuiper.components.ComponentDefinition
import cc.unitmesh.xuiper.components.ComponentConverterUtils
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.spec.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Button Component - Clickable button
 * 
 * Example: `Button("Add to Cart", intent="primary"): on_click: ...`
 */
object ButtonComponent : ComponentDefinition {
    override val name = "Button"
    
    private val INTENT_VALUES = listOf("primary", "secondary", "danger", "default")
    
    override val spec = ComponentSpec(
        name = "Button",
        category = ComponentCategory.INPUT,
        requiredProps = listOf(PropSpec("label", PropType.STRING)),
        optionalProps = listOf(
            PropSpec("intent", PropType.ENUM, "default", allowedValues = INTENT_VALUES),
            PropSpec("icon", PropType.STRING),
            PropSpec("disabled_if", PropType.EXPRESSION, description = "Conditional disable expression")
        ),
        allowsActions = true,
        description = "Clickable button"
    )
    
    override fun createASTNode(props: Map<String, Any>, children: List<NanoNode>): NanoNode {
        return NanoNode.Button(
            label = props["label"] as? String ?: "",
            intent = props["intent"] as? String,
            icon = props["icon"] as? String,
            disabledIf = props["disabled_if"] as? String,
            onClick = props["onClick"] as? NanoAction
        )
    }
    
    override fun convertToIR(node: NanoNode): NanoIR {
        require(node is NanoNode.Button)
        
        val props = mutableMapOf<String, JsonElement>(
            "label" to JsonPrimitive(node.label)
        )
        node.intent?.let { props["intent"] = JsonPrimitive(it) }
        node.icon?.let { props["icon"] = JsonPrimitive(it) }
        node.disabledIf?.let { props["disabled_if"] = JsonPrimitive(it) }

        val actions = node.onClick?.let {
            mapOf("onClick" to ComponentConverterUtils.convertAction(it))
        }

        return NanoIR(type = "Button", props = props, actions = actions)
    }
}

