package cc.unitmesh.devins.ui.nano

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.devins.ui.nano.theme.NanoThemeState
import cc.unitmesh.devins.ui.nano.theme.LocalNanoThemeApplied
import cc.unitmesh.devins.ui.nano.theme.ProvideNanoTheme
import cc.unitmesh.devins.ui.nano.theme.rememberNanoThemeState
import cc.unitmesh.xuiper.render.stateful.StatefulNanoSession
import cc.unitmesh.xuiper.render.stateful.StatefulNanoTreeDispatcher

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
 * - [cc.unitmesh.xuiper.eval.evaluator.NanoExpressionEvaluator] - Helper functions and utilities
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
        modifier: Modifier = Modifier,
        themeState: NanoThemeState? = null
    ) {
        when {
            themeState != null -> ProvideNanoTheme(state = themeState) {
                RenderInternal(ir = ir, modifier = modifier)
            }

            // Avoid overriding an already-applied NanoTheme upstream.
            LocalNanoThemeApplied.current -> RenderInternal(ir = ir, modifier = modifier)

            else -> {
                val defaultThemeState = rememberNanoThemeState()
                ProvideNanoTheme(state = defaultThemeState) {
                    RenderInternal(ir = ir, modifier = modifier)
                }
            }
        }
    }

    @Composable
    private fun RenderInternal(
        ir: NanoIR,
        modifier: Modifier = Modifier
    ) {
        // Runtime wraps NanoState + action application logic.
        // Recreated when IR changes (e.g. live preview re-parses NanoDSL).
        // NOTE: Compose doesn't allow try/catch around composable invocations, so we catch
        // failures in non-composable initialization here to keep UI alive.
        val sessionResult = remember(ir) { StatefulNanoSession.create(ir) }
        val session = sessionResult.getOrNull()
        val runtimeError = sessionResult.exceptionOrNull()

        if (session == null) {
            println("StatefulNanoRenderer init error: $runtimeError")
            Surface(
                modifier = modifier,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = "渲染初始化失败：${runtimeError?.message ?: runtimeError?.let { it::class.simpleName }.orEmpty()}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            return
        }

        // Subscribe to declared state keys so Compose recomposes when they change.
        // IMPORTANT: we must *read* the collected State's `.value` so Compose tracks it.
        // Use a stable key order to keep hook ordering deterministic across recompositions.
        val observedKeys = remember(session) { session.observedKeys }
        observedKeys.forEach { key ->
            session.flow(key).collectAsState().value
        }

        val dispatcher = remember {
            StatefulNanoTreeDispatcher<Modifier, @Composable () -> Unit> { node, state, onAction, nodeModifier, renderChild ->
                val renderNode: @Composable (NanoIR, Map<String, Any>, (NanoActionIR) -> Unit, Modifier) -> Unit =
                    { childIr, _, _, childModifier ->
                        renderChild(childIr, childModifier).invoke()
                    }

                when (node.type) {
                    // Layout
                    "VStack" -> ({ NanoLayoutComponents.RenderVStack(node, state, onAction, nodeModifier, renderNode) })
                    "HStack" -> ({ NanoLayoutComponents.RenderHStack(node, state, onAction, nodeModifier, renderNode) })
                    // Container
                    "Card" -> ({ NanoLayoutComponents.RenderCard(node, state, onAction, nodeModifier, renderNode) })
                    "Form" -> ({ NanoLayoutComponents.RenderForm(node, state, onAction, nodeModifier, renderNode) })
                    // Content
                    "Text" -> ({ NanoContentComponents.RenderText(node, state, nodeModifier) })
                    "Image" -> ({ RenderImage(node, nodeModifier) })
                    "Badge" -> ({ NanoContentComponents.RenderBadge(node, state, nodeModifier) })
                    "Icon" -> ({ NanoContentComponents.RenderIcon(node, nodeModifier) })
                    "Divider" -> ({ NanoContentComponents.RenderDivider(nodeModifier) })
                    "Code" -> ({ NanoContentComponents.RenderCode(node, state, nodeModifier) })
                    "Link" -> ({ NanoContentComponents.RenderLink(node, state, nodeModifier) })
                    "Blockquote" -> ({ NanoContentComponents.RenderBlockquote(node, state, nodeModifier) })
                    // Input
                    "Button" -> ({ NanoInputComponents.RenderButton(node, state, onAction, nodeModifier) })
                    "Input" -> ({ NanoInputComponents.RenderInput(node, state, onAction, nodeModifier) })
                    "Checkbox" -> ({ NanoInputComponents.RenderCheckbox(node, state, onAction, nodeModifier) })
                    "TextArea" -> ({ NanoInputComponents.RenderTextArea(node, state, onAction, nodeModifier) })
                    "Select" -> ({ NanoInputComponents.RenderSelect(node, state, onAction, nodeModifier) })
                    // P0: Core Form Input Components
                    "DatePicker" -> ({ NanoInputComponents.RenderDatePicker(node, state, onAction, nodeModifier) })
                    "Radio" -> ({ NanoInputComponents.RenderRadio(node, state, onAction, nodeModifier) })
                    "RadioGroup" -> ({ NanoInputComponents.RenderRadioGroup(node, state, onAction, nodeModifier, renderNode) })
                    "Switch" -> ({ NanoInputComponents.RenderSwitch(node, state, onAction, nodeModifier) })
                    "NumberInput" -> ({ NanoInputComponents.RenderNumberInput(node, state, onAction, nodeModifier) })
                    // P0: Feedback Components
                    "Modal" -> ({ NanoFeedbackComponents.RenderModal(node, state, onAction, nodeModifier, renderNode) })
                    "Alert" -> ({ NanoFeedbackComponents.RenderAlert(node, nodeModifier, onAction) })
                    "Progress" -> ({ NanoFeedbackComponents.RenderProgress(node, state, nodeModifier) })
                    "Spinner" -> ({ NanoFeedbackComponents.RenderSpinner(node, nodeModifier) })
                    // Tier 1-3: GenUI Components
                    "SplitView" -> ({ NanoLayoutComponents.RenderSplitView(node, state, onAction, nodeModifier, renderNode) })
                    "SmartTextField" -> ({ NanoInputComponents.RenderSmartTextField(node, state, onAction, nodeModifier) })
                    "Slider" -> ({ NanoInputComponents.RenderSlider(node, state, onAction, nodeModifier) })
                    "DateRangePicker" -> ({ NanoInputComponents.RenderDateRangePicker(node, state, onAction, nodeModifier) })
                    "DataChart" -> ({ NanoDataComponents.RenderDataChart(node, state, nodeModifier) })
                    "DataTable" -> ({ NanoDataComponents.RenderDataTable(node, state, onAction, nodeModifier) })
                    // Control Flow
                    "Conditional" -> ({ NanoControlFlowComponents.RenderConditional(node, state, onAction, nodeModifier, renderNode) })
                    "ForLoop" -> ({ NanoControlFlowComponents.RenderForLoop(node, state, onAction, nodeModifier, renderNode) })
                    // Meta
                    "Component" -> ({ NanoLayoutComponents.RenderComponent(node, state, onAction, nodeModifier, renderNode) })
                    else -> ({ NanoControlFlowComponents.RenderUnknown(node, nodeModifier) })
                }
            }
        }

        dispatcher.render(session, ir, modifier).invoke()
    }
}

/**
 * Decode image bytes to ImageBitmap.
 * Platform-specific implementation.
 */
internal expect fun decodeImageBytesToBitmap(bytes: ByteArray): ImageBitmap
