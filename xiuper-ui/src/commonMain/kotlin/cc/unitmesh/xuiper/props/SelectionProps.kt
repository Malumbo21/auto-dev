package cc.unitmesh.xuiper.props

import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.props.PropExtractors.boolProp
import cc.unitmesh.xuiper.props.PropExtractors.stringProp
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Properties for Select component.
 */
data class SelectProps(
    val placeholder: String,
    val label: String?,
    val disabled: Boolean,
    val options: List<NanoOption>,
    val selectedValue: String,
    val statePath: String?,
    val onChange: NanoActionIR?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR, state: Map<String, Any> = emptyMap()): SelectProps {
            val statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind")
            val selectedFromState = statePath?.let { state[it]?.toString() }
            val selectedProp = ir.props["value"]?.jsonPrimitive?.contentOrNull
            
            return SelectProps(
                placeholder = ir.stringProp("placeholder", "Select..."),
                label = ir.stringProp("label"),
                disabled = ir.boolProp("disabled", false),
                options = NanoOptionParser.parse(ir.props["options"]),
                selectedValue = selectedFromState ?: selectedProp ?: "",
                statePath = statePath,
                onChange = ir.actions?.get("onChange")
            )
        }
    }
}

/**
 * Properties for Radio component.
 */
data class RadioProps(
    val label: String?,
    val value: String,
    val disabled: Boolean,
    val selectedValue: String,
    val statePath: String?,
    val onChange: NanoActionIR?
) : ComponentProps {
    
    /** Whether this radio is currently selected */
    val isSelected: Boolean get() = value == selectedValue
    
    companion object {
        fun parse(ir: NanoIR, state: Map<String, Any> = emptyMap()): RadioProps {
            val statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind")
            val selectedFromState = statePath?.let { state[it]?.toString() }
            val selectedProp = ir.props["selected"]?.jsonPrimitive?.contentOrNull
            val value = ir.stringProp("value", "")
            
            return RadioProps(
                label = ir.stringProp("label"),
                value = value,
                disabled = ir.boolProp("disabled", false),
                selectedValue = selectedFromState ?: selectedProp ?: "",
                statePath = statePath,
                onChange = ir.actions?.get("onChange")
            )
        }
    }
}

/**
 * Properties for RadioGroup component.
 */
data class RadioGroupProps(
    val label: String?,
    val options: List<NanoOption>,
    val selectedValue: String,
    val direction: String,
    val disabled: Boolean,
    val statePath: String?,
    val onChange: NanoActionIR?
) : ComponentProps {
    
    /** Whether layout should be horizontal */
    val isHorizontal: Boolean get() = direction == "horizontal" || direction == "row"
    
    companion object {
        fun parse(ir: NanoIR, state: Map<String, Any> = emptyMap()): RadioGroupProps {
            val statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind")
            val selectedFromState = statePath?.let { state[it]?.toString() }
            val selectedProp = ir.props["value"]?.jsonPrimitive?.contentOrNull
            
            return RadioGroupProps(
                label = ir.stringProp("label"),
                options = NanoOptionParser.parse(ir.props["options"]),
                selectedValue = selectedFromState ?: selectedProp ?: "",
                direction = ir.stringProp("direction", "vertical"),
                disabled = ir.boolProp("disabled", false),
                statePath = statePath,
                onChange = ir.actions?.get("onChange")
            )
        }
    }
}

/**
 * Properties for CheckboxGroup component.
 */
data class CheckboxGroupProps(
    val label: String?,
    val options: List<NanoOption>,
    val selectedValues: Set<String>,
    val direction: String,
    val disabled: Boolean,
    val statePath: String?,
    val onChange: NanoActionIR?
) : ComponentProps {
    
    /** Whether layout should be horizontal */
    val isHorizontal: Boolean get() = direction == "horizontal" || direction == "row"
    
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun parse(ir: NanoIR, state: Map<String, Any> = emptyMap()): CheckboxGroupProps {
            val statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind")
            val selectedFromState = statePath?.let { 
                when (val v = state[it]) {
                    is Set<*> -> (v as? Set<String>) ?: emptySet()
                    is List<*> -> (v as? List<String>)?.toSet() ?: emptySet()
                    is String -> setOf(v)
                    else -> emptySet()
                }
            } ?: emptySet()
            
            return CheckboxGroupProps(
                label = ir.stringProp("label"),
                options = NanoOptionParser.parse(ir.props["options"]),
                selectedValues = selectedFromState,
                direction = ir.stringProp("direction", "vertical"),
                disabled = ir.boolProp("disabled", false),
                statePath = statePath,
                onChange = ir.actions?.get("onChange")
            )
        }
    }
}

