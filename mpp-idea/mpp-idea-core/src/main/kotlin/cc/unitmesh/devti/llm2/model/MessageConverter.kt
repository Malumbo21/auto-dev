package cc.unitmesh.devti.llm2.model

import cc.unitmesh.devins.llm.Message as KoogMessage
import cc.unitmesh.devins.llm.MessageRole
import cc.unitmesh.devti.llms.custom.Message as IdeaMessage

/**
 * Extension functions for converting between IDEA Message and mpp-core Message types
 */

/**
 * Convert IDEA Message to mpp-core Message
 */
fun IdeaMessage.toKoogMessage(): KoogMessage {
    val messageRole = when (this.role.lowercase()) {
        "user" -> MessageRole.USER
        "assistant" -> MessageRole.ASSISTANT
        "system" -> MessageRole.SYSTEM
        else -> MessageRole.USER
    }
    return KoogMessage(messageRole, this.content)
}

/**
 * Convert mpp-core Message to IDEA Message
 */
fun KoogMessage.toIdeaMessage(): IdeaMessage {
    val roleString = when (this.role) {
        MessageRole.USER -> "user"
        MessageRole.ASSISTANT -> "assistant"
        MessageRole.SYSTEM -> "system"
    }
    return IdeaMessage(roleString, this.content)
}

/**
 * Convert a list of IDEA Messages to mpp-core Messages
 */
fun List<IdeaMessage>.toKoogMessages(): List<KoogMessage> {
    return this.map { it.toKoogMessage() }
}

/**
 * Convert a list of mpp-core Messages to IDEA Messages
 */
fun List<KoogMessage>.toIdeaMessages(): List<IdeaMessage> {
    return this.map { it.toIdeaMessage() }
}

