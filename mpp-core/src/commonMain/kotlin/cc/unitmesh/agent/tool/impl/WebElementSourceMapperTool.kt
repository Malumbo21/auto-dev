package cc.unitmesh.agent.tool.impl

import cc.unitmesh.agent.tool.*
import cc.unitmesh.agent.tool.filesystem.ToolFileSystem
import cc.unitmesh.agent.tool.schema.DeclarativeToolSchema
import cc.unitmesh.agent.tool.schema.SchemaPropertyBuilder.string
import cc.unitmesh.agent.tool.schema.ToolCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parameters for the WebElementSourceMapper tool
 */
@Serializable
data class WebElementSourceMapperParams(
    /**
     * HTML tag name of the element (e.g., "button", "div", "input")
     */
    val tagName: String,

    /**
     * CSS selector for the element
     */
    val selector: String,

    /**
     * Element attributes as JSON string (id, class, data-*, etc.)
     */
    val attributesJson: String? = null,

    /**
     * Text content of the element (if any)
     */
    val textContent: String? = null,

    /**
     * Source hints inferred from the element as comma-separated string
     */
    val sourceHints: String? = null,

    /**
     * The project path to search in
     */
    val projectPath: String? = null
)

/**
 * Result of source mapping for a web element
 */
@Serializable
data class SourceMappingResult(
    /**
     * Whether a source was found
     */
    val found: Boolean,

    /**
     * Likely source file paths
     */
    val sourceFiles: List<SourceFileMatch> = emptyList(),

    /**
     * Framework/technology detected
     */
    val framework: String? = null,

    /**
     * Suggested search patterns for manual lookup
     */
    val searchSuggestions: List<String> = emptyList(),

    /**
     * Additional analysis notes
     */
    val notes: String? = null
)

/**
 * A matched source file with relevance info
 */
@Serializable
data class SourceFileMatch(
    /**
     * Path to the source file
     */
    val path: String,

    /**
     * Line numbers where the element might be defined
     */
    val lineNumbers: List<Int> = emptyList(),

    /**
     * Confidence score (0.0 to 1.0)
     */
    val confidence: Double,

    /**
     * Why this file was matched
     */
    val matchReason: String
)

object WebElementSourceMapperSchema : DeclarativeToolSchema(
    description = """
        Maps web DOM elements to their corresponding source code locations.
        Given information about a DOM element (tag, selector, attributes, text),
        this tool searches the project for files that likely define or render that element.
        
        Useful for:
        - Finding React/Vue/Angular component source from DOM inspection
        - Locating template files that render specific elements
        - Identifying CSS/style source for element classes
    """.trimIndent(),
    properties = mapOf(
        "tagName" to string(
            description = "HTML tag name of the element (e.g., 'button', 'div', 'input')",
            required = true
        ),
        "selector" to string(
            description = "CSS selector for the element",
            required = true
        ),
        "attributesJson" to string(
            description = "Element attributes as JSON string, e.g., {\"class\": \"btn\", \"id\": \"submit\"}",
            required = false
        ),
        "textContent" to string(
            description = "Text content of the element",
            required = false
        ),
        "sourceHints" to string(
            description = "Source hints inferred from the element, comma-separated",
            required = false
        ),
        "projectPath" to string(
            description = "Project path to search in",
            required = false
        )
    )
) {
    override fun getExampleUsage(toolName: String): String = """
        /$toolName tagName="button" selector="button.submit-btn" attributesJson="{\"class\": \"submit-btn primary\", \"data-testid\": \"submit-button\"}" textContent="Submit Order"
    """.trimIndent()
}

/**
 * Tool for mapping DOM elements to source code locations.
 * This helps developers find where in their codebase a specific UI element is defined.
 */
class WebElementSourceMapperTool(
    private val fileSystem: ToolFileSystem,
    private val projectPath: String
) : BaseExecutableTool<WebElementSourceMapperParams, ToolResult.Success>() {

    override val name = "web_element_source_mapper"
    override val description = WebElementSourceMapperSchema.description

    override val metadata = ToolMetadata(
        displayName = "Web Element Source Mapper",
        tuiEmoji = "🔍",
        composeIcon = "search",
        category = ToolCategory.Search,
        schema = WebElementSourceMapperSchema
    )

    override fun getParameterClass(): String = "WebElementSourceMapperParams"

    override fun createToolInvocation(
        params: WebElementSourceMapperParams
    ): ToolInvocation<WebElementSourceMapperParams, ToolResult.Success> {
        return WebElementSourceMapperInvocation(params, this, fileSystem, projectPath)
    }
}

/**
 * Tool invocation for source mapping
 */
