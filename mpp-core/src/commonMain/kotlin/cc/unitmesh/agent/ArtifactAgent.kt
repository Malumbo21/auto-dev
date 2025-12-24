package cc.unitmesh.agent

import cc.unitmesh.agent.logging.getLogger
import cc.unitmesh.agent.render.ArtifactRenderer
import cc.unitmesh.agent.render.CodingAgentRenderer
import cc.unitmesh.agent.render.DefaultCodingAgentRenderer
import cc.unitmesh.devins.llm.Message
import cc.unitmesh.devins.llm.MessageRole
import cc.unitmesh.llm.KoogLLMService
import kotlinx.coroutines.flow.toList

/**
 * ArtifactAgent - Generates self-contained, executable artifacts (HTML/JS, Python, React)
 * Inspired by Claude's Artifacts system: https://gist.github.com/dedlim/6bf6d81f77c19e20cd40594aa09e3ecd
 *
 * Unlike CodingAgent, ArtifactAgent focuses on generating complete, runnable artifacts
 * without file system or shell access. The artifacts are displayed in a WebView preview.
 */
class ArtifactAgent(
    private val llmService: KoogLLMService,
    private val renderer: CodingAgentRenderer = DefaultCodingAgentRenderer(),
    private val language: String = "EN"
) {
    private val logger = getLogger("ArtifactAgent")

    /**
     * Parsed artifact result
     */
    data class ArtifactResult(
        val success: Boolean,
        val artifacts: List<Artifact>,
        val rawResponse: String,
        val error: String? = null
    )

    /**
     * Single artifact with metadata
     */
    data class Artifact(
        val identifier: String,
        val type: ArtifactType,
        val title: String,
        val content: String
    ) {
        enum class ArtifactType(val mimeType: String) {
            HTML("application/autodev.artifacts.html"),
            REACT("application/autodev.artifacts.react"),
            PYTHON("application/autodev.artifacts.python"),
            SVG("application/autodev.artifacts.svg"),
            MERMAID("application/autodev.artifacts.mermaid");

            companion object {
                fun fromMimeType(mimeType: String): ArtifactType? {
                    return entries.find { it.mimeType == mimeType }
                }
            }
        }
    }

    /**
     * Generate artifact from user prompt
     */
    suspend fun generate(
        prompt: String,
        onProgress: (String) -> Unit = {}
    ): ArtifactResult {
        val systemPrompt = if (language == "ZH") {
            ArtifactAgentTemplate.ZH
        } else {
            ArtifactAgentTemplate.EN
        }

        onProgress("🎨 Generating artifact...")

        val responseBuilder = StringBuilder()

        // Pass system prompt as a system message in history
        val historyMessages = listOf(
            Message(role = MessageRole.SYSTEM, content = systemPrompt)
        )

        try {
            renderer.renderLLMResponseStart()

            llmService.streamPrompt(
                userPrompt = prompt,
                historyMessages = historyMessages,
                compileDevIns = false
            ).toList().forEach { chunk ->
                responseBuilder.append(chunk)
                renderer.renderLLMResponseChunk(chunk)
                onProgress(chunk)
            }

            renderer.renderLLMResponseEnd()

            val rawResponse = responseBuilder.toString()
            val artifacts = parseArtifacts(rawResponse)

            logger.info { "Generated ${artifacts.size} artifact(s)" }

            // Notify renderer about artifacts
            if (renderer is ArtifactRenderer) {
                artifacts.forEach { artifact ->
                    renderer.renderArtifact(
                        identifier = artifact.identifier,
                        type = artifact.type.mimeType,
                        title = artifact.title,
                        content = artifact.content
                    )
                }
            }

            return ArtifactResult(
                success = artifacts.isNotEmpty(),
                artifacts = artifacts,
                rawResponse = rawResponse
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate artifact: ${e.message}" }
            renderer.renderError("Failed to generate artifact: ${e.message}")
            return ArtifactResult(
                success = false,
                artifacts = emptyList(),
                rawResponse = responseBuilder.toString(),
                error = e.message
            )
        }
    }

    /**
     * Parse artifacts from LLM response
     */
    internal fun parseArtifacts(response: String): List<Artifact> {
        val artifacts = mutableListOf<Artifact>()

        // Pattern to match <autodev-artifact ...>...</autodev-artifact>
        val artifactPattern = Regex(
            """<autodev-artifact\s+([^>]+)>([\s\S]*?)</autodev-artifact>""",
            RegexOption.MULTILINE
        )

        artifactPattern.findAll(response).forEach { match ->
            try {
                val attributesStr = match.groupValues[1]
                val content = match.groupValues[2].trim()

                val identifier = extractAttribute(attributesStr, "identifier") ?: "artifact-${artifacts.size}"
                val typeStr = extractAttribute(attributesStr, "type") ?: "application/autodev.artifacts.html"
                val title = extractAttribute(attributesStr, "title") ?: "Untitled Artifact"

                val type = Artifact.ArtifactType.fromMimeType(typeStr) ?: Artifact.ArtifactType.HTML

                artifacts.add(
                    Artifact(
                        identifier = identifier,
                        type = type,
                        title = title,
                        content = content
                    )
                )

                logger.debug { "Parsed artifact: $identifier ($type) - $title" }
            } catch (e: Exception) {
                logger.warn { "Failed to parse artifact: ${e.message}" }
            }
        }

        // Fallback: If the model didn't follow <autodev-artifact> format, try to recover a single HTML artifact
        // from either a fenced ```html code block or a raw HTML document.
        if (artifacts.isEmpty()) {
            val recoveredHtml = extractHtmlFallback(response)
            if (!recoveredHtml.isNullOrBlank()) {
                val title = extractHtmlTitle(recoveredHtml) ?: "HTML Artifact"
                artifacts.add(
                    Artifact(
                        identifier = "artifact-0",
                        type = Artifact.ArtifactType.HTML,
                        title = title,
                        content = recoveredHtml.trim()
                    )
                )
            }
        }

        return artifacts
    }

    /**
     * Try to extract HTML content when <autodev-artifact> wrapper is missing.
     * Supports:
     * - Markdown fenced blocks: ```html ... ```
     * - Raw HTML documents: starting at <!doctype html> or <html
     */
    private fun extractHtmlFallback(response: String): String? {
        // 1) Markdown fenced ```html block
        val fenced = Regex("(?is)```\\s*html\\s*\\n([\\s\\S]*?)\\n```").find(response)?.groupValues?.get(1)
        if (!fenced.isNullOrBlank()) return fenced

        // 2) Raw HTML document
        val lower = response.lowercase()
        val doctypeIndex = lower.indexOf("<!doctype html")
        val htmlIndex = lower.indexOf("<html")
        val start = when {
            doctypeIndex >= 0 -> doctypeIndex
            htmlIndex >= 0 -> htmlIndex
            else -> -1
        }
        if (start < 0) return null
        return response.substring(start)
    }

    private fun extractHtmlTitle(html: String): String? {
        return Regex("(?is)<title\\s*>\\s*(.*?)\\s*</title>").find(html)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Extract attribute value from attribute string
     */
    private fun extractAttribute(attributesStr: String, name: String): String? {
        // Match both single and double quotes
        val pattern = Regex("""$name\s*=\s*["']([^"']+)["']""")
        return pattern.find(attributesStr)?.groupValues?.get(1)
    }

    /**
     * Validate HTML artifact is well-formed
     */
    fun validateHtmlArtifact(html: String): ValidationResult {
        val errors = mutableListOf<String>()

        // Check for basic HTML structure
        if (!html.contains("<!DOCTYPE html>", ignoreCase = true) &&
            !html.contains("<html", ignoreCase = true)
        ) {
            errors.add("Missing HTML doctype or html tag")
        }

        if (!html.contains("<head", ignoreCase = true)) {
            errors.add("Missing <head> section")
        }

        if (!html.contains("<body", ignoreCase = true)) {
            errors.add("Missing <body> section")
        }

        // Check for unclosed tags (basic check)
        val openTags = Regex("<([a-zA-Z][a-zA-Z0-9]*)(?:\\s[^>]*)?>").findAll(html)
            .map { it.groupValues[1].lowercase() }
            .filter { it !in setOf("br", "hr", "img", "input", "meta", "link", "!doctype") }
            .toList()

        val closeTags = Regex("</([a-zA-Z][a-zA-Z0-9]*)>").findAll(html)
            .map { it.groupValues[1].lowercase() }
            .toList()

        // Simple check - count should roughly match
        val openCount = openTags.groupingBy { it }.eachCount()
        val closeCount = closeTags.groupingBy { it }.eachCount()

        openCount.forEach { (tag, count) ->
            val closeTagCount = closeCount[tag] ?: 0
            if (count != closeTagCount && tag !in setOf("html", "head", "body")) {
                // Allow some flexibility, just warn
                logger.debug { "Tag '$tag' may have mismatched open ($count) and close ($closeTagCount) tags" }
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>
    )
}

