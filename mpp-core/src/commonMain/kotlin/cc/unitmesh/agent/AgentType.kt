package cc.unitmesh.agent

/**
 * Unified Agent Type Enum
 *
 * Represents all available agent modes in the system:
 * - LOCAL: Simple local chat mode without heavy tooling
 * - CODING: Local coding agent with full tool access (file system, shell, etc.)
 * - CODE_REVIEW: Dedicated code review agent with git integration
 * - KNOWLEDGE: Document reader mode for AI-native document reading
 * - CHAT_DB: Database chat mode for text-to-SQL interactions
 * - REMOTE: Remote agent connected to mpp-server
 */
enum class AgentType {
    /**
     * Local chat mode - lightweight conversation without heavy tooling
     */
    LOCAL_CHAT,

    /**
     * Coding agent mode - full-featured local agent with file system, shell, and all tools
     */
    CODING,

    /**
     * Code review mode - specialized agent for code analysis and review
     */
    CODE_REVIEW,

    /**
     * Document reader mode - AI-native document reading and analysis
     */
    KNOWLEDGE,

    /**
     * Database chat mode - text-to-SQL agent for database queries
     */
    CHAT_DB,

    /**
     * Remote agent mode - connects to remote mpp-server for distributed execution
     */
    REMOTE,

    /**
     * Web edit mode - browse, select DOM elements, and interact with web pages
     */
    WEB_EDIT;

    fun getDisplayName(): String = when (this) {
        LOCAL_CHAT -> "Chat"
        CODING -> "Agentic"
        CODE_REVIEW -> "Review"
        KNOWLEDGE -> "Knowledge"
        CHAT_DB -> "ChatDB"
        REMOTE -> "Remote"
        WEB_EDIT -> "WebEdit"
    }

    companion object {
        fun fromString(type: String): AgentType {
            return when (type.lowercase()) {
                "remote" -> REMOTE
                "local" -> LOCAL_CHAT
                "coding" -> CODING
                "codereview" -> CODE_REVIEW
                "documentreader", "documents" -> KNOWLEDGE
                "chatdb", "database" -> CHAT_DB
                "webedit", "web" -> WEB_EDIT
                else -> LOCAL_CHAT
            }
        }
    }
}