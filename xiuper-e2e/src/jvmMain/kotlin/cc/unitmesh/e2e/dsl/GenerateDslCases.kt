package cc.unitmesh.e2e.dsl

import cc.unitmesh.config.ConfigManager
import cc.unitmesh.llm.LLMService
import cc.unitmesh.llm.LLMProviderType
import cc.unitmesh.llm.ModelConfig
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Generate E2E DSL test cases using LLM.
 *
 * This script loads configuration from ~/.autodev/config.yaml and generates
 * test cases for various scenarios.
 */
fun main() = runBlocking {
    println("=== E2E DSL Test Case Generator ===\n")

    // Load configuration
    val configWrapper = ConfigManager.load()
    val activeConfig = configWrapper.getActiveConfig()

    if (activeConfig == null) {
        println("Error: No active LLM configuration found.")
        println("Please configure ~/.autodev/config.yaml")
        return@runBlocking
    }

    println("Using LLM: ${activeConfig.provider}/${activeConfig.model}")

    // Create ModelConfig
    val modelConfig = ModelConfig(
        provider = LLMProviderType.valueOf(activeConfig.provider.uppercase()),
        modelName = activeConfig.model,
        apiKey = activeConfig.apiKey,
        baseUrl = activeConfig.baseUrl ?: "",
        temperature = activeConfig.temperature ?: 0.7,
        maxTokens = activeConfig.maxTokens ?: 8192
    )

    val llmService = LLMService.create(modelConfig)
    val generator = E2EDslLLMGenerator(llmService)

    // Define test scenarios to generate
    val testScenarios = listOf(
        TestScenarioSpec(
            description = "User login with valid credentials",
            url = "https://example.com/login",
            context = "Login form with username and password fields, remember me checkbox, and submit button"
        ),
        TestScenarioSpec(
            description = "User registration with form validation",
            url = "https://example.com/register",
            context = "Registration form with email, password, confirm password, terms checkbox"
        ),
        TestScenarioSpec(
            description = "Add item to shopping cart and checkout",
            url = "https://shop.example.com/products",
            context = "E-commerce product listing with add to cart buttons, cart icon, checkout flow"
        ),
        TestScenarioSpec(
            description = "Search for products and filter results",
            url = "https://shop.example.com/search",
            context = "Search bar, category filters, price range slider, sort dropdown"
        ),
        TestScenarioSpec(
            description = "Submit a contact form",
            url = "https://example.com/contact",
            context = "Contact form with name, email, subject, message fields and submit button"
        ),
        TestScenarioSpec(
            description = "Navigate through a multi-step wizard",
            url = "https://example.com/wizard",
            context = "Multi-step form with next/previous buttons, progress indicator"
        ),
        TestScenarioSpec(
            description = "Upload a file and verify upload success",
            url = "https://example.com/upload",
            context = "File upload area with drag-drop support, file type validation"
        ),
        TestScenarioSpec(
            description = "Edit user profile settings",
            url = "https://example.com/settings/profile",
            context = "Profile form with avatar upload, name, bio, save button"
        ),
        TestScenarioSpec(
            description = "Delete an item with confirmation dialog",
            url = "https://example.com/items",
            context = "Item list with delete buttons, confirmation modal with cancel/confirm"
        ),
        TestScenarioSpec(
            description = "Pagination through a data table",
            url = "https://example.com/data",
            context = "Data table with pagination controls, page size selector, column sorting"
        )
    )

    // Output directory
    val outputDir = File("docs/test-scripts/e2e-dsl-cases")
    outputDir.mkdirs()

    println("\nGenerating ${testScenarios.size} test cases...\n")

    var successCount = 0
    var failCount = 0

    testScenarios.forEachIndexed { index, spec ->
        println("[${ index + 1}/${testScenarios.size}] Generating: ${spec.description}")

        try {
            val result = generator.generateFromDescription(
                description = spec.description,
                targetUrl = spec.url,
                context = spec.context
            )

            if (result.success && result.dsl.isNotEmpty()) {
                val fileName = "case_${index + 1}_${spec.description.take(30).replace(Regex("[^a-zA-Z0-9]"), "_")}.e2e"
                val outputFile = File(outputDir, fileName)
                outputFile.writeText(result.dsl)
                println("   OK -> ${outputFile.name}")
                successCount++
            } else {
                println("   FAILED: ${result.errors.joinToString(", ")}")
                failCount++
            }
        } catch (e: Exception) {
            println("   ERROR: ${e.message}")
            failCount++
        }
    }

    println("\n=== Generation Complete ===")
    println("Success: $successCount, Failed: $failCount")
    println("Output directory: ${outputDir.absolutePath}")
}

data class TestScenarioSpec(
    val description: String,
    val url: String,
    val context: String = ""
)

