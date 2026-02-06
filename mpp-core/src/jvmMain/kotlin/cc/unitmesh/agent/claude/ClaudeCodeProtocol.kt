package cc.unitmesh.agent.claude

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Claude Code stream-json protocol messages.
 *
 * Reference: IDEA ml-llm implementation and zed-industries/claude-code-acp.
 * Claude Code uses `-p --output-format stream-json --input-format stream-json`
 * for bidirectional JSON-line communication over stdio.
 */

// ─── Outgoing (stdin) ──────────────────────────────────────────────

@Serializable
data class ClaudeUserInput(
    val type: String = "user",
    val message: ClaudeInputMessage,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("parent_tool_use_id") val parentToolUseId: String? = null,
)

@Serializable
data class ClaudeInputMessage(
    val role: String = "user",
    val content: List<ClaudeInputContent>,
)

@Serializable
data class ClaudeInputContent(
    val type: String = "text",
    val text: String = "",
)

// ─── Incoming (stdout) ─────────────────────────────────────────────

/**
 * Top-level message types from Claude Code's stream-json output.
 * Parsed manually by type-field since kotlinx.serialization polymorphism
 * needs extra setup and Claude's JSON isn't strictly polymorphic.
 */
enum class ClaudeMessageType {
    SYSTEM, ASSISTANT, USER, RESULT, STREAM_EVENT, UNKNOWN;

    companion object {
        fun fromString(type: String?): ClaudeMessageType = when (type) {
            "system" -> SYSTEM
            "assistant" -> ASSISTANT
            "user" -> USER
            "result" -> RESULT
            "stream_event" -> STREAM_EVENT
            else -> UNKNOWN
        }
    }
}

/**
 * Content block inside an assistant message.
 * Matches the IDEA `Content` class: type, text, id, name, input, tool_use_id, content, thinking.
 */
@Serializable
data class ClaudeContent(
    val type: String,
    val text: String? = null,
    val thinking: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: JsonElement? = null,
    @SerialName("tool_use_id") val toolUseId: String? = null,
    val content: JsonElement? = null,
    @SerialName("is_error") val isError: Boolean? = null,
)

/**
 * Stream event delta content.
 */
@Serializable
data class ClaudeStreamDelta(
    val type: String,
    val text: String? = null,
    val thinking: String? = null,
    @SerialName("partial_json") val partialJson: String? = null,
)

/**
 * Stream event content block.
 */
@Serializable
data class ClaudeStreamContentBlock(
    val type: String,
    val text: String? = null,
    val thinking: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: JsonElement? = null,
)

/**
 * Stream event wrapper.
 */
@Serializable
data class ClaudeStreamEvent(
    val type: String,
    val index: Int? = null,
    @SerialName("content_block") val contentBlock: ClaudeStreamContentBlock? = null,
    val delta: ClaudeStreamDelta? = null,
)

/**
 * Parsed representation of a single JSON line from Claude Code.
 */
data class ClaudeOutputMessage(
    val type: ClaudeMessageType,
    val subtype: String? = null,
    val sessionId: String? = null,
    /** For assistant/user messages: the content blocks. */
    val content: List<ClaudeContent> = emptyList(),
    /** For stream_event messages. */
    val streamEvent: ClaudeStreamEvent? = null,
    /** For result messages. */
    val result: String? = null,
    val isError: Boolean = false,
    /** Raw JSON for debugging. */
    val rawJson: JsonObject? = null,
)

// ─── Parser ────────────────────────────────────────────────────────

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * Parse a single JSON line from Claude Code's stream-json output.
 */
fun parseClaudeOutputLine(line: String): ClaudeOutputMessage? {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null

    return try {
        val jsonObj = json.parseToJsonElement(trimmed).jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.contentOrNull
        val messageType = ClaudeMessageType.fromString(type)
        val sessionId = jsonObj["session_id"]?.jsonPrimitive?.contentOrNull

        when (messageType) {
            ClaudeMessageType.SYSTEM -> {
                val subtype = jsonObj["subtype"]?.jsonPrimitive?.contentOrNull
                ClaudeOutputMessage(
                    type = messageType,
                    subtype = subtype,
                    sessionId = sessionId,
                    rawJson = jsonObj,
                )
            }

            ClaudeMessageType.ASSISTANT, ClaudeMessageType.USER -> {
                val messageObj = jsonObj["message"]?.jsonObject
                val contentArray = messageObj?.get("content")
                val content = parseContentArray(contentArray)
                ClaudeOutputMessage(
                    type = messageType,
                    sessionId = sessionId,
                    content = content,
                    rawJson = jsonObj,
                )
            }

            ClaudeMessageType.RESULT -> {
                val subtype = jsonObj["subtype"]?.jsonPrimitive?.contentOrNull
                val resultText = jsonObj["result"]?.jsonPrimitive?.contentOrNull ?: ""
                val isError = jsonObj["is_error"]?.jsonPrimitive?.booleanOrNull ?: false
                ClaudeOutputMessage(
                    type = messageType,
                    subtype = subtype,
                    sessionId = sessionId,
                    result = resultText,
                    isError = isError,
                    rawJson = jsonObj,
                )
            }

            ClaudeMessageType.STREAM_EVENT -> {
                val eventObj = jsonObj["event"]?.jsonObject
                val streamEvent = eventObj?.let { parseStreamEvent(it) }
                ClaudeOutputMessage(
                    type = messageType,
                    sessionId = sessionId,
                    streamEvent = streamEvent,
                    rawJson = jsonObj,
                )
            }

            ClaudeMessageType.UNKNOWN -> {
                ClaudeOutputMessage(
                    type = messageType,
                    sessionId = sessionId,
                    rawJson = jsonObj,
                )
            }
        }
    } catch (e: Exception) {
        null
    }
}

private fun parseContentArray(element: JsonElement?): List<ClaudeContent> {
    if (element == null) return emptyList()
    // Content can be a string or an array of content blocks
    return when (element) {
        is JsonPrimitive -> {
            listOf(ClaudeContent(type = "text", text = element.contentOrNull))
        }
        is JsonArray -> {
            element.mapNotNull { item ->
                try {
                    json.decodeFromJsonElement<ClaudeContent>(item)
                } catch (_: Exception) {
                    null
                }
            }
        }
        else -> emptyList()
    }
}

private fun parseStreamEvent(eventObj: JsonObject): ClaudeStreamEvent {
    val eventType = eventObj["type"]?.jsonPrimitive?.contentOrNull ?: ""
    val index = eventObj["index"]?.jsonPrimitive?.intOrNull

    val contentBlock = eventObj["content_block"]?.let {
        try {
            json.decodeFromJsonElement<ClaudeStreamContentBlock>(it)
        } catch (_: Exception) {
            null
        }
    }

    val delta = eventObj["delta"]?.let {
        try {
            json.decodeFromJsonElement<ClaudeStreamDelta>(it)
        } catch (_: Exception) {
            null
        }
    }

    return ClaudeStreamEvent(
        type = eventType,
        index = index,
        contentBlock = contentBlock,
        delta = delta,
    )
}

// ─── Helpers ───────────────────────────────────────────────────────

/**
 * Build a user input JSON string to send to Claude Code's stdin.
 */
fun buildClaudeUserInput(text: String, sessionId: String? = null): String {
    val input = ClaudeUserInput(
        message = ClaudeInputMessage(
            content = listOf(ClaudeInputContent(text = text))
        ),
        sessionId = sessionId,
    )
    return json.encodeToString(ClaudeUserInput.serializer(), input)
}
