package cc.unitmesh.agent.external

import cc.unitmesh.agent.AgentEdit
import cc.unitmesh.agent.AgentEditOperation
import cc.unitmesh.agent.AgentResult
import cc.unitmesh.agent.AgentStep
import cc.unitmesh.agent.AgentTask
import cc.unitmesh.agent.CodingAgentContext
import cc.unitmesh.agent.CodingAgentPromptRenderer
import cc.unitmesh.agent.CodingAgentService
import cc.unitmesh.agent.render.CodingAgentRenderer
import cc.unitmesh.agent.render.DefaultCodingAgentRenderer
import cc.unitmesh.agent.tool.filesystem.DefaultToolFileSystem
import cc.unitmesh.agent.tool.filesystem.ToolFileSystem
import cc.unitmesh.agent.tool.shell.DefaultShellExecutor
import cc.unitmesh.agent.tool.shell.ShellExecutionConfig
import cc.unitmesh.agent.tool.shell.ShellExecutor
import cc.unitmesh.agent.tool.shell.ShellUtils
import kotlinx.datetime.Clock

/**
 * Run external "coding agents" via their own CLIs (e.g., Claude Code, Codex CLI).
 *
 * Design goals:
 * - Reuse existing renderer contract for output UX.
 * - Be cross-platform where shell execution exists (JVM, JS/Node, Android).
 * - Track workspace edits via git porcelain output (best-effort).
 *
 * Note: This agent does NOT participate in our tool-calling loop. It delegates the entire workflow
 * to an external CLI agent which may edit the workspace directly.
 */
