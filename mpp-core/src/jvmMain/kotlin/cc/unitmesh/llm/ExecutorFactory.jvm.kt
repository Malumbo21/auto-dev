package cc.unitmesh.llm

import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import cc.unitmesh.llm.provider.GithubCopilotClientProvider
import cc.unitmesh.llm.provider.LLMClientProvider
import cc.unitmesh.llm.provider.LLMClientRegistry
import kotlinx.coroutines.runBlocking

/**
 * JVM implementation: Creates and registers GithubCopilotClientProvider
 */
internal actual fun tryAutoRegisterGithubCopilot(): LLMClientProvider? {
    return try {
        val provider = GithubCopilotClientProvider()
        if (provider.isAvailable()) {
            LLMClientRegistry.register(provider)
            provider
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * JVM implementation: Uses runBlocking to create executor synchronously
 */
internal actual fun createExecutorBlocking(
    provider: LLMClientProvider,
    config: ModelConfig
): SingleLLMPromptExecutor? {
    return runBlocking {
        provider.createExecutor(config)
    }
}

