package cc.unitmesh.agent.artifact

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * ArtifactBundle - A self-contained, reversible artifact package (.unit format)
 *
 * Structure:
 * ```
 * my-artifact.unit/
 * ├── ARTIFACT.md          # Core metadata & AI operation instructions
 * ├── package.json         # Execution metadata: dependencies & runtime
 * ├── index.html           # Main entry (for HTML artifacts)
 * ├── index.js             # Main logic (for JS artifacts)
 * ├── .artifact/           # Hidden metadata directory (for Load-Back)
 * │   └── context.json     # Context metadata: conversation history & reasoning state
 * ├── assets/              # Static resources (CSS/images)
 * └── lib/                 # Helper modules
 * ```
 *
 * This format enables:
 * - **Reversibility**: Load back the artifact with full generation context
 * - **Self-Bootstrapping**: Zero-config local execution
 * - **Progressive Disclosure**: Efficient indexing and on-demand loading
 */
@Serializable
data class ArtifactBundle(
    /** Unique identifier for this artifact */
    val id: String,

    /** Human-readable name */
    val name: String,

    /** Short description */
    val description: String,

    /** Artifact type (html, react, python, etc.) */
    val type: ArtifactType,

    /** Version string */
    val version: String = "1.0.0",

    /** Main content (HTML, JS, Python code, etc.) */
    val mainContent: String,

    /** Additional files (path -> content) */
    val files: Map<String, String> = emptyMap(),

    /** Dependencies (for package.json) */
    val dependencies: Map<String, String> = emptyMap(),

    /** Context for Load-Back support */
    val context: ArtifactContext,

    /** Creation timestamp */
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),

    /** Last modified timestamp */
    val updatedAt: Long = Clock.System.now().toEpochMilliseconds()
) {
    companion object {
        const val BUNDLE_EXTENSION = ".unit"
        const val ARTIFACT_MD = "ARTIFACT.md"
        const val PACKAGE_JSON = "package.json"
        const val CONTEXT_DIR = ".artifact"
        const val CONTEXT_JSON = ".artifact/context.json"

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /**
         * Create a bundle from an artifact generation result
         */
        fun fromArtifact(
            artifact: cc.unitmesh.agent.ArtifactAgent.Artifact,
            conversationHistory: List<ConversationMessage> = emptyList(),
            modelInfo: ModelInfo? = null
        ): ArtifactBundle {
            val id = artifact.identifier.ifBlank { generateId() }
            val type = when (artifact.type) {
                cc.unitmesh.agent.ArtifactAgent.Artifact.ArtifactType.HTML -> ArtifactType.HTML
                cc.unitmesh.agent.ArtifactAgent.Artifact.ArtifactType.REACT -> ArtifactType.REACT
                cc.unitmesh.agent.ArtifactAgent.Artifact.ArtifactType.PYTHON -> ArtifactType.PYTHON
                cc.unitmesh.agent.ArtifactAgent.Artifact.ArtifactType.SVG -> ArtifactType.SVG
                cc.unitmesh.agent.ArtifactAgent.Artifact.ArtifactType.MERMAID -> ArtifactType.MERMAID
            }

            return ArtifactBundle(
                id = id,
                name = artifact.title,
                description = "Generated artifact: ${artifact.title}",
                type = type,
                mainContent = artifact.content,
                context = ArtifactContext(
                    model = modelInfo,
                    conversationHistory = conversationHistory,
                    fingerprint = calculateFingerprint(artifact.content)
                )
            )
        }

        private fun generateId(): String {
            val timestamp = Clock.System.now().toEpochMilliseconds()
            val random = (0..999999).random()
            return "artifact-$timestamp-$random"
        }

        private fun calculateFingerprint(content: String): String {
            // Simple hash for fingerprint (cross-platform compatible)
            var hash = 0L
            for (char in content) {
                hash = 31 * hash + char.code
            }
            return hash.toString(16)
        }
    }

    /**
     * Generate ARTIFACT.md content (Progressive Disclosure format)
     */
    fun generateArtifactMd(): String = buildString {
        // Level 1: YAML Frontmatter (always loaded for indexing)
        appendLine("---")
        appendLine("id: $id")
        appendLine("name: $name")
        appendLine("description: $description")
        appendLine("type: ${type.name.lowercase()}")
        appendLine("version: $version")
        appendLine("created_at: $createdAt")
        appendLine("updated_at: $updatedAt")
        appendLine("---")
        appendLine()

        // Level 2: Detailed documentation (loaded on demand)
        appendLine("# $name")
        appendLine()
        appendLine(description)
        appendLine()

        appendLine("## Usage")
        appendLine()
        when (type) {
            ArtifactType.HTML -> {
                appendLine("Open `index.html` in a browser to view the artifact.")
                appendLine()
                appendLine("```bash")
                appendLine("open index.html")
                appendLine("```")
            }
            ArtifactType.REACT -> {
                appendLine("Install dependencies and start the development server:")
                appendLine()
                appendLine("```bash")
                appendLine("npm install")
                appendLine("npm start")
                appendLine("```")
            }
            ArtifactType.PYTHON -> {
                appendLine("Run the Python script:")
                appendLine()
                appendLine("```bash")
                appendLine("python index.py")
                appendLine("```")
            }
            else -> {
                appendLine("See the main content file for usage instructions.")
            }
        }
        appendLine()

        appendLine("## Generation Context")
        appendLine()
        context.model?.let { model ->
            appendLine("- **Model**: ${model.name}")
            model.provider?.let { appendLine("- **Provider**: $it") }
        }
        appendLine("- **Generated at**: $createdAt")
        appendLine()

        if (context.conversationHistory.isNotEmpty()) {
            appendLine("## Conversation Summary")
            appendLine()
            val lastUserMessage = context.conversationHistory
                .lastOrNull { it.role == "user" }
            lastUserMessage?.let {
                appendLine("> ${it.content.take(200)}${if (it.content.length > 200) "..." else ""}")
            }
        }
    }

    /**
     * Generate package.json content
     * Note: Using manual JSON building to avoid serialization issues with Map<String, Any>
     */
    fun generatePackageJson(): String = buildString {
        appendLine("{")
        appendLine("  \"name\": \"${id.replace(Regex("[^a-z0-9-]"), "-").lowercase()}\",")
        appendLine("  \"version\": \"$version\",")
        appendLine("  \"description\": \"${description.replace("\"", "\\\"")}\",")

        when (type) {
            ArtifactType.HTML -> {
                appendLine("  \"main\": \"index.html\",")
            }
            ArtifactType.REACT -> {
                appendLine("  \"main\": \"index.js\",")
                appendLine("  \"scripts\": {")
                appendLine("    \"start\": \"react-scripts start\",")
                appendLine("    \"build\": \"react-scripts build\",")
                appendLine("    \"setup\": \"npm install\"")
                appendLine("  },")
            }
            ArtifactType.PYTHON -> {
                appendLine("  \"main\": \"index.py\",")
                appendLine("  \"scripts\": {")
                appendLine("    \"start\": \"python index.py\",")
                appendLine("    \"setup\": \"pip install -r requirements.txt\"")
                appendLine("  },")
            }
            else -> {
                appendLine("  \"main\": \"index.${type.extension}\",")
            }
        }

        if (dependencies.isNotEmpty()) {
            appendLine("  \"dependencies\": {")
            dependencies.entries.forEachIndexed { index, (key, value) ->
                val comma = if (index < dependencies.size - 1) "," else ""
                appendLine("    \"$key\": \"$value\"$comma")
            }
            appendLine("  },")
        }

        appendLine("  \"engines\": {")
        appendLine("    \"node\": \">=18\"")
        appendLine("  },")
        appendLine("  \"artifact\": {")
        appendLine("    \"type\": \"${type.name.lowercase()}\",")
        appendLine("    \"generated\": true,")
        appendLine("    \"loadBackSupported\": true")
        appendLine("  }")
        appendLine("}")
    }

    /**
     * Get the main file name based on artifact type
     */
    fun getMainFileName(): String = when (type) {
        ArtifactType.HTML -> "index.html"
        ArtifactType.REACT -> "index.jsx"
        ArtifactType.PYTHON -> "index.py"
        ArtifactType.SVG -> "index.svg"
        ArtifactType.MERMAID -> "diagram.mmd"
    }

    /**
     * Get all files to be included in the bundle
     */
    fun getAllFiles(): Map<String, String> = buildMap {
        // Core files
        put(ARTIFACT_MD, generateArtifactMd())
        put(PACKAGE_JSON, generatePackageJson())
        put(getMainFileName(), mainContent)
        put(CONTEXT_JSON, json.encodeToString(context))

        // Additional files
        putAll(files)
    }
}

/**
 * Artifact type enumeration
 */
@Serializable
enum class ArtifactType(val extension: String, val mimeType: String) {
    HTML("html", "text/html"),
    REACT("jsx", "text/javascript"),
    PYTHON("py", "text/x-python"),
    SVG("svg", "image/svg+xml"),
    MERMAID("mmd", "text/plain");

    companion object {
        fun fromExtension(ext: String): ArtifactType? =
            entries.find { it.extension == ext.lowercase() }

        fun fromMimeType(mime: String): ArtifactType? =
            entries.find { it.mimeType == mime }
    }
}

/**
 * Context metadata for Load-Back support
 */
@Serializable
data class ArtifactContext(
    /** Model information used for generation */
    val model: ModelInfo? = null,

    /** Conversation history (summarized for context restoration) */
    val conversationHistory: List<ConversationMessage> = emptyList(),

    /** Content fingerprint for change detection */
    val fingerprint: String = "",

    /** Custom metadata */
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Model information
 */
@Serializable
data class ModelInfo(
    val name: String,
    val provider: String? = null,
    val version: String? = null
)

/**
 * Simplified conversation message for context storage
 */
@Serializable
data class ConversationMessage(
    val role: String,
    val content: String,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
)

