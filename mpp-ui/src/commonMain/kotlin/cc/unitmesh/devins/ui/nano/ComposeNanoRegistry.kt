package cc.unitmesh.devins.ui.nano

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cc.unitmesh.devins.ui.nano.components.content.ContentComponents
import cc.unitmesh.devins.ui.nano.components.control.ControlFlowComponents
import cc.unitmesh.devins.ui.nano.components.data.DataComponents
import cc.unitmesh.devins.ui.nano.components.feedback.FeedbackComponents
import cc.unitmesh.devins.ui.nano.components.input.ButtonComponents
import cc.unitmesh.devins.ui.nano.components.input.DateComponents
import cc.unitmesh.devins.ui.nano.components.input.InputComponents
import cc.unitmesh.devins.ui.nano.components.input.SelectionComponents
import cc.unitmesh.devins.ui.nano.components.layout.LayoutComponents
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.render.stateful.*

// Type aliases for cleaner code (must be at top level in Kotlin)
private typealias ComposeRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit>
private typealias ComposeContext = NanoNodeContext<Modifier, @Composable () -> Unit>

/**
 * ComposeNanoRegistry - Compose-specific component registry
 *
 * Provides a pre-configured NanoNodeRegistry for Compose rendering.
 * Maps all standard NanoDSL component types to their Compose implementations.
 *
 * Component organization:
 * - Layout: [LayoutComponents] - VStack, HStack, Card, Form, Component, SplitView
 * - Content: [ContentComponents] - Text, Badge, Icon, Divider, Code, Link, Blockquote
 * - Input: [NanoInputComponents] - Button, Input, Checkbox, TextArea, Select, etc.
 * - Feedback: [NanoFeedbackComponents] - Modal, Alert, Progress, Spinner
 * - Data: [NanoDataComponents] - DataChart, DataTable
 * - Control: [ControlFlowComponents] - Conditional, ForLoop
 *
 * Usage:
 * ```kotlin
 * val registry = ComposeNanoRegistry.create()
 * val dispatcher = StatefulNanoNodeDispatcher(registry)
 * dispatcher.render(session, ir, Modifier)
 * ```
 */
object ComposeNanoRegistry {

    /**
     * Create a standard Compose registry with all built-in components.
     */
    fun create(): NanoNodeRegistry<Modifier, @Composable () -> Unit> {
        return NanoNodeRegistry.build {
            // Layout
            register(NanoComponentTypes.VSTACK, LayoutComponents.vstackRenderer)
            register(NanoComponentTypes.HSTACK, LayoutComponents.hstackRenderer)
            register(NanoComponentTypes.SPLIT_VIEW, LayoutComponents.splitViewRenderer)

            // Container
            register(NanoComponentTypes.CARD, LayoutComponents.cardRenderer)
            register(NanoComponentTypes.FORM, LayoutComponents.formRenderer)
            register(NanoComponentTypes.COMPONENT, LayoutComponents.componentRenderer)

            // Content
            register(NanoComponentTypes.TEXT, ContentComponents.textRenderer)
            register(NanoComponentTypes.IMAGE, imageRenderer)
            register(NanoComponentTypes.BADGE, ContentComponents.badgeRenderer)
            register(NanoComponentTypes.ICON, ContentComponents.iconRenderer)
            register(NanoComponentTypes.DIVIDER, ContentComponents.dividerRenderer)
            register(NanoComponentTypes.CODE, ContentComponents.codeRenderer)
            register(NanoComponentTypes.LINK, ContentComponents.linkRenderer)
            register(NanoComponentTypes.BLOCKQUOTE, ContentComponents.blockquoteRenderer)

            // Input - Core
            register(NanoComponentTypes.BUTTON, ButtonComponents.buttonRenderer)
            register(NanoComponentTypes.INPUT, InputComponents.inputRenderer)
            register(NanoComponentTypes.CHECKBOX, InputComponents.checkboxRenderer)
            register(NanoComponentTypes.TEXT_AREA, InputComponents.textAreaRenderer)
            register(NanoComponentTypes.SELECT, SelectionComponents.selectRenderer)

            // Input - Form
            register(NanoComponentTypes.DATE_PICKER, DateComponents.datePickerRenderer)
            register(NanoComponentTypes.RADIO, SelectionComponents.radioRenderer)
            register(NanoComponentTypes.RADIO_GROUP, SelectionComponents.radioGroupRenderer)
            register(NanoComponentTypes.SWITCH, InputComponents.switchRenderer)
            register(NanoComponentTypes.NUMBER_INPUT, InputComponents.numberInputRenderer)

            // Input - Advanced
            register(NanoComponentTypes.SMART_TEXT_FIELD, InputComponents.smartTextFieldRenderer)
            register(NanoComponentTypes.SLIDER, InputComponents.sliderRenderer)
            register(NanoComponentTypes.DATE_RANGE_PICKER, DateComponents.dateRangePickerRenderer)

            // Feedback
            register(NanoComponentTypes.MODAL, FeedbackComponents.modalRenderer)
            register(NanoComponentTypes.ALERT, FeedbackComponents.alertRenderer)
            register(NanoComponentTypes.PROGRESS, FeedbackComponents.progressRenderer)
            register(NanoComponentTypes.SPINNER, FeedbackComponents.spinnerRenderer)

            // Data
            register(NanoComponentTypes.DATA_CHART, DataComponents.dataChartRenderer)
            register(NanoComponentTypes.DATA_TABLE, DataComponents.dataTableRenderer)

            // Control Flow
            register(NanoComponentTypes.CONDITIONAL, ControlFlowComponents.conditionalRenderer)
            register(NanoComponentTypes.FOR_LOOP, ControlFlowComponents.forLoopRenderer)

            // Fallback
            fallback(ControlFlowComponents.unknownRenderer)
        }
    }

    /**
     * Adapter function to bridge old-style renderNode to new context-based rendering.
     */
    private fun ComposeContext.toRenderNode(): @Composable (NanoIR, Map<String, Any>, (NanoActionIR) -> Unit, Modifier) -> Unit {
        return { childIr, _, _, childModifier ->
            renderChild(childIr, childModifier).invoke()
        }
    }

    // ==================== Image Renderer (special case with cache) ====================

    private val imageRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { RenderImage(ctx.node, ctx.payload) }
    }
}
