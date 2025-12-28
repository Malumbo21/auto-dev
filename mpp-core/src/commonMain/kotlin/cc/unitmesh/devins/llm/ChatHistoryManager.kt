package cc.unitmesh.devins.llm

import cc.unitmesh.agent.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 聊天历史管理器
 * 管理多个聊天会话
 *
 * 功能增强：
 * - 自动持久化到磁盘（~/.autodev/sessions/chat-sessions.json）
 * - 启动时自动加载历史会话
 * - 保持现有 API 完全兼容
 * - 提供 StateFlow 用于 UI 响应式更新
 * - 只保存有消息的会话（空会话不保存）
 */
class ChatHistoryManager {
    private val sessions = mutableMapOf<String, ChatSession>()
    private var currentSessionId: String? = null

    private val dispatcher = if (Platform.isWasm || Platform.isJs) Dispatchers.Main else Dispatchers.Default
    private val scope = CoroutineScope(dispatcher)
    private var initialized = false

    // 用于通知 UI 更新的 StateFlow
    private val _sessionsUpdateTrigger = MutableStateFlow(0)
    val sessionsUpdateTrigger: StateFlow<Int> = _sessionsUpdateTrigger.asStateFlow()

    /**
     * 初始化：从磁盘加载历史会话
     */
    suspend fun initialize() {
        if (initialized) return

        try {
            val loadedSessions = SessionStorage.loadSessions()
            var titlesFixed = 0
            loadedSessions.forEach { session ->
                // Fix sessions with null title by generating from first user message
                // or first assistant message if no user message exists
                if (session.title == null && session.messages.isNotEmpty()) {
                    val firstUserMessage = session.messages.firstOrNull { it.role == MessageRole.USER }
                    if (firstUserMessage != null) {
                        session.title = generateTitleFromContent(firstUserMessage.content)
                        titlesFixed++
                    } else {
                        // Fallback: use first assistant message if no user message
                        val firstAssistantMessage = session.messages.firstOrNull { it.role == MessageRole.ASSISTANT }
                        if (firstAssistantMessage != null) {
                            session.title = generateTitleFromContent(firstAssistantMessage.content)
                            titlesFixed++
                        }
                    }
                }
                sessions[session.id] = session
            }

            // Save if we fixed any titles
            if (titlesFixed > 0) {
                saveSessions()
                println("Fixed $titlesFixed session titles")
            }

            // 如果有会话，设置最新的为当前会话
            if (sessions.isNotEmpty()) {
                currentSessionId = sessions.values.maxByOrNull { it.updatedAt }?.id
            }

            println("Loaded ${sessions.size} chat sessions from disk")
            initialized = true

            // 通知 UI 更新，确保初始加载的会话能够显示
            _sessionsUpdateTrigger.value++
        } catch (e: Exception) {
            println("Failed to initialize ChatHistoryManager: ${e.message}")
            initialized = true
        }
    }

    /**
     * 保存所有会话到磁盘（同步版本）
     * 只保存有消息的会话
     * 用于需要立即保存的场景（如添加消息后）
     */
    private suspend fun saveSessions() {
        try {
            // 过滤掉空会话（没有消息的会话）
            val nonEmptySessions = sessions.values.filter { it.messages.isNotEmpty() }
            SessionStorage.saveSessions(nonEmptySessions)

            // 通知 UI 更新
            _sessionsUpdateTrigger.value++
        } catch (e: Exception) {
            println("⚠️ Failed to save sessions: ${e.message}")
        }
    }

    /**
     * 保存所有会话到磁盘（异步版本）
     * 用于不需要等待保存完成的场景（如切换会话、删除会话）
     */
    private fun saveSessionsAsync() {
        scope.launch {
            saveSessions()
        }
    }

    /**
     * 创建新会话
     * 注意：空会话不会被保存，只有添加消息后才会保存
     */
    @OptIn(ExperimentalUuidApi::class)
    fun createSession(): ChatSession {
        val sessionId = Uuid.random().toString()
        val session = ChatSession(id = sessionId)
        sessions[sessionId] = session
        currentSessionId = sessionId

        // 空会话不保存，等有消息时再保存
        // 但通知 UI 更新（虽然不会显示空会话）
        _sessionsUpdateTrigger.value++

        return session
    }

    /**
     * 获取当前会话
     */
    fun getCurrentSession(): ChatSession {
        return currentSessionId?.let { sessions[it] }
            ?: createSession()
    }

