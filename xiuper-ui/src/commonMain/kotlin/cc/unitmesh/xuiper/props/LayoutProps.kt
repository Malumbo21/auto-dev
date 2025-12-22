package cc.unitmesh.xuiper.props

import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.props.PropExtractors.boolProp
import cc.unitmesh.xuiper.props.PropExtractors.floatProp
import cc.unitmesh.xuiper.props.PropExtractors.paddingProp
import cc.unitmesh.xuiper.props.PropExtractors.shadowProp
import cc.unitmesh.xuiper.props.PropExtractors.spacingProp
import cc.unitmesh.xuiper.props.PropExtractors.stringProp

/**
 * Properties for VStack component.
 */
data class VStackProps(
    val spacing: Int,
    val align: String?,
    val flex: Float?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): VStackProps = VStackProps(
            spacing = ir.spacingProp("spacing"),
            align = ir.stringProp("align"),
            flex = ir.floatProp("flex")
        )
    }
}

/**
 * Properties for HStack component.
 */
data class HStackProps(
    val spacing: Int,
    val align: String?,
    val justify: String?,
    val wrap: Boolean,
    val flex: Float?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): HStackProps = HStackProps(
            spacing = ir.spacingProp("spacing"),
            align = ir.stringProp("align"),
            justify = ir.stringProp("justify"),
            wrap = ir.boolProp("wrap", false),
            flex = ir.floatProp("flex")
        )
    }
}

/**
 * Properties for Card component.
 */
data class CardProps(
    val padding: Int,
    val shadow: Int,
    val radius: Int
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): CardProps = CardProps(
            padding = ir.paddingProp("padding", 16),
            shadow = ir.shadowProp("shadow"),
            radius = NanoSizeMapper.parseRadius(ir.stringProp("radius"), 8)
        )
    }
}

/**
 * Properties for SplitView component.
 */
data class SplitViewProps(
    val ratio: Float,
    val direction: String
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): SplitViewProps = SplitViewProps(
            ratio = ir.floatProp("ratio", 0.5f),
            direction = ir.stringProp("direction", "horizontal")
        )
    }
}

/**
 * Properties for ScrollView component.
 */
data class ScrollViewProps(
    val direction: String,
    val showScrollbar: Boolean
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): ScrollViewProps = ScrollViewProps(
            direction = ir.stringProp("direction", "vertical"),
            showScrollbar = ir.boolProp("showScrollbar", true)
        )
    }
}

/**
 * Properties for Spacer component.
 */
data class SpacerProps(
    val size: Int,
    val flex: Float?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): SpacerProps = SpacerProps(
            size = ir.spacingProp("size", 16),
            flex = ir.floatProp("flex")
        )
    }
}

/**
 * Properties for Divider component.
 */
data class DividerProps(
    val thickness: Int,
    val color: String?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): DividerProps = DividerProps(
            thickness = ir.spacingProp("thickness", 1),
            color = ir.stringProp("color")
        )
    }
}

