package cc.unitmesh.devins.idea.renderer.nano

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.xuiper.components.input.SelectOption
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.render.NanoSelectionRenderer
import cc.unitmesh.xuiper.render.toSelectionState
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text

/**
 * Jewel (IntelliJ IDEA) implementation of selection components.
 * 
 * Uses utilities from [NanoSelectionRenderer] for parsing and state resolution,
 * and renders using Jewel Compose components for native IntelliJ look and feel.
 */
object JewelSelectionRenderer {

    @Composable
    fun RenderSelect(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier = Modifier
    ) {
        val placeholder = ir.props["placeholder"]?.jsonPrimitive?.content ?: "Select..."
        val selectionState = ir.toSelectionState(state)
        var uncontrolledSelected by remember(selectionState.statePath, selectionState.selectedValue) {
            mutableStateOf(selectionState.selectedValue)
        }
        val selectedValue = if (selectionState.statePath != null) {
            state[selectionState.statePath]?.toString() ?: uncontrolledSelected
        } else {
            uncontrolledSelected
        }
        var expanded by remember { mutableStateOf(false) }
        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()

        val displayText = if (selectedValue.isNotEmpty()) {
            selectionState.options.find { it.value == selectedValue }?.label ?: selectedValue
        } else {
            placeholder
        }

        Box(modifier = modifier.widthIn(min = 120.dp)) {
            // Select button styled like IntelliJ dropdown
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .hoverable(interactionSource = interactionSource)
                    .background(
                        if (isHovered || expanded)
                            JewelTheme.globalColors.borders.normal.copy(alpha = 0.3f)
                        else
                            JewelTheme.globalColors.panelBackground
                    )
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = displayText,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 13.sp,
                        color = if (selectedValue.isEmpty()) 
                            JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                        else 
                            JewelTheme.globalColors.text.normal
                    )
                )
                Text(
                    text = "▼",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 10.sp,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                    )
                )
            }

            // Dropdown popup
            if (expanded) {
                PopupMenu(
                    onDismissRequest = {
                        expanded = false
                        true
                    },
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.widthIn(min = 150.dp)
                ) {
                    selectionState.options.forEach { option ->
                        selectableItem(
                            selected = option.value == selectedValue,
                            onClick = {
                                expanded = false
                                dispatchSelection(selectionState.statePath, option.value, onAction) {
                                    uncontrolledSelected = option.value
                                }
                                selectionState.onChange?.let { onAction(it) }
                            }
                        ) {
                            Text(
                                text = option.label,
                                style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun RenderRadio(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier = Modifier
    ) {
        val option = ir.props["option"]?.jsonPrimitive?.content ?: ""
        val label = ir.props["label"]?.jsonPrimitive?.content ?: option
        val selectionState = ir.toSelectionState(state)
        var uncontrolledSelected by remember(selectionState.statePath, selectionState.selectedValue) {
            mutableStateOf(selectionState.selectedValue)
        }
        val selectedValue = if (selectionState.statePath != null) {
            state[selectionState.statePath]?.toString() ?: uncontrolledSelected
        } else {
            uncontrolledSelected
        }
        val isSelected = selectedValue == option

        RadioButtonRow(
            selected = isSelected,
            onClick = {
                dispatchSelection(selectionState.statePath, option, onAction) {
                    uncontrolledSelected = option
                }
                selectionState.onChange?.let { onAction(it) }
            },
            modifier = modifier
        ) {
            Text(label, color = JewelTheme.globalColors.text.normal)
        }
    }

    @Composable
    fun RenderRadioGroup(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier = Modifier,
        renderNode: @Composable (NanoIR, Map<String, Any>, (NanoActionIR) -> Unit, Modifier) -> Unit
    ) {
        val children = ir.children.orEmpty()
        if (children.isNotEmpty()) {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                children.forEach { child -> renderNode(child, state, onAction, Modifier) }
            }
            return
        }

        val selectionState = ir.toSelectionState(state)
        var uncontrolledSelected by remember(selectionState.statePath) { mutableStateOf("") }
        val selectedValue = if (selectionState.statePath != null) {
            state[selectionState.statePath]?.toString() ?: uncontrolledSelected
        } else {
            uncontrolledSelected
        }

        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            selectionState.options.forEach { option ->
                RadioButtonRow(
                    selected = selectedValue == option.value,
                    onClick = {
                        dispatchSelection(selectionState.statePath, option.value, onAction) {
                            uncontrolledSelected = option.value
                        }
                    },
                    modifier = Modifier
                ) {
                    Text(option.label, color = JewelTheme.globalColors.text.normal)
                }
            }
        }
    }

    private fun dispatchSelection(
        statePath: String?,
        value: String,
        onAction: (NanoActionIR) -> Unit,
        onUncontrolled: () -> Unit
    ) {
        if (statePath != null) {
            onAction(NanoActionIR(
                type = "stateMutation",
                payload = mapOf(
                    "path" to JsonPrimitive(statePath),
                    "operation" to JsonPrimitive("SET"),
                    "value" to JsonPrimitive(value)
                )
            ))
        } else {
            onUncontrolled()
        }
    }
}

