package cc.unitmesh.agent.review

/**
 * Supported PR review platforms
 */
enum class PRPlatformType {
    GITHUB,
    GITLAB,
    BITBUCKET,
    AZURE_DEVOPS,
    UNKNOWN
}

/**
 * Configuration for creating a PR review service
 */
data class PRReviewConfig(
    val platform: PRPlatformType,
    val owner: String = "",
    val repoName: String = "",
    val projectId: String = "",  // For GitLab
    val token: String? = null,
    val apiUrl: String? = null   // Custom API URL for enterprise instances
)

/**
 * Factory for creating platform-specific PR review services
 */
object PRReviewServiceFactory {
    
    /**
     * Create a PR review service based on configuration
     */
    fun create(config: PRReviewConfig): PRReviewService {
        return when (config.platform) {
            PRPlatformType.GITHUB -> GitHubPRReviewService(
                repoOwner = config.owner,
                repoName = config.repoName,
                token = config.token,
                apiUrl = config.apiUrl ?: "https://api.github.com"
            )
            PRPlatformType.GITLAB -> GitLabPRReviewService(
                projectId = config.projectId.ifBlank { "${config.owner}/${config.repoName}" },
                token = config.token,
                apiUrl = config.apiUrl ?: "https://gitlab.com/api/v4"
            )
            PRPlatformType.BITBUCKET -> {
                // TODO: Implement BitbucketPRReviewService
                NoOpPRReviewService()
            }
            PRPlatformType.AZURE_DEVOPS -> {
                // TODO: Implement AzureDevOpsPRReviewService
                NoOpPRReviewService()
            }
            PRPlatformType.UNKNOWN -> NoOpPRReviewService()
        }
    }
    
    /**
     * Detect platform type from repository URL
     */
    fun detectPlatform(repoUrl: String): PRPlatformType {
        return when {
            repoUrl.contains("github.com") || repoUrl.contains("github.") -> PRPlatformType.GITHUB
            repoUrl.contains("gitlab.com") || repoUrl.contains("gitlab.") -> PRPlatformType.GITLAB
            repoUrl.contains("bitbucket.org") || repoUrl.contains("bitbucket.") -> PRPlatformType.BITBUCKET
            repoUrl.contains("dev.azure.com") || repoUrl.contains("visualstudio.com") -> PRPlatformType.AZURE_DEVOPS
            else -> PRPlatformType.UNKNOWN
        }
    }
    
    /**
     * Create a PR review service from a repository URL
     * Automatically detects the platform and parses owner/repo
     */
    fun createFromUrl(repoUrl: String, token: String? = null): PRReviewService {
        val platform = detectPlatform(repoUrl)
        
        return when (platform) {
            PRPlatformType.GITHUB -> {
                val (owner, repo) = GitHubPRReviewService.parseRepoUrl(repoUrl) 
                    ?: return NoOpPRReviewService()
                GitHubPRReviewService(
                    repoOwner = owner,
                    repoName = repo,
                    token = token
                )
            }
            PRPlatformType.GITLAB -> {
                val projectId = GitLabPRReviewService.parseRepoUrl(repoUrl) 
                    ?: return NoOpPRReviewService()
                GitLabPRReviewService(
                    projectId = projectId,
                    token = token
                )
            }
            else -> NoOpPRReviewService()
        }
    }
}

