package cc.unitmesh.xuiper.ir

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Lightweight, platform-agnostic helpers for reading props from [NanoIR].
 *
 * Renderer implementations (Compose/Jewel/HTML/Swing) often need to read primitive
 * props with minimal boilerplate.
 */
object NanoIRProps

fun NanoIR.stringProp(key: String): String? {
    val element = props[key] ?: return null
    return (element as? JsonPrimitive)?.content ?: element.toString()
}

fun NanoIR.intProp(key: String): Int? {
    val element = props[key] as? JsonPrimitive ?: return null
    return element.intOrNull
}

fun NanoIR.doubleProp(key: String): Double? {
    val element = props[key] as? JsonPrimitive ?: return null
    return element.doubleOrNull
}

fun NanoIR.booleanProp(key: String): Boolean? {
    val element = props[key] as? JsonPrimitive ?: return null
    return element.booleanOrNull
}
