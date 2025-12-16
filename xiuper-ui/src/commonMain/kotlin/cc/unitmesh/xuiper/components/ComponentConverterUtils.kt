package cc.unitmesh.xuiper.components

import cc.unitmesh.xuiper.action.BodyField
import cc.unitmesh.xuiper.action.NanoAction
import cc.unitmesh.xuiper.ast.Binding
import cc.unitmesh.xuiper.ir.NanoActionIR
import cc.unitmesh.xuiper.ir.NanoBindingIR
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Shared utility functions for component conversion
 * These are used by individual component files to avoid code duplication
 */
object ComponentConverterUtils {
    
    fun convertBinding(binding: Binding): NanoBindingIR {
        return when (binding) {
            is Binding.Subscribe -> NanoBindingIR(
                mode = "subscribe",
                expression = binding.expression
            )
            is Binding.TwoWay -> NanoBindingIR(
                mode = "twoWay",
                expression = binding.path
            )
            is Binding.Static -> NanoBindingIR(
                mode = "static",
                expression = binding.value
            )
        }
    }
    
    fun convertAction(action: NanoAction): NanoActionIR {
        return when (action) {
            is NanoAction.StateMutation -> NanoActionIR(
                type = "stateMutation",
                payload = mapOf(
                    "path" to JsonPrimitive(action.path),
                    "operation" to JsonPrimitive(action.operation.name),
                    "value" to JsonPrimitive(action.value)
                )
            )
            is NanoAction.Navigate -> NanoActionIR(
                type = "navigate",
                payload = mapOf("to" to JsonPrimitive(action.to))
            )
            is NanoAction.Fetch -> {
                val payload = mutableMapOf<String, JsonElement>(
                    "url" to JsonPrimitive(action.url),
                    "method" to JsonPrimitive(action.method.name),
                    "contentType" to JsonPrimitive(action.contentType.mimeType)
                )

                // Add body fields
                action.body?.let { body ->
                    payload["body"] = JsonObject(body.mapValues { convertBodyField(it.value) })
                }

                // Add headers
                action.headers?.let { headers ->
                    payload["headers"] = JsonObject(headers.mapValues { JsonPrimitive(it.value) })
                }

                // Add params
                action.params?.let { params ->
                    payload["params"] = JsonObject(params.mapValues { JsonPrimitive(it.value) })
                }

                // Add state bindings
                action.loadingState?.let { payload["loadingState"] = JsonPrimitive(it) }
                action.responseBinding?.let { payload["responseBinding"] = JsonPrimitive(it) }
                action.errorBinding?.let { payload["errorBinding"] = JsonPrimitive(it) }

                NanoActionIR(type = "fetch", payload = payload)
            }
            is NanoAction.ShowToast -> NanoActionIR(
                type = "showToast",
                payload = mapOf("message" to JsonPrimitive(action.message))
            )
            is NanoAction.Sequence -> NanoActionIR(
                type = "sequence"
            )
        }
    }
    
    private fun convertBodyField(field: BodyField): JsonElement {
        return when (field) {
            is BodyField.Literal -> JsonPrimitive(field.value)
            is BodyField.StateBinding -> JsonObject(
                mapOf(
                    "type" to JsonPrimitive("binding"),
                    "path" to JsonPrimitive(field.path)
                )
            )
        }
    }
}

