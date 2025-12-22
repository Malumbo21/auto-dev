package cc.unitmesh.xuiper.props

import cc.unitmesh.xuiper.ir.NanoIR
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Unified utility for resolving bindings from NanoIR components.
 * 
 * Handles various binding syntaxes:
 * - Direct binding: `bindings.value` or `bindings.bind`
 * - Props with binding syntax: `:= state.path` or `<< state.path`
 * - Simple state reference: `state.path`
 * 
 * This consolidates the duplicated `resolveStatePathFromBinding` functions
 * that were scattered across NanoInputComponents, NanoDateComponents, 
 * and NanoSelectionRenderer.
 */
object NanoBindingResolver {
    
    /**
     * Regex pattern for valid state paths.
     * Matches: identifier, identifier.nested, etc.
     */
    private val STATE_PATH_PATTERN = Regex("[A-Za-z_]\\w*(\\.[A-Za-z_]\\w*)*")
    
    /**
     * Resolve state path from binding expressions.
     * 
     * Checks bindings and props for the given keys, extracts the state path,
     * and normalizes it (removes "state." prefix, binding mode prefixes).
     * 
     * @param ir The NanoIR component
     * @param keys Keys to check for bindings (e.g., "value", "bind", "checked")
     * @return The resolved state path, or null if not found or invalid
     */
    fun resolveStatePath(ir: NanoIR, vararg keys: String): String? {
        // Try bindings first
        val binding = keys.firstNotNullOfOrNull { ir.bindings?.get(it) }
        val exprFromBinding = binding?.expression?.trim()
        
        // Then try props
        val exprFromProp = keys.firstNotNullOfOrNull { key ->
            ir.props[key]?.jsonPrimitive?.contentOrNull?.trim()
        }
        
        val rawExpr = (exprFromBinding ?: exprFromProp)?.trim() ?: return null
        
        // Remove binding mode prefixes
        val withoutMode = when {
            rawExpr.startsWith(":=") -> rawExpr.removePrefix(":=").trim()
            rawExpr.startsWith("<<") -> rawExpr.removePrefix("<<").trim()
            else -> rawExpr
        }
        
        // Remove "state." prefix if present
        val normalized = if (withoutMode.startsWith("state.")) {
            withoutMode.removePrefix("state.")
        } else {
            withoutMode
        }
        
        // Only treat simple identifiers or dotted paths as a writable state path.
        // This prevents expressions like '"x" in state.items' from being treated as a path.
        return normalized.takeIf { it.matches(STATE_PATH_PATTERN) }
    }
    
    /**
     * Resolve the current value from state using the binding.
     * 
     * @param ir The NanoIR component
     * @param state Current state map
     * @param keys Keys to check for bindings
     * @return The value from state, or null if not bound or not found
     */
    fun resolveValue(ir: NanoIR, state: Map<String, Any>, vararg keys: String): Any? {
        val path = resolveStatePath(ir, *keys) ?: return null
        return getNestedValue(state, path)
    }
    
    /**
     * Resolve the current value as String from state using the binding.
     * 
     * @param ir The NanoIR component
     * @param state Current state map
     * @param keys Keys to check for bindings
     * @return The value as String, or null if not bound or not found
     */
    fun resolveStringValue(ir: NanoIR, state: Map<String, Any>, vararg keys: String): String? {
        return resolveValue(ir, state, *keys)?.toString()
    }
    
    /**
     * Resolve the current value as Boolean from state using the binding.
     * 
     * @param ir The NanoIR component
     * @param state Current state map
     * @param keys Keys to check for bindings
     * @return The value as Boolean, or null if not bound or not found
     */
    fun resolveBooleanValue(ir: NanoIR, state: Map<String, Any>, vararg keys: String): Boolean? {
        return when (val value = resolveValue(ir, state, *keys)) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull()
            else -> null
        }
    }
    
    /**
     * Resolve the current value as Number from state using the binding.
     * 
     * @param ir The NanoIR component
     * @param state Current state map
     * @param keys Keys to check for bindings
     * @return The value as Number, or null if not bound or not found
     */
    fun resolveNumberValue(ir: NanoIR, state: Map<String, Any>, vararg keys: String): Number? {
        return when (val value = resolveValue(ir, state, *keys)) {
            is Number -> value
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }
    
    /**
     * Get a nested value from a map using dot notation.
     * e.g., "user.profile.name" -> state["user"]["profile"]["name"]
     */
    private fun getNestedValue(state: Map<String, Any>, path: String): Any? {
        val parts = path.split(".")
        var current: Any? = state
        
        for (part in parts) {
            current = when (current) {
                is Map<*, *> -> current[part]
                else -> return null
            }
        }
        
        return current
    }
}

