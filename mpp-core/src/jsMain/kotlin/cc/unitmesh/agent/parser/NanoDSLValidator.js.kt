package cc.unitmesh.agent.parser

/**
 * JavaScript implementation of NanoDSLValidator.
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
        
        // JavaScript platform: return source-only IR
        return NanoDSLParseResult.Success(createSourceOnlyIR(source))
    }
    
    /**
     * Basic validation without full AST parsing
     */
    private fun performBasicValidation(source: String): NanoDSLValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<String>()
        val lines = source.lines()

        // Check for component definition (now optional - bare components are supported)
        val hasComponentDef = lines.any { it.trim().startsWith("component ") && it.trim().endsWith(":") }
        if (!hasComponentDef) {
            // Check if it starts with a valid component (bare component)
            val firstNonBlank = lines.indexOfFirst { it.isNotBlank() }
            if (firstNonBlank >= 0) {
                val firstLine = lines[firstNonBlank].trim()
                val isBareComponent = Regex("""^(\w+)(?:\(.*\))?:\s*$""").matches(firstLine)
                if (!isBareComponent) {
                    errors.add(ValidationError(
                        "Invalid syntax. Expected 'component Name:' or a bare component like 'VStack:'",
                        0,
                        "Add 'component YourComponentName:' at the start or start with a component like 'VStack:'"
                    ))
                }
            }
        }
        
        // Check indentation consistency
        for ((lineNum, line) in lines.withIndex()) {
            if (line.isBlank()) continue
            
            val indent = line.takeWhile { it == ' ' }.length
            
            // Check for tabs (not allowed)
            if (line.contains('\t')) {
                errors.add(ValidationError(
                    "Tabs are not allowed. Use 4 spaces for indentation.",
                    lineNum,
                    "Replace tabs with 4 spaces"
                ))
            }
            
            // Check for odd indentation (should be multiple of 4)
            if (indent % 4 != 0) {
                warnings.add("Line ${lineNum + 1}: Indentation should be a multiple of 4 spaces")
            }
        }
        
        // Check for known components
        val knownComponents = setOf(
            "VStack", "HStack", "Card", "Text", "Button", "Input", "Image",
            "Badge", "Checkbox", "Toggle", "Select", "List", "Grid",
            "Spacer", "Divider", "Form", "Section"
        )
        
        for ((lineNum, line) in lines.withIndex()) {
            val trimmed = line.trim()
            
            // Check for component-like patterns
            val componentMatch = Regex("""^(\w+)(?:\(.*\))?:\s*$""").find(trimmed)
            if (componentMatch != null) {
                val componentName = componentMatch.groupValues[1]
                if (componentName !in knownComponents && 
                    componentName != "component" && 
                    componentName != "state" &&
                    componentName != "if" &&
                    componentName != "for" &&
                    componentName != "on_click" &&
                    componentName != "content") {
                    warnings.add("Line ${lineNum + 1}: Unknown component '$componentName'")
                }
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
            errors.add(ValidationError(
                "Unbalanced parentheses",
                0,
                "Check for missing '(' or ')'"
            ))
        }
        
        if (braceCount != 0) {
            errors.add(ValidationError(
                "Unbalanced braces",
                0,
                "Check for missing '{' or '}'"
            ))
        }
        
        return NanoDSLValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    /**
     * Create a source-only IR JSON for non-JVM platforms
     */
    private fun createSourceOnlyIR(source: String): String {
        // Extract component name from source
        val componentNameMatch = Regex("""component\s+(\w+):""").find(source)
        val componentName = componentNameMatch?.groupValues?.get(1) ?: "AnonymousComponent"
        
        // Escape the source for JSON
        val escapedSource = source
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        
        return """{"type":"Component","props":{"name":"$componentName"},"source":"$escapedSource"}"""
    }
}

