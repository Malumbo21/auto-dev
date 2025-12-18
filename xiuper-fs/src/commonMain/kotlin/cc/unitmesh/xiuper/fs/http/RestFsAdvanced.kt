package cc.unitmesh.xiuper.fs.http

import cc.unitmesh.xiuper.fs.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Advanced REST-FS features builder extensions.
 * 
 * This file provides convenient extensions to RestSchema.Builder for:
 * - Field projection: Access JSON fields as individual files
 * - Magic files: Special files that trigger HTTP operations
 * - Pagination: Virtual directories for paginated results
 */

/**
 * Add field projection for a JSON resource.
 * 
 * Creates virtual /fields/ directory that exposes JSON fields as files:
 * ```
 * /api/users/123/data.json    -> {"name": "Alice", "email": "alice@example.com"}
 * /api/users/123/fields/      -> [name, email]
 * /api/users/123/fields/name  -> "Alice"
 * /api/users/123/fields/email -> "alice@example.com"
 * ```
 * 
 * @param dataPath Path pattern for the JSON resource (e.g., "/api/users/{id}/data.json")
 * @param fieldsBasePath Path pattern for the fields directory (e.g., "/api/users/{id}/fields")
 */
fun RestSchema.Builder.addFieldProjection(
    dataPath: String,
    fieldsBasePath: String
): RestSchema.Builder {
    // Stat: fields directory exists
    stat(fieldsBasePath) { p, _ ->
        FsStat(p.path, isDirectory = true)
    }
    
    // Stat: individual field files
    stat("$fieldsBasePath/{field}") { p, _ ->
        FsStat(p.path, isDirectory = false, mime = "text/plain")
    }
    
    // Dir: return all JSON keys as files
    dir(fieldsBasePath) { _, _ ->
        // This is a limitation: we can't read the data synchronously in the dir handler.
        // In a real implementation, you might need to cache the JSON data or use a different approach.
        // For now, return empty list as a placeholder.
        emptyList()
    }
    
    // Read: extract field value from JSON
    read("$fieldsBasePath/{field}") { p, _ ->
        // Extract field value by reading data.json and parsing
        val json = """{"placeholder": "Field projection requires async data loading"}"""
        ResolvedRead(
            http = HttpCall(
                url = dataPath.replace("{id}", p["id"]),
                method = HttpMethod.Get
            ),
            transform = { result ->
                try {
                    val jsonElement = Json.parseToJsonElement(result.bytes.decodeToString())
                    if (jsonElement is JsonObject) {
                        val fieldName = p["field"]
                        val value = jsonElement[fieldName]
                        val content = when (value) {
                            is JsonPrimitive -> value.content
                            null -> throw FsException(FsErrorCode.ENOENT, "Field not found: $fieldName")
                            else -> value.toString()
                        }
                        ReadResult(content.encodeToByteArray(), "text/plain")
                    } else {
                        throw FsException(FsErrorCode.EIO, "Not a JSON object")
                    }
                } catch (e: Exception) {
                    throw FsException(FsErrorCode.EIO, "Failed to parse JSON: ${e.message}")
                }
            }
        )
    }
    
    return this
}

/**
 * Add a magic file that triggers HTTP POST on write.
 * 
 * Writing to this file creates a new resource:
 * ```
 * echo '{"title": "New Issue"}' > /github/owner/repo/issues/new
 * # -> POST /repos/owner/repo/issues
 * ```
 * 
 * @param magicPath Path pattern for the magic file (e.g., "/api/issues/new")
 * @param targetUrl URL to POST to when the file is written
 */
fun RestSchema.Builder.addMagicFile(
    magicPath: String,
    targetUrl: String
): RestSchema.Builder {
    // Stat: magic file exists as a special file
    stat(magicPath) { p, _ ->
        FsStat(p.path, isDirectory = false, size = 0, mime = "application/json")
    }
    
    // Read: return empty or placeholder
    read(magicPath) { _, _ ->
        ResolvedRead(
            http = HttpCall(url = "", method = HttpMethod.Get),
            transform = { ReadResult("{}".encodeToByteArray(), "application/json") }
        )
    }
    
    // Write: POST to target URL
    write(magicPath) { p, content, options, _ ->
        ResolvedWrite.Http(
            HttpCall(
                url = targetUrl.replace(Regex("\\{(\\w+)\\}")) { match ->
                    p[match.groupValues[1]]
                },
                method = HttpMethod.Post,
                body = content,
                contentType = options.contentType ?: "application/json"
            )
        )
    }
    
    return this
}

/**
 * Add pagination support with virtual page directories.
 * 
 * Creates virtual /pages/{n}/ directories:
 * ```
 * /api/issues/pages/1/       -> [data.json, next]
 * /api/issues/pages/1/data.json -> {..., "items": [...]}
 * /api/issues/pages/2/       -> [data.json, prev, next]
 * ```
 * 
 * @param basePath Path pattern for the paginated collection (e.g., "/api/issues")
 * @param pageUrlTemplate URL template with {page} placeholder (e.g., "/issues?page={page}")
 * @param maxPages Optional maximum number of pages to expose
 */
fun RestSchema.Builder.addPagination(
    basePath: String,
    pageUrlTemplate: String,
    maxPages: Int? = null
): RestSchema.Builder {
    // Stat: pages directory
    stat("$basePath/pages") { p, _ ->
        FsStat(p.path, isDirectory = true)
    }
    
    // Stat: individual page directories
    stat("$basePath/pages/{page}") { p, _ ->
        FsStat(p.path, isDirectory = true)
    }
    
    // Stat: page data.json
    stat("$basePath/pages/{page}/data.json") { p, _ ->
        FsStat(p.path, isDirectory = false, mime = "application/json")
    }
    
    // Dir: pages directory shows numbered page directories
    dir("$basePath/pages") { _, _ ->
        val pages = maxPages ?: 10 // Default to showing 10 pages
        (1..pages).map { page ->
            FsEntry.Directory(page.toString())
        }
    }
    
    // Dir: page directory shows data.json and navigation links
    dir("$basePath/pages/{page}") { p, _ ->
        val pageNum = p["page"].toIntOrNull() ?: 1
        buildList {
            add(FsEntry.File("data.json", size = null, mime = "application/json"))
            if (pageNum > 1) {
                add(FsEntry.File("prev", size = null, mime = "text/plain"))
            }
            if (maxPages == null || pageNum < maxPages) {
                add(FsEntry.File("next", size = null, mime = "text/plain"))
            }
        }
    }
    
    // Read: page data
    read("$basePath/pages/{page}/data.json") { p, _ ->
        val url = pageUrlTemplate.replace("{page}", p["page"])
        ResolvedRead(
            http = HttpCall(url = url, method = HttpMethod.Get)
        )
    }
    
    // Read: navigation links
    read("$basePath/pages/{page}/prev") { p, _ ->
        val pageNum = p["page"].toIntOrNull() ?: 1
        val prevPage = maxOf(1, pageNum - 1)
        ResolvedRead(
            http = HttpCall(url = "", method = HttpMethod.Get),
            transform = { ReadResult("$prevPage".encodeToByteArray(), "text/plain") }
        )
    }
    
    read("$basePath/pages/{page}/next") { p, _ ->
        val pageNum = p["page"].toIntOrNull() ?: 1
        val nextPage = pageNum + 1
        ResolvedRead(
            http = HttpCall(url = "", method = HttpMethod.Get),
            transform = { ReadResult("$nextPage".encodeToByteArray(), "text/plain") }
        )
    }
    
    return this
}
