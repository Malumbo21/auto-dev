package cc.unitmesh.agent.render

/**
 * Extended renderer interface for Artifact Agent.
 * Adds artifact-specific rendering capabilities on top of CodingAgentRenderer.
 */
interface ArtifactRenderer : CodingAgentRenderer {

    /**
     * Render an artifact in the preview panel.
     *
     * @param identifier Unique identifier for the artifact
     * @param type The artifact MIME type (e.g., "application/autodev.artifacts.html")
     * @param title Human-readable title
     * @param content The artifact content (HTML, Python, React, etc.)
     */
    fun renderArtifact(
        identifier: String,
        type: String,
        title: String,
        content: String
    )

    /**
     * Update an existing artifact.
     * Called when the user requests modifications to a previously generated artifact.
     *
     * @param identifier The identifier of the artifact to update
     * @param content The new content
     */
    fun updateArtifact(identifier: String, content: String) {
        // Default: no-op
    }

    /**
     * Log a console message from the artifact WebView.
     * Captures console.log() output from HTML artifacts.
     *
     * @param identifier The artifact identifier
     * @param level The log level ("log", "warn", "error", "info")
     * @param message The log message
     * @param timestamp Timestamp of the log message
     */
    fun logConsoleMessage(
        identifier: String,
        level: String,
        message: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        // Default: no-op, renderers can override to display console output
    }

    /**
     * Clear console logs for an artifact.
     *
     * @param identifier The artifact identifier, or null to clear all logs
     */
    fun clearConsoleLogs(identifier: String? = null) {
        // Default: no-op
    }

    /**
     * Get all console logs for an artifact.
     *
     * @param identifier The artifact identifier
     * @return List of console log entries
     */
    fun getConsoleLogs(identifier: String): List<ConsoleLogEntry> {
        return emptyList()
    }

    /**
     * Export artifact as a standalone file.
     *
     * @param identifier The artifact identifier
     * @param format The export format ("html", "exe", "app")
     * @return The exported file path, or null if export failed
     */
    suspend fun exportArtifact(identifier: String, format: String): String? {
        return null
    }

    /**
     * Show/hide the artifact preview panel.
     *
     * @param visible Whether to show the panel
     */
    fun setArtifactPreviewVisible(visible: Boolean) {
        // Default: no-op
    }

    /**
     * Check if artifact preview is currently visible.
     */
    fun isArtifactPreviewVisible(): Boolean = false
}

/**
 * Console log entry from artifact WebView
 */
data class ConsoleLogEntry(
    val identifier: String,
    val level: String,
    val message: String,
    val timestamp: Long
)

