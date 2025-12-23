package cc.unitmesh.devins.idea.renderer.nano

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cc.unitmesh.devins.idea.renderer.nano.components.*
import cc.unitmesh.xuiper.render.stateful.NanoComponentTypes
import cc.unitmesh.xuiper.render.stateful.NanoNodeRegistry
import cc.unitmesh.xuiper.render.stateful.NanoNodeRenderer

/**
 * JewelNanoRegistry - IntelliJ IDEA Jewel-specific component registry
 *
 * Provides a pre-configured NanoNodeRegistry for Jewel (IntelliJ IDEA) rendering.
 * Maps all standard NanoDSL component types to their Jewel implementations.
 *
 * This registry enables mpp-idea to reuse the entire stateful rendering mechanism
 * from xiuper-ui while providing native IntelliJ look and feel.
 *
 * Component organization:
 * - Layout: [JewelLayoutComponents] - VStack, HStack, Card, Form, Component, SplitView
 * - Content: [JewelContentComponents] - Text, Badge, Icon, Divider, Code, Link, Blockquote
 * - Input: [JewelInputComponents] - Input, TextArea, Switch, NumberInput, SmartTextField, Slider, Checkbox
 * - Selection: [JewelSelectionRenderer] - Select, Radio, RadioGroup
 * - Button: [JewelButtonComponents] - Button with dynamic dialog
 * - Date: [JewelDateComponents] - DatePicker, DateRangePicker
 * - Feedback: [JewelFeedbackComponents] - Modal, Alert, Progress, Spinner
 * - Data: [JewelDataComponents] - DataChart, DataTable
 * - Control: [JewelControlFlowComponents] - Conditional, ForLoop
 *
 * Usage:
 * ```kotlin
 * val registry = JewelNanoRegistry.create()
 * val dispatcher = StatefulNanoNodeDispatcher(registry)
 * dispatcher.render(session, ir, Modifier)
 * ```
 */
object JewelNanoRegistry {

    /**
     * Create a standard Jewel registry with all built-in components.
     */
    fun create(): NanoNodeRegistry<Modifier, @Composable () -> Unit> {
        return NanoNodeRegistry.build {
            // Layout
            register(NanoComponentTypes.VSTACK, JewelLayoutComponents.vstackRenderer)
            register(NanoComponentTypes.HSTACK, JewelLayoutComponents.hstackRenderer)
            register(NanoComponentTypes.SPLIT_VIEW, JewelLayoutComponents.splitViewRenderer)

            // Container
            register(NanoComponentTypes.CARD, JewelLayoutComponents.cardRenderer)
            register(NanoComponentTypes.FORM, JewelLayoutComponents.formRenderer)
            register(NanoComponentTypes.COMPONENT, JewelLayoutComponents.componentRenderer)

            // Content
            register(NanoComponentTypes.TEXT, JewelContentComponents.textRenderer)
            register(NanoComponentTypes.BADGE, JewelContentComponents.badgeRenderer)
            register(NanoComponentTypes.ICON, JewelContentComponents.iconRenderer)
            register(NanoComponentTypes.DIVIDER, JewelContentComponents.dividerRenderer)
            register(NanoComponentTypes.CODE, JewelContentComponents.codeRenderer)
            register(NanoComponentTypes.LINK, JewelContentComponents.linkRenderer)
            register(NanoComponentTypes.BLOCKQUOTE, JewelContentComponents.blockquoteRenderer)

            // Input - Core
            register(NanoComponentTypes.BUTTON, JewelButtonComponents.buttonRenderer)
            register(NanoComponentTypes.INPUT, JewelInputComponents.inputRenderer)
            register(NanoComponentTypes.CHECKBOX, JewelInputComponents.checkboxRenderer)
            register(NanoComponentTypes.TEXT_AREA, JewelInputComponents.textAreaRenderer)

            // Input - Form
            register(NanoComponentTypes.SWITCH, JewelInputComponents.switchRenderer)
            register(NanoComponentTypes.NUMBER_INPUT, JewelInputComponents.numberInputRenderer)
            register(NanoComponentTypes.DATE_PICKER, JewelDateComponents.datePickerRenderer)

            // Input - Advanced
            register(NanoComponentTypes.SMART_TEXT_FIELD, JewelInputComponents.smartTextFieldRenderer)
            register(NanoComponentTypes.SLIDER, JewelInputComponents.sliderRenderer)
            register(NanoComponentTypes.DATE_RANGE_PICKER, JewelDateComponents.dateRangePickerRenderer)

            // Selection (using JewelSelectionRenderer)
            register(NanoComponentTypes.SELECT, selectRenderer)
            register(NanoComponentTypes.RADIO, radioRenderer)
            register(NanoComponentTypes.RADIO_GROUP, radioGroupRenderer)

            // Feedback
            register(NanoComponentTypes.MODAL, JewelFeedbackComponents.modalRenderer)
            register(NanoComponentTypes.ALERT, JewelFeedbackComponents.alertRenderer)
            register(NanoComponentTypes.PROGRESS, JewelFeedbackComponents.progressRenderer)
            register(NanoComponentTypes.SPINNER, JewelFeedbackComponents.spinnerRenderer)

            // Data
            register(NanoComponentTypes.DATA_CHART, JewelDataComponents.dataChartRenderer)
            register(NanoComponentTypes.DATA_TABLE, JewelDataComponents.dataTableRenderer)

            // Control Flow
            register(NanoComponentTypes.CONDITIONAL, JewelControlFlowComponents.conditionalRenderer)
            register(NanoComponentTypes.FOR_LOOP, JewelControlFlowComponents.forLoopRenderer)

            // Fallback
            fallback(JewelControlFlowComponents.unknownRenderer)
        }
    }

    /**
     * Create an extended registry with additional custom renderers.
     */
    fun createExtended(
        additionalRenderers: Map<String, NanoNodeRenderer<Modifier, @Composable () -> Unit>>
    ): NanoNodeRegistry<Modifier, @Composable () -> Unit> {
        return create().extend(additionalRenderers)
    }

    // Selection renderers using JewelSelectionRenderer
    private val selectRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { JewelSelectionRenderer.RenderSelect(ctx.node, ctx.state, ctx.onAction, ctx.payload) }
    }

    private val radioRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { JewelSelectionRenderer.RenderRadio(ctx.node, ctx.state, ctx.onAction, ctx.payload) }
    }

    private val radioGroupRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        {
            JewelSelectionRenderer.RenderRadioGroup(ctx.node, ctx.state, ctx.onAction, ctx.payload) { child, _, _, m ->
                ctx.renderChild(child, m).invoke()
            }
        }
    }
}
