package cc.unitmesh.llm

import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import cc.unitmesh.llm.provider.LLMClientProvider
import kotlinx.coroutines.runBlocking

/**
 * Android implementation: GitHub Copilot not supported
 */
internal actual fun tryAutoRegisterGithubCopilot(): LLMClientProvider? = null

/**
 * Android implementation: Uses runBlocking to create executor synchronously
 */
internal actual fun createExecutorBlocking(
    provider: LLMClientProvider,
    config: ModelConfig
): SingleLLMPromptExecutor? {
    return runBlocking {
        provider.createExecutor(config)
    }
}

