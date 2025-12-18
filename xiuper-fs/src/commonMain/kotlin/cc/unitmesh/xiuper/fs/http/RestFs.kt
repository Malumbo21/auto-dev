package cc.unitmesh.xiuper.fs.http

import cc.unitmesh.xiuper.fs.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * REST-FS backend (schema-first).
 *
 * This is intentionally minimal: it provides a stable SPI and a basic implementation for
 * reading/writing raw endpoints, while leaving higher-level resource/field projections to schema.
 */
class RestFsBackend(
    private val service: RestServiceConfig,
    private val schema: RestSchema = RestSchema(),
    private val client: HttpClient = HttpClientFactory.create(service)
) : FsBackend, CapabilityAwareBackend {

    override val capabilities: BackendCapabilities = BackendCapabilities(
        supportsMkdir = false,
        supportsDelete = false
    )

    private val runtime = RestRuntimeState()

    override suspend fun stat(path: FsPath): FsStat {
        return schema.stat(path, runtime)
    }

    override suspend fun list(path: FsPath): List<FsEntry> {
        return schema.list(path, runtime)
    }

    override suspend fun read(path: FsPath, options: ReadOptions): ReadResult {
        val resolved = schema.resolveRead(path, runtime)
            ?: throw FsException(FsErrorCode.ENOTSUP, "No schema mapping for read: ${path.value}")

        val response: HttpResponse = client.request(resolved.http.url) {
            method = resolved.http.method
            service.auth.apply(this)
            resolved.http.headers.forEach { (k, v) -> header(k, v) }
        }

        if (!response.status.isSuccess()) {
            throw FsException(FsErrorCode.EIO, "HTTP ${response.status.value}: ${response.status.description}")
        }

        val bytes = response.body<ByteArray>()
        val base = ReadResult(bytes = bytes, contentType = response.contentType()?.toString())
        return resolved.transform?.invoke(base) ?: base
    }

    override suspend fun write(path: FsPath, content: ByteArray, options: WriteOptions): WriteResult {
        val resolved = schema.resolveWrite(path, content, options, runtime)
            ?: throw FsException(FsErrorCode.ENOTSUP, "No schema mapping for write: ${path.value}")

        when (resolved) {
            is ResolvedWrite.Local -> return resolved.action(runtime)
            is ResolvedWrite.Http -> {
                val response: HttpResponse = client.request(resolved.http.url) {
                    this.method = resolved.http.method
                    service.auth.apply(this)
                    resolved.http.headers.forEach { (k, v) -> header(k, v) }
                    resolved.http.contentType?.let { contentType(ContentType.parse(it)) }
                    setBody(resolved.http.body ?: content)
                }

                if (!response.status.isSuccess()) {
                    return WriteResult(ok = false, message = "HTTP ${response.status.value}: ${response.status.description}")
                }

                return WriteResult(ok = true, message = "HTTP ${response.status.value}")
            }
        }
    }

    override suspend fun delete(path: FsPath) {
        throw FsException(FsErrorCode.ENOTSUP, "delete is not supported by RestFsBackend yet")
    }

    override suspend fun mkdir(path: FsPath) {
        throw FsException(FsErrorCode.ENOTSUP, "mkdir is not supported by RestFsBackend")
    }
}

data class RestServiceConfig(
    val baseUrl: String,
    val auth: AuthProvider = AuthProvider.None,
    val defaultHeaders: Map<String, String> = emptyMap()
)

sealed interface AuthProvider {
    fun apply(builder: HttpRequestBuilder)

    data object None : AuthProvider {
        override fun apply(builder: HttpRequestBuilder) = Unit
    }

    data class BearerToken(private val tokenProvider: () -> String?) : AuthProvider {
        override fun apply(builder: HttpRequestBuilder) {
            val token = tokenProvider() ?: return
            builder.header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    data class Header(private val name: String, private val valueProvider: () -> String?) : AuthProvider {
        override fun apply(builder: HttpRequestBuilder) {
            val v = valueProvider() ?: return
            builder.header(name, v)
        }
    }
}

/**
 * Schema for mapping paths to HTTP requests.
 *
 * The initial version keeps the interface small:
 * - Root entries for discovery.
 * - Path-based resolution for read/write.
 */
data class HttpCall(
    val url: String,
    val method: HttpMethod,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val contentType: String? = null
)

data class ResolvedRead(
    val http: HttpCall,
    val transform: ((ReadResult) -> ReadResult)? = null
)

sealed class ResolvedWrite {
    data class Http(val http: HttpCall) : ResolvedWrite()
    data class Local(val action: (RestSchemaRuntime) -> WriteResult) : ResolvedWrite()
}

/**
 * Runtime state for control/magic files (e.g., query state).
 */
interface RestSchemaRuntime {
    fun setQuery(scopePath: String, query: Map<String, String>)
    fun getQuery(scopePath: String): Map<String, String>?
}

internal class RestRuntimeState : RestSchemaRuntime {
    private val queryByScope: MutableMap<String, Map<String, String>> = mutableMapOf()
    override fun setQuery(scopePath: String, query: Map<String, String>) {
        queryByScope[scopePath] = query
    }

