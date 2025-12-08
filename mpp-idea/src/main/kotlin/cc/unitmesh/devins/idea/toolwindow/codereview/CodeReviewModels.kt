package cc.unitmesh.devins.idea.toolwindow.codereview

import cc.unitmesh.agent.diff.ChangeType
import cc.unitmesh.agent.diff.DiffHunk

/**
 * Information about a changed file
 */
data class DiffFileInfo(
    val path: String,
    val oldPath: String? = null,
    val changeType: ChangeType,
    val hunks: List<DiffHunk> = emptyList(),
    val additions: Int = 0,
    val deletions: Int = 0,
    val isBinary: Boolean = false
) {
    val displayPath: String
        get() = if (oldPath != null && oldPath != path) {
            "$oldPath → $path"
        } else {
            path
        }
}

/**
 * Stages of code review analysis
 */
enum class AnalysisStage {
    IDLE,
    FETCHING_DIFF,
    ANALYZING_CHANGES,
    GENERATING_REVIEW,
    COMPLETED,
    ERROR,
    // Additional stages for AI-powered code review
    RUNNING_LINT,
    ANALYZING_LINT,
    GENERATING_PLAN,
    WAITING_FOR_USER_INPUT,
    GENERATING_FIX
}

/**
 * Represents an issue found in code review
 */
data class ReviewIssue(
    val file: String,
    val line: Int?,
    val severity: Severity,
    val category: Category,
    val message: String,
    val suggestion: String? = null
) {
    enum class Severity {
        CRITICAL,
        MAJOR,
        MINOR,
        INFO
    }

    enum class Category {
        BUG,
        SECURITY,
        PERFORMANCE,
        STYLE,
        DOCUMENTATION,
        TESTING,
        OTHER
    }
}

/**
 * Summary of code review analysis
 */
data class ReviewSummary(
    val filesAnalyzed: Int,
    val issuesFound: Int,
    val criticalIssues: Int,
    val majorIssues: Int,
    val minorIssues: Int,
    val overallScore: Int, // 0-100
    val recommendation: String
)

/**
 * Complete code review result
 */
data class CodeReviewResult(
    val summary: ReviewSummary,
    val issues: List<ReviewIssue>,
    val generalComments: List<String> = emptyList()
)

/**
 * Git commit information
 */
data class CommitInfo(
    val hash: String,
    val shortHash: String = hash.take(7),
    val message: String,
    val author: String,
    val timestamp: Long,
    val date: String = "",
    val filesChanged: Int = 0,
    val additions: Int = 0,
    val deletions: Int = 0,
    val issueInfo: cc.unitmesh.agent.tracker.IssueInfo? = null,
    val isLoadingIssue: Boolean = false,
    val issueLoadError: String? = null,
    val issueFromCache: Boolean = false,
    val issueCacheAge: String? = null
)

/**
 * AI analysis progress for streaming display
 */
data class AIAnalysisProgress(
    val stage: AnalysisStage = AnalysisStage.IDLE,
    val currentFile: String? = null,
    val currentLine: Int? = null,
    val lintOutput: String = "",
    val lintResults: List<cc.unitmesh.agent.linter.LintFileResult> = emptyList(),
    val modifiedCodeRanges: Map<String, List<cc.unitmesh.agent.codereview.ModifiedCodeRange>> = emptyMap(),
    val analysisOutput: String = "",
    val planOutput: String = "",
    val fixOutput: String = "",
    val userFeedback: String = "",
    val fixRenderer: Any? = null  // Renderer for fix generation
)

/**
 * State for code review UI
 */
data class CodeReviewState(
    val stage: AnalysisStage = AnalysisStage.IDLE,
    val selectedCommits: List<CommitInfo> = emptyList(),
    val selectedCommitIndices: Set<Int> = emptySet(),
    val diffFiles: List<DiffFileInfo> = emptyList(),
    val reviewResult: CodeReviewResult? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val commitHistory: List<CommitInfo> = emptyList(),
    val error: String? = null,
    val isLoadingDiff: Boolean = false,
    val aiProgress: AIAnalysisProgress = AIAnalysisProgress()
)

