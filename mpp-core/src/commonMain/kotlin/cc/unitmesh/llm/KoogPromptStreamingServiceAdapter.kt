package cc.unitmesh.llm

import cc.unitmesh.devins.filesystem.ProjectFileSystem
import cc.unitmesh.devins.llm.Message
import cc.unitmesh.llm.compression.TokenInfo
import kotlinx.coroutines.flow.Flow

/**
 * Adapter to expose LLMService through PromptStreamingService.
 *
 * Kotlin doesn't allow default values on overriding functions, so LLMService keeps its
 * default-arg API while agents can depend on the interface for testability.
 */
class KoogPromptStreamingServiceAdapter(
    private val delegate: LLMService
) : PromptStreamingService {
    override fun streamPrompt(
        userPrompt: String,
        fileSystem: ProjectFileSystem,
        historyMessages: List<Message>,
        compileDevIns: Boolean,
        onTokenUpdate: ((TokenInfo) -> Unit)?,
        onCompressionNeeded: ((Int, Int) -> Unit)?
    ): Flow<String> {
        return delegate.streamPrompt(
            userPrompt = userPrompt,
            fileSystem = fileSystem,
            historyMessages = historyMessages,
            compileDevIns = compileDevIns,
            onTokenUpdate = onTokenUpdate,
            onCompressionNeeded = onCompressionNeeded
        )
    }
}
