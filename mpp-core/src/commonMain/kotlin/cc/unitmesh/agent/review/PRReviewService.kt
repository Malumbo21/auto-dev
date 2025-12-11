package cc.unitmesh.agent.review

/**
 * Abstract interface for PR review services
 * Supports GitHub, GitLab, Bitbucket, etc.
 * 
 * Similar to IssueTracker, this provides a unified API for working with
 * pull request review comments across different platforms.
 */
interface PRReviewService {
    /**
     * Get all comment threads for a pull request
     * 
     * @param prNumber PR number
     * @return List of comment threads, empty if none found
     */
    suspend fun getCommentThreads(prNumber: Int): List<PRCommentThread>
    
    /**
     * Get comment threads for a specific file in a PR
     * 
     * @param prNumber PR number
     * @param filePath File path relative to repository root
     * @return List of comment threads for the file
     */
    suspend fun getCommentThreadsForFile(prNumber: Int, filePath: String): List<PRCommentThread>
    
    /**
     * Add a new comment to a PR
     * 
     * @param prNumber PR number
     * @param body Comment body (Markdown supported)
     * @param location Location of the comment in the diff
     * @return Result containing the created comment or error
     */
    suspend fun addComment(
        prNumber: Int,
        body: String,
        location: PRCommentLocation
    ): AddCommentResult
    
    /**
     * Reply to an existing comment thread
     * 
     * @param prNumber PR number
     * @param threadId Thread ID to reply to
     * @param body Reply body (Markdown supported)
     * @return Result containing the created comment or error
     */
    suspend fun replyToThread(
        prNumber: Int,
        threadId: String,
        body: String
    ): AddCommentResult
    
    /**
     * Resolve a comment thread
     * 
     * @param prNumber PR number
     * @param threadId Thread ID to resolve
     * @return Result indicating success or error
     */
    suspend fun resolveThread(
        prNumber: Int,
        threadId: String
    ): ResolveThreadResult
    
    /**
     * Unresolve a previously resolved comment thread
     * 
     * @param prNumber PR number
     * @param threadId Thread ID to unresolve
     * @return Result indicating success or error
     */
    suspend fun unresolveThread(
        prNumber: Int,
        threadId: String
    ): ResolveThreadResult
    
    /**
     * Get PR information
     * 
     * @param prNumber PR number
     * @return PR info or null if not found
     */
    suspend fun getPRInfo(prNumber: Int): PRInfo?
    
    /**
     * Get all reviews for a PR
     * 
     * @param prNumber PR number
     * @return List of reviews
     */
    suspend fun getReviews(prNumber: Int): List<PRReview>
    
    /**
     * Check if the service is properly configured
     * 
     * @return true if configured and ready to use
     */
    fun isConfigured(): Boolean
    
    /**
     * Get the platform type (e.g., "github", "gitlab", "bitbucket")
     */
    fun getPlatformType(): String
    
    /**
     * Get repository owner
     */
    fun getOwner(): String
    
    /**
     * Get repository name
     */
    fun getRepoName(): String
}

/**
 * No-op PR review service for cases where PR review is not configured
 */
class NoOpPRReviewService : PRReviewService {
    override suspend fun getCommentThreads(prNumber: Int): List<PRCommentThread> = emptyList()
    
    override suspend fun getCommentThreadsForFile(prNumber: Int, filePath: String): List<PRCommentThread> = emptyList()
    
    override suspend fun addComment(prNumber: Int, body: String, location: PRCommentLocation): AddCommentResult {
        return AddCommentResult(success = false, error = "PR review service not configured")
    }
    
    override suspend fun replyToThread(prNumber: Int, threadId: String, body: String): AddCommentResult {
        return AddCommentResult(success = false, error = "PR review service not configured")
    }
    
    override suspend fun resolveThread(prNumber: Int, threadId: String): ResolveThreadResult {
        return ResolveThreadResult(success = false, error = "PR review service not configured")
    }
    
    override suspend fun unresolveThread(prNumber: Int, threadId: String): ResolveThreadResult {
        return ResolveThreadResult(success = false, error = "PR review service not configured")
    }
    
    override suspend fun getPRInfo(prNumber: Int): PRInfo? = null
    
    override suspend fun getReviews(prNumber: Int): List<PRReview> = emptyList()
    
    override fun isConfigured(): Boolean = false
    
    override fun getPlatformType(): String = "none"
    
    override fun getOwner(): String = ""
    
    override fun getRepoName(): String = ""
}

