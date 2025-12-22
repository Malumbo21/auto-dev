package cc.unitmesh.xuiper.action

import cc.unitmesh.xuiper.ir.NanoActionIR
import kotlinx.serialization.json.JsonPrimitive

/**
 * Factory for creating NanoActionIR instances.
 *
 * This factory provides convenient methods for creating common action types,
 * reducing boilerplate code in UI components.
 *
 * ## Usage
 *
 * ```kotlin
 * // Create a SET mutation
 * val action = NanoActionFactory.stateMutation("count", "5")
 *
 * // Create an APPEND mutation
 * val appendAction = NanoActionFactory.append("items", "newItem")
 *
 * // Create a REMOVE mutation
 * val removeAction = NanoActionFactory.remove("items", "oldItem")
 * ```
 */
object NanoActionFactory {

    /**
     * Create a stateMutation action with the specified operation.
     *
     * @param path The state path to mutate (e.g., "count", "user.name")
     * @param value The value to apply
     * @param operation The mutation operation (default: SET)
     * @return A NanoActionIR representing the state mutation
     */
    fun stateMutation(
        path: String,
        value: String,
        operation: MutationOp = MutationOp.SET
    ): NanoActionIR {
        return NanoActionIR(
            type = "stateMutation",
            payload = mapOf(
                "path" to JsonPrimitive(path),
                "operation" to JsonPrimitive(operation.name),
                "value" to JsonPrimitive(value)
            )
        )
    }

    /**
     * Create a SET mutation action.
     *
     * @param path The state path to set
     * @param value The value to set
     * @return A NanoActionIR representing the SET mutation
     */
    fun set(path: String, value: String): NanoActionIR {
        return stateMutation(path, value, MutationOp.SET)
    }

    /**
     * Create a SET mutation action for boolean values.
     *
     * @param path The state path to set
     * @param value The boolean value to set
     * @return A NanoActionIR representing the SET mutation
     */
    fun set(path: String, value: Boolean): NanoActionIR {
        return stateMutation(path, value.toString(), MutationOp.SET)
    }

    /**
     * Create a SET mutation action for numeric values.
     *
     * @param path The state path to set
     * @param value The numeric value to set
     * @return A NanoActionIR representing the SET mutation
     */
    fun set(path: String, value: Number): NanoActionIR {
        return stateMutation(path, value.toString(), MutationOp.SET)
    }

    /**
     * Create an ADD mutation action (increment numeric value).
     *
     * @param path The state path to increment
     * @param value The value to add
     * @return A NanoActionIR representing the ADD mutation
     */
    fun add(path: String, value: String): NanoActionIR {
        return stateMutation(path, value, MutationOp.ADD)
    }

    /**
     * Create an ADD mutation action for numeric values.
     *
     * @param path The state path to increment
     * @param value The numeric value to add
     * @return A NanoActionIR representing the ADD mutation
     */
    fun add(path: String, value: Number): NanoActionIR {
        return stateMutation(path, value.toString(), MutationOp.ADD)
    }

    /**
     * Create a SUBTRACT mutation action (decrement numeric value).
     *
     * @param path The state path to decrement
     * @param value The value to subtract
     * @return A NanoActionIR representing the SUBTRACT mutation
     */
    fun subtract(path: String, value: String): NanoActionIR {
        return stateMutation(path, value, MutationOp.SUBTRACT)
    }

    /**
     * Create a SUBTRACT mutation action for numeric values.
     *
     * @param path The state path to decrement
     * @param value The numeric value to subtract
     * @return A NanoActionIR representing the SUBTRACT mutation
     */
    fun subtract(path: String, value: Number): NanoActionIR {
        return stateMutation(path, value.toString(), MutationOp.SUBTRACT)
    }

    /**
     * Create an APPEND mutation action (add item to list).
     *
     * @param path The state path of the list
     * @param value The value to append
     * @return A NanoActionIR representing the APPEND mutation
     */
    fun append(path: String, value: String): NanoActionIR {
        return stateMutation(path, value, MutationOp.APPEND)
    }

    /**
     * Create a REMOVE mutation action (remove item from list).
     *
     * @param path The state path of the list
     * @param value The value to remove
     * @return A NanoActionIR representing the REMOVE mutation
     */
    fun remove(path: String, value: String): NanoActionIR {
        return stateMutation(path, value, MutationOp.REMOVE)
    }

    /**
     * Dispatch a selection change action.
     *
     * This is a convenience method for selection components (Select, Radio, RadioGroup)
     * that need to update state when a selection changes.
     *
     * @param statePath The state path to update, or null for uncontrolled components
     * @param value The selected value
     * @param onAction Callback to dispatch the action
     * @param onUncontrolled Callback for uncontrolled components (when statePath is null)
     */
    inline fun dispatchSelection(
        statePath: String?,
        value: String,
        onAction: (NanoActionIR) -> Unit,
        onUncontrolled: () -> Unit
    ) {
        if (statePath != null) {
            onAction(set(statePath, value))
        } else {
            onUncontrolled()
        }
    }
}

