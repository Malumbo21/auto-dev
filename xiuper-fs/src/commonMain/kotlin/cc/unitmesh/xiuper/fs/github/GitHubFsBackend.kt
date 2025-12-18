package cc.unitmesh.xiuper.fs.github

import cc.unitmesh.xiuper.fs.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * GitHub API as a filesystem backend.
 * 
 * Maps GitHub repository structure to a filesystem:
 * - Directories → GitHub directories
 * - Files → GitHub blobs
 * - Read → Get file contents via GitHub API
 * - List → List directory contents
 * 
 * Path format: /owner/repo/ref/path/to/file
 * Example: /microsoft/vscode/main/README.md
 * 
 * Usage:
 * ```kotlin
 * val backend = GitHubFsBackend(token = "ghp_xxxxx")
 * val shell = ShellFsInterpreter(backend)
 * 
 * shell.execute("ls /microsoft/vscode/main")
 * shell.execute("cat /microsoft/vscode/main/README.md")
 * ```
 */
class GitHubFsBackend(
    private val token: String? = null,
    private val baseUrl: String = "https://api.github.com"
) : FsBackend, AutoCloseable {

    private val client = HttpClient {
        expectSuccess = false
    }

    /**
     * Close the HTTP client to release resources.
     * Should be called when the backend is no longer needed.
     */
    override fun close() {
        client.close()
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override suspend fun stat(path: FsPath): FsStat {
        val parsed = parsePath(path.value)
        
        return try {
            val response: HttpResponse = client.get("$baseUrl/repos/${parsed.owner}/${parsed.repo}/contents/${parsed.path}") {
                parameter("ref", parsed.ref)
                token?.let { header("Authorization", "Bearer $it") }
            }
            
            if (!response.status.isSuccess()) {
                throw FsException(FsErrorCode.ENOENT, "Path not found: ${path.value}")
            }
            
            val content: GitHubContent = json.decodeFromString(response.bodyAsText())
            
            FsStat(
                path = path,
                isDirectory = content.type == "dir",
                size = content.size?.toLong()
            )
        } catch (e: FsException) {
            throw e
        } catch (e: Exception) {
            throw FsException(FsErrorCode.EIO, "Failed to stat ${path.value}: ${e.message}")
        }
    }
    
    override suspend fun list(path: FsPath): List<FsEntry> {
        val parsed = parsePath(path.value)
        
        return try {
            val response: HttpResponse = client.get("$baseUrl/repos/${parsed.owner}/${parsed.repo}/contents/${parsed.path}") {
                parameter("ref", parsed.ref)
                token?.let { header("Authorization", "Bearer $it") }
            }
            
            if (!response.status.isSuccess()) {
                throw FsException(FsErrorCode.ENOENT, "Directory not found: ${path.value}")
            }
            
            val contents: List<GitHubContent> = json.decodeFromString(response.bodyAsText())
            
            contents.map { item ->
                when (item.type) {
                    "dir" -> FsEntry.Directory(item.name)
                    "file" -> FsEntry.File(item.name, size = item.size?.toLong())
                    else -> FsEntry.File(item.name, size = item.size?.toLong())
                }
            }
        } catch (e: FsException) {
            throw e
        } catch (e: Exception) {
            throw FsException(FsErrorCode.EIO, "Failed to list ${path.value}: ${e.message}")
        }
    }
    
    override suspend fun read(path: FsPath, options: ReadOptions): ReadResult {
        val parsed = parsePath(path.value)
        
        return try {
            val response: HttpResponse = client.get("$baseUrl/repos/${parsed.owner}/${parsed.repo}/contents/${parsed.path}") {
                parameter("ref", parsed.ref)
                token?.let { header("Authorization", "Bearer $it") }
                header("Accept", "application/vnd.github.raw+json")
            }
            
            if (!response.status.isSuccess()) {
                throw FsException(FsErrorCode.ENOENT, "File not found: ${path.value}")
            }
            
            val bytes = response.body<ByteArray>()
            ReadResult(bytes = bytes)
        } catch (e: FsException) {
            throw e
        } catch (e: Exception) {
            throw FsException(FsErrorCode.EIO, "Failed to read ${path.value}: ${e.message}")
        }
    }
    
    override suspend fun write(path: FsPath, content: ByteArray, options: WriteOptions): WriteResult {
        throw FsException(FsErrorCode.EACCES, "GitHub backend is read-only")
    }
    
    override suspend fun delete(path: FsPath) {
        throw FsException(FsErrorCode.EACCES, "GitHub backend is read-only")
    }
    
    override suspend fun mkdir(path: FsPath) {
        throw FsException(FsErrorCode.EACCES, "GitHub backend is read-only")
    }
    
    private fun parsePath(path: String): ParsedGitHubPath {
        // Format: /owner/repo/ref/path/to/file
        val parts = path.trim('/').split('/', limit = 4)
        
        return when {
            parts.size < 2 -> throw FsException(
                FsErrorCode.EINVAL, 
                "Invalid path format. Expected: /owner/repo/ref/path"
            )
            parts.size == 2 -> ParsedGitHubPath(
                owner = parts[0],
                repo = parts[1],
                ref = "main",
                path = ""
            )
            parts.size == 3 -> ParsedGitHubPath(
                owner = parts[0],
                repo = parts[1],
                ref = parts[2],
                path = ""
            )
            else -> ParsedGitHubPath(
                owner = parts[0],
                repo = parts[1],
                ref = parts[2],
                path = parts[3]
            )
        }
    }
    
    private data class ParsedGitHubPath(
        val owner: String,
        val repo: String,
        val ref: String,
        val path: String
    )
}

@Serializable
private data class GitHubContent(
    val name: String,
    val path: String,
    val type: String,  // "file" or "dir"
    val size: Int? = null,
    val sha: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null
)
