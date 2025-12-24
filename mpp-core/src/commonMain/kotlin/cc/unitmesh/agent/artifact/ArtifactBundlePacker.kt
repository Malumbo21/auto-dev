package cc.unitmesh.agent.artifact

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * ArtifactBundlePacker - Packs and unpacks .unit artifact bundles
 *
 * The .unit format is a ZIP archive containing:
 * - ARTIFACT.md: Human-readable metadata with YAML frontmatter
 * - package.json: Node.js compatible execution metadata
 * - Main content file (index.html, index.py, etc.)
 * - .artifact/context.json: AI context for Load-Back support
 * - Additional asset files
 *
 * Platform-specific implementations handle the actual ZIP operations.
 */
expect class ArtifactBundlePacker() {
    /**
     * Pack a bundle into a .unit file (ZIP format)
     * @param bundle The artifact bundle to pack
     * @param outputPath Path to save the .unit file
     * @return Result with the output path or error
     */
    suspend fun pack(bundle: ArtifactBundle, outputPath: String): PackResult

    /**
     * Unpack a .unit file into an ArtifactBundle
     * @param inputPath Path to the .unit file
     * @return Result with the unpacked bundle or error
     */
    suspend fun unpack(inputPath: String): UnpackResult

    /**
     * Extract a .unit file to a directory
     * @param inputPath Path to the .unit file
     * @param outputDir Directory to extract to
     * @return Result with the output directory or error
     */
    suspend fun extractToDirectory(inputPath: String, outputDir: String): PackResult
}

/**
 * Result of packing operation
 */
sealed class PackResult {
    data class Success(val outputPath: String) : PackResult()
    data class Error(val message: String, val cause: Throwable? = null) : PackResult()
}

/**
 * Result of unpacking operation
 */
sealed class UnpackResult {
    data class Success(val bundle: ArtifactBundle) : UnpackResult()
    data class Error(val message: String, val cause: Throwable? = null) : UnpackResult()
}

/**
 * Common utilities for bundle packing (shared across platforms)
 */
object ArtifactBundleUtils {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Serialize a bundle to JSON (for in-memory operations)
     */
    fun serializeBundle(bundle: ArtifactBundle): String {
        return json.encodeToString(bundle)
    }

    /**
     * Deserialize a bundle from JSON
     */
    fun deserializeBundle(jsonString: String): ArtifactBundle {
        return json.decodeFromString(jsonString)
    }

    /**
     * Parse ARTIFACT.md YAML frontmatter
     */
    fun parseArtifactMdFrontmatter(content: String): Map<String, String> {
        val frontmatterRegex = Regex("^---\\n([\\s\\S]*?)\\n---", RegexOption.MULTILINE)
        val match = frontmatterRegex.find(content) ?: return emptyMap()

        val yaml = match.groupValues[1]
        return yaml.lines()
            .filter { it.contains(":") }
            .associate { line ->
                val parts = line.split(":", limit = 2)
                parts[0].trim() to parts[1].trim()
            }
    }

    /**
     * Validate bundle structure
     */
    fun validateBundle(files: Map<String, String>): ValidationResult {
        val errors = mutableListOf<String>()

        if (!files.containsKey(ArtifactBundle.ARTIFACT_MD)) {
            errors.add("Missing ${ArtifactBundle.ARTIFACT_MD}")
        }

        if (!files.containsKey(ArtifactBundle.PACKAGE_JSON)) {
            errors.add("Missing ${ArtifactBundle.PACKAGE_JSON}")
        }

        // Check for main content file
        val hasMainFile = files.keys.any { key ->
            key.startsWith("index.") ||
                    key == "diagram.mmd" ||
                    key.endsWith(".html") ||
                    key.endsWith(".py") ||
                    key.endsWith(".jsx") ||
                    key.endsWith(".js")
        }
        if (!hasMainFile) {
            errors.add("Missing main content file (index.html, index.py, etc.)")
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }

    /**
     * Reconstruct bundle from extracted files
     */
    fun reconstructBundle(files: Map<String, String>): ArtifactBundle? {
        // Parse ARTIFACT.md for basic info
        val artifactMd = files[ArtifactBundle.ARTIFACT_MD] ?: return null
        val frontmatter = parseArtifactMdFrontmatter(artifactMd)

        // Parse context.json
        val contextJson = files[ArtifactBundle.CONTEXT_JSON]
        val context = contextJson?.let {
            try {
                json.decodeFromString<ArtifactContext>(it)
            } catch (e: Exception) {
                ArtifactContext()
            }
        } ?: ArtifactContext()

        // Determine type and main content
        val typeStr = frontmatter["type"] ?: "html"
        val type = ArtifactType.entries.find { it.name.equals(typeStr, ignoreCase = true) }
            ?: ArtifactType.HTML

        val mainFileName = when (type) {
            ArtifactType.HTML -> "index.html"
            ArtifactType.REACT -> "index.jsx"
            ArtifactType.PYTHON -> "index.py"
            ArtifactType.SVG -> "index.svg"
            ArtifactType.MERMAID -> "diagram.mmd"
        }
        val mainContent = files[mainFileName] ?: files.entries
            .firstOrNull { it.key.startsWith("index.") }?.value ?: ""

        // Collect additional files (excluding metadata)
        val additionalFiles = files.filterKeys { key ->
            key != ArtifactBundle.ARTIFACT_MD &&
                    key != ArtifactBundle.PACKAGE_JSON &&
                    key != ArtifactBundle.CONTEXT_JSON &&
                    key != mainFileName
        }

        return ArtifactBundle(
            id = frontmatter["id"] ?: "unknown",
            name = frontmatter["name"] ?: "Untitled",
            description = frontmatter["description"] ?: "",
            type = type,
            version = frontmatter["version"] ?: "1.0.0",
            mainContent = mainContent,
            files = additionalFiles,
            context = context,
            createdAt = frontmatter["created_at"]?.toLongOrNull()
                ?: kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            updatedAt = frontmatter["updated_at"]?.toLongOrNull()
                ?: kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        )
    }

    /**
     * Generate a safe filename from artifact name
     */
    fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[^a-zA-Z0-9\\-_ ]"), "")
            .replace(" ", "-")
            .lowercase()
            .take(50)
            .ifBlank { "artifact" }
    }
}

/**
 * Bundle validation result
 */
sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val errors: List<String>) : ValidationResult()
}

