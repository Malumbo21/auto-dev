package cc.unitmesh.xuiper.props

import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.props.PropExtractors.boolProp
import cc.unitmesh.xuiper.props.PropExtractors.stringProp

/**
 * Properties for Text component.
 */
data class TextProps(
    /** Text content (may contain interpolation expressions) */
    val content: String,
    /** Typography style (h1, h2, h3, body, caption, etc.) */
    val style: String?,
    /** Text color name */
    val color: String?,
    /** Font weight (normal, bold, light) */
    val weight: String?,
    /** Text alignment (left, center, right) */
    val align: String?,
    /** Whether text should be selectable */
    val selectable: Boolean
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): TextProps = TextProps(
            content = ir.stringProp("content", ""),
            style = ir.stringProp("style"),
            color = ir.stringProp("color"),
            weight = ir.stringProp("weight"),
            align = ir.stringProp("align"),
            selectable = ir.boolProp("selectable", false)
        )
    }
}

/**
 * Properties for Code (inline code) component.
 */
data class CodeProps(
    /** Code content */
    val content: String,
    /** Text color name */
    val color: String?,
    /** Background color name */
    val bgColor: String?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): CodeProps = CodeProps(
            content = ir.stringProp("content", ""),
            color = ir.stringProp("color"),
            bgColor = ir.stringProp("bgColor") ?: ir.stringProp("backgroundColor")
        )
    }
}

/**
 * Properties for Link component.
 */
data class LinkProps(
    /** Link text content */
    val content: String,
    /** URL to navigate to */
    val url: String,
    /** Link color name */
    val color: String?,
    /** Whether to show an external link icon */
    val showIcon: Boolean,
    /** Target (_blank, _self, etc.) */
    val target: String?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): LinkProps = LinkProps(
            content = ir.stringProp("content", ""),
            url = ir.stringProp("url", ""),
            color = ir.stringProp("color"),
            showIcon = ir.boolProp("showIcon", false),
            target = ir.stringProp("target")
        )
    }
}

/**
 * Properties for Label component.
 */
data class LabelProps(
    /** Label text */
    val text: String,
    /** Associated input name/id */
    val forInput: String?,
    /** Whether the field is required */
    val required: Boolean
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): LabelProps = LabelProps(
            text = ir.stringProp("text") ?: ir.stringProp("content", ""),
            forInput = ir.stringProp("for"),
            required = ir.boolProp("required", false)
        )
    }
}

/**
 * Properties for Heading component.
 */
data class HeadingProps(
    /** Heading text content */
    val content: String,
    /** Heading level (1-6) */
    val level: Int,
    /** Text color name */
    val color: String?
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): HeadingProps {
            // Try to infer level from type if not specified
            val level = ir.stringProp("level")?.toIntOrNull() ?: 1
            
            return HeadingProps(
                content = ir.stringProp("content", ""),
                level = level.coerceIn(1, 6),
                color = ir.stringProp("color")
            )
        }
    }
}

/**
 * Properties for Markdown component.
 */
data class MarkdownProps(
    /** Markdown content */
    val content: String,
    /** Whether to allow HTML in markdown */
    val allowHtml: Boolean
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): MarkdownProps = MarkdownProps(
            content = ir.stringProp("content", ""),
            allowHtml = ir.boolProp("allowHtml", false)
        )
    }
}