class ExternalCliCodingAgent(
    private val projectPath: String,
    private val kind: ExternalCliKind,
    private val renderer: CodingAgentRenderer = DefaultCodingAgentRenderer(),
    private val fileSystem: ToolFileSystem = DefaultToolFileSystem(projectPath = projectPath),
    private val shellExecutor: ShellExecutor = DefaultShellExecutor(),
    private val mode: ExternalCliMode = ExternalCliMode.NON_INTERACTIVE,
    private val timeoutMs: Long = 30 * 60 * 1000L,
    private val extraArgs: List<String> = emptyList(),
) : CodingAgentService {
    private val promptRenderer = CodingAgentPromptRenderer()

    override suspend fun executeTask(task: AgentTask): AgentResult {
        val startTime = Clock.System.now().toEpochMilliseconds()
        val steps = mutableListOf<AgentStep>()

        if (!shellExecutor.isAvailable()) {
            return AgentResult(
                success = false,
                message = "Shell execution is not supported on this platform.",
                steps = steps,
                edits = emptyList()
            )
        }

        // Best-effort baseline snapshot (git), so we can populate edits after the run.
        val beforeSnapshot = GitWorkspaceSnapshot.capture(shellExecutor, projectPath)

        val command = buildCommand(task.requirement)
        renderer.renderIterationHeader(1, 1)
        renderer.renderInfo("Running external agent: ${kind.id} (${mode.id})")
        renderer.renderInfo("Command: ${kind.binary} ...")

        val output = StringBuilder()
        val (success, runMessage) = try {
            renderer.renderLLMResponseStart()
            val result = shellExecutor.execute(
                command = command,
                config = ShellExecutionConfig(
                    workingDirectory = projectPath,
                    timeoutMs = timeoutMs,
                    inheritIO = (mode == ExternalCliMode.INTERACTIVE)
                )
            )

            val combined = result.getCombinedOutput()
            if (combined.isNotBlank()) {
                output.append(combined)
                renderer.renderLLMResponseChunk(combined + "\n")
            }
            renderer.renderLLMResponseEnd()

            val ok = result.isSuccess()
            ok to (if (ok) "External agent finished successfully." else "External agent exited with code ${result.exitCode}.")
        } catch (e: Throwable) {
            renderer.renderLLMResponseEnd()
            false to ("External agent failed: ${e.message ?: e::class.simpleName}")
        }

        steps += AgentStep(
            step = 1,
            action = "run_external_cli_agent",
            tool = "shell",
            params = mapOf(
                "kind" to kind.id,
                "mode" to mode.id,
                "timeoutMs" to timeoutMs.toString()
            ),
            result = runMessage,
            success = success
        )

        val afterSnapshot = GitWorkspaceSnapshot.capture(shellExecutor, projectPath)
        val edits = GitWorkspaceSnapshot.diff(beforeSnapshot, afterSnapshot).mapNotNull { change ->
            when (change.operation) {
                AgentEditOperation.DELETE -> AgentEdit(file = change.path, operation = AgentEditOperation.DELETE, content = null)
                AgentEditOperation.CREATE,
                AgentEditOperation.UPDATE -> {
                    val content = try {
                        fileSystem.readFile(change.path)
                    } catch (_: Throwable) {
                        null
                    }
                    AgentEdit(file = change.path, operation = change.operation, content = content)
                }
            }
        }

        val totalMs = Clock.System.now().toEpochMilliseconds() - startTime
        renderer.renderTaskComplete(executionTimeMs = totalMs, toolsUsedCount = 0)

        return AgentResult(
            success = success,
            message = buildString {
                append(runMessage)
                if (edits.isNotEmpty()) {
                    append("\nWorkspace changes: ${edits.size} file(s).")
                }
            },
            steps = steps,
            edits = edits
        )
    }

    override fun buildSystemPrompt(context: CodingAgentContext, language: String): String {
        // We reuse the prompt renderer so the UI can display "system prompt" consistently,
        // but external CLI agents have their own prompts; this is informational only.
        return promptRenderer.render(context, language)
    }

    override suspend fun initializeWorkspace(projectPath: String) {
        // No-op. External CLI agents manage their own initialization.
        // We still rely on ShellExecutor availability checks in executeTask.
    }

    private fun buildCommand(requirement: String): String {
        val promptArg = ShellUtils.escapeShellArg(requirement)
        val args = extraArgs.joinToString(" ") { ShellUtils.escapeShellArg(it) }
        return when (kind) {
            ExternalCliKind.CLAUDE -> when (mode) {
                ExternalCliMode.NON_INTERACTIVE -> buildString {
                    append(kind.binary)
                    append(" -p --output-format text ")
                    if (args.isNotBlank()) append(args).append(' ')
                    append(promptArg)
                }
                ExternalCliMode.INTERACTIVE -> buildString {
                    append(kind.binary).append(' ')
                    if (args.isNotBlank()) append(args).append(' ')
                    append(promptArg)
                }
            }
            ExternalCliKind.CODEX -> when (mode) {
                ExternalCliMode.NON_INTERACTIVE -> buildString {
                    append(kind.binary)
                    append(" exec --color auto -a never -s workspace-write -C ")
                    append(ShellUtils.escapeShellArg(projectPath))
                    append(' ')
                    if (args.isNotBlank()) append(args).append(' ')
                    append(promptArg)
                }
                ExternalCliMode.INTERACTIVE -> buildString {
                    append(kind.binary)
                    append(" -C ")
                    append(ShellUtils.escapeShellArg(projectPath))
                    append(" --no-alt-screen ")
                    if (args.isNotBlank()) append(args).append(' ')
                    append(promptArg)
                }
            }
        }
    }
}

enum class ExternalCliKind(val id: String, val binary: String) {
    CLAUDE("claude", "claude"),
    CODEX("codex", "codex");

    companion object {
        fun fromId(id: String): ExternalCliKind? {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
        }
    }
}

enum class ExternalCliMode(val id: String) {
    NON_INTERACTIVE("non-interactive"),
    INTERACTIVE("interactive");

    companion object {
        fun fromId(id: String): ExternalCliMode? {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
        }
    }
}

