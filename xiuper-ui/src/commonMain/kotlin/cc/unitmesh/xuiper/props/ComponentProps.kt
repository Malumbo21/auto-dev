package cc.unitmesh.xuiper.props

import cc.unitmesh.xuiper.ir.NanoIR
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Base interface for component properties.
 *
 * Each component type has a corresponding Props data class that:
 * 1. Parses raw JsonElement props from NanoIR
 * 2. Provides type-safe, already-resolved values
 * 3. Applies default values where appropriate
 *
 * This moves parsing logic from renderers to xiuper-ui, making it
 * platform-agnostic and reusable across Compose, React, Flutter, etc.
 *
 * ## Usage
 *
 * ```kotlin
 * // In renderer
 * val props = LayoutProps.parse(ir)
 * Row(horizontalArrangement = Arrangement.spacedBy(props.spacing.dp))
 * ```
 */
interface ComponentProps {
    companion object
}

/**
 * Extension functions for extracting typed values from NanoIR props.
 */
object PropExtractors {

    /**
     * Get a string property value.
     */
    fun NanoIR.stringProp(key: String): String? =
        props[key]?.jsonPrimitive?.contentOrNull

    /**
     * Get a string property with default value.
     */
    fun NanoIR.stringProp(key: String, default: String): String =
        props[key]?.jsonPrimitive?.contentOrNull ?: default

    /**
     * Get an integer property value.
     */
    fun NanoIR.intProp(key: String): Int? =
        props[key]?.jsonPrimitive?.intOrNull

    /**
     * Get an integer property with default value.
     */
    fun NanoIR.intProp(key: String, default: Int): Int =
        props[key]?.jsonPrimitive?.intOrNull ?: default

    /**
     * Get a float property value.
     */
    fun NanoIR.floatProp(key: String): Float? =
        props[key]?.jsonPrimitive?.floatOrNull

    /**
     * Get a float property with default value.
     */
    fun NanoIR.floatProp(key: String, default: Float): Float =
        props[key]?.jsonPrimitive?.floatOrNull ?: default

    /**
     * Get a boolean property value.
     */
    fun NanoIR.boolProp(key: String): Boolean? =
        props[key]?.jsonPrimitive?.booleanOrNull
            ?: props[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()

    /**
     * Get a boolean property with default value.
     */
    fun NanoIR.boolProp(key: String, default: Boolean): Boolean =
        boolProp(key) ?: default

    /**
     * Get spacing value from a property (parses tokens like "sm", "md", "lg").
     */
    fun NanoIR.spacingProp(key: String, default: Int = NanoSpacingUtils.DEFAULT_SPACING): Int =
        NanoSpacingUtils.parseSpacing(props[key]?.jsonPrimitive?.contentOrNull, default)

    /**
     * Get padding value from a property.
     */
    fun NanoIR.paddingProp(key: String, default: Int = NanoSpacingUtils.DEFAULT_SPACING): Int =
        NanoSpacingUtils.parsePadding(props[key]?.jsonPrimitive?.contentOrNull, default)

    /**
     * Get icon size from a property.
     */
    fun NanoIR.iconSizeProp(key: String, default: Int = 24): Int =
        NanoSizeMapper.parseIconSize(props[key]?.jsonPrimitive?.contentOrNull, default)

    /**
     * Get radius from a property.
     */
    fun NanoIR.radiusProp(key: String, default: Int = 8): Int =
        NanoSizeMapper.parseRadius(props[key]?.jsonPrimitive?.contentOrNull, default)

    /**
     * Get shadow level from a property.
     */
    fun NanoIR.shadowProp(key: String, default: Int = 0): Int =
        NanoSizeMapper.parseShadow(props[key]?.jsonPrimitive?.contentOrNull, default)

    /**
     * Get aspect ratio from a property.
     */
    fun NanoIR.aspectRatioProp(key: String): Float? =
        NanoAspectRatioParser.parse(props[key]?.jsonPrimitive?.contentOrNull)

    /**
     * Try multiple keys and return the first non-null string value.
     */
    fun NanoIR.stringPropAny(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { props[it]?.jsonPrimitive?.contentOrNull }

    /**
     * Try multiple keys and return the first non-null float value.
     */
    fun NanoIR.floatPropAny(vararg keys: String): Float? =
        keys.firstNotNullOfOrNull { props[it]?.jsonPrimitive?.floatOrNull }
}

