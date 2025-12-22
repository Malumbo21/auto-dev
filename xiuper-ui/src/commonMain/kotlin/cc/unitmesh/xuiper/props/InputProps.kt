package cc.unitmesh.xuiper.props

import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.props.PropExtractors.boolProp
import cc.unitmesh.xuiper.props.PropExtractors.floatProp
import cc.unitmesh.xuiper.props.PropExtractors.intProp
import cc.unitmesh.xuiper.props.PropExtractors.stringProp

/**
 * Properties for Input component.
 */
data class InputProps(
    val placeholder: String,
    val label: String?,
    val type: String,
    val disabled: Boolean,
    val statePath: String?,
    val onChange: NanoActionIR?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): InputProps = InputProps(
            placeholder = ir.stringProp("placeholder", ""),
            label = ir.stringProp("label"),
            type = ir.stringProp("type", "text"),
            disabled = ir.boolProp("disabled", false),
            statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind"),
            onChange = ir.actions?.get("onChange")
        )
    }
}

/**
 * Properties for TextArea component.
 */
data class TextAreaProps(
    val placeholder: String,
    val label: String?,
    val rows: Int,
    val disabled: Boolean,
    val statePath: String?,
    val onChange: NanoActionIR?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): TextAreaProps = TextAreaProps(
            placeholder = ir.stringProp("placeholder", ""),
            label = ir.stringProp("label"),
            rows = ir.intProp("rows", 4),
            disabled = ir.boolProp("disabled", false),
            statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind"),
            onChange = ir.actions?.get("onChange")
        )
    }
}

/**
 * Properties for Switch component.
 */
data class SwitchProps(
    val label: String?,
    val disabled: Boolean,
    val statePath: String?,
    val onChange: NanoActionIR?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): SwitchProps = SwitchProps(
            label = ir.stringProp("label"),
            disabled = ir.boolProp("disabled", false),
            statePath = NanoBindingResolver.resolveStatePath(ir, "checked", "value", "bind"),
            onChange = ir.actions?.get("onChange")
        )
    }
}

/**
 * Properties for Checkbox component.
 */
data class CheckboxProps(
    val label: String?,
    val value: String?,
    val disabled: Boolean,
    val statePath: String?,
    val onChange: NanoActionIR?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): CheckboxProps = CheckboxProps(
            label = ir.stringProp("label"),
            value = ir.stringProp("value"),
            disabled = ir.boolProp("disabled", false),
            statePath = NanoBindingResolver.resolveStatePath(ir, "checked", "value", "bind"),
            onChange = ir.actions?.get("onChange")
        )
    }
}

/**
 * Properties for Slider component.
 */
data class SliderProps(
    val label: String?,
    val min: Float,
    val max: Float,
    val step: Float?,
    val disabled: Boolean,
    val statePath: String?,
    val onChange: NanoActionIR?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): SliderProps = SliderProps(
            label = ir.stringProp("label"),
            min = ir.floatProp("min", 0f),
            max = ir.floatProp("max", 100f),
            step = ir.floatProp("step"),
            disabled = ir.boolProp("disabled", false),
            statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind"),
            onChange = ir.actions?.get("onChange")
        )
    }
}

/**
 * Properties for Stepper component.
 */
data class StepperProps(
    val label: String?,
    val min: Int,
    val max: Int,
    val step: Int,
    val disabled: Boolean,
    val statePath: String?,
    val onChange: NanoActionIR?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): StepperProps = StepperProps(
            label = ir.stringProp("label"),
            min = ir.intProp("min", 0),
            max = ir.intProp("max", 100),
            step = ir.intProp("step", 1),
            disabled = ir.boolProp("disabled", false),
            statePath = NanoBindingResolver.resolveStatePath(ir, "value", "bind"),
            onChange = ir.actions?.get("onChange")
        )
    }
}

