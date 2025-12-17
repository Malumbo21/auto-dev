package cc.unitmesh.devins.ui.nano

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import cc.unitmesh.yaml.YamlUtils

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

    private fun parseStructuredDefault(raw: String): Any {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        return try {
            // JSON is valid YAML 1.2, so this handles both JSON/YAML list/map literals.
            YamlUtils.load(trimmed) ?: raw
        } catch (_: Exception) {
            raw
        }
    }

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
        // Initialize state from IR
        val stateMap = remember { mutableStateMapOf<String, Any>() }

        // Initialize state values from IR state definitions
        LaunchedEffect(ir) {
            ir.state?.variables?.forEach { (name, varDef) ->
                val defaultValue = varDef.defaultValue
                stateMap[name] = when (varDef.type) {
                    "int" -> defaultValue?.jsonPrimitive?.intOrNull ?: 0
                    "float" -> defaultValue?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                    "bool" -> defaultValue?.jsonPrimitive?.booleanOrNull ?: false
                    "str" -> defaultValue?.jsonPrimitive?.content ?: ""
                    "list" -> {
                        val raw = defaultValue?.jsonPrimitive?.content ?: "[]"
                        val parsed = parseStructuredDefault(raw)
                        if (parsed is List<*>) parsed else emptyList<Any>()
                    }
                    "dict", "map", "object" -> {
                        val raw = defaultValue?.jsonPrimitive?.content ?: "{}"
                        val parsed = parseStructuredDefault(raw)
                        if (parsed is Map<*, *>) parsed else emptyMap<String, Any>()
                    }
                    else -> defaultValue?.jsonPrimitive?.content ?: ""
                }
            }
        }

        // Create action handler
        val handleAction: (NanoActionIR) -> Unit = handleAction@{ action ->
            when (action.type) {
                "stateMutation" -> {
                    val payload = action.payload ?: return@handleAction
                    val path = payload["path"]?.jsonPrimitive?.content ?: return@handleAction
                    val operation = payload["operation"]?.jsonPrimitive?.content ?: "SET"
                    val valueStr = payload["value"]?.jsonPrimitive?.content ?: ""

                    val currentValue = stateMap[path]
                    val newValue = when (operation) {
                        "ADD" -> {
                            when (currentValue) {
                                is Int -> currentValue + (valueStr.toIntOrNull() ?: 1)
                                is Float -> currentValue + (valueStr.toFloatOrNull() ?: 1f)
                                else -> currentValue
                            }
                        }
                        "SUBTRACT" -> {
                            when (currentValue) {
                                is Int -> currentValue - (valueStr.toIntOrNull() ?: 1)
                                is Float -> currentValue - (valueStr.toFloatOrNull() ?: 1f)
                                else -> currentValue
                            }
                        }
                        "SET" -> {
                            when (currentValue) {
                                is Int -> valueStr.toIntOrNull() ?: 0
                                is Float -> valueStr.toFloatOrNull() ?: 0f
                                is Boolean -> valueStr.toBooleanStrictOrNull() ?: false
                                else -> valueStr
                            }
                        }
                        else -> valueStr
                    }

                    if (newValue != null) {
                        stateMap[path] = newValue
                    }
                }
            }
        }

        RenderNode(ir, stateMap, handleAction, modifier)
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
            "Badge" -> NanoContentComponents.RenderBadge(ir, modifier)
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
            "Alert" -> NanoFeedbackComponents.RenderAlert(ir, modifier)
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