    /**
     * 切换到指定会话
     * 会先保存当前会话，再切换到新会话
     */
    fun switchSession(sessionId: String): ChatSession? {
        currentSessionId?.let { currentId ->
            sessions[currentId]?.let { currentSession ->
                if (currentSession.messages.isNotEmpty()) {
                    saveSessionsAsync()
                }
            }
        }

        return sessions[sessionId]?.also {
            currentSessionId = sessionId
            _sessionsUpdateTrigger.value++
        }
    }

    /**
     * 删除会话
     */
    fun deleteSession(sessionId: String) {
        sessions.remove(sessionId)
        if (currentSessionId == sessionId) {
            currentSessionId = null
        }

        // 自动保存并通知 UI 更新
        saveSessionsAsync()
    }

    /**
     * 获取所有会话（包括空会话，以便用户可以看到新建的会话）
     */
    fun getAllSessions(): List<ChatSession> {
        return sessions.values
            .sortedByDescending { it.updatedAt }
    }

    /**
     * 清空当前会话历史
     */
    fun clearCurrentSession() {
        getCurrentSession().clear()

        // 自动保存
        saveSessionsAsync()
    }

    /**
     * 添加用户消息到当前会话
     * 立即同步保存到磁盘，确保消息不会丢失
     * 如果是第一条用户消息且 title 为空，自动设置 title
     */
    suspend fun addUserMessage(content: String) {
        val session = getCurrentSession()

        // Auto-generate title from first user message if title is null
        if (session.title == null && session.messages.none { it.role == MessageRole.USER }) {
            session.title = generateTitleFromContent(content)
        }

        session.addUserMessage(content)

        // 立即同步保存
        saveSessions()
    }

    /**
     * Generate a session title from message content
     * Takes the first 50 characters of the content as title
     */
    private fun generateTitleFromContent(content: String): String {
        val cleanContent = content.trim()
            .replace("\n", " ")
            .replace("\r", "")
        return if (cleanContent.length > 50) {
            cleanContent.take(47) + "..."
        } else {
            cleanContent
        }
    }

    /**
     * 添加助手消息到当前会话
     * 立即同步保存到磁盘，确保消息不会丢失
     */
    suspend fun addAssistantMessage(content: String) {
        getCurrentSession().addAssistantMessage(content)

        // 立即同步保存
        saveSessions()
    }

    /**
     * 添加完整的 Message 对象到当前会话（包含 metadata）
     * 如果是用户消息且 title 为空，自动设置 title
     * 立即同步保存到磁盘
     */
    suspend fun addMessage(message: Message) {
        val session = getCurrentSession()

        // Auto-generate title from user message if title is null
        if (message.role == MessageRole.USER && session.title == null) {
            session.title = generateTitleFromContent(message.content)
        }

        session.messages.add(message)
        session.updatedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

        // 立即同步保存
        saveSessions()
    }

    /**
     * 批量添加多个 Message 对象到当前会话
     * 如果 title 为空，自动从第一条用户消息设置 title
     * 如果没有用户消息，则从第一条助手消息设置 title
     * 完成后立即同步保存到磁盘
     */
    suspend fun addMessages(messages: List<Message>) {
        if (messages.isEmpty()) return

        val session = getCurrentSession()

        // Add messages first
        session.messages.addAll(messages)
        session.updatedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

        // Auto-generate title from first user message in session if title is still null
        // Check session.messages (which now includes the new messages) for the first user message
        if (session.title == null && session.messages.isNotEmpty()) {
            val firstUserMessage = session.messages.firstOrNull { it.role == MessageRole.USER }
            if (firstUserMessage != null) {
                session.title = generateTitleFromContent(firstUserMessage.content)
            } else {
                // Fallback: use first assistant message if no user message
                val firstAssistantMessage = session.messages.firstOrNull { it.role == MessageRole.ASSISTANT }
                if (firstAssistantMessage != null) {
                    session.title = generateTitleFromContent(firstAssistantMessage.content)
                }
            }
        }

        // 立即同步保存
        saveSessions()
    }

    /**
     * 重命名会话（添加标题）
     */
    fun renameSession(sessionId: String, title: String) {
        sessions[sessionId]?.let { session ->
            session.title = title
            session.updatedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            saveSessionsAsync()
        }
    }

    /**
     * 获取当前会话的消息历史
     */
    fun getMessages(): List<Message> {
        return getCurrentSession().messages
    }

    /**
     * 获取当前会话的最近 N 条消息
     */
    fun getRecentMessages(count: Int): List<Message> {
        return getCurrentSession().getRecentMessages(count)
    }

    companion object {
        private var instance: ChatHistoryManager? = null

        /**
         * 获取全局单例
         */
        fun getInstance(): ChatHistoryManager {
            return instance ?: run {
                val newInstance = ChatHistoryManager()
                instance = newInstance
                newInstance
            }
        }
    }
}

