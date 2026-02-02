package cc.unitmesh.agent.external

import cc.unitmesh.agent.AgentEditOperation
import cc.unitmesh.agent.tool.shell.ShellExecutionConfig
import cc.unitmesh.agent.tool.shell.ShellExecutor

/**
 * Best-effort workspace snapshot based on `git status --porcelain`.
 *
 * We intentionally keep parsing conservative to avoid false positives. This is used to
 * populate our `AgentEdit` list when an external CLI agent edits the workspace directly.
 */
internal data class GitWorkspaceSnapshot(
    val isGitRepo: Boolean,
    val entries: Map<String, GitStatusEntry>
) {
    companion object {
        suspend fun capture(shellExecutor: ShellExecutor, projectPath: String): GitWorkspaceSnapshot {
            val isGitRepo = try {
                val res = shellExecutor.execute(
                    "git rev-parse --is-inside-work-tree",
                    ShellExecutionConfig(workingDirectory = projectPath, timeoutMs = 5000L)
                )
                res.isSuccess() && res.stdout.trim() == "true"
            } catch (_: Throwable) {
                false
            }

            if (!isGitRepo) {
                return GitWorkspaceSnapshot(isGitRepo = false, entries = emptyMap())
            }

            val status = try {
                val res = shellExecutor.execute(
                    "git status --porcelain",
                    ShellExecutionConfig(workingDirectory = projectPath, timeoutMs = 15000L)
                )
                res.stdout
            } catch (_: Throwable) {
                ""
            }

            val parsed = parsePorcelain(status)
            return GitWorkspaceSnapshot(isGitRepo = true, entries = parsed)
        }

        fun diff(before: GitWorkspaceSnapshot, after: GitWorkspaceSnapshot): List<WorkspaceChange> {
            if (!before.isGitRepo || !after.isGitRepo) return emptyList()

            val changes = mutableListOf<WorkspaceChange>()
            val allPaths = (before.entries.keys + after.entries.keys).toSet()

            for (path in allPaths) {
                val b = before.entries[path]
                val a = after.entries[path]

                if (b == null && a != null) {
                    // Newly appeared in status output: usually untracked or staged new file
                    changes += WorkspaceChange(path = path, operation = AgentEditOperation.CREATE)
                    continue
                }
                if (b != null && a == null) {
                    // Disappeared from status output: could be committed or reverted. We ignore.
                    continue
                }
                if (b != null && a != null) {
                    val op = when {
                        a.isDeleted -> AgentEditOperation.DELETE
                        a.isUntracked && !b.isUntracked -> AgentEditOperation.CREATE
                        a.isModified && !a.isDeleted -> AgentEditOperation.UPDATE
                        else -> null
                    }
                    if (op != null) {
                        changes += WorkspaceChange(path = path, operation = op)
                    }
                }
            }

            return changes.distinctBy { it.path to it.operation }
        }

        private fun parsePorcelain(stdout: String): Map<String, GitStatusEntry> {
            // Format: XY <path> (with optional rename "old -> new")
            // We only care about the final path that currently exists in working tree.
            val result = mutableMapOf<String, GitStatusEntry>()
            stdout.lineSequence()
                .map { it.trimEnd() }
                .filter { it.isNotBlank() }
                .forEach { line ->
                    if (line.length < 4) return@forEach
                    val x = line[0]
                    val y = line[1]
                    val rawPath = line.substring(3)
                    val path = if (rawPath.contains(" -> ")) rawPath.substringAfter(" -> ") else rawPath

                    // Untracked: "??"
                    val isUntracked = x == '?' && y == '?'
                    val isDeleted = x == 'D' || y == 'D'
                    val isModified = x == 'M' || y == 'M' || x == 'A' || y == 'A' || x == 'R' || y == 'R'

                    result[path] = GitStatusEntry(
                        x = x,
                        y = y,
                        path = path,
                        isUntracked = isUntracked,
                        isDeleted = isDeleted,
                        isModified = isModified
                    )
                }

            return result
        }
    }
}

internal data class GitStatusEntry(
    val x: Char,
    val y: Char,
    val path: String,
    val isUntracked: Boolean,
    val isDeleted: Boolean,
    val isModified: Boolean
)

internal data class WorkspaceChange(
    val path: String,
    val operation: AgentEditOperation
)

