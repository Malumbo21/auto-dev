package cc.unitmesh.xuiper.render

import cc.unitmesh.xuiper.components.input.SelectOption
import cc.unitmesh.xuiper.components.input.SelectionUtils
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoBindingIR
import cc.unitmesh.xuiper.ir.NanoIR
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
 * - Common parsing utilities via [SelectionUtils]
 * - State path resolution via [resolveStatePathFromBinding]
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
         */
        fun parseOptions(optionsElement: JsonElement?): List<SelectOption> {
            return SelectionUtils.parseOptions(optionsElement)
        }
        
        /**
         * Resolve state path from binding expressions.
         * 
         * Supports:
         * - Direct binding: `bindings.value` or `bindings.bind`
         * - Props with binding syntax: `:= state.path` or `<< state.path`
         * 
         * @param ir The NanoIR component
         * @param keys Keys to check for bindings (e.g., "value", "bind")
         * @return The resolved state path, or null if not found
         */
        fun resolveStatePathFromBinding(ir: NanoIR, vararg keys: String): String? {
            val binding = keys.firstNotNullOfOrNull { ir.bindings?.get(it) }
            val exprFromBinding = binding?.expression?.trim()
            val exprFromProp = keys.firstNotNullOfOrNull { key ->
                ir.props[key]?.jsonPrimitive?.contentOrNull?.trim()
            }

            val rawExpr = (exprFromBinding ?: exprFromProp)?.trim() ?: return null
            val withoutMode = when {
                rawExpr.startsWith(":=") -> rawExpr.removePrefix(":=").trim()
                rawExpr.startsWith("<<") -> rawExpr.removePrefix("<<").trim()
                else -> rawExpr
            }

            val normalized = if (withoutMode.startsWith("state.")) withoutMode.removePrefix("state.") else withoutMode
            // Only treat simple identifiers or dotted paths as a writable state path.
            // This prevents expressions like '"x" in state.items' from being treated as a path.
            return normalized.takeIf { it.matches(Regex("[A-Za-z_]\\w*(\\.[A-Za-z_]\\w*)*")) }
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
    val options: List<SelectOption>,
    val onChange: NanoActionIR?
)

/**
 * Helper to extract common selection state from IR.
 */
fun NanoIR.toSelectionState(
    state: Map<String, Any>,
    valueKeys: Array<String> = arrayOf("value", "bind")
): SelectionState {
    val statePath = NanoSelectionRenderer.resolveStatePathFromBinding(this, *valueKeys)
    val selectedFromState = statePath?.let { state[it]?.toString() }
    val selectedProp = props["value"]?.jsonPrimitive?.contentOrNull
    val selectedValue = selectedFromState ?: selectedProp ?: ""
    val options = NanoSelectionRenderer.parseOptions(props["options"])
    val onChange = actions?.get("onChange")
    
    return SelectionState(
        statePath = statePath,
        selectedValue = selectedValue,
        options = options,
        onChange = onChange
    )
}

