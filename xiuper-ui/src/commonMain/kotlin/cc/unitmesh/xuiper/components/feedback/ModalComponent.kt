package cc.unitmesh.xuiper.components.feedback

import cc.unitmesh.xuiper.action.NanoAction
import cc.unitmesh.xuiper.ast.Binding
import cc.unitmesh.xuiper.ast.NanoNode
import cc.unitmesh.xuiper.components.ComponentDefinition
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.components.ComponentConverterUtils
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoBindingIR
import cc.unitmesh.xuiper.ir.NanoIRConverter
import cc.unitmesh.xuiper.spec.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Modal Component - Modal dialog
 * 
 * Example: `Modal(open := state.showModal, title="Confirm"): ...`
 */
object ModalComponent : ComponentDefinition {
    override val name = "Modal"
    
    private val MODAL_SIZE_VALUES = listOf("sm", "md", "lg", "xl")
    
    override val spec = ComponentSpec(
        name = "Modal",
        category = ComponentCategory.CONTAINER,
        optionalProps = listOf(
            PropSpec("open", PropType.BINDING, description = "Modal visibility binding"),
            PropSpec("title", PropType.STRING, description = "Modal title"),
            PropSpec("size", PropType.ENUM, "md", allowedValues = MODAL_SIZE_VALUES),
            PropSpec("closable", PropType.BOOLEAN, "true", description = "Show close button")
        ),
        allowsChildren = true,
        allowsActions = true,
        description = "Modal dialog component"
    )
    
    override fun createASTNode(props: Map<String, Any>, children: List<NanoNode>): NanoNode {
        return NanoNode.Modal(
            open = props["open"] as? Binding,
            title = props["title"] as? String,
            size = props["size"] as? String,
            closable = props["closable"] as? Boolean,
            onClose = props["onClose"] as? NanoAction,
            children = children
        )
    }
    
    override fun convertToIR(node: NanoNode): NanoIR {
        require(node is NanoNode.Modal)
        
        val props = mutableMapOf<String, JsonElement>()
        node.title?.let { props["title"] = JsonPrimitive(it) }
        node.size?.let { props["size"] = JsonPrimitive(it) }
        node.closable?.let { props["closable"] = JsonPrimitive(it) }

        val bindings = node.open?.let {
            mapOf("open" to convertBinding(it))
        }

        val actions = node.onClose?.let {
            mapOf("onClose" to ComponentConverterUtils.convertAction(it))
        }

        return NanoIR(
            type = "Modal",
            props = props,
            bindings = bindings,
            actions = actions,
            children = node.children.map { NanoIRConverter.convert(it) }
        )
    }
    
    private fun convertBinding(binding: Binding): NanoBindingIR {
        return ComponentConverterUtils.convertBinding(binding)
    }
}