class WebElementSourceMapperInvocation(
    override val params: WebElementSourceMapperParams,
    override val tool: ExecutableTool<WebElementSourceMapperParams, ToolResult.Success>,
    private val fileSystem: ToolFileSystem,
    private val projectPath: String
) : ToolInvocation<WebElementSourceMapperParams, ToolResult.Success> {

    // Parse attributes from JSON string
    private val attributes: Map<String, String> by lazy {
        params.attributesJson?.let { json ->
            try {
                val jsonObject = Json.parseToJsonElement(json) as? JsonObject
                jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        } ?: emptyMap()
    }
    
    // Parse source hints from comma-separated string
    private val sourceHints: List<String> by lazy {
        params.sourceHints?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }

    override fun getDescription(): String {
        return "Mapping DOM element <${params.tagName}> (${params.selector}) to source code"
    }

    override fun getToolLocations(): List<ToolLocation> = emptyList()

    override suspend fun execute(context: ToolExecutionContext): ToolResult.Success {
        val result = mapElementToSource()
        return ToolResult.Success(formatResult(result))
    }

    private suspend fun mapElementToSource(): SourceMappingResult {
        val sourceFiles = mutableListOf<SourceFileMatch>()
        val searchSuggestions = mutableListOf<String>()
        var detectedFramework: String? = null

        // Extract search patterns from element info
        val searchPatterns = buildSearchPatterns()

        // Detect framework from attributes
        detectedFramework = detectFramework()

        // Search for matches using patterns
        for (pattern in searchPatterns) {
            val matches = searchForPattern(pattern.pattern, pattern.fileExtensions)
            sourceFiles.addAll(matches.map { match ->
                SourceFileMatch(
                    path = match.path,
                    lineNumbers = match.lineNumbers,
                    confidence = pattern.confidence,
                    matchReason = pattern.reason
                )
            })
        }

        // Add search suggestions for manual lookup
        searchSuggestions.addAll(generateSearchSuggestions())

        // Sort by confidence and deduplicate
        val uniqueFiles = sourceFiles
            .groupBy { it.path }
            .map { (path, matches) ->
                matches.maxByOrNull { it.confidence } ?: matches.first()
            }
            .sortedByDescending { it.confidence }
            .take(10)

        return SourceMappingResult(
            found = uniqueFiles.isNotEmpty(),
            sourceFiles = uniqueFiles,
            framework = detectedFramework,
            searchSuggestions = searchSuggestions,
            notes = generateAnalysisNotes(detectedFramework, uniqueFiles)
        )
    }

    /**
     * Build search patterns based on element information
     */
    private fun buildSearchPatterns(): List<SearchPattern> {
        val patterns = mutableListOf<SearchPattern>()

        // Pattern 1: Search for class names in JSX/TSX/Vue/Angular templates
        attributes["class"]?.let { className ->
            val classNames = className.split("\\s+".toRegex()).filter { it.isNotBlank() }
            for (cls in classNames.take(3)) {
                patterns.add(
                    SearchPattern(
                        pattern = "className=[\"'].*$cls.*[\"']|class=[\"'].*$cls.*[\"']",
                        fileExtensions = listOf("tsx", "jsx", "vue", "html", "svelte"),
                        confidence = 0.8,
                        reason = "Class name '$cls' found in template"
                    )
                )
            }
        }

        // Pattern 2: Search for data-testid
        attributes["data-testid"]?.let { testId ->
            patterns.add(
                SearchPattern(
                    pattern = "data-testid=[\"']$testId[\"']",
                    fileExtensions = listOf("tsx", "jsx", "vue", "html", "svelte", "ts", "js"),
                    confidence = 0.95,
                    reason = "Test ID '$testId' is a strong indicator"
                )
            )
        }

        // Pattern 3: Search for ID attribute
        attributes["id"]?.let { id ->
            patterns.add(
                SearchPattern(
                    pattern = "id=[\"']$id[\"']",
                    fileExtensions = listOf("tsx", "jsx", "vue", "html", "svelte"),
                    confidence = 0.9,
                    reason = "ID attribute '$id'"
                )
            )
        }

        // Pattern 4: Search for text content in components
        params.textContent?.takeIf { it.length in 3..50 }?.let { text ->
            val escapedText = Regex.escape(text.trim())
            patterns.add(
                SearchPattern(
                    pattern = ">\\s*$escapedText\\s*<|[\"']$escapedText[\"']",
                    fileExtensions = listOf("tsx", "jsx", "vue", "html", "svelte"),
                    confidence = 0.6,
                    reason = "Text content match"
                )
            )
        }

        // Pattern 5: Search for component names from source hints
        for (hint in sourceHints) {
            if (hint.contains("Component:")) {
                val componentName = hint.substringAfter("Component:").trim()
                patterns.add(
                    SearchPattern(
                        pattern = "(function|const|class)\\s+$componentName|export\\s+(default\\s+)?$componentName",
                        fileExtensions = listOf("tsx", "jsx", "ts", "js", "vue", "svelte"),
                        confidence = 0.85,
                        reason = "Component name from hint"
                    )
                )
            }
        }

        // Pattern 6: BEM naming convention
        attributes["class"]?.let { className ->
            if (className.contains("__") || className.contains("--")) {
                val block = className.split("__", "--").first()
                patterns.add(
                    SearchPattern(
                        pattern = "\\.$block[_\\-{]|[\"']$block[\"']",
                        fileExtensions = listOf("scss", "sass", "css", "less"),
                        confidence = 0.7,
                        reason = "BEM block name '$block' in styles"
                    )
                )
            }
        }

        return patterns
    }

    /**
     * Detect framework from element attributes
     */
    private fun detectFramework(): String? {
        return when {
            attributes.any { it.key.startsWith("ng-") || it.key.startsWith("_ng") } -> "Angular"
            attributes.any { it.key.startsWith("v-") || it.key.startsWith(":") } -> "Vue"
            attributes.containsKey("data-reactroot") || attributes.any { it.key.startsWith("data-react") } -> "React"
            attributes.any { it.key.startsWith("svelte-") } -> "Svelte"
            attributes.containsKey("data-livewire") -> "Laravel Livewire"
            attributes.containsKey("x-data") || attributes.any { it.key.startsWith("x-") } -> "Alpine.js"
            else -> null
        }
    }

    /**
     * Search for a pattern in project files
     */
    private suspend fun searchForPattern(
        pattern: String,
        extensions: List<String>
    ): List<FileMatch> {
        val matches = mutableListOf<FileMatch>()
        val searchPath = params.projectPath ?: projectPath

        try {
            // Use the file system to search
            val files = fileSystem.listFilesRecursive(searchPath)
                .filter { file ->
                    extensions.any { ext -> file.endsWith(".$ext") }
                }
                .take(500) // Limit file count

            val regex = try {
                Regex(pattern, RegexOption.IGNORE_CASE)
            } catch (e: Exception) {
                return matches
            }

            for (file in files) {
                try {
                    val content = fileSystem.readFile(file) ?: continue
                    val lines = content.lines()
                    val matchingLines = mutableListOf<Int>()

                    lines.forEachIndexed { index, line ->
                        if (regex.containsMatchIn(line)) {
                            matchingLines.add(index + 1)
                        }
                    }

                    if (matchingLines.isNotEmpty()) {
                        matches.add(FileMatch(file, matchingLines))
                    }
                } catch (e: Exception) {
                    // Skip files that can't be read
                }
            }
        } catch (e: Exception) {
            // Log error but continue
        }

        return matches
    }

    /**
     * Generate search suggestions for manual lookup
     */
    private fun generateSearchSuggestions(): List<String> {
        val suggestions = mutableListOf<String>()

        attributes["class"]?.let { className ->
            suggestions.add("Search for: className=\"$className\" or class=\"$className\"")
        }

        attributes["id"]?.let { id ->
            suggestions.add("Search for: id=\"$id\"")
        }

        params.textContent?.let { text ->
            if (text.length <= 30) {
                suggestions.add("Search for text: \"$text\"")
            }
        }

        // Suggest component file names
        attributes["class"]?.split(" ")?.firstOrNull()?.let { mainClass ->
            val pascalCase = mainClass.split("-", "_")
                .joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
            suggestions.add("Look for component file: $pascalCase.tsx or $pascalCase.vue")
        }

        return suggestions
    }

    /**
     * Generate analysis notes
     */
    private fun generateAnalysisNotes(
        framework: String?,
        matches: List<SourceFileMatch>
    ): String {
        val notes = StringBuilder()

        if (framework != null) {
            notes.appendLine("Detected framework: $framework")
        }

        if (matches.isEmpty()) {
            notes.appendLine("No exact matches found. Try the search suggestions above.")
        } else {
            notes.appendLine("Found ${matches.size} potential source file(s).")
            matches.firstOrNull()?.let {
                notes.appendLine("Best match: ${it.path} (confidence: ${(it.confidence * 100).toInt()}%)")
            }
        }

        return notes.toString()
    }

    private fun formatResult(result: SourceMappingResult): String {
        val sb = StringBuilder()
        sb.appendLine("## Web Element Source Mapping Result")
        sb.appendLine()

        if (result.framework != null) {
            sb.appendLine("**Detected Framework:** ${result.framework}")
            sb.appendLine()
        }

        if (result.sourceFiles.isNotEmpty()) {
            sb.appendLine("### Matched Source Files")
            result.sourceFiles.forEach { file ->
                sb.appendLine("- **${file.path}**")
                sb.appendLine("  - Confidence: ${(file.confidence * 100).toInt()}%")
                sb.appendLine("  - Reason: ${file.matchReason}")
                if (file.lineNumbers.isNotEmpty()) {
                    sb.appendLine("  - Lines: ${file.lineNumbers.take(5).joinToString(", ")}")
                }
            }
            sb.appendLine()
        }

        if (result.searchSuggestions.isNotEmpty()) {
            sb.appendLine("### Search Suggestions")
            result.searchSuggestions.forEach { suggestion ->
                sb.appendLine("- $suggestion")
            }
            sb.appendLine()
        }

        result.notes?.let {
            sb.appendLine("### Notes")
            sb.appendLine(it)
        }

        return sb.toString()
    }

    /**
     * Internal data class for search patterns
     */
    private data class SearchPattern(
        val pattern: String,
        val fileExtensions: List<String>,
        val confidence: Double,
        val reason: String
    )

    /**
     * Internal data class for file matches
     */
    private data class FileMatch(
        val path: String,
        val lineNumbers: List<Int>
    )
}
