package cc.unitmesh.xuiper.components

import cc.unitmesh.xuiper.action.NanoAction
import cc.unitmesh.xuiper.ast.Binding
import cc.unitmesh.xuiper.ast.NanoNode
import cc.unitmesh.xuiper.ir.NanoIR
import cc.unitmesh.xuiper.ir.NanoIRConverter
import cc.unitmesh.xuiper.spec.ComponentSpec
import kotlinx.serialization.json.JsonElement

/**
 * Component Definition - Complete definition for a single component
 * 
 * Each component should have its own file containing:
 * 1. ComponentSpec - Schema definition for validation
 * 2. AST Node - Parsed representation
 * 3. IR Converter - Conversion to platform-agnostic IR
 */
interface ComponentDefinition {
    /** Component name */
    val name: String
    
    /** Component specification for schema validation */
    val spec: ComponentSpec
    
    /** Create AST node from parsed data (implemented in each component file) */
    fun createASTNode(props: Map<String, Any>, children: List<NanoNode>): NanoNode
    
    /** Convert AST node to IR (implemented in each component file) */
    fun convertToIR(node: NanoNode): NanoIR
}

