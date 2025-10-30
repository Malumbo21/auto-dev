package cc.unitmesh.agent.tool.impl

import cc.unitmesh.agent.tool.*
import cc.unitmesh.agent.tool.filesystem.ToolFileSystem
import kotlinx.serialization.Serializable

/**
 * Parameters for the ReadFile tool
 */
@Serializable
data class ReadFileParams(
    /**
     * The file path to read (relative to project root or absolute)
     */
    val path: String,
    
    /**
     * The line number to start reading from (1-based, optional)
     */
    val startLine: Int? = null,
    
    /**
     * The line number to end reading at (1-based, optional)
     */
    val endLine: Int? = null,
    
    /**
     * Maximum number of lines to read (optional)
     */
    val maxLines: Int? = null
)

/**
 * Tool invocation for reading files
 */
class ReadFileInvocation(
    params: ReadFileParams,
    tool: ReadFileTool,
    private val fileSystem: ToolFileSystem
) : BaseToolInvocation<ReadFileParams, ToolResult>(params, tool) {
    
    override fun getDescription(): String {
        val rangeDesc = when {
            params.startLine != null && params.endLine != null -> 
                " (lines ${params.startLine}-${params.endLine})"
            params.startLine != null -> 
                " (from line ${params.startLine})"
            params.maxLines != null -> 
                " (max ${params.maxLines} lines)"
            else -> ""
        }
        return "Read file: ${params.path}$rangeDesc"
    }
    
    override fun getToolLocations(): List<ToolLocation> {
        return listOf(ToolLocation(params.path, LocationType.FILE))
    }
    
    override suspend fun execute(context: ToolExecutionContext): ToolResult {
        return ToolErrorUtils.safeExecute(ToolErrorType.FILE_NOT_FOUND) {
            // Check if file exists
            if (!fileSystem.exists(params.path)) {
                throw ToolException("File not found: ${params.path}", ToolErrorType.FILE_NOT_FOUND)
            }

            // Read file content
            val content = fileSystem.readFile(params.path)
                ?: throw ToolException("Could not read file: ${params.path}", ToolErrorType.FILE_NOT_FOUND)
            
            // Process line range if specified
            val processedContent = processLineRange(content)
            
            // Get file info for metadata
            val fileInfo = fileSystem.getFileInfo(params.path)
            val metadata = mutableMapOf<String, String>().apply {
                put("file_path", params.path)
                put("total_lines", content.lines().size.toString())
                fileInfo?.let { info ->
                    put("file_size", info.size.toString())
                    put("is_directory", info.isDirectory.toString())
                }
                params.startLine?.let { put("start_line", it.toString()) }
                params.endLine?.let { put("end_line", it.toString()) }
                params.maxLines?.let { put("max_lines", it.toString()) }
            }
            
            ToolResult.Success(processedContent, metadata)
        }
    }
    
    private fun processLineRange(content: String): String {
        val lines = content.lines()
        val totalLines = lines.size
        
        // If no line range specified, return full content
        if (params.startLine == null && params.endLine == null && params.maxLines == null) {
            return content
        }
        
        // Calculate actual start and end indices (convert from 1-based to 0-based)
        val startIndex = (params.startLine?.minus(1) ?: 0).coerceAtLeast(0)
        val endIndex = when {
            params.endLine != null -> (params.endLine - 1).coerceAtMost(totalLines - 1)
            params.maxLines != null -> (startIndex + params.maxLines - 1).coerceAtMost(totalLines - 1)
            else -> totalLines - 1
        }
        
        // Validate range
        if (startIndex >= totalLines) {
            throw ToolException(
                "Start line ${params.startLine} is beyond file length ($totalLines lines)",
                ToolErrorType.PARAMETER_OUT_OF_RANGE
            )
        }

        if (startIndex > endIndex) {
            throw ToolException(
                "Start line ${params.startLine} is after end line ${params.endLine}",
                ToolErrorType.PARAMETER_OUT_OF_RANGE
            )
        }
        
        // Extract the specified range
        val selectedLines = lines.subList(startIndex, endIndex + 1)
        return selectedLines.joinToString("\n")
    }
}

/**
 * Tool for reading file contents with optional line range support
 */
class ReadFileTool(
    private val fileSystem: ToolFileSystem
) : BaseExecutableTool<ReadFileParams, ToolResult>() {
    
    override val name: String = ToolNames.READ_FILE
    override val description: String = """
        Read and retrieve file content from project using relative or absolute path. 
        Supports line ranges for reading specific portions of files.
        Essential for examining existing code, configurations, or documentation before modifications.
        Returns complete file content with metadata including line numbers and file information.
    """.trimIndent()
    
    override fun getParameterClass(): String = ReadFileParams::class.simpleName ?: "ReadFileParams"
    
    override fun createToolInvocation(params: ReadFileParams): ToolInvocation<ReadFileParams, ToolResult> {
        // Validate parameters
        validateParameters(params)
        return ReadFileInvocation(params, this, fileSystem)
    }
    
    private fun validateParameters(params: ReadFileParams) {
        if (params.path.isBlank()) {
            throw ToolException("File path cannot be empty", ToolErrorType.MISSING_REQUIRED_PARAMETER)
        }

        if (params.startLine != null && params.startLine <= 0) {
            throw ToolException("Start line must be positive", ToolErrorType.PARAMETER_OUT_OF_RANGE)
        }

        if (params.endLine != null && params.endLine <= 0) {
            throw ToolException("End line must be positive", ToolErrorType.PARAMETER_OUT_OF_RANGE)
        }

        if (params.startLine != null && params.endLine != null && params.startLine > params.endLine) {
            throw ToolException("Start line cannot be greater than end line", ToolErrorType.PARAMETER_OUT_OF_RANGE)
        }

        if (params.maxLines != null && params.maxLines <= 0) {
            throw ToolException("Max lines must be positive", ToolErrorType.PARAMETER_OUT_OF_RANGE)
        }
    }
}
