package cc.unitmesh.agent.parser

/**
 * WASM JavaScript implementation of NanoDSLValidator.
 * 
 * Performs basic syntax validation without full AST parsing.
 * Full parsing with IR generation is only available on JVM platforms.
 */
actual class NanoDSLValidator actual constructor() {
    
    actual fun validate(source: String): NanoDSLValidationResult {
        if (source.isBlank()) {
            return NanoDSLValidationResult(
                isValid = false,
                errors = listOf(ValidationError("Empty source code", 0))
            )
        }
        
        return performBasicValidation(source)
    }
    
    actual fun parse(source: String): NanoDSLParseResult {
        val validationResult = validate(source)
        if (!validationResult.isValid) {
            return NanoDSLParseResult.Failure(validationResult.errors)
        }
        
        // WASM platform: return source-only IR
        return NanoDSLParseResult.Success(createSourceOnlyIR(source))
    }
    
    private fun performBasicValidation(source: String): NanoDSLValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<String>()
        val lines = source.lines()
        
        // Check for component definition
        val hasComponentDef = lines.any { it.trim().startsWith("component ") && it.trim().endsWith(":") }
        if (!hasComponentDef) {
            errors.add(ValidationError(
                "Missing component definition. Expected 'component Name:'",
                0,
                "Add 'component YourComponentName:' at the start"
            ))
        }
        
        // Check indentation
        for ((lineNum, line) in lines.withIndex()) {
            if (line.isBlank()) continue
            
            if (line.contains('\t')) {
                errors.add(ValidationError(
                    "Tabs are not allowed. Use 4 spaces for indentation.",
                    lineNum,
                    "Replace tabs with 4 spaces"
                ))
            }
        }
        
        // Check for unclosed blocks
        var parenCount = 0
        var braceCount = 0
        for (line in lines) {
            for (char in line) {
                when (char) {
                    '(' -> parenCount++
                    ')' -> parenCount--
                    '{' -> braceCount++
                    '}' -> braceCount--
                }
            }
        }
        
        if (parenCount != 0) {
            errors.add(ValidationError("Unbalanced parentheses", 0))
        }
        
        if (braceCount != 0) {
            errors.add(ValidationError("Unbalanced braces", 0))
        }
        
        return NanoDSLValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    private fun createSourceOnlyIR(source: String): String {
        val componentNameMatch = Regex("""component\s+(\w+):""").find(source)
        val componentName = componentNameMatch?.groupValues?.get(1) ?: "UnknownComponent"
        
        val escapedSource = source
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        
        return """{"type":"Component","props":{"name":"$componentName"},"source":"$escapedSource"}"""
    }
}

