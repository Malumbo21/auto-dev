package cc.unitmesh.xuiper.components

import cc.unitmesh.xuiper.components.feedback.*
import cc.unitmesh.xuiper.components.input.*
import cc.unitmesh.xuiper.spec.ComponentSpec

/**
 * Component Registry - Central registry for all components
 * 
 * This registry collects all component definitions and provides
 * a unified interface for accessing component specs and creating nodes.
 */
object ComponentRegistry {
    /**
     * All registered components by name
     */
    private val components: Map<String, ComponentDefinition> = mapOf(
        // Input components
        ButtonComponent.name to ButtonComponent,
        DatePickerComponent.name to DatePickerComponent,
        // TODO: Add more components as they are refactored
        // Feedback components
        ModalComponent.name to ModalComponent,
        // TODO: Add Alert, Progress, Spinner, etc.
    )
    
    /**
     * Get component definition by name
     */
    fun getComponent(name: String): ComponentDefinition? {
        return components[name]
    }
    
    /**
     * Get all component specs
     */
    fun getAllSpecs(): Map<String, ComponentSpec> {
        return components.mapValues { it.value.spec }
    }
    
    /**
     * Check if a component exists
     */
    fun hasComponent(name: String): Boolean {
        return name in components
    }
    
    /**
     * Get all registered component names
     */
    fun getAllComponentNames(): Set<String> {
        return components.keys
    }
}

