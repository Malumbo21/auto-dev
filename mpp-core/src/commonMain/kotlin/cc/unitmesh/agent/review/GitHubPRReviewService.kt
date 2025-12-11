package cc.unitmesh.agent.review

import cc.unitmesh.agent.logging.getLogger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * GitHub implementation of PRReviewService
 * Uses GitHub REST API v3 for PR review comments
 */
class GitHubPRReviewService(
    private val repoOwner: String,
    private val repoName: String,
    private val token: String? = null,
    private val apiUrl: String = "https://api.github.com"
) : PRReviewService {
    
    private val logger = getLogger("GitHubPRReviewService")
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }
    
    override suspend fun getCommentThreads(prNumber: Int): List<PRCommentThread> {
        if (!isConfigured()) {
            logger.warn { "GitHub PR Review Service not configured" }
            return emptyList()
        }
        
        return try {
            val url = "$apiUrl/repos/$repoOwner/$repoName/pulls/$prNumber/comments"
            logger.info { "Fetching PR comments: $url" }
            
            val response = client.get(url) {
                configureHeaders()
            }
            
            if (response.status.isSuccess()) {
                val comments: List<GitHubPRComment> = response.body()
                groupCommentsIntoThreads(comments)
            } else {
                logger.warn { "Failed to fetch PR comments: ${response.status}" }
                emptyList()
            }
        } catch (e: Exception) {
            logger.error(e) { "Error fetching PR comments: ${e.message}" }
            emptyList()
        }
    }
    
    override suspend fun getCommentThreadsForFile(prNumber: Int, filePath: String): List<PRCommentThread> {
        val allThreads = getCommentThreads(prNumber)
        return allThreads.filter { it.location.filePath == filePath }
    }
    
    override suspend fun addComment(
        prNumber: Int,
        body: String,
        location: PRCommentLocation
    ): AddCommentResult {
        if (!isConfigured() || token.isNullOrBlank()) {
            return AddCommentResult(success = false, error = "GitHub token not configured")
        }
        
        return try {
            val url = "$apiUrl/repos/$repoOwner/$repoName/pulls/$prNumber/comments"
            
            val requestBody = GitHubCreateCommentRequest(
                body = body,
                path = location.filePath,
                line = location.lineNumber,
                side = if (location.side == DiffSide.LEFT) "LEFT" else "RIGHT",
                startLine = location.startLineNumber,
                startSide = if (location.isMultiLine) {
                    if (location.side == DiffSide.LEFT) "LEFT" else "RIGHT"
                } else null
            )
            
            val response = client.post(url) {
                configureHeaders()
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            
            if (response.status.isSuccess()) {
                val ghComment: GitHubPRComment = response.body()
                AddCommentResult(success = true, comment = ghComment.toPRComment())
            } else {
                val errorBody = response.bodyAsText()
                logger.warn { "Failed to add comment: ${response.status} - $errorBody" }
                AddCommentResult(success = false, error = "Failed to add comment: ${response.status}")
            }
        } catch (e: Exception) {
            logger.error(e) { "Error adding comment: ${e.message}" }
            AddCommentResult(success = false, error = "Error: ${e.message}")
        }
    }
    
    override suspend fun replyToThread(
        prNumber: Int,
        threadId: String,
        body: String
    ): AddCommentResult {
        if (!isConfigured() || token.isNullOrBlank()) {
            return AddCommentResult(success = false, error = "GitHub token not configured")
        }
        
        return try {
            val url = "$apiUrl/repos/$repoOwner/$repoName/pulls/$prNumber/comments"
            
            val requestBody = GitHubReplyRequest(
                body = body,
                inReplyTo = threadId.toLongOrNull() ?: return AddCommentResult(
                    success = false, 
                    error = "Invalid thread ID"
                )
            )
            
            val response = client.post(url) {
                configureHeaders()
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            
            if (response.status.isSuccess()) {
                val ghComment: GitHubPRComment = response.body()
                AddCommentResult(success = true, comment = ghComment.toPRComment())
            } else {
                val errorBody = response.bodyAsText()
                AddCommentResult(success = false, error = "Failed to reply: ${response.status}")
            }
        } catch (e: Exception) {
            logger.error(e) { "Error replying to thread: ${e.message}" }
            AddCommentResult(success = false, error = "Error: ${e.message}")
        }
    }
    
    override suspend fun resolveThread(prNumber: Int, threadId: String): ResolveThreadResult {
        // GitHub doesn't have a direct "resolve" API for PR comments via REST
        // This would typically be handled through GraphQL API
        logger.warn { "Resolve thread not supported for GitHub REST API" }
        return ResolveThreadResult(success = false, error = "Not supported via REST API")
    }

    override suspend fun unresolveThread(prNumber: Int, threadId: String): ResolveThreadResult {
        return ResolveThreadResult(success = false, error = "Not supported via REST API")
    }

    override suspend fun getPRInfo(prNumber: Int): PRInfo? {
        if (!isConfigured()) return null

        return try {
            val url = "$apiUrl/repos/$repoOwner/$repoName/pulls/$prNumber"
            val response = client.get(url) {
                configureHeaders()
            }

            if (response.status.isSuccess()) {
                val ghPR: GitHubPR = response.body()
                ghPR.toPRInfo()
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Error fetching PR info: ${e.message}" }
            null
        }
    }

    override suspend fun getReviews(prNumber: Int): List<PRReview> {
        if (!isConfigured()) return emptyList()

        return try {
            val url = "$apiUrl/repos/$repoOwner/$repoName/pulls/$prNumber/reviews"
            val response = client.get(url) {
                configureHeaders()
            }

            if (response.status.isSuccess()) {
                val ghReviews: List<GitHubReview> = response.body()
                ghReviews.map { it.toPRReview() }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logger.error(e) { "Error fetching reviews: ${e.message}" }
            emptyList()
        }
    }

    override fun isConfigured(): Boolean = repoOwner.isNotBlank() && repoName.isNotBlank()

    override fun getPlatformType(): String = "github"

    override fun getOwner(): String = repoOwner

    override fun getRepoName(): String = repoName

    private fun HttpRequestBuilder.configureHeaders() {
        if (!token.isNullOrBlank()) {
            header("Authorization", "Bearer $token")
        }
        header("Accept", "application/vnd.github+json")
        header("X-GitHub-Api-Version", "2022-11-28")
    }

    private fun groupCommentsIntoThreads(comments: List<GitHubPRComment>): List<PRCommentThread> {
        // Group by in_reply_to_id to form threads
        val rootComments = comments.filter { it.inReplyToId == null }
        val repliesMap = comments.filter { it.inReplyToId != null }
            .groupBy { it.inReplyToId!! }

        return rootComments.map { root ->
            val replies = repliesMap[root.id] ?: emptyList()
            val allComments = listOf(root) + replies.sortedBy { it.createdAt }

            PRCommentThread(
                id = root.id.toString(),
                location = PRCommentLocation(
                    filePath = root.path,
                    side = if (root.side == "LEFT") DiffSide.LEFT else DiffSide.RIGHT,
                    lineNumber = root.line ?: root.originalLine ?: 0,
                    isMultiLine = root.startLine != null,
                    startLineNumber = root.startLine,
                    originalPosition = root.originalPosition
                ),
                comments = allComments.map { it.toPRComment() },
                isResolved = false, // GitHub REST API doesn't expose this
                isOutdated = root.position == null && root.originalPosition != null
            )
        }
    }

    companion object {
        /**
         * Parse repository URL to extract owner and repo name
         */
        fun parseRepoUrl(url: String): Pair<String, String>? {
            val patterns = listOf(
                Regex("""github\.com[/:]([^/]+)/([^/.]+)(?:\.git)?"""),
                Regex("""^([^/]+)/([^/]+)$""")
            )

            for (pattern in patterns) {
                val match = pattern.find(url)
                if (match != null) {
                    return Pair(match.groupValues[1], match.groupValues[2].removeSuffix(".git"))
                }
            }
            return null
        }
    }
}

// GitHub API Response Models

@Serializable
private data class GitHubPRComment(
    val id: Long,
    val body: String,
    val path: String,
    val line: Int? = null,
    @SerialName("original_line") val originalLine: Int? = null,
    val side: String = "RIGHT",
    @SerialName("start_line") val startLine: Int? = null,
    @SerialName("start_side") val startSide: String? = null,
    val position: Int? = null,
    @SerialName("original_position") val originalPosition: Int? = null,
    @SerialName("in_reply_to_id") val inReplyToId: Long? = null,
    val user: GitHubUser,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toPRComment(): PRComment = PRComment(
        id = id.toString(),
        author = user.login,
        authorAvatarUrl = user.avatarUrl,
        body = body,
        createdAt = createdAt,
        updatedAt = updatedAt,
        location = PRCommentLocation(
            filePath = path,
            side = if (side == "LEFT") DiffSide.LEFT else DiffSide.RIGHT,
            lineNumber = line ?: originalLine ?: 0,
            isMultiLine = startLine != null,
            startLineNumber = startLine,
            originalPosition = originalPosition
        ),
        isOutdated = position == null && originalPosition != null,
        platformId = id.toString()
    )
}

@Serializable
private data class GitHubUser(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
private data class GitHubCreateCommentRequest(
    val body: String,
    val path: String,
    val line: Int,
    val side: String,
    @SerialName("start_line") val startLine: Int? = null,
    @SerialName("start_side") val startSide: String? = null
)

@Serializable
private data class GitHubReplyRequest(
    val body: String,
    @SerialName("in_reply_to") val inReplyTo: Long
)

@Serializable
private data class GitHubPR(
    val id: Long,
    val number: Int,
    val title: String,
    val body: String? = null,
    val state: String,
    val draft: Boolean = false,
    val user: GitHubUser,
    val head: GitHubBranch,
    val base: GitHubBranch,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    val comments: Int = 0,
    @SerialName("review_comments") val reviewComments: Int = 0
) {
    fun toPRInfo(): PRInfo = PRInfo(
        id = id.toString(),
        number = number,
        title = title,
        description = body,
        author = user.login,
        authorAvatarUrl = user.avatarUrl,
        status = when {
            draft -> PRStatus.DRAFT
            state == "closed" -> PRStatus.CLOSED
            state == "merged" -> PRStatus.MERGED
            else -> PRStatus.OPEN
        },
        sourceBranch = head.ref,
        targetBranch = base.ref,
        createdAt = createdAt,
        updatedAt = updatedAt,
        commentCount = comments + reviewComments
    )
}

@Serializable
private data class GitHubBranch(
    val ref: String,
    val sha: String
)

@Serializable
private data class GitHubReview(
    val id: Long,
    val user: GitHubUser,
    val body: String? = null,
    val state: String,
    @SerialName("submitted_at") val submittedAt: String? = null
) {
    fun toPRReview(): PRReview = PRReview(
        id = id.toString(),
        author = user.login,
        authorAvatarUrl = user.avatarUrl,
        status = when (state) {
            "APPROVED" -> PRReviewStatus.APPROVED
            "CHANGES_REQUESTED" -> PRReviewStatus.CHANGES_REQUESTED
            "COMMENTED" -> PRReviewStatus.COMMENTED
            "DISMISSED" -> PRReviewStatus.DISMISSED
            else -> PRReviewStatus.PENDING
        },
        body = body,
        createdAt = submittedAt ?: "",
        submittedAt = submittedAt
    )
}

