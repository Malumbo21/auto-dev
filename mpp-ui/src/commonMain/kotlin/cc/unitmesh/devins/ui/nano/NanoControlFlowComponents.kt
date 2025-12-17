package cc.unitmesh.devins.ui.nano

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR

/**
 * Control flow components for NanoUI Compose renderer.
 * Includes: Conditional, ForLoop, Unknown
 */
object NanoControlFlowComponents {

    @Composable
    fun RenderConditional(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier,
        renderNode: @Composable (NanoIR, Map<String, Any>, (NanoActionIR) -> Unit, Modifier) -> Unit
    ) {
        val condition = ir.condition
        val isVisible = NanoRenderUtils.evaluateCondition(condition, state)

        if (isVisible) {
            Column(modifier = modifier) {
                ir.children?.forEach { child -> renderNode(child, state, onAction, Modifier) }
            }
        }
    }

    @Composable
    fun RenderForLoop(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier,
        renderNode: @Composable (NanoIR, Map<String, Any>, (NanoActionIR) -> Unit, Modifier) -> Unit
    ) {
        val loop = ir.loop
        val variable = loop?.variable
        val iterable = loop?.iterable?.removePrefix("state.")
        val items = state[iterable] as? List<*> ?: emptyList<Any>()

        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEachIndexed { index, item ->
                val itemState = state.toMutableMap().apply {
                    if (variable != null) {
                        this[variable] = item ?: ""
                    }
                }
                ir.children?.forEach { child -> renderNode(child, itemState, onAction, Modifier) }
            }
        }
    }

    @Composable
    fun RenderUnknown(ir: NanoIR, modifier: Modifier) {
        Surface(
            modifier = modifier.border(1.dp, Color.Red, RoundedCornerShape(4.dp)),
            color = Color.Red.copy(alpha = 0.1f)
        ) {
            Text(
                text = "Unknown: ${ir.type}",
                modifier = Modifier.padding(8.dp),
                color = Color.Red
            )
        }
    }
}
