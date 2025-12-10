package cc.unitmesh.llm

import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import cc.unitmesh.llm.provider.LLMClientProvider

/**
 * WASM implementation: GitHub Copilot not supported
 */
internal actual fun tryAutoRegisterGithubCopilot(): LLMClientProvider? = null

/**
 * WASM implementation: Blocking executor creation not supported
 * Use createAsync() instead for async initialization
 */
internal actual fun createExecutorBlocking(
    provider: LLMClientProvider,
    config: ModelConfig
): SingleLLMPromptExecutor? = null

