package cc.unitmesh.devins.ui.nano

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.xuiper.action.NanoActionFactory
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.ir.stringProp
import cc.unitmesh.xuiper.render.toSelectionState

/**
 * Material3 Compose implementation of selection components.
 *
 * Uses utilities from [NanoSelectionRenderer] for parsing and state resolution,
 * and renders using Material3 Compose components.
 */
object NanoSelectionRenderer {

    @Composable
    fun RenderSelect(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier = Modifier
    ) {
        val placeholder = ir.stringProp("placeholder") ?: "Select..."
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

        val displayText = if (selectedValue.isNotEmpty()) {
            selectionState.options.find { it.value == selectedValue }?.label ?: selectedValue
        } else {
            placeholder
        }

        Box(modifier = modifier.widthIn(min = 120.dp)) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(displayText)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                selectionState.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            expanded = false
                            dispatchSelection(selectionState.statePath, option.value, onAction) {
                                uncontrolledSelected = option.value
                            }
                            selectionState.onChange?.let { onAction(it) }
                        }
                    )
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
        val option = ir.stringProp("option") ?: ""
        val label = ir.stringProp("label") ?: option
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

        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = isSelected,
                onClick = {
                    dispatchSelection(selectionState.statePath, option, onAction) {
                        uncontrolledSelected = option
                    }
                    selectionState.onChange?.let { onAction(it) }
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedValue == option.value,
                        onClick = {
                            dispatchSelection(selectionState.statePath, option.value, onAction) {
                                uncontrolledSelected = option.value
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(option.label)
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
        NanoActionFactory.dispatchSelection(statePath, value, onAction, onUncontrolled)
    }
}

