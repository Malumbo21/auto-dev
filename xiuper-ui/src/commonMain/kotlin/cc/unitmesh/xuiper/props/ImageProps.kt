package cc.unitmesh.xuiper.props

import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.props.PropExtractors.aspectRatioProp
import cc.unitmesh.xuiper.props.PropExtractors.intProp
import cc.unitmesh.xuiper.props.PropExtractors.radiusProp
import cc.unitmesh.xuiper.props.PropExtractors.stringProp
import cc.unitmesh.xuiper.props.PropExtractors.stringPropAny

/**
 * Properties for Image component.
 */
data class ImageProps(
    /** Image source (URL, data URI, or prompt for generation) */
    val src: String,
    /** Alt text for accessibility */
    val alt: String?,
    /** Aspect ratio (e.g., 16/9 = 1.77) */
    val aspectRatio: Float?,
    /** Corner radius in dp */
    val radius: Int,
    /** Explicit width in dp (optional) */
    val width: Int?,
    /** Explicit height in dp (optional) */
    val height: Int?,
    /** Object fit mode (cover, contain, fill, none) */
    val fit: String
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): ImageProps = ImageProps(
            src = ir.stringProp("src", ""),
            alt = ir.stringProp("alt"),
            aspectRatio = ir.aspectRatioProp("aspect") ?: ir.aspectRatioProp("aspectRatio"),
            radius = ir.radiusProp("radius"),
            width = ir.intProp("width"),
            height = ir.intProp("height"),
            fit = ir.stringProp("fit", "cover")
        )
    }
    
    /** Check if src is a direct image (data URI or URL) vs a generation prompt */
    val isDirectImage: Boolean get() {
        val trimmed = src.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("data:image/", ignoreCase = true)) return true
        if (trimmed.startsWith("http://", ignoreCase = true)) return true
        if (trimmed.startsWith("https://", ignoreCase = true)) return true
        return false
    }
}

/**
 * Properties for Avatar component.
 */
data class AvatarProps(
    /** Image source */
    val src: String?,
    /** Fallback text (usually initials) */
    val fallback: String?,
    /** Size token (sm, md, lg) or explicit size */
    val size: Int,
    /** Shape (circle, square, rounded) */
    val shape: String
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): AvatarProps {
            val sizeStr = ir.stringProp("size")
            val size = when (sizeStr) {
                "sm", "small" -> 32
                "md", "medium" -> 40
                "lg", "large" -> 56
                "xl" -> 72
                else -> sizeStr?.toIntOrNull() ?: 40
            }
            
            return AvatarProps(
                src = ir.stringProp("src"),
                fallback = ir.stringProp("fallback") ?: ir.stringProp("name")?.take(2)?.uppercase(),
                size = size,
                shape = ir.stringProp("shape", "circle")
            )
        }
    }
}

/**
 * Properties for Thumbnail component.
 */
data class ThumbnailProps(
    /** Image source */
    val src: String,
    /** Alt text */
    val alt: String?,
    /** Width in dp */
    val width: Int,
    /** Height in dp */
    val height: Int,
    /** Corner radius in dp */
    val radius: Int,
    /** Whether to show a border */
    val bordered: Boolean
) : ComponentProps {
    companion object {
        fun parse(ir: NanoIR): ThumbnailProps = ThumbnailProps(
            src = ir.stringProp("src", ""),
            alt = ir.stringProp("alt"),
            width = ir.intProp("width", 80),
            height = ir.intProp("height", 80),
            radius = ir.radiusProp("radius", 4),
            bordered = ir.stringProp("bordered")?.toBooleanStrictOrNull() ?: false
        )
    }
}

