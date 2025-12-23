package cc.unitmesh.devins.ui.nano.components.input

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.nano.ComposeNodeContext
import cc.unitmesh.devins.ui.nano.toLegacyRenderNode
import cc.unitmesh.xuiper.action.NanoActionFactory
import cc.unitmesh.xuiper.ir.stringProp
import cc.unitmesh.xuiper.render.stateful.NanoNodeRenderer
import cc.unitmesh.xuiper.render.toSelectionState

/**
 * Selection components for NanoUI Compose renderer.
 * Includes: Select, Radio, RadioGroup
 *
 * All components use the unified NanoNodeContext interface.
 */
object SelectionComponents {

    val selectRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderSelect(ctx) }
    }

    val radioRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderRadio(ctx) }
    }

    val radioGroupRenderer = NanoNodeRenderer<Modifier, @Composable () -> Unit> { ctx ->
        { RenderRadioGroup(ctx) }
    }

    @Composable
    fun RenderSelect(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val placeholder = ir.stringProp("placeholder") ?: "Select..."
        val selectionState = ir.toSelectionState(ctx.state)
        var uncontrolledSelected by remember(selectionState.statePath, selectionState.selectedValue) {
            mutableStateOf(selectionState.selectedValue)
        }
        val selectedValue = if (selectionState.statePath != null) {
            ctx.state[selectionState.statePath]?.toString() ?: uncontrolledSelected
        } else {
            uncontrolledSelected
        }
        var expanded by remember { mutableStateOf(false) }

        val displayText = if (selectedValue.isNotEmpty()) {
            selectionState.options.find { it.value == selectedValue }?.label ?: selectedValue
        } else {
            placeholder
        }

        Box(modifier = ctx.payload.widthIn(min = 120.dp)) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(displayText)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                selectionState.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            expanded = false
                            NanoActionFactory.dispatchSelection(selectionState.statePath, option.value, ctx.onAction) {
                                uncontrolledSelected = option.value
                            }
                            selectionState.onChange?.let { ctx.onAction(it) }
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun RenderRadio(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val option = ir.stringProp("option") ?: ""
        val label = ir.stringProp("label") ?: option
        val selectionState = ir.toSelectionState(ctx.state)
        var uncontrolledSelected by remember(selectionState.statePath, selectionState.selectedValue) {
            mutableStateOf(selectionState.selectedValue)
        }
        val selectedValue = if (selectionState.statePath != null) {
            ctx.state[selectionState.statePath]?.toString() ?: uncontrolledSelected
        } else {
            uncontrolledSelected
        }
        val isSelected = selectedValue == option

        Row(modifier = ctx.payload, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = isSelected,
                onClick = {
                    NanoActionFactory.dispatchSelection(selectionState.statePath, option, ctx.onAction) {
                        uncontrolledSelected = option
                    }
                    selectionState.onChange?.let { ctx.onAction(it) }
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label)
        }
    }

    @Composable
    fun RenderRadioGroup(ctx: ComposeNodeContext) {
        val ir = ctx.node
        val children = ir.children.orEmpty()

        // If has children, render them
        if (children.isNotEmpty()) {
            Column(modifier = ctx.payload, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                children.forEach { child ->
                    ctx.renderChild(child, Modifier).invoke()
                }
            }
            return
        }

        // Otherwise render from options
        val selectionState = ir.toSelectionState(ctx.state)
        var uncontrolledSelected by remember(selectionState.statePath) { mutableStateOf("") }
        val selectedValue = if (selectionState.statePath != null) {
            ctx.state[selectionState.statePath]?.toString() ?: uncontrolledSelected
        } else {
            uncontrolledSelected
        }

        Column(modifier = ctx.payload, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            selectionState.options.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedValue == option.value,
                        onClick = {
                            NanoActionFactory.dispatchSelection(selectionState.statePath, option.value, ctx.onAction) {
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
}
