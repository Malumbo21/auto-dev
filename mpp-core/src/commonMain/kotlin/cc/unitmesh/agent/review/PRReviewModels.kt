package cc.unitmesh.agent.review

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Side of the diff where the comment is located
 */
enum class DiffSide {
    LEFT,   // Old/deleted side
    RIGHT   // New/added side
}

/**
 * Location of a PR comment in the diff
 * Platform-agnostic representation that can map to GitHub, GitLab, etc.
 */
@Serializable
data class PRCommentLocation(
    val filePath: String,
    val side: DiffSide = DiffSide.RIGHT,
    val lineNumber: Int,
    val isMultiLine: Boolean = false,
    val startLineNumber: Int? = null,
    /**
     * Original position in the diff (for outdated comments)
     * This is used when the code has changed since the comment was made
     */
    val originalPosition: Int? = null
)

/**
 * A single PR review comment
 * Unified model that can represent comments from GitHub, GitLab, Bitbucket, etc.
 */
@Serializable
data class PRComment(
    val id: String,
    val author: String,
    val authorAvatarUrl: String? = null,
    val body: String,
    val createdAt: String,
    val updatedAt: String? = null,
    val location: PRCommentLocation,
    /**
     * Whether this comment has been resolved
     */
    val isResolved: Boolean = false,
    /**
     * Whether this comment is outdated (code has changed since comment was made)
     */
    val isOutdated: Boolean = false,
    /**
     * Platform-specific ID for API operations
     */
    val platformId: String? = null
)

/**
 * A thread of PR comments (a comment and its replies)
 */
@Serializable
data class PRCommentThread(
    val id: String,
    val location: PRCommentLocation,
    val comments: List<PRComment>,
    val isResolved: Boolean = false,
    val isCollapsed: Boolean = false,
    val isOutdated: Boolean = false,
    /**
     * When this thread was fetched/cached
     */
    val cachedAt: Instant? = null
) {
    val isFromCache: Boolean get() = cachedAt != null
    
    val firstComment: PRComment? get() = comments.firstOrNull()
    
    val replyCount: Int get() = (comments.size - 1).coerceAtLeast(0)
    
    fun withCacheTimestamp(timestamp: Instant = Clock.System.now()): PRCommentThread {
        return copy(cachedAt = timestamp)
    }
}

/**
 * PR review status
 */
enum class PRReviewStatus {
    PENDING,
    APPROVED,
    CHANGES_REQUESTED,
    COMMENTED,
    DISMISSED
}

/**
 * A complete PR review containing multiple comment threads
 */
@Serializable
data class PRReview(
    val id: String,
    val author: String,
    val authorAvatarUrl: String? = null,
    val status: PRReviewStatus = PRReviewStatus.PENDING,
    val body: String? = null,
    val threads: List<PRCommentThread> = emptyList(),
    val createdAt: String,
    val submittedAt: String? = null
)

/**
 * Summary information about a pull request
 */
@Serializable
data class PRInfo(
    val id: String,
    val number: Int,
    val title: String,
    val description: String? = null,
    val author: String,
    val authorAvatarUrl: String? = null,
    val status: PRStatus = PRStatus.OPEN,
    val sourceBranch: String,
    val targetBranch: String,
    val createdAt: String,
    val updatedAt: String? = null,
    val commentCount: Int = 0,
    val reviewCount: Int = 0
)

/**
 * PR status
 */
enum class PRStatus {
    OPEN,
    CLOSED,
    MERGED,
    DRAFT
}

/**
 * Result of adding a new comment
 */
@Serializable
data class AddCommentResult(
    val success: Boolean,
    val comment: PRComment? = null,
    val error: String? = null
)

/**
 * Result of resolving a thread
 */
@Serializable
data class ResolveThreadResult(
    val success: Boolean,
    val error: String? = null
)

