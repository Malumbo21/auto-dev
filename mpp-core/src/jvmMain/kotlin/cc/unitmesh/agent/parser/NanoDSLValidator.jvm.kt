package cc.unitmesh.agent.parser

import cc.unitmesh.agent.logging.getLogger

private val logger = getLogger("NanoDSLValidator.JVM")

/**
 * JVM implementation of NanoDSLValidator.
 * 
 * Attempts to use xuiper-ui's NanoParser if available via reflection,
 * otherwise falls back to basic validation.
 */
actual class NanoDSLValidator actual constructor() {
    
    private val fullParserAvailable: Boolean
    private var nanoDSLClass: Class<*>? = null
    private var validateMethod: java.lang.reflect.Method? = null
    private var parseResultMethod: java.lang.reflect.Method? = null
    private var toJsonMethod: java.lang.reflect.Method? = null
    
    init {
        // Try to load xuiper-ui's NanoDSL class via reflection
        fullParserAvailable = try {
            nanoDSLClass = Class.forName("cc.unitmesh.xuiper.dsl.NanoDSL")
            validateMethod = nanoDSLClass!!.getDeclaredMethod("validate", String::class.java)
            parseResultMethod = nanoDSLClass!!.getDeclaredMethod("parseResult", String::class.java)
            toJsonMethod = nanoDSLClass!!.getDeclaredMethod("toJson", String::class.java, Boolean::class.javaPrimitiveType)
            logger.info { "NanoDSL parser available via xuiper-ui" }
            true
        } catch (e: Exception) {
            logger.debug { "NanoDSL parser not available: ${e.message}" }
            false
        }
    }
    
    actual fun validate(source: String): NanoDSLValidationResult {
        if (source.isBlank()) {
            return NanoDSLValidationResult(
                isValid = false,
                errors = listOf(ValidationError("Empty source code", 0))
            )
        }
        
        // Try to use full parser if available
        if (fullParserAvailable && validateMethod != null) {
            return try {
                val result = validateMethod!!.invoke(nanoDSLClass!!.kotlin.objectInstance, source)
                convertValidationResult(result)
            } catch (e: Exception) {
                logger.warn(e) { "Full validation failed, falling back to basic validation" }
                performBasicValidation(source)
            }
        }
        
        return performBasicValidation(source)
    }
    
    actual fun parse(source: String): NanoDSLParseResult {
        val validationResult = validate(source)
        if (!validationResult.isValid) {
            return NanoDSLParseResult.Failure(validationResult.errors)
        }
        
        // Try to use full parser if available
        if (fullParserAvailable && toJsonMethod != null) {
            return try {
                val irJson = toJsonMethod!!.invoke(nanoDSLClass!!.kotlin.objectInstance, source, true) as String
                NanoDSLParseResult.Success(irJson)
            } catch (e: Exception) {
                logger.warn(e) { "Full parsing failed: ${e.message}" }
                val errorMessage = e.cause?.message ?: e.message ?: "Unknown parse error"
                NanoDSLParseResult.Failure(listOf(ValidationError(errorMessage, 0)))
            }
        }
        
        // Fallback: return a minimal IR wrapper
        return NanoDSLParseResult.Success(createMinimalIR(source))
    }
    
    /**
     * Convert xuiper-ui's ValidationResult to our NanoDSLValidationResult via reflection
     */
    private fun convertValidationResult(result: Any): NanoDSLValidationResult {
        return try {
            val isValid = result::class.java.getMethod("isValid").invoke(result) as Boolean
            val errorsField = result::class.java.getMethod("getErrors").invoke(result) as List<*>
            val warningsField = result::class.java.getMethod("getWarnings").invoke(result) as List<*>
            
            val errors = errorsField.mapNotNull { error ->
                if (error == null) return@mapNotNull null
                val message = error::class.java.getMethod("getMessage").invoke(error) as String
                val line = error::class.java.getMethod("getLine").invoke(error) as Int
                ValidationError(message, line)
            }
            
            val warnings = warningsField.mapNotNull { warning ->
                if (warning == null) return@mapNotNull null
                warning::class.java.getMethod("getMessage").invoke(warning) as String
            }
            
            NanoDSLValidationResult(isValid, errors, warnings)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to convert validation result" }
            NanoDSLValidationResult(isValid = true) // Assume valid if conversion fails
        }
    }
    
    /**
     * Basic validation without full AST parsing
     */
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
        
        // Check indentation consistency
        var expectedIndent = 0
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
                    warnings.add("Line ${lineNum + 1}: Unknown component '$componentName'. Consider using standard components.")
                }
            }
        }
        
        // Check for unclosed blocks (basic bracket matching)
        var parenCount = 0
        var braceCount = 0
        for ((lineNum, line) in lines.withIndex()) {
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
     * Create a minimal IR JSON when full parser is not available
     */
    private fun createMinimalIR(source: String): String {
        // Extract component name from source
        val componentNameMatch = Regex("""component\s+(\w+):""").find(source)
        val componentName = componentNameMatch?.groupValues?.get(1) ?: "UnknownComponent"
        
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

