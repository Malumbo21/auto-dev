package cc.unitmesh.devins.ui.nano

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cc.unitmesh.xuiper.components.input.SelectOption
import cc.unitmesh.xuiper.components.input.SelectionUtils
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import kotlinx.serialization.json.JsonElement

/**
 * Selection components for NanoUI Compose renderer.
 * Includes: Select, Radio, RadioGroup with shared option parsing logic.
 *
 * This object now delegates to [Material3SelectionRenderer] for actual rendering,
 * and uses [SelectionUtils] from xiuper-ui for parsing logic.
 *
 * The parsing utilities are exposed for backward compatibility with existing tests.
 */
object NanoSelectionComponents {

    /**
     * Parse options from JsonElement. Supports:
     * - JsonArray of strings: ["A", "B", "C"]
     * - JsonArray of objects: [{"value": "a", "label": "Option A"}]
     * - JsonPrimitive (string) containing JSON or DSL format
     *
     * Delegates to [SelectionUtils.parseOptions] from xiuper-ui.
     */
    fun parseOptions(optionsElement: JsonElement?): List<SelectOption> {
        return SelectionUtils.parseOptions(optionsElement)
    }

    /**
     * Render a dropdown select component.
     * Delegates to [Material3SelectionRenderer.RenderSelect].
     */
    @Composable
    fun RenderSelect(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        Material3SelectionRenderer.RenderSelect(ir, state, onAction, modifier)
    }

    /**
     * Render a single radio button component.
     * Delegates to [Material3SelectionRenderer.RenderRadio].
     */
    @Composable
    fun RenderRadio(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        Material3SelectionRenderer.RenderRadio(ir, state, onAction, modifier)
    }

    /**
     * Render a radio group component.
     * Delegates to [Material3SelectionRenderer.RenderRadioGroup].
     */
    @Composable
    fun RenderRadioGroup(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier,
        renderNode: @Composable (NanoIR, Map<String, Any>, (NanoActionIR) -> Unit, Modifier) -> Unit
    ) {
        Material3SelectionRenderer.RenderRadioGroup(ir, state, onAction, modifier, renderNode)
    }
}