    override fun getQuery(scopePath: String): Map<String, String>? = queryByScope[scopePath]
}

class RestSchema private constructor(
    private val statRoutes: List<Pair<FsPathPattern, (RouteParams, RestSchemaRuntime) -> FsStat>>,
    private val listRoutes: List<Pair<FsPathPattern, (RouteParams, RestSchemaRuntime) -> List<FsEntry>>>,
    private val readRoutes: List<Pair<FsPathPattern, (RouteParams, RestSchemaRuntime) -> ResolvedRead>>,
    private val writeRoutes: List<Pair<FsPathPattern, (RouteParams, ByteArray, WriteOptions, RestSchemaRuntime) -> ResolvedWrite>>
) {
    data class RouteParams(val path: FsPath, val params: Map<String, String>) {
        operator fun get(key: String): String = params[key] ?: ""
    }

    constructor() : this(
        statRoutes = emptyList(),
        listRoutes = emptyList(),
        readRoutes = listOf(
            FsPathPattern.parse("/raw/{path...}") to { p, _ ->
                val rest = (p.params["path"] ?: "").trim('/')
                ResolvedRead(HttpCall(url = rest, method = HttpMethod.Get))
            }
        ),
        writeRoutes = listOf(
            FsPathPattern.parse("/raw/{path...}") to { p, _, options, _ ->
                val rest = (p.params["path"] ?: "").trim('/')
                ResolvedWrite.Http(HttpCall(url = rest, method = HttpMethod.Put, contentType = options.contentType))
            }
        )
    )

    fun stat(path: FsPath, runtime: RestSchemaRuntime): FsStat {
        for ((pattern, handler) in statRoutes) {
            val m = pattern.match(path) ?: continue
            return handler(RouteParams(path, m), runtime)
        }
        return if (path.value == "/") FsStat(path, isDirectory = true) else FsStat(path, isDirectory = false)
    }

    fun list(path: FsPath, runtime: RestSchemaRuntime): List<FsEntry> {
        for ((pattern, handler) in listRoutes) {
            val m = pattern.match(path) ?: continue
            return handler(RouteParams(path, m), runtime)
        }
        return emptyList()
    }

    fun resolveRead(path: FsPath, runtime: RestSchemaRuntime): ResolvedRead? {
        for ((pattern, handler) in readRoutes) {
            val m = pattern.match(path) ?: continue
            return handler(RouteParams(path, m), runtime)
        }
        return null
    }

    fun resolveWrite(path: FsPath, content: ByteArray, options: WriteOptions, runtime: RestSchemaRuntime): ResolvedWrite? {
        for ((pattern, handler) in writeRoutes) {
            val m = pattern.match(path) ?: continue
            return handler(RouteParams(path, m), content, options, runtime)
        }
        return null
    }

    class Builder {
        private val statRoutes = mutableListOf<Pair<FsPathPattern, (RouteParams, RestSchemaRuntime) -> FsStat>>()
        private val listRoutes = mutableListOf<Pair<FsPathPattern, (RouteParams, RestSchemaRuntime) -> List<FsEntry>>>()
        private val readRoutes = mutableListOf<Pair<FsPathPattern, (RouteParams, RestSchemaRuntime) -> ResolvedRead>>()
        private val writeRoutes = mutableListOf<Pair<FsPathPattern, (RouteParams, ByteArray, WriteOptions, RestSchemaRuntime) -> ResolvedWrite>>()

        fun stat(pattern: String, handler: (RouteParams, RestSchemaRuntime) -> FsStat): Builder {
            statRoutes.add(FsPathPattern.parse(pattern) to handler)
            return this
        }

        fun dir(pattern: String, handler: (RouteParams, RestSchemaRuntime) -> List<FsEntry>): Builder {
            listRoutes.add(FsPathPattern.parse(pattern) to handler)
            return this
        }

        fun read(pattern: String, handler: (RouteParams, RestSchemaRuntime) -> ResolvedRead): Builder {
            readRoutes.add(FsPathPattern.parse(pattern) to handler)
            return this
        }

        fun write(pattern: String, handler: (RouteParams, ByteArray, WriteOptions, RestSchemaRuntime) -> ResolvedWrite): Builder {
            writeRoutes.add(FsPathPattern.parse(pattern) to handler)
            return this
        }

        fun build(): RestSchema {
            val base = RestSchema()
            return RestSchema(
                statRoutes = statRoutes.toList(),
                listRoutes = listRoutes.toList(),
                readRoutes = readRoutes.toList() + base.readRoutes,
                writeRoutes = writeRoutes.toList() + base.writeRoutes
            )
        }
    }

    companion object {
        fun builder(): Builder = Builder()

        fun githubIssues(): RestSchema {
            val issuesScope = "/github/{owner}/{repo}/issues"
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            return builder()
                .stat(issuesScope) { p, _ -> FsStat(p.path, isDirectory = true) }
                .stat("$issuesScope/new") { p, _ -> FsStat(p.path, isDirectory = false) }
                .stat("$issuesScope/query") { p, _ -> FsStat(p.path, isDirectory = false) }
                .stat("$issuesScope/results") { p, _ -> FsStat(p.path, isDirectory = true) }
                .stat("$issuesScope/page_{page}") { p, _ -> FsStat(p.path, isDirectory = true) }
                .stat("$issuesScope/page_{page}/data.json") { p, _ -> FsStat(p.path, isDirectory = false, mime = "application/json") }
                .stat("$issuesScope/results/page_{page}") { p, _ -> FsStat(p.path, isDirectory = true) }
                .stat("$issuesScope/results/page_{page}/data.json") { p, _ -> FsStat(p.path, isDirectory = false, mime = "application/json") }
                .stat("$issuesScope/{id}") { p, _ -> FsStat(p.path, isDirectory = true) }
                .stat("$issuesScope/{id}/data.json") { p, _ -> FsStat(p.path, isDirectory = false, mime = "application/json") }
                .stat("$issuesScope/{id}/fields") { p, _ -> FsStat(p.path, isDirectory = true) }
                .stat("$issuesScope/{id}/fields/{field}") { p, _ -> FsStat(p.path, isDirectory = false) }

                .dir(issuesScope) { _, _ ->
                    listOf(
                        FsEntry.Directory("page_1"),
                        FsEntry.Special("new", FsEntry.SpecialKind.MagicNew),
                        FsEntry.Special("query", FsEntry.SpecialKind.ControlQuery),
                        FsEntry.Directory("results")
                    )
                }
                .dir("$issuesScope/page_{page}") { _, _ -> listOf(FsEntry.File("data.json", mime = "application/json")) }
                .dir("$issuesScope/results") { _, _ -> listOf(FsEntry.Directory("page_1")) }
                .dir("$issuesScope/results/page_{page}") { _, _ -> listOf(FsEntry.File("data.json", mime = "application/json")) }
                .dir("$issuesScope/{id}") { _, _ -> listOf(FsEntry.File("data.json", mime = "application/json"), FsEntry.Directory("fields")) }
                .dir("$issuesScope/{id}/fields") { _, _ -> listOf(FsEntry.File("title"), FsEntry.File("body")) }

                // Page routes must come before {id} routes to avoid treating page_3 as an issue id.
                .read("$issuesScope/page_{page}/data.json") { p, _ ->
                    val owner = p["owner"]
                    val repo = p["repo"]
                    val page = p["page"]
                    ResolvedRead(HttpCall(url = "repos/$owner/$repo/issues?page=$page", method = HttpMethod.Get))
                }
                .read("$issuesScope/results/page_{page}/data.json") { p, runtime ->
                    val owner = p["owner"]
                    val repo = p["repo"]
                    val page = p["page"]
                    val scope = "/github/$owner/$repo/issues"
                    val q = runtime.getQuery(scope).orEmpty()
                    val state = q["state"]
                    val queryString = buildString {
                        append("page=$page")
                        if (!state.isNullOrBlank()) append("&state=$state")
                    }
                    ResolvedRead(HttpCall(url = "repos/$owner/$repo/issues?$queryString", method = HttpMethod.Get))
                }

                .read("$issuesScope/{id}/data.json") { p, _ ->
                    val owner = p["owner"]
                    val repo = p["repo"]
                    val id = p["id"]
                    ResolvedRead(HttpCall(url = "repos/$owner/$repo/issues/$id", method = HttpMethod.Get))
                }
                .read("$issuesScope/{id}/fields/{field}") { p, _ ->
                    val owner = p["owner"]
                    val repo = p["repo"]
                    val id = p["id"]
                    val field = p["field"]
                    ResolvedRead(
                        http = HttpCall(url = "repos/$owner/$repo/issues/$id", method = HttpMethod.Get),
                        transform = { rr ->
                            val text = rr.textOrNull() ?: return@ResolvedRead rr
                            val obj = json.parseToJsonElement(text).jsonObject
                            val v = obj[field]?.jsonPrimitive?.let { prim -> runCatching { prim.content }.getOrNull() } ?: ""
                            ReadResult(bytes = v.encodeToByteArray(), contentType = "text/plain")
                        }
                    )
                }

                .write("$issuesScope/new") { p, content, options, _ ->
                    val owner = p["owner"]
                    val repo = p["repo"]
                    ResolvedWrite.Http(
                        HttpCall(
                            url = "repos/$owner/$repo/issues",
                            method = HttpMethod.Post,
                            body = content,
                            contentType = options.contentType ?: "application/json"
                        )
                    )
                }
                .write("$issuesScope/query") { p, content, _, _ ->
                    val owner = p["owner"]
                    val repo = p["repo"]
                    val scope = "/github/$owner/$repo/issues"
                    ResolvedWrite.Local { rt ->
                        val queryText = runCatching { content.decodeToString() }.getOrDefault("")
                        rt.setQuery(scope, parseControlFile(queryText))
                        WriteResult(ok = true)
                    }
                }
                .write("$issuesScope/{id}/fields/{field}") { p, content, options, _ ->
                    val owner = p["owner"]
                    val repo = p["repo"]
                    val id = p["id"]
                    val field = p["field"]
                    val text = runCatching { content.decodeToString() }.getOrDefault("")
                    val payload = JsonObject(mapOf(field to JsonPrimitive(text)))
                    ResolvedWrite.Http(
                        HttpCall(
                            url = "repos/$owner/$repo/issues/$id",
                            method = HttpMethod.Patch,
                            body = Json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray(),
                            contentType = options.contentType ?: "application/json"
                        )
                    )
                }
                .build()
        }
    }
}

internal class FsPathPattern private constructor(
    private val raw: String,
    private val segments: List<Segment>
) {
    sealed interface Segment {
        data class Literal(val value: String) : Segment
        data class Var(val name: String) : Segment
        data class VarWithAffix(val name: String, val prefix: String, val suffix: String) : Segment
        data class Rest(val name: String) : Segment
    }

    fun match(path: FsPath): Map<String, String>? {
        val pSegs = path.segments()
        val out = mutableMapOf<String, String>()

        var i = 0
        var j = 0
        while (i < segments.size && j < pSegs.size) {
            when (val s = segments[i]) {
                is Segment.Literal -> {
                    if (pSegs[j] != s.value) return null
                    i++; j++
                }
                is Segment.Var -> {
                    out[s.name] = pSegs[j]
                    i++; j++
                }
                is Segment.VarWithAffix -> {
                    val actual = pSegs[j]
                    if (!actual.startsWith(s.prefix) || !actual.endsWith(s.suffix)) return null
                    val mid = actual.removePrefix(s.prefix).removeSuffix(s.suffix)
                    if (mid.isEmpty()) return null
                    out[s.name] = mid
                    i++; j++
                }
                is Segment.Rest -> {
                    out[s.name] = pSegs.drop(j).joinToString("/")
                    i = segments.size
                    j = pSegs.size
                }
            }
        }

        if (i < segments.size) {
            // Remaining pattern segments can only be Rest
            val last = segments.drop(i).singleOrNull() as? Segment.Rest ?: return null
            out[last.name] = ""
            i = segments.size
        }

        if (j != pSegs.size) return null
        return out
    }

    companion object {
        fun parse(pattern: String): FsPathPattern {
            val normalized = FsPath.normalize(pattern)
            val segs = normalized.trim('/').takeIf { it.isNotEmpty() }?.split('/') ?: emptyList()

            val parsed = segs.map { seg ->
                when {
                    seg.startsWith("{") && seg.endsWith("...}") -> {
                        val name = seg.removePrefix("{").removeSuffix("...}")
                        Segment.Rest(name)
                    }
                    seg.startsWith("{") && seg.endsWith("}") -> Segment.Var(seg.removePrefix("{").removeSuffix("}"))
                    seg.contains("{") && seg.contains("}") -> {
                        val start = seg.indexOf('{')
                        val end = seg.indexOf('}', start + 1)
                        if (start == -1 || end == -1) Segment.Literal(seg)
                        else {
                            val prefix = seg.substring(0, start)
                            val name = seg.substring(start + 1, end)
                            val suffix = seg.substring(end + 1)
                            Segment.VarWithAffix(name, prefix, suffix)
                        }
                    }
                    else -> Segment.Literal(seg)
                }
            }

            return FsPathPattern(normalized, parsed)
        }
    }
}

private fun parseControlFile(text: String): Map<String, String> {
    return text
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val k = line.substring(0, idx).trim()
            val v = line.substring(idx + 1).trim()
            if (k.isBlank()) return@mapNotNull null
            k to v
        }
        .toMap()
}

expect object HttpClientFactory {
    fun create(service: RestServiceConfig): HttpClient
}
