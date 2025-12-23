package cc.unitmesh.devins.ui.nano

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
            register(NanoComponentTypes.VSTACK, vstackRenderer)
            register(NanoComponentTypes.HSTACK, hstackRenderer)
            register(NanoComponentTypes.SPLIT_VIEW, splitViewRenderer)

            // Container
            register(NanoComponentTypes.CARD, cardRenderer)
            register(NanoComponentTypes.FORM, formRenderer)
            register(NanoComponentTypes.COMPONENT, componentRenderer)

            // Content
            register(NanoComponentTypes.TEXT, textRenderer)
            register(NanoComponentTypes.IMAGE, imageRenderer)
            register(NanoComponentTypes.BADGE, badgeRenderer)
            register(NanoComponentTypes.ICON, iconRenderer)
            register(NanoComponentTypes.DIVIDER, dividerRenderer)
            register(NanoComponentTypes.CODE, codeRenderer)
            register(NanoComponentTypes.LINK, linkRenderer)
            register(NanoComponentTypes.BLOCKQUOTE, blockquoteRenderer)

            // Input - Core
            register(NanoComponentTypes.BUTTON, buttonRenderer)
            register(NanoComponentTypes.INPUT, inputRenderer)
            register(NanoComponentTypes.CHECKBOX, checkboxRenderer)
            register(NanoComponentTypes.TEXT_AREA, textAreaRenderer)
            register(NanoComponentTypes.SELECT, selectRenderer)

            // Input - Form
            register(NanoComponentTypes.DATE_PICKER, datePickerRenderer)
            register(NanoComponentTypes.RADIO, radioRenderer)
            register(NanoComponentTypes.RADIO_GROUP, radioGroupRenderer)
            register(NanoComponentTypes.SWITCH, switchRenderer)
            register(NanoComponentTypes.NUMBER_INPUT, numberInputRenderer)

            // Input - Advanced
            register(NanoComponentTypes.SMART_TEXT_FIELD, smartTextFieldRenderer)
            register(NanoComponentTypes.SLIDER, sliderRenderer)
            register(NanoComponentTypes.DATE_RANGE_PICKER, dateRangePickerRenderer)

            // Feedback
            register(NanoComponentTypes.MODAL, modalRenderer)
            register(NanoComponentTypes.ALERT, alertRenderer)
            register(NanoComponentTypes.PROGRESS, progressRenderer)
            register(NanoComponentTypes.SPINNER, spinnerRenderer)

            // Data
            register(NanoComponentTypes.DATA_CHART, dataChartRenderer)
            register(NanoComponentTypes.DATA_TABLE, dataTableRenderer)

            // Control Flow
            register(NanoComponentTypes.CONDITIONAL, conditionalRenderer)
            register(NanoComponentTypes.FOR_LOOP, forLoopRenderer)

            // Fallback
            fallback(unknownRenderer)
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

    // ==================== Layout Renderers ====================

    private val vstackRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoLayoutComponents.RenderVStack(ctx.node, ctx.state, ctx.onAction, ctx.payload, ctx.toRenderNode()) }
    }

    private val hstackRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoLayoutComponents.RenderHStack(ctx.node, ctx.state, ctx.onAction, ctx.payload, ctx.toRenderNode()) }
    }

    private val splitViewRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoLayoutComponents.RenderSplitView(ctx.node, ctx.state, ctx.onAction, ctx.payload, ctx.toRenderNode()) }
    }

    // ==================== Container Renderers ====================

    private val cardRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoLayoutComponents.RenderCard(ctx.node, ctx.state, ctx.onAction, ctx.payload, ctx.toRenderNode()) }
    }

    private val formRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoLayoutComponents.RenderForm(ctx.node, ctx.state, ctx.onAction, ctx.payload, ctx.toRenderNode()) }
    }

    private val componentRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoLayoutComponents.RenderComponent(ctx.node, ctx.state, ctx.onAction, ctx.payload, ctx.toRenderNode()) }
    }

    // ==================== Content Renderers ====================

    private val textRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoContentComponents.RenderText(ctx.node, ctx.state, ctx.payload) }
    }

    private val imageRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { RenderImage(ctx.node, ctx.payload) }
    }

    private val badgeRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoContentComponents.RenderBadge(ctx.node, ctx.state, ctx.payload) }
    }

    private val iconRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoContentComponents.RenderIcon(ctx.node, ctx.payload) }
    }

    private val dividerRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoContentComponents.RenderDivider(ctx.payload) }
    }

    private val codeRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoContentComponents.RenderCode(ctx.node, ctx.state, ctx.payload) }
    }

    private val linkRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoContentComponents.RenderLink(ctx.node, ctx.state, ctx.payload) }
    }

    private val blockquoteRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoContentComponents.RenderBlockquote(ctx.node, ctx.state, ctx.payload) }
    }

    // ==================== Input Renderers ====================

    private val buttonRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoInputComponents.RenderButton(ctx.node, ctx.state, ctx.onAction, ctx.payload) }
    }

    private val inputRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoInputComponents.RenderInput(ctx.node, ctx.state, ctx.onAction, ctx.payload) }
    }

    private val checkboxRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoInputComponents.RenderCheckbox(ctx.node, ctx.state, ctx.onAction, ctx.payload) }
    }

    private val textAreaRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoInputComponents.RenderTextArea(ctx.node, ctx.state, ctx.onAction, ctx.payload) }
    }

    private val selectRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        {
            NanoSelectionRenderer.RenderSelect(ctx.node, ctx.state, ctx.onAction, ctx.payload)
        }
    }

    private val datePickerRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        {
            NanoDateComponents.RenderDatePicker(ctx.node, ctx.state, ctx.onAction, ctx.payload)
        }
    }

    private val radioRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        {
            NanoSelectionRenderer.RenderRadio(ctx.node, ctx.state, ctx.onAction, ctx.payload)
        }
    }

    private val radioGroupRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        {
            NanoSelectionRenderer.RenderRadioGroup(ctx.node, ctx.state, ctx.onAction, ctx.payload, ctx.toRenderNode())
        }
    }

    private val switchRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoInputComponents.RenderSwitch(ctx.node, ctx.state, ctx.onAction, ctx.payload) }
    }

    private val numberInputRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoInputComponents.RenderNumberInput(ctx.node, ctx.state, ctx.onAction, ctx.payload) }
    }

    private val smartTextFieldRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoInputComponents.RenderSmartTextField(ctx.node, ctx.state, ctx.onAction, ctx.payload) }
    }

    private val sliderRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoInputComponents.RenderSlider(ctx.node, ctx.state, ctx.onAction, ctx.payload) }
    }

    private val dateRangePickerRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoInputComponents.RenderDateRangePicker(ctx.node, ctx.state, ctx.onAction, ctx.payload) }
    }

    // ==================== Feedback Renderers ====================

    private val modalRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoFeedbackComponents.RenderModal(ctx.node, ctx.state, ctx.onAction, ctx.payload, ctx.toRenderNode()) }
    }

    private val alertRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoFeedbackComponents.RenderAlert(ctx.node, ctx.payload, ctx.onAction) }
    }

    private val progressRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoFeedbackComponents.RenderProgress(ctx.node, ctx.state, ctx.payload) }
    }

    private val spinnerRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoFeedbackComponents.RenderSpinner(ctx.node, ctx.payload) }
    }

    // ==================== Data Renderers ====================

    private val dataChartRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoDataComponents.RenderDataChart(ctx.node, ctx.state, ctx.payload) }
    }

    private val dataTableRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoDataComponents.RenderDataTable(ctx.node, ctx.state, ctx.onAction, ctx.payload) }
    }

    // ==================== Control Flow Renderers ====================

    private val conditionalRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoControlFlowComponents.RenderConditional(ctx.node, ctx.state, ctx.onAction, ctx.payload, ctx.toRenderNode()) }
    }

    private val forLoopRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoControlFlowComponents.RenderForLoop(ctx.node, ctx.state, ctx.onAction, ctx.payload, ctx.toRenderNode()) }
    }

    // ==================== Fallback Renderer ====================

    private val unknownRenderer: ComposeRenderer = NanoNodeRenderer { ctx ->
        { NanoControlFlowComponents.RenderUnknown(ctx.node, ctx.payload) }
    }
}
