package cc.unitmesh.xuiper.render

import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.props.NanoBindingResolver
import cc.unitmesh.xuiper.props.NanoOption
import cc.unitmesh.xuiper.props.NanoOptionParser
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Platform-agnostic interface for rendering selection components.
 *
 * This interface defines the contract for rendering Select, Radio, and RadioGroup
 * components. Platform-specific implementations (Material3, Jewel, etc.) should
 * implement this interface.
 *
 * The interface provides:
 * - Common parsing utilities via [NanoOptionParser]
 * - State path resolution via [NanoBindingResolver]
 * - Platform-specific rendering methods to be implemented
 */
interface NanoSelectionRenderer {

    /**
     * Render a dropdown select component.
     *
     * @param ir The NanoIR for the Select component
     * @param state Current state map
     * @param onAction Callback for dispatching actions
     */
    fun renderSelect(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit
    )

    /**
     * Render a single radio button component.
     *
     * @param ir The NanoIR for the Radio component
     * @param state Current state map
     * @param onAction Callback for dispatching actions
     */
    fun renderRadio(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit
    )

    /**
     * Render a radio group component.
     *
     * @param ir The NanoIR for the RadioGroup component
     * @param state Current state map
     * @param onAction Callback for dispatching actions
     * @param renderChild Callback for rendering child components (for nested Radio buttons)
     */
    fun renderRadioGroup(
        ir: NanoIR,
        state: Map<String, Any>,
        onAction: (NanoActionIR) -> Unit,
        renderChild: (NanoIR, Map<String, Any>, (NanoActionIR) -> Unit) -> Unit
    )

    companion object {
        /**
         * Parse options from IR props.
         * Delegates to [NanoOptionParser].
         */
        fun parseOptions(optionsElement: JsonElement?): List<NanoOption> {
            return NanoOptionParser.parse(optionsElement)
        }

        /**
         * Resolve state path from binding expressions.
         * Delegates to [NanoBindingResolver].
         *
         * @param ir The NanoIR component
         * @param keys Keys to check for bindings (e.g., "value", "bind")
         * @return The resolved state path, or null if not found
         */
        fun resolveStatePathFromBinding(ir: NanoIR, vararg keys: String): String? {
            return NanoBindingResolver.resolveStatePath(ir, *keys)
        }
    }
}

/**
 * Data class representing the resolved selection state.
 * Used by renderers to manage controlled/uncontrolled state.
 */
data class SelectionState(
    val statePath: String?,
    val selectedValue: String,
    val options: List<NanoOption>,
    val onChange: NanoActionIR?
)

/**
 * Helper to extract common selection state from IR.
 */
fun NanoIR.toSelectionState(
    state: Map<String, Any>,
    valueKeys: Array<String> = arrayOf("value", "bind")
): SelectionState {
    val statePath = NanoBindingResolver.resolveStatePath(this, *valueKeys)
    val selectedFromState = statePath?.let { state[it]?.toString() }
    val selectedProp = props["value"]?.jsonPrimitive?.contentOrNull
    val selectedValue = selectedFromState ?: selectedProp ?: ""
    val options = NanoOptionParser.parse(props["options"])
    val onChange = actions?.get("onChange")

    return SelectionState(
        statePath = statePath,
        selectedValue = selectedValue,
        options = options,
        onChange = onChange
    )
}

