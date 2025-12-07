package cc.unitmesh.devins.ui.compose.omnibar

import cc.unitmesh.agent.Platform

/**
 * Search engine for Omnibar with fuzzy matching and weighted ranking.
 *
 * Ranking factors (in priority order):
 * 1. Recency - recently used items get higher priority
 * 2. Context relevance - items matching current context (open file type, etc.)
 * 3. Match quality - exact > prefix > contains > fuzzy
 * 4. Base weight - configured priority of item type
 */
class OmnibarSearchEngine {

    companion object {
        private const val RECENCY_WEIGHT = 100
        private const val RECENCY_DECAY_HOURS = 24
        private const val MAX_RESULTS = 50
    }

    /**
     * Search and rank items based on query and context.
     *
     * @param items All available items
     * @param query Search query (can be empty for default ranking)
     * @param contextTags Optional tags for context-aware ranking (e.g., "kotlin", "test")
     * @return Ranked and filtered list of items
     */
    fun search(
        items: List<OmnibarItem>,
        query: String,
        contextTags: Set<String> = emptySet()
    ): List<OmnibarItem> {
        if (query.isBlank()) {
            // No query - return items ranked by recency and weight
            return items
                .map { it to calculateRecencyBonus(it) }
                .sortedByDescending { (item, recencyBonus) ->
                    item.weight + recencyBonus + contextBonus(item, contextTags)
                }
                .take(MAX_RESULTS)
                .map { it.first }
        }

        // With query - filter and rank by match quality
        return items
            .map { item ->
                val matchScore = item.matchScore(query)
                val recencyBonus = calculateRecencyBonus(item)
                val contextScore = contextBonus(item, contextTags)
                item to (matchScore + recencyBonus + contextScore)
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(MAX_RESULTS)
            .map { it.first }
    }

    /**
     * Group items by category for display.
     */
    fun groupByCategory(items: List<OmnibarItem>): Map<String, List<OmnibarItem>> {
        return items.groupBy { item ->
            item.category.ifBlank {
                when (item.type) {
                    OmnibarItemType.COMMAND -> "Commands"
                    OmnibarItemType.CUSTOM_COMMAND -> "Custom Commands"
                    OmnibarItemType.FILE -> "Files"
                    OmnibarItemType.SYMBOL -> "Symbols"
                    OmnibarItemType.AGENT -> "Agents"
                    OmnibarItemType.RECENT -> "Recent"
                    OmnibarItemType.SETTING -> "Settings"
                }
            }
        }
    }

    /**
     * Calculate recency bonus based on last used timestamp.
     * Items used recently get higher scores, with exponential decay.
     */
    private fun calculateRecencyBonus(item: OmnibarItem): Int {
        if (item.lastUsedTimestamp == 0L) return 0

        val currentTime = Platform.getCurrentTimestamp()
        val ageHours = (currentTime - item.lastUsedTimestamp) / (1000L * 60 * 60)

        return when {
            ageHours < 1 -> RECENCY_WEIGHT * 4  // Last hour
            ageHours < 6 -> RECENCY_WEIGHT * 2  // Last 6 hours
            ageHours < RECENCY_DECAY_HOURS -> RECENCY_WEIGHT  // Last 24 hours
            ageHours < 72 -> RECENCY_WEIGHT / 2  // Last 3 days
            else -> 0
        }
    }

    /**
     * Calculate context bonus for items matching current context.
     */
    private fun contextBonus(item: OmnibarItem, contextTags: Set<String>): Int {
        if (contextTags.isEmpty()) return 0

        val itemTags = item.metadata["tags"] as? List<*> ?: return 0
        val matchCount = itemTags.count { tag ->
            tag is String && contextTags.contains(tag.lowercase())
        }

        return matchCount * 50
    }
}
