package cc.unitmesh.devins.ui.compose.config

import java.io.File

/**
 * JVM implementation: use `which` to detect installed CLI tools.
 */
actual fun detectInstalledPresets(): List<AcpAgentPreset> {
    return AcpAgentPresets.allPresets.mapNotNull { preset ->
        val path = findExecutable(preset.command)
        if (path != null) {
            preset.copy(command = path)
        } else {
            null
        }
    }
}

/**
 * Find absolute path of an executable using `which` (Unix/macOS) or `where` (Windows).
 * Returns null if not found.
 */
private fun findExecutable(name: String): String? {
    val osName = System.getProperty("os.name", "").lowercase()
    val isWindows = osName.contains("win")

    val whichCmd = if (isWindows) "where" else "which"
    val pb = ProcessBuilder(whichCmd, name)
        .redirectErrorStream(true)

    return try {
        val proc = pb.start()
        val output = proc.inputStream.bufferedReader().readText().trim()
        val exitCode = proc.waitFor()

        if (exitCode == 0 && output.isNotBlank()) {
            // On Windows, `where` may return multiple lines; take the first
            val firstLine = output.lines().firstOrNull()?.trim()
            if (firstLine != null && File(firstLine).exists()) {
                firstLine
            } else {
                null
            }
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}
