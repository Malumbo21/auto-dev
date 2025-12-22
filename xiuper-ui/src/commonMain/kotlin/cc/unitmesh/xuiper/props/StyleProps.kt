package cc.unitmesh.xuiper.props

import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.props.PropExtractors.iconSizeProp
import cc.unitmesh.xuiper.props.PropExtractors.radiusProp
import cc.unitmesh.xuiper.props.PropExtractors.shadowProp
import cc.unitmesh.xuiper.props.PropExtractors.stringProp

/**
 * Common style properties used across multiple components.
 *
 * This provides a unified way to parse color, size, radius, and shadow
 * properties that are commonly used in UI components.
 */
data class StyleProps(
    /** Color name (e.g., "primary", "secondary", "green", "red") */
    val color: String?,
    /** Background color name */
    val bgColor: String?,
    /** Size token (e.g., "sm", "md", "lg") */
    val size: String?,
    /** Parsed radius in dp */
    val radius: Int,
    /** Parsed shadow elevation in dp */
    val shadow: Int
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): StyleProps = StyleProps(
            color = ir.stringProp("color"),
            bgColor = ir.stringProp("bgColor") ?: ir.stringProp("backgroundColor"),
            size = ir.stringProp("size"),
            radius = ir.radiusProp("radius"),
            shadow = ir.shadowProp("shadow")
        )
    }
}

/**
 * Properties for Icon component.
 */
data class IconProps(
    /** Icon name (e.g., "check", "close", "star") */
    val name: String,
    /** Parsed icon size in dp */
    val size: Int,
    /** Color name for tinting */
    val color: String?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): IconProps = IconProps(
            name = ir.stringProp("name", "info"),
            size = ir.iconSizeProp("size"),
            color = ir.stringProp("color")
        )
    }
}

/**
 * Properties for Badge component.
 */
data class BadgeProps(
    /** Badge text content */
    val text: String,
    /** Color name for badge background */
    val color: String?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): BadgeProps = BadgeProps(
            text = ir.stringProp("text", ""),
            color = ir.stringProp("color")
        )
    }
}

/**
 * Properties for Progress component.
 */
data class ProgressProps(
    /** Progress value (0-100) */
    val value: Float,
    /** Maximum value */
    val max: Float,
    /** Progress bar color */
    val color: String?,
    /** Show percentage label */
    val showLabel: Boolean,
    /** Progress status (normal, success, exception) */
    val status: String?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): ProgressProps {
            val value = ir.stringProp("value")?.toFloatOrNull() ?: 0f
            val max = ir.stringProp("max")?.toFloatOrNull() ?: 100f
            return ProgressProps(
                value = value,
                max = max,
                color = ir.stringProp("color"),
                showLabel = ir.stringProp("showLabel")?.toBooleanStrictOrNull() ?: false,
                status = ir.stringProp("status")
            )
        }
    }

    /** Get progress as a fraction (0.0 to 1.0) */
    val fraction: Float get() = if (max > 0) (value / max).coerceIn(0f, 1f) else 0f
}

