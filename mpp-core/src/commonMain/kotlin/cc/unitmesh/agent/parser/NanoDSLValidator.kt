package cc.unitmesh.agent.parser

/**
 * Platform-agnostic NanoDSL validation result
 */
data class NanoDSLValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * Validation error with location information
 */
data class ValidationError(
    val message: String,
    val line: Int,
    val suggestion: String? = null
)

/**
 * Parse result for NanoDSL source code
 */
sealed class NanoDSLParseResult {
    /**
     * Successful parse with IR JSON representation
     */
    data class Success(val irJson: String) : NanoDSLParseResult()
    
    /**
     * Failed parse with error details
     */
    data class Failure(val errors: List<ValidationError>) : NanoDSLParseResult()
    
    fun isSuccess(): Boolean = this is Success
    fun getIrJsonOrNull(): String? = (this as? Success)?.irJson
}

/**
 * NanoDSL validator interface - cross-platform
 * 
 * JVM platforms perform full AST parsing via xuiper-ui's NanoParser.
 * Non-JVM platforms perform lightweight syntax checks.
 * 
 * Usage:
 * ```kotlin
 * val validator = NanoDSLValidator()
 * val result = validator.validate(source)
 * if (result.isValid) {
 *     val parseResult = validator.parse(source)
 *     // Use IR JSON for rendering
 * }
 * ```
 */
expect class NanoDSLValidator() {
    /**
     * Validate NanoDSL source code without full parsing.
     * 
     * @param source The NanoDSL source code to validate
     * @return Validation result with errors and warnings
     */
    fun validate(source: String): NanoDSLValidationResult
    
    /**
     * Parse NanoDSL source code and convert to IR JSON.
     * 
     * On JVM platforms, this performs full AST parsing and converts to NanoIR.
     * On non-JVM platforms, this performs basic validation only and returns
     * a simple wrapper or failure.
     * 
     * @param source The NanoDSL source code to parse
     * @return Parse result with IR JSON or errors
     */
    fun parse(source: String): NanoDSLParseResult
}

