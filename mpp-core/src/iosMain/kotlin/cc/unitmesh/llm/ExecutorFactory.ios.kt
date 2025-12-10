package cc.unitmesh.llm

import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import cc.unitmesh.llm.provider.LLMClientProvider

/**
 * iOS implementation: GitHub Copilot not supported
 */
internal actual fun tryAutoRegisterGithubCopilot(): LLMClientProvider? = null

/**
 * iOS implementation: Blocking executor creation not supported
 * iOS doesn't support runBlocking, so we return null
 */
internal actual fun createExecutorBlocking(
    provider: LLMClientProvider,
    config: ModelConfig
): SingleLLMPromptExecutor? = null

