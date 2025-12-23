package cc.unitmesh.devins.ui.nano.components.layout

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.nano.ComposeNodeContext
import cc.unitmesh.devins.ui.nano.toLegacyRenderNode
import cc.unitmesh.xuiper.ir.stringProp
import cc.unitmesh.xuiper.props.NanoSpacingUtils
import cc.unitmesh.xuiper.render.stateful.NanoNodeRenderer

/**
 * Layout components for NanoUI Compose renderer.
 * Includes: VStack, HStack, Card, Form, Component, SplitView
 *
 * All components use the unified NanoNodeContext interface.
 */
object LayoutComponents {

    val vstackRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderVStack(ctx) }
    }

    val hstackRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderHStack(ctx) }
    }

    val cardRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderCard(ctx) }
    }

    val formRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderForm(ctx) }
    }

    val componentRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderComponent(ctx) }
    }

    val splitViewRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderSplitView(ctx) }
    }

    @Composable
    fun RenderVStack(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val spacing = ir.stringProp("spacing")?.toSpacing() ?: 8.dp
        val padding = ir.stringProp("padding")?.toPadding()
        val align = ir.stringProp("align")

        val horizontalAlignment = when (align) {
            "center" -> Alignment.CenterHorizontally
            "start" -> Alignment.Start
            "end" -> Alignment.End
            "stretch" -> Alignment.Start
            else -> Alignment.Start
        }

        val finalModifier = if (padding != null) ctx.payload.padding(padding) else ctx.payload
        Column(
            modifier = finalModifier,
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = horizontalAlignment
        ) {
            ir.children?.forEach { child ->
                ctx.renderChild(child, Modifier).invoke()
            }
        }
    }

    @Composable
    fun RenderHStack(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val spacing = ir.stringProp("spacing")?.toSpacing() ?: 8.dp
        val padding = ir.stringProp("padding")?.toPadding()
        val align = ir.stringProp("align")
        val justify = ir.stringProp("justify")
        val wrap = ir.stringProp("wrap")

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

        val baseModifier = if (justify != null) ctx.payload.fillMaxWidth() else ctx.payload
        val finalModifier = if (padding != null) baseModifier.padding(padding) else baseModifier

        val children = ir.children.orEmpty()
        val containsImage = remember(children) { children.any { it.type == "Image" } }
        val containsVStack = remember(children) { children.any { it.type == "VStack" } }
        val shouldWrap = containsImage && containsVStack
        val explicitWrap = wrap == "wrap" || wrap == "true"

        val vstackOrCardChildren = children.count { it.type == "VStack" || it.type == "Card" }
        val shouldAutoDistribute = vstackOrCardChildren >= 2

        val flexibleInputTypes = setOf(
            "DatePicker", "DateRangePicker", "Input", "TextArea", "Select", "Slider"
        )
        val flexibleInputChildren = children.count { it.type in flexibleInputTypes }
        val shouldDistributeInputs = justify == "between" && flexibleInputChildren >= 2

        if (explicitWrap || shouldWrap) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = finalModifier,
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                children.forEach { child ->
                    val childModifier = if (explicitWrap && child.type == "Checkbox") Modifier.fillMaxWidth() else Modifier
                    ctx.renderChild(child, childModifier).invoke()
                }
            }
            return
        }

        Row(
            modifier = if (children.any { it.type == "Divider" }) finalModifier.height(IntrinsicSize.Min) else finalModifier,
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment
        ) {
            children.forEach { child ->
                if (child.type == "Divider") {
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight().width(1.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    return@forEach
                }

                val childFlex = child.stringProp("flex")?.toFloatOrNull()
                val childWeight = child.stringProp("weight")?.toFloatOrNull()
                val weight = childFlex ?: childWeight

                if (weight != null && weight > 0f) {
                    Box(modifier = Modifier.weight(weight).wrapContentHeight(unbounded = true)) {
                        ctx.renderChild(child, Modifier).invoke()
                    }
                } else if (shouldAutoDistribute && (child.type == "VStack" || child.type == "Card")) {
                    Box(modifier = Modifier.weight(1f).wrapContentHeight(unbounded = true)) {
                        ctx.renderChild(child, Modifier).invoke()
                    }
                } else if (shouldDistributeInputs && child.type in flexibleInputTypes) {
                    Box(modifier = Modifier.weight(1f).wrapContentHeight(unbounded = true)) {
                        ctx.renderChild(child, Modifier.fillMaxWidth()).invoke()
                    }
                } else {
                    ctx.renderChild(child, Modifier).invoke()
                }
            }
        }
    }

    @Composable
    fun RenderCard(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val padding = ir.stringProp("padding")?.toPadding() ?: 16.dp
        val shadow = ir.stringProp("shadow")

        val elevation = when (shadow) {
            "sm" -> CardDefaults.cardElevation(defaultElevation = 2.dp)
            "md" -> CardDefaults.cardElevation(defaultElevation = 4.dp)
            "lg" -> CardDefaults.cardElevation(defaultElevation = 8.dp)
            else -> CardDefaults.cardElevation()
        }

        Card(modifier = ctx.payload.fillMaxWidth(), elevation = elevation) {
            Column(
                modifier = Modifier.padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ctx.node.children?.forEach { child ->
                    ctx.renderChild(child, Modifier).invoke()
                }
            }
        }
    }

    @Composable
    fun RenderForm(ctx: ComposeNodeContext) {
        Column(
            modifier = ctx.payload.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ctx.node.children?.forEach { child ->
                ctx.renderChild(child, Modifier).invoke()
            }
        }
    }

    @Composable
    fun RenderComponent(ctx: ComposeNodeContext) {
        Column(modifier = ctx.payload) {
            ctx.node.children?.forEach { child ->
                ctx.renderChild(child, Modifier).invoke()
            }
        }
    }

    @Composable
    fun RenderSplitView(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val ratio = ir.stringProp("ratio")?.toFloatOrNull() ?: 0.5f
        val children = ir.children.orEmpty()

        BoxWithConstraints(modifier = ctx.payload.fillMaxWidth()) {
            val safeRatio = ratio.coerceIn(0.1f, 0.9f)

            if (maxWidth < 720.dp) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    children.firstOrNull()?.let { ctx.renderChild(it, Modifier.fillMaxWidth()).invoke() }
                    children.getOrNull(1)?.let { ctx.renderChild(it, Modifier.fillMaxWidth()).invoke() }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.weight(safeRatio)) {
                        children.firstOrNull()?.let { ctx.renderChild(it, Modifier.fillMaxWidth()).invoke() }
                    }
                    Box(modifier = Modifier.weight(1f - safeRatio)) {
                        children.getOrNull(1)?.let { ctx.renderChild(it, Modifier.fillMaxWidth()).invoke() }
                    }
                }
            }
        }
    }
}

/**
 * Convert spacing string to Dp value.
 */
fun String.toSpacing(): Dp = NanoSpacingUtils.parseSpacing(this).dp

/**
 * Convert padding string to Dp value.
 */
fun String.toPadding(): Dp = NanoSpacingUtils.parsePadding(this).dp
