package cc.unitmesh.server.workflow

import cc.unitmesh.server.auth.AuthService
import cc.unitmesh.server.workflow.engine.WorkflowEngine
import cc.unitmesh.server.workflow.models.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.ServerSentEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * 发送信号请求
 */
@Serializable
data class SendSignalRequest(
    val signalName: String,
    val data: Map<String, String> = emptyMap()
)

/**
 * 工作流路由配置
 */
fun Route.workflowRoutes(
    workflowEngine: WorkflowEngine,
    authService: AuthService
) {
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    
    route("/api/workflows") {
        
        // 启动工作流
        post("/start") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
            val username = token?.let { authService.validateToken(it) }
            
            if (username == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                return@post
            }
            
            try {
                val request = call.receive<StartWorkflowRequest>()
                val response = workflowEngine.startWorkflow(request.copy(userId = username))
                call.respond(HttpStatusCode.Created, response)
            } catch (e: Exception) {
                logger.error(e) { "Failed to start workflow" }
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        
        // 获取工作流状态
        get("/{workflowId}") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
            val username = token?.let { authService.validateToken(it) }
            
            if (username == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                return@get
            }
            
            val workflowId = call.parameters["workflowId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Missing workflowId")
            )
            
            val metadata = workflowEngine.queryMetadata(workflowId)
            if (metadata == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Workflow not found"))
                return@get
            }
            
            // 检查权限
            if (metadata.ownerId != username) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                return@get
            }
            
            call.respond(metadata)
        }
        
        // 查询工作流状态
        get("/{workflowId}/query") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
            val username = token?.let { authService.validateToken(it) }
            
            if (username == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                return@get
            }
            
            val workflowId = call.parameters["workflowId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Missing workflowId")
            )
            
            val state = workflowEngine.queryState(workflowId)
            if (state == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Workflow state not found"))
                return@get
            }
            
            call.respond(state)
        }
        
        // 发送信号
        post("/{workflowId}/signal") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
            val username = token?.let { authService.validateToken(it) }
            
            if (username == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                return@post
            }
            
            val workflowId = call.parameters["workflowId"] ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Missing workflowId")
            )
            
            try {
                val request = call.receive<SendSignalRequest>()
                workflowEngine.sendSignal(
                    workflowId = workflowId,
                    signalName = request.signalName,
                    signalData = request.data.mapValues { it.value as Any }
                )
                call.respond(HttpStatusCode.OK, mapOf("success" to true))
            } catch (e: Exception) {
                logger.error(e) { "Failed to send signal to workflow $workflowId" }
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
            }
        }
        
        // 取消工作流
        post("/{workflowId}/cancel") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
            val username = token?.let { authService.validateToken(it) }
            
            if (username == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                return@post
            }
            
            val workflowId = call.parameters["workflowId"] ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Missing workflowId")
            )
            
            val metadata = workflowEngine.queryMetadata(workflowId)
            if (metadata == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Workflow not found"))
                return@post
            }
            
            // 检查权限
            if (metadata.ownerId != username) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                return@post
            }
            
            workflowEngine.cancelWorkflow(workflowId)
            call.respond(HttpStatusCode.OK, mapOf("success" to true, "message" to "Cancellation requested"))
        }
        
        // 订阅工作流事件（SSE）
        route("/{workflowId}/events", HttpMethod.Get) {
            sse {
                val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                val username = token?.let { authService.validateToken(it) }
                
                if (username == null) {
                    send(ServerSentEvent(
                        data = """{"error": "Unauthorized"}""",
                        event = "error"
                    ))
                    return@sse
                }
                
                val workflowId = call.parameters["workflowId"]
                if (workflowId == null) {
                    send(ServerSentEvent(
                        data = """{"error": "Missing workflowId"}""",
                        event = "error"
                    ))
                    return@sse
                }
                
                logger.info { "User $username subscribing to workflow $workflowId events" }
                
                try {
                    workflowEngine.subscribeToEvents(workflowId).collect { event ->
                        send(ServerSentEvent(
                            data = json.encodeToString(event),
                            event = event.eventType,
                            id = event.id
                        ))
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Error in SSE stream for workflow $workflowId" }
                    send(ServerSentEvent(
                        data = """{"error": "${e.message}"}""",
                        event = "error"
                    ))
                }
            }
        }
    }
}
