package cc.unitmesh.devti.llm2

import cc.unitmesh.llm.provider.GithubCopilotClientProvider
import cc.unitmesh.llm.provider.LLMClientRegistry
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Startup activity that initializes GitHub Copilot support.
 * 
 * This activity:
 * 1. Registers GithubCopilotClientProvider with LLMClientRegistry
 * 2. Initializes GithubCopilotManager for model caching
 * 
 * This ensures that when users select GitHub Copilot models,
 * the ExecutorFactory can create the appropriate executor.
 */
class GithubCopilotModelInitActivity : ProjectActivity {
    private val logger = Logger.getInstance(GithubCopilotModelInitActivity::class.java)

    override suspend fun execute(project: Project) {
        withContext(Dispatchers.IO) {
            try {
                // Check if GitHub Copilot is configured
                if (!GithubCopilotDetector.isGithubCopilotConfigured()) {
                    logger.info("GitHub Copilot is not configured, skipping initialization")
                    return@withContext
                }
                
                // Register the provider with LLMClientRegistry
                val provider = GithubCopilotClientProvider()
                if (provider.isAvailable()) {
                    LLMClientRegistry.register(provider)
                    logger.info("GitHub Copilot provider registered with LLMClientRegistry")
                }
                
                // Also initialize the IDEA-specific manager for model caching
                GithubCopilotManager.getInstance().initialize()
                logger.info("GitHub Copilot model initialization started")
            } catch (e: Exception) {
                logger.warn("Failed to initialize GitHub Copilot support", e)
            }
        }
    }
}

