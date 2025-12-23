package cc.unitmesh.devins.idea.renderer.nano.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cc.unitmesh.xuiper.ir.stringProp
import cc.unitmesh.xuiper.props.NanoSpacingUtils
import cc.unitmesh.xuiper.render.stateful.NanoNodeRenderer
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * Jewel layout components for NanoUI IntelliJ IDEA renderer.
 * Includes: VStack, HStack, Card, Form, Component, SplitView
 */
object JewelLayoutComponents {

    val vstackRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val spacing = NanoSpacingUtils.parseSpacing(ctx.node.stringProp("spacing"), default = 8).dp
            Column(
                modifier = ctx.payload,
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                ctx.node.children?.forEach { child ->
                    ctx.renderChild(child, Modifier).invoke()
                }
            }
        }
    }

    val hstackRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val spacing = NanoSpacingUtils.parseSpacing(ctx.node.stringProp("spacing"), default = 8).dp
            val justify = ctx.node.stringProp("justify") ?: "start"
            Row(
                modifier = ctx.payload,
                horizontalArrangement = when (justify) {
                    "between" -> Arrangement.SpaceBetween
                    "around" -> Arrangement.SpaceAround
                    "evenly" -> Arrangement.SpaceEvenly
                    "center" -> Arrangement.Center
                    "end" -> Arrangement.End
                    else -> Arrangement.spacedBy(spacing)
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                ctx.node.children?.forEach { child ->
                    ctx.renderChild(child, Modifier).invoke()
                }
            }
        }
    }

    val cardRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val padding = NanoSpacingUtils.parsePadding(ctx.node.stringProp("padding"), default = 16).dp
            Box(
                modifier = ctx.payload
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(8.dp))
                    .background(JewelTheme.globalColors.panelBackground)
                    .padding(padding)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ctx.node.children?.forEach { child ->
                        ctx.renderChild(child, Modifier).invoke()
                    }
                }
            }
        }
    }

    val formRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            Column(
                modifier = ctx.payload,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ctx.node.children?.forEach { child ->
                    ctx.renderChild(child, Modifier.fillMaxWidth()).invoke()
                }
            }
        }
    }

    val componentRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            Column(modifier = ctx.payload) {
                ctx.node.children?.forEach { child ->
                    ctx.renderChild(child, Modifier).invoke()
                }
            }
        }
    }

    val splitViewRenderer: JewelRenderer = NanoNodeRenderer { ctx ->
        {
            val direction = ctx.node.stringProp("direction") ?: "horizontal"
            val children = ctx.node.children.orEmpty()

            if (direction == "vertical") {
                Column(
                    modifier = ctx.payload.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    children.forEach { child ->
                        Box(modifier = Modifier.weight(1f)) {
                            ctx.renderChild(child, Modifier.fillMaxSize()).invoke()
                        }
                    }
                }
            } else {
                Row(
                    modifier = ctx.payload.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    children.forEach { child ->
                        Box(modifier = Modifier.weight(1f)) {
                            ctx.renderChild(child, Modifier.fillMaxSize()).invoke()
                        }
                    }
                }
            }
        }
    }
}
