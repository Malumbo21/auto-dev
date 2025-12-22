package cc.unitmesh.devins.ui.nano

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.props.NanoOption
import cc.unitmesh.xuiper.props.NanoOptionParser
import kotlinx.serialization.json.JsonElement

/**
 * Selection components for NanoUI Compose renderer.
 * Includes: Select, Radio, RadioGroup with shared option parsing logic.
 *
 * This object now delegates to [NanoSelectionRenderer] for actual rendering,
 * and uses [NanoOptionParser] from xiuper-ui for parsing logic.
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
     * Delegates to [NanoOptionParser.parse] from xiuper-ui.
     */
    fun parseOptions(optionsElement: JsonElement?): List<NanoOption> {
        return NanoOptionParser.parse(optionsElement)
    }

    /**
     * Render a dropdown select component.
     * Delegates to [NanoSelectionRenderer.RenderSelect].
     */
    @Composable
    fun RenderSelect(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        NanoSelectionRenderer.RenderSelect(ir, state, onAction, modifier)
    }

    /**
     * Render a single radio button component.
     * Delegates to [NanoSelectionRenderer.RenderRadio].
     */
    @Composable
    fun RenderRadio(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier
    ) {
        NanoSelectionRenderer.RenderRadio(ir, state, onAction, modifier)
    }

    /**
     * Render a radio group component.
     * Delegates to [NanoSelectionRenderer.RenderRadioGroup].
     */
    @Composable
    fun RenderRadioGroup(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        modifier: Modifier,
        renderNode: @Composable (NanoIR, Map<String, Any>, (NanoActionIR) -> Unit, Modifier) -> Unit
    ) {
        NanoSelectionRenderer.RenderRadioGroup(ir, state, onAction, modifier, renderNode)
    }
}

