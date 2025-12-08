package cc.unitmesh.devins.idea.omnibar.model

/**
 * Search engine for filtering and ranking Omnibar items based on query.
 */
object OmnibarSearchEngine {
    /**
     * Search through items and return matching items sorted by relevance.
     * 
     * @param items All available items
     * @param query User's search query
     * @param maxResults Maximum number of results to return (default: 50)
     * @return List of items sorted by match score (highest first)
     */
    fun search(
        items: List<OmnibarItem>,
        query: String,
        maxResults: Int = 50
    ): List<OmnibarItem> {
        if (query.isBlank()) {
            // Return all items sorted by weight and recency
            return items
                .sortedWith(
                    compareByDescending<OmnibarItem> { it.weight }
                        .thenByDescending { it.lastUsedTimestamp }
                )
                .take(maxResults)
        }
        
        // Calculate scores and filter out non-matches
        val scoredItems = items
            .map { item -> item to item.matchScore(query) }
            .filter { (_, score) -> score > 0 }
        
        // Sort by score (descending) and recency (descending)
        return scoredItems
            .sortedWith(
                compareByDescending<Pair<OmnibarItem, Int>> { it.second }
                    .thenByDescending { it.first.lastUsedTimestamp }
            )
            .take(maxResults)
            .map { it.first }
    }
    
    /**
     * Group items by category after searching.
     * 
     * @param items Search results
     * @return Map of category name to list of items
     */
    fun groupByCategory(items: List<OmnibarItem>): Map<String, List<OmnibarItem>> {
        return items.groupBy { it.category.ifBlank { "Other" } }
    }
}

