package cc.unitmesh.devins.ui.nano

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import kotlinx.serialization.json.jsonPrimitive

/**
 * Layout components for NanoUI Compose renderer.
 * Includes: VStack, HStack, Card, Form, Component, SplitView
 */
object NanoLayoutComponents {

    @Composable
    fun RenderVStack(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier,
        renderNode: @Composable (NanoIR, Map<String, Any>, (NanoActionIR) -> Unit, Modifier) -> Unit
    ) {
        val spacing = ir.props["spacing"]?.jsonPrimitive?.content?.toSpacing() ?: 8.dp
        val padding = ir.props["padding"]?.jsonPrimitive?.content?.toPadding()
        val finalModifier = if (padding != null) modifier.padding(padding) else modifier
        Column(modifier = finalModifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
            ir.children?.forEach { child -> renderNode(child, state, onAction, Modifier) }
        }
    }

    @Composable
    fun RenderHStack(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier,
        renderNode: @Composable (NanoIR, Map<String, Any>, (NanoActionIR) -> Unit, Modifier) -> Unit
    ) {
        val spacing = ir.props["spacing"]?.jsonPrimitive?.content?.toSpacing() ?: 8.dp
        val padding = ir.props["padding"]?.jsonPrimitive?.content?.toPadding()
        val align = ir.props["align"]?.jsonPrimitive?.content
        val justify = ir.props["justify"]?.jsonPrimitive?.content
        val wrap = ir.props["wrap"]?.jsonPrimitive?.content

        val verticalAlignment = when (align) {
            "center" -> Alignment.CenterVertically
            "start", "top" -> Alignment.Top
            "end", "bottom" -> Alignment.Bottom
            else -> Alignment.CenterVertically
        }
        val horizontalArrangement = when (justify) {
            "center" -> Arrangement.Center
            "between" -> Arrangement.SpaceBetween
            "around" -> Arrangement.SpaceAround
            "evenly" -> Arrangement.SpaceEvenly
            "end" -> Arrangement.End
            else -> Arrangement.spacedBy(spacing)
        }

        // Apply fillMaxWidth when justify is specified to make space distribution work
        val baseModifier = if (justify != null) modifier.fillMaxWidth() else modifier
        val finalModifier = if (padding != null) baseModifier.padding(padding) else baseModifier

        val children = ir.children.orEmpty()
        val containsImage = remember(children) { children.any { it.type == "Image" } }
        val containsVStack = remember(children) { children.any { it.type == "VStack" } }
        // When mixing Image + VStack in a horizontal layout, Row can squeeze text into 1-char columns.
        // FlowRow lets the VStack wrap below the image when space is tight.
        val shouldWrap = justify == null && containsImage && containsVStack

        val explicitWrap = wrap == "wrap" || wrap == "true"

        // Count VStack/Card children to determine if we should auto-distribute space
        val vstackOrCardChildren = children.count {
            it.type == "VStack" || it.type == "Card"
        }
        val shouldAutoDistribute = vstackOrCardChildren >= 2

        if (explicitWrap || shouldWrap) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = finalModifier,
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                children.forEach { child ->
                    // Checkbox groups are commonly authored as HStack(wrap="wrap"); treat them as block items.
                    val childModifier = if (explicitWrap && child.type == "Checkbox") Modifier.fillMaxWidth() else Modifier
                    renderNode(child, state, onAction, childModifier)
                }
            }
            return
        }

        Row(
            modifier = finalModifier,
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment
        ) {
            children.forEach { child ->
                // Check if child has explicit flex/weight property
                val childFlex = child.props["flex"]?.jsonPrimitive?.content?.toFloatOrNull()
                val childWeight = child.props["weight"]?.jsonPrimitive?.content?.toFloatOrNull()
                val weight = childFlex ?: childWeight

                if (weight != null && weight > 0f) {
                    // Explicit weight specified
                    Box(modifier = Modifier.weight(weight).wrapContentHeight(unbounded = true)) {
                        renderNode(child, state, onAction, Modifier)
                    }
                } else if (shouldAutoDistribute && (child.type == "VStack" || child.type == "Card")) {
                    // VStack/Card in HStack with multiple siblings should share space equally
                    Box(modifier = Modifier.weight(1f).wrapContentHeight(unbounded = true)) {
                        renderNode(child, state, onAction, Modifier)
                    }
                } else {
                    // Default: size to content
                    renderNode(child, state, onAction, Modifier)
                }
            }
        }
    }

    @Composable
    fun RenderCard(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier,
        renderNode: @Composable (NanoIR, Map<String, Any>, (NanoActionIR) -> Unit, Modifier) -> Unit
    ) {
        val padding = ir.props["padding"]?.jsonPrimitive?.content?.toPadding() ?: 16.dp
        val shadow = ir.props["shadow"]?.jsonPrimitive?.content

        val elevation = when (shadow) {
            "sm" -> CardDefaults.cardElevation(defaultElevation = 2.dp)
            "md" -> CardDefaults.cardElevation(defaultElevation = 4.dp)
            "lg" -> CardDefaults.cardElevation(defaultElevation = 8.dp)
            else -> CardDefaults.cardElevation()
        }

        Card(modifier = modifier.fillMaxWidth(), elevation = elevation) {
            Column(modifier = Modifier.padding(padding)) {
                ir.children?.forEach { child -> renderNode(child, state, onAction, Modifier) }
            }
        }
    }

    @Composable
    fun RenderForm(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier,
        renderNode: @Composable (NanoIR, Map<String, Any>, (NanoActionIR) -> Unit, Modifier) -> Unit
    ) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ir.children?.forEach { child -> renderNode(child, state, onAction, Modifier) }
        }
    }

    @Composable
    fun RenderComponent(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier,
        renderNode: @Composable (NanoIR, Map<String, Any>, (NanoActionIR) -> Unit, Modifier) -> Unit
    ) {
        Column(modifier = modifier) {
            ir.children?.forEach { child -> renderNode(child, state, onAction, Modifier) }
        }
    }

    @Composable
    fun RenderSplitView(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier,
        renderNode: @Composable (NanoIR, Map<String, Any>, (NanoActionIR) -> Unit, Modifier) -> Unit
    ) {
        val ratio = ir.props["ratio"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0.5f
        val children = ir.children.orEmpty()

        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            val safeRatio = ratio.coerceIn(0.1f, 0.9f)

            // On narrow screens, a split view becomes two cramped columns. Stack instead.
            if (maxWidth < 720.dp) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    children.firstOrNull()?.let { renderNode(it, state, onAction, Modifier.fillMaxWidth()) }
                    children.getOrNull(1)?.let { renderNode(it, state, onAction, Modifier.fillMaxWidth()) }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.weight(safeRatio)) {
                        children.firstOrNull()?.let { renderNode(it, state, onAction, Modifier.fillMaxWidth()) }
                    }
                    Box(modifier = Modifier.weight(1f - safeRatio)) {
                        children.getOrNull(1)?.let { renderNode(it, state, onAction, Modifier.fillMaxWidth()) }
                    }
                }
            }
        }
    }
}
