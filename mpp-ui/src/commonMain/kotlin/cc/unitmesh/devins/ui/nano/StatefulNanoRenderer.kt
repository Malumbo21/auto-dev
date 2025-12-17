package cc.unitmesh.devins.ui.nano

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR

/**
 * Stateful NanoUI Compose Renderer
 *
 * This renderer maintains state and handles actions for interactive NanoDSL components.
 * It wraps the component rendering with a state context that:
 * 1. Initializes state from NanoIR state definitions
 * 2. Passes state values to components via bindings
 * 3. Updates state when actions are triggered
 * 4. Generates images for Image components if ImageGenerationService is provided
 *
 * Components are organized into separate files:
 * - [NanoLayoutComponents] - VStack, HStack, Card, Form, Component, SplitView
 * - [NanoContentComponents] - Text, Image, Badge, Icon, Divider
 * - [NanoInputComponents] - Button, Input, Checkbox, TextArea, Select, DatePicker, Radio, Switch, etc.
 * - [NanoFeedbackComponents] - Modal, Alert, Progress, Spinner
 * - [NanoDataComponents] - DataChart, DataTable
 * - [NanoControlFlowComponents] - Conditional, ForLoop
 * - [NanoRenderUtils] - Helper functions and utilities
 */
object StatefulNanoRenderer {

    /**
     * Render a NanoIR tree with state management.
     * Automatically initializes state from the IR and provides action handlers.
     *
     * @param ir The NanoIR tree to render
     * @param modifier Modifier for the root component
     */
    @Composable
    fun Render(
        ir: NanoIR,
        modifier: Modifier = Modifier
    ) {
        // Runtime wraps NanoState + action application logic.
        // Recreated when IR changes (e.g. live preview re-parses NanoDSL).
        val runtime = remember(ir) { NanoStateRuntime(ir) }

        // Subscribe to declared state keys so Compose recomposes when they change.
        // We only track keys declared in the component's `state:` block.
        runtime.declaredKeys.forEach { key ->
            runtime.state.flow(key).collectAsState()
        }

        val snapshot = runtime.snapshot()
        val handleAction: (NanoActionIR) -> Unit = { action -> runtime.apply(action) }

        RenderNode(ir, snapshot, handleAction, modifier)
    }

    @Composable
    private fun RenderNode(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier = Modifier
    ) {
        // Create a composable lambda for recursive rendering
        val renderNode: @Composable (NanoIR, Map<String, Any>, (NanoActionIR) -> Unit, Modifier) -> Unit =
            { childIr, childState, childOnAction, childModifier ->
                RenderNode(childIr, childState, childOnAction, childModifier)
            }

        when (ir.type) {
            // Layout
            "VStack" -> NanoLayoutComponents.RenderVStack(ir, state, onAction, modifier, renderNode)
            "HStack" -> NanoLayoutComponents.RenderHStack(ir, state, onAction, modifier, renderNode)
            // Container
            "Card" -> NanoLayoutComponents.RenderCard(ir, state, onAction, modifier, renderNode)
            "Form" -> NanoLayoutComponents.RenderForm(ir, state, onAction, modifier, renderNode)
            // Content
            "Text" -> NanoContentComponents.RenderText(ir, state, modifier)
            "Image" -> NanoContentComponents.RenderImage(ir, modifier)
            "Badge" -> NanoContentComponents.RenderBadge(ir, state, modifier)
            "Icon" -> NanoContentComponents.RenderIcon(ir, modifier)
            "Divider" -> NanoContentComponents.RenderDivider(modifier)
            // Input
            "Button" -> NanoInputComponents.RenderButton(ir, state, onAction, modifier)
            "Input" -> NanoInputComponents.RenderInput(ir, state, onAction, modifier)
            "Checkbox" -> NanoInputComponents.RenderCheckbox(ir, state, onAction, modifier)
            "TextArea" -> NanoInputComponents.RenderTextArea(ir, state, onAction, modifier)
            "Select" -> NanoInputComponents.RenderSelect(ir, state, onAction, modifier)
            // P0: Core Form Input Components
            "DatePicker" -> NanoInputComponents.RenderDatePicker(ir, state, onAction, modifier)
            "Radio" -> NanoInputComponents.RenderRadio(ir, state, onAction, modifier)
            "RadioGroup" -> NanoInputComponents.RenderRadioGroup(ir, state, onAction, modifier, renderNode)
            "Switch" -> NanoInputComponents.RenderSwitch(ir, state, onAction, modifier)
            "NumberInput" -> NanoInputComponents.RenderNumberInput(ir, state, onAction, modifier)
            // P0: Feedback Components
            "Modal" -> NanoFeedbackComponents.RenderModal(ir, state, onAction, modifier, renderNode)
            "Alert" -> NanoFeedbackComponents.RenderAlert(ir, modifier, onAction)
            "Progress" -> NanoFeedbackComponents.RenderProgress(ir, state, modifier)
            "Spinner" -> NanoFeedbackComponents.RenderSpinner(ir, modifier)
            // Tier 1-3: GenUI Components
            "SplitView" -> NanoLayoutComponents.RenderSplitView(ir, state, onAction, modifier, renderNode)
            "SmartTextField" -> NanoInputComponents.RenderSmartTextField(ir, state, onAction, modifier)
            "Slider" -> NanoInputComponents.RenderSlider(ir, state, onAction, modifier)
            "DateRangePicker" -> NanoInputComponents.RenderDateRangePicker(ir, state, onAction, modifier)
            "DataChart" -> NanoDataComponents.RenderDataChart(ir, state, modifier)
            "DataTable" -> NanoDataComponents.RenderDataTable(ir, state, onAction, modifier)
            // Control Flow
            "Conditional" -> NanoControlFlowComponents.RenderConditional(ir, state, onAction, modifier, renderNode)
            "ForLoop" -> NanoControlFlowComponents.RenderForLoop(ir, state, onAction, modifier, renderNode)
            // Meta
            "Component" -> NanoLayoutComponents.RenderComponent(ir, state, onAction, modifier, renderNode)
            else -> NanoControlFlowComponents.RenderUnknown(ir, modifier)
        }
    }
}

/**
 * Decode image bytes to ImageBitmap.
 * Platform-specific implementation.
 */
internal expect fun decodeImageBytesToBitmap(bytes: ByteArray): ImageBitmap
