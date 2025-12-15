package cc.unitmesh.viewer.web.webedit

import kotlinx.serialization.Serializable

/**
 * Structured browser action request executed by the injected WebEdit bridge script.
 *
 * Keep this schema small and stable so LLMs can reliably emit it.
 */
@Serializable
data class WebEditAction(
    val action: String,
    val selector: String? = null,
    val text: String? = null,
    val clearFirst: Boolean? = null,
    val value: String? = null,
    val key: String? = null,
    val id: String? = null
)


