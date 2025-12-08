package cc.unitmesh.devins.idea.omnibar.model

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents the type of an Omnibar item
 */
enum class OmnibarItemType {
    /** Built-in command (e.g., /file, /shell, /write) */
    COMMAND,
    /** Custom command from team prompts or spec-kit */
    CUSTOM_COMMAND,
    /** File or folder reference */
    FILE,
    /** Code symbol (class, function, etc.) */
    SYMBOL,
    /** Agent or skill */
    AGENT,
    /** Recent action or history */
    RECENT,
    /** Setting or configuration */
    SETTING
}

/**
 * Data model for Omnibar items.
 * Represents a searchable/selectable item in the Omnibar command palette.
 */
data class OmnibarItem(
    /** Unique identifier */
    val id: String,
    /** Display title */
    val title: String,
    /** Item type for categorization and icon selection */
    val type: OmnibarItemType,
    /** Optional description or subtitle */
    val description: String = "",
    /** Optional keyboard shortcut hint (e.g., "⌘K") */
    val shortcutHint: String = "",
    /** Optional icon (platform-specific) */
    val icon: ImageVector? = null,
    /** Optional icon resource name for cross-platform usage */
    val iconName: String = "",
    /** Category for grouping (e.g., "Commands", "Files", "Symbols") */
    val category: String = "",
    /** Weight for priority sorting (higher = more important) */
    val weight: Int = 0,
    /** Last used timestamp for recency sorting */
    val lastUsedTimestamp: Long = 0,
    /** Additional metadata for action execution */
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Calculate match score for fuzzy search.
     * Returns 0 if no match, higher values for better matches.
     */
    fun matchScore(query: String): Int {
        if (query.isBlank()) return weight
        
        val lowerQuery = query.lowercase()
        val lowerTitle = title.lowercase()
        val lowerDescription = description.lowercase()
        
        return when {
            // Exact match on title
            lowerTitle == lowerQuery -> 1000 + weight
            // Title starts with query
            lowerTitle.startsWith(lowerQuery) -> 500 + weight
            // Title contains query
            lowerTitle.contains(lowerQuery) -> 200 + weight
            // Fuzzy match on title (all query chars appear in order)
            fuzzyMatches(lowerTitle, lowerQuery) -> 100 + weight
            // Description contains query
            lowerDescription.contains(lowerQuery) -> 50 + weight
            // No match
            else -> 0
        }
    }
    
    private fun fuzzyMatches(text: String, query: String): Boolean {
        var queryIndex = 0
        for (char in text) {
            if (queryIndex < query.length && char == query[queryIndex]) {
                queryIndex++
            }
        }
        return queryIndex == query.length
    }
}

/**
 * Result of an Omnibar action execution.
 */
sealed class OmnibarActionResult {
    /** Action executed successfully */
    data class Success(val message: String = "") : OmnibarActionResult()
    
    /** Insert text into input area */
    data class InsertText(val text: String) : OmnibarActionResult()
    
    /** Execute as LLM query */
    data class LLMQuery(val query: String) : OmnibarActionResult()
    
    /** Open file or navigate to symbol */
    data class Navigate(val path: String, val line: Int = 0) : OmnibarActionResult()
    
    /** Show submenu with more options */
    data class ShowSubmenu(val items: List<OmnibarItem>) : OmnibarActionResult()
    
    /** Action failed */
    data class Error(val message: String) : OmnibarActionResult()
}

/**
 * Provider interface for Omnibar data.
 * Platform-specific implementations (IDEA, Desktop, CLI) should implement this.
 */
interface OmnibarDataProvider {
    /**
     * Get all available items for the Omnibar.
     * This should include commands, files, symbols, etc.
     */
    suspend fun getItems(): List<OmnibarItem>
    
    /**
     * Get recently used items for priority display.
     */
    suspend fun getRecentItems(): List<OmnibarItem>
    
    /**
     * Execute an action when an item is selected.
     */
    suspend fun executeAction(item: OmnibarItem): OmnibarActionResult
    
    /**
     * Record that an item was used (for recency tracking).
     */
    suspend fun recordUsage(item: OmnibarItem)
}

/**
 * Default implementation that returns empty results.
 */
object DefaultOmnibarDataProvider : OmnibarDataProvider {
    override suspend fun getItems(): List<OmnibarItem> = emptyList()
    override suspend fun getRecentItems(): List<OmnibarItem> = emptyList()
    override suspend fun executeAction(item: OmnibarItem): OmnibarActionResult = 
        OmnibarActionResult.Success()
    override suspend fun recordUsage(item: OmnibarItem) {}
}

