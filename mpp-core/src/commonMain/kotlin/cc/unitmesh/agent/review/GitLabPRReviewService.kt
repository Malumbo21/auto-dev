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
 * GitLab implementation of PRReviewService
 * Uses GitLab REST API for Merge Request discussion/notes
 * 
 * GitLab terminology:
 * - Pull Request = Merge Request (MR)
 * - PR Comment = Discussion/Note
 * - Thread = Discussion with multiple notes
 */
class GitLabPRReviewService(
    private val projectId: String,  // Can be "owner/repo" or numeric ID
    private val token: String? = null,
    private val apiUrl: String = "https://gitlab.com/api/v4"
) : PRReviewService {
    
    private val logger = getLogger("GitLabPRReviewService")
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }
    
    // Extract owner and repo from projectId if in "owner/repo" format
    private val ownerAndRepo: Pair<String, String>? by lazy {
        if (projectId.contains("/")) {
            val parts = projectId.split("/", limit = 2)
            if (parts.size == 2) Pair(parts[0], parts[1]) else null
        } else {
            null
        }
    }
    
    private val encodedProjectId: String
        get() = projectId.replace("/", "%2F")
    
    override suspend fun getCommentThreads(prNumber: Int): List<PRCommentThread> {
        if (!isConfigured()) {
            logger.warn { "GitLab MR Review Service not configured" }
            return emptyList()
        }
        
        return try {
            val url = "$apiUrl/projects/$encodedProjectId/merge_requests/$prNumber/discussions"
            logger.info { "Fetching MR discussions: $url" }
            
            val response = client.get(url) {
                configureHeaders()
            }
            
            if (response.status.isSuccess()) {
                val discussions: List<GitLabDiscussion> = response.body()
                discussions.filter { it.notes.any { note -> note.position != null } }
                    .map { it.toPRCommentThread() }
            } else {
                logger.warn { "Failed to fetch MR discussions: ${response.status}" }
                emptyList()
            }
        } catch (e: Exception) {
            logger.error(e) { "Error fetching MR discussions: ${e.message}" }
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
            return AddCommentResult(success = false, error = "GitLab token not configured")
        }
        
        return try {
            val url = "$apiUrl/projects/$encodedProjectId/merge_requests/$prNumber/discussions"
            
            val position = GitLabPosition(
                baseSha = "", // Would need to be fetched from MR details
                startSha = "",
                headSha = "",
                positionType = "text",
                newPath = if (location.side == DiffSide.RIGHT) location.filePath else null,
                oldPath = if (location.side == DiffSide.LEFT) location.filePath else null,
                newLine = if (location.side == DiffSide.RIGHT) location.lineNumber else null,
                oldLine = if (location.side == DiffSide.LEFT) location.lineNumber else null
            )
            
            val requestBody = GitLabCreateDiscussionRequest(
                body = body,
                position = position
            )
            
            val response = client.post(url) {
                configureHeaders()
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            
            if (response.status.isSuccess()) {
                val discussion: GitLabDiscussion = response.body()
                val firstNote = discussion.notes.firstOrNull()
                AddCommentResult(
                    success = true, 
                    comment = firstNote?.toPRComment(discussion.id)
                )
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
            return AddCommentResult(success = false, error = "GitLab token not configured")
        }
        
        return try {
            val url = "$apiUrl/projects/$encodedProjectId/merge_requests/$prNumber/discussions/$threadId/notes"
            
            val response = client.post(url) {
                configureHeaders()
                contentType(ContentType.Application.Json)
                setBody(mapOf("body" to body))
            }
            
            if (response.status.isSuccess()) {
                val note: GitLabNote = response.body()
                AddCommentResult(success = true, comment = note.toPRComment(threadId))
            } else {
                AddCommentResult(success = false, error = "Failed to reply: ${response.status}")
            }
        } catch (e: Exception) {
            logger.error(e) { "Error replying to thread: ${e.message}" }
            AddCommentResult(success = false, error = "Error: ${e.message}")
        }
    }

    override suspend fun resolveThread(prNumber: Int, threadId: String): ResolveThreadResult {
        if (!isConfigured() || token.isNullOrBlank()) {
            return ResolveThreadResult(success = false, error = "GitLab token not configured")
        }

        return try {
            val url = "$apiUrl/projects/$encodedProjectId/merge_requests/$prNumber/discussions/$threadId"

            val response = client.put(url) {
                configureHeaders()
                contentType(ContentType.Application.Json)
                setBody(mapOf("resolved" to true))
            }

            ResolveThreadResult(success = response.status.isSuccess())
        } catch (e: Exception) {
            logger.error(e) { "Error resolving thread: ${e.message}" }
            ResolveThreadResult(success = false, error = "Error: ${e.message}")
        }
    }

    override suspend fun unresolveThread(prNumber: Int, threadId: String): ResolveThreadResult {
        if (!isConfigured() || token.isNullOrBlank()) {
            return ResolveThreadResult(success = false, error = "GitLab token not configured")
        }

        return try {
            val url = "$apiUrl/projects/$encodedProjectId/merge_requests/$prNumber/discussions/$threadId"

            val response = client.put(url) {
                configureHeaders()
                contentType(ContentType.Application.Json)
                setBody(mapOf("resolved" to false))
            }

            ResolveThreadResult(success = response.status.isSuccess())
        } catch (e: Exception) {
            logger.error(e) { "Error unresolving thread: ${e.message}" }
            ResolveThreadResult(success = false, error = "Error: ${e.message}")
        }
    }

    override suspend fun getPRInfo(prNumber: Int): PRInfo? {
        if (!isConfigured()) return null

        return try {
            val url = "$apiUrl/projects/$encodedProjectId/merge_requests/$prNumber"
            val response = client.get(url) {
                configureHeaders()
            }

            if (response.status.isSuccess()) {
                val mr: GitLabMergeRequest = response.body()
                mr.toPRInfo()
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Error fetching MR info: ${e.message}" }
            null
        }
    }

    override suspend fun getReviews(prNumber: Int): List<PRReview> {
        // GitLab doesn't have a separate "reviews" concept like GitHub
        // Approvals are handled differently
        return emptyList()
    }

    override fun isConfigured(): Boolean = projectId.isNotBlank()

    override fun getPlatformType(): String = "gitlab"

    override fun getOwner(): String = ownerAndRepo?.first ?: ""

    override fun getRepoName(): String = ownerAndRepo?.second ?: projectId

    private fun HttpRequestBuilder.configureHeaders() {
        if (!token.isNullOrBlank()) {
            header("PRIVATE-TOKEN", token)
        }
        header("Accept", "application/json")
    }

    companion object {
        /**
         * Parse GitLab repository URL to extract project path
         */
        fun parseRepoUrl(url: String): String? {
            val patterns = listOf(
                Regex("""gitlab\.com[/:]([^/]+/[^/.]+)(?:\.git)?"""),
                Regex("""^([^/]+/[^/]+)$""")
            )

            for (pattern in patterns) {
                val match = pattern.find(url)
                if (match != null) {
                    return match.groupValues[1].removeSuffix(".git")
                }
            }
            return null
        }
    }
}

// GitLab API Response Models

@Serializable
private data class GitLabDiscussion(
    val id: String,
    val notes: List<GitLabNote>
) {
    fun toPRCommentThread(): PRCommentThread {
        val firstNote = notes.firstOrNull()
        val position = firstNote?.position

        return PRCommentThread(
            id = id,
            location = PRCommentLocation(
                filePath = position?.newPath ?: position?.oldPath ?: "",
                side = if (position?.newLine != null) DiffSide.RIGHT else DiffSide.LEFT,
                lineNumber = position?.newLine ?: position?.oldLine ?: 0
            ),
            comments = notes.map { it.toPRComment(id) },
            isResolved = notes.all { it.resolved == true }
        )
    }
}

@Serializable
private data class GitLabNote(
    val id: Long,
    val body: String,
    val author: GitLabAuthor,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    val resolved: Boolean? = null,
    val position: GitLabPosition? = null
) {
    fun toPRComment(discussionId: String): PRComment = PRComment(
        id = id.toString(),
        author = author.username,
        authorAvatarUrl = author.avatarUrl,
        body = body,
        createdAt = createdAt,
        updatedAt = updatedAt,
        location = PRCommentLocation(
            filePath = position?.newPath ?: position?.oldPath ?: "",
            side = if (position?.newLine != null) DiffSide.RIGHT else DiffSide.LEFT,
            lineNumber = position?.newLine ?: position?.oldLine ?: 0
        ),
        isResolved = resolved == true,
        platformId = id.toString()
    )
}

@Serializable
private data class GitLabAuthor(
    val username: String,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
private data class GitLabPosition(
    @SerialName("base_sha") val baseSha: String = "",
    @SerialName("start_sha") val startSha: String = "",
    @SerialName("head_sha") val headSha: String = "",
    @SerialName("position_type") val positionType: String = "text",
    @SerialName("new_path") val newPath: String? = null,
    @SerialName("old_path") val oldPath: String? = null,
    @SerialName("new_line") val newLine: Int? = null,
    @SerialName("old_line") val oldLine: Int? = null
)

@Serializable
private data class GitLabCreateDiscussionRequest(
    val body: String,
    val position: GitLabPosition
)

@Serializable
private data class GitLabMergeRequest(
    val id: Long,
    val iid: Int,
    val title: String,
    val description: String? = null,
    val state: String,
    val draft: Boolean = false,
    val author: GitLabAuthor,
    @SerialName("source_branch") val sourceBranch: String,
    @SerialName("target_branch") val targetBranch: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("user_notes_count") val userNotesCount: Int = 0
) {
    fun toPRInfo(): PRInfo = PRInfo(
        id = id.toString(),
        number = iid,
        title = title,
        description = description,
        author = author.username,
        authorAvatarUrl = author.avatarUrl,
        status = when {
            draft -> PRStatus.DRAFT
            state == "closed" -> PRStatus.CLOSED
            state == "merged" -> PRStatus.MERGED
            else -> PRStatus.OPEN
        },
        sourceBranch = sourceBranch,
        targetBranch = targetBranch,
        createdAt = createdAt,
        updatedAt = updatedAt,
        commentCount = userNotesCount
    )
}

