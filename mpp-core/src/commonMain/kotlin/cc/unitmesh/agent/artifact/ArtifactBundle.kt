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
         * 
         * For Node.js artifacts, intelligently selects the correct artifact containing code
         * (not package.json) when multiple artifacts are generated.
         * Also auto-detects dependencies from require()/import statements.
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
                cc.unitmesh.agent.ArtifactAgent.Artifact.ArtifactType.NODEJS -> ArtifactType.NODEJS
                cc.unitmesh.agent.ArtifactAgent.Artifact.ArtifactType.PYTHON -> ArtifactType.PYTHON
                cc.unitmesh.agent.ArtifactAgent.Artifact.ArtifactType.SVG -> ArtifactType.SVG
                cc.unitmesh.agent.ArtifactAgent.Artifact.ArtifactType.MERMAID -> ArtifactType.MERMAID
            }

            // Auto-detect dependencies for Node.js/React artifacts
            val dependencies = if (type == ArtifactType.NODEJS || type == ArtifactType.REACT) {
                detectNodeDependencies(artifact.content)
            } else {
                emptyMap()
            }

            return ArtifactBundle(
                id = id,
                name = artifact.title,
                description = "Generated artifact: ${artifact.title}",
                type = type,
                mainContent = artifact.content,
                dependencies = dependencies,
                context = ArtifactContext(
                    model = modelInfo,
                    conversationHistory = conversationHistory,
                    fingerprint = calculateFingerprint(artifact.content)
                )
            )
        }

        /**
         * Detect Node.js dependencies from code content.
         * Parses require() and import statements to extract package names.
         * 
         * @param content The JavaScript/TypeScript code content
         * @return Map of package name to version (using "latest" as default)
         */
        private fun detectNodeDependencies(content: String): Map<String, String> {
            val dependencies = mutableSetOf<String>()

            // Match require('package') or require("package")
            val requirePattern = Regex("""require\s*\(\s*['"]([^'"./][^'"]*)['"]\s*\)""")
            requirePattern.findAll(content).forEach { match ->
                val packageName = match.groupValues[1].split("/").first()
                dependencies.add(packageName)
            }

            // Match import ... from 'package' or import ... from "package"
            val importPattern = Regex("""import\s+.*?\s+from\s+['"]([^'"./][^'"]*)['"]\s*;?""")
            importPattern.findAll(content).forEach { match ->
                val packageName = match.groupValues[1].split("/").first()
                dependencies.add(packageName)
            }

            // Match import 'package' (side-effect imports)
            val sideEffectImportPattern = Regex("""import\s+['"]([^'"./][^'"]*)['"]\s*;?""")
            sideEffectImportPattern.findAll(content).forEach { match ->
                val packageName = match.groupValues[1].split("/").first()
                dependencies.add(packageName)
            }

            // Filter out Node.js built-in modules
            val builtInModules = setOf(
                "assert", "async_hooks", "buffer", "child_process", "cluster",
                "console", "constants", "crypto", "dgram", "diagnostics_channel",
                "dns", "domain", "events", "fs", "http", "http2", "https",
                "inspector", "module", "net", "os", "path", "perf_hooks",
                "process", "punycode", "querystring", "readline", "repl",
                "stream", "string_decoder", "sys", "timers", "tls", "trace_events",
                "tty", "url", "util", "v8", "vm", "wasi", "worker_threads", "zlib",
                // Node.js prefixed modules
                "node:assert", "node:buffer", "node:child_process", "node:cluster",
                "node:console", "node:constants", "node:crypto", "node:dgram",
                "node:dns", "node:events", "node:fs", "node:http", "node:http2",
                "node:https", "node:module", "node:net", "node:os", "node:path",
                "node:perf_hooks", "node:process", "node:querystring", "node:readline",
                "node:repl", "node:stream", "node:string_decoder", "node:timers",
                "node:tls", "node:tty", "node:url", "node:util", "node:v8", "node:vm",
                "node:worker_threads", "node:zlib"
            )

            // Map common packages to recommended versions
            val packageVersions = mapOf(
                "express" to "^4.18.2",
                "react" to "^18.2.0",
                "react-dom" to "^18.2.0",
                "axios" to "^1.6.0",
                "lodash" to "^4.17.21",
                "moment" to "^2.29.4",
                "uuid" to "^9.0.0",
                "dotenv" to "^16.3.1",
                "cors" to "^2.8.5",
                "body-parser" to "^1.20.2",
                "mongoose" to "^8.0.0",
                "pg" to "^8.11.3",
                "mysql2" to "^3.6.0",
                "redis" to "^4.6.10",
                "socket.io" to "^4.7.2",
                "jsonwebtoken" to "^9.0.2",
                "bcrypt" to "^5.1.1",
                "multer" to "^1.4.5-lts.1",
                "nodemailer" to "^6.9.7",
                "winston" to "^3.11.0",
                "jest" to "^29.7.0",
                "typescript" to "^5.3.2",
                "ts-node" to "^10.9.1"
            )

            return dependencies
                .filter { it !in builtInModules && !it.startsWith("node:") }
                .associateWith { packageVersions[it] ?: "latest" }
        }

        /**
         * Select the best artifact from multiple artifacts
         * 
         * For Node.js/React artifacts, avoids selecting package.json as main content
         * and prefers the artifact containing actual code.
         * 
         * @param artifacts List of artifacts to choose from
         * @return The best artifact for creating a bundle, or null if empty
         */
        fun selectBestArtifact(artifacts: List<cc.unitmesh.agent.ArtifactAgent.Artifact>): cc.unitmesh.agent.ArtifactAgent.Artifact? {
            if (artifacts.isEmpty()) return null
            if (artifacts.size == 1) return artifacts.first()

            // Group artifacts by type
            val nodeJsArtifacts = artifacts.filter { 
                it.type == cc.unitmesh.agent.ArtifactAgent.Artifact.ArtifactType.NODEJS ||
                it.type == cc.unitmesh.agent.ArtifactAgent.Artifact.ArtifactType.REACT
            }

            if (nodeJsArtifacts.size > 1) {
                // For Node.js, skip artifacts that look like package.json
                val codeArtifact = nodeJsArtifacts.find { artifact ->
                    val content = artifact.content.trim()
                    // Skip if it's clearly JSON (package.json)
                    !(content.startsWith("{") && content.contains("\"name\"") && content.contains("\"dependencies\""))
                }
                
                if (codeArtifact != null) {
                    return codeArtifact
                }
            }

            // Fallback: return first artifact
            return artifacts.first()
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
            ArtifactType.NODEJS -> {
                appendLine("Install dependencies and run the Node.js application:")
                appendLine()
                appendLine("```bash")
                appendLine("npm install")
                appendLine("node index.js")
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
            ArtifactType.NODEJS -> {
                appendLine("  \"main\": \"index.js\",")
                // Note: Not using "type": "module" to support both CommonJS (require) and ES modules (import)
                appendLine("  \"scripts\": {")
                appendLine("    \"start\": \"node index.js\",")
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
        ArtifactType.NODEJS -> "index.js"
        ArtifactType.PYTHON -> "index.py"
        ArtifactType.SVG -> "index.svg"
        ArtifactType.MERMAID -> "diagram.mmd"
    }

    /**
     * Get all files to be included in the bundle
     * Note: Core files (ARTIFACT.md, package.json, main file, context.json) take precedence
     * over files in the additional files map to prevent conflicts.
     */
    fun getAllFiles(): Map<String, String> = buildMap {
        // Core files - these must not be overridden
        put(ARTIFACT_MD, generateArtifactMd())
        put(PACKAGE_JSON, generatePackageJson())
        put(getMainFileName(), mainContent)
        put(CONTEXT_JSON, json.encodeToString(context))

        // Additional files - exclude any that conflict with core files
        val coreFileNames = setOf(ARTIFACT_MD, PACKAGE_JSON, getMainFileName(), CONTEXT_JSON)
        files.filterKeys { key -> key !in coreFileNames }.forEach { (key, value) ->
            put(key, value)
        }
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
    NODEJS("js", "application/autodev.artifacts.nodejs"),
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

