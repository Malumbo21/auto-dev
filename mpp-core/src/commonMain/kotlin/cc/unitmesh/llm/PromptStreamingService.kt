package cc.unitmesh.llm

import cc.unitmesh.devins.filesystem.EmptyFileSystem
import cc.unitmesh.devins.filesystem.ProjectFileSystem
import cc.unitmesh.devins.llm.Message
import cc.unitmesh.llm.compression.TokenInfo
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction for an LLM service that can stream prompt output.
 *
 * This exists mainly for testability: agents can depend on this interface and unit tests
 * can provide deterministic fake implementations without making network calls.
 */
interface PromptStreamingService {
    fun streamPrompt(
        userPrompt: String,
        fileSystem: ProjectFileSystem = EmptyFileSystem(),
        historyMessages: List<Message> = emptyList(),
        compileDevIns: Boolean = true,
        onTokenUpdate: ((TokenInfo) -> Unit)? = null,
        onCompressionNeeded: ((Int, Int) -> Unit)? = null
    ): Flow<String>
}
