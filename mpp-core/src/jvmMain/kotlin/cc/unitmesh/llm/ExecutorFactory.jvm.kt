package cc.unitmesh.llm

import cc.unitmesh.llm.provider.GithubCopilotClientProvider
import cc.unitmesh.llm.provider.LLMClientProvider
import cc.unitmesh.llm.provider.LLMClientRegistry

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

