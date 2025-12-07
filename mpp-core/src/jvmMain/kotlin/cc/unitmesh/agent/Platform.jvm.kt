package cc.unitmesh.agent

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

actual object Platform {
    actual val name: String = "JVM"
    actual val isJvm: Boolean = true
    actual val isJs: Boolean = false
    actual val isWasm: Boolean = false
    actual val isAndroid: Boolean = false

    actual val isIOS: Boolean = false

    actual fun getOSName(): String {
        return System.getProperty("os.name", "Unknown")
    }

    actual fun getDefaultShell(): String {
        val osName = System.getProperty("os.name", "")
        return when {
            osName.contains("Windows", ignoreCase = true) -> "cmd.exe"
            osName.contains("Mac", ignoreCase = true) -> "/bin/zsh"
            else -> "/bin/bash"
        }
    }

    actual fun getCurrentTimestamp(): Long {
        val now = ZonedDateTime.now()
        return now.toInstant().toEpochMilli()
    }

    actual fun getOSInfo(): String {
        val osName = System.getProperty("os.name", "Unknown")
        val osVersion = System.getProperty("os.version", "")
        val osArch = System.getProperty("os.arch", "")
        return "$osName $osVersion ($osArch)"
    }

    actual fun getOSVersion(): String {
        return System.getProperty("os.version", "Unknown")
    }

    actual fun getUserHomeDir(): String {
        return System.getProperty("user.home", "~")
    }

    actual fun getLogDir(): String {
        return "${getUserHomeDir()}/.autodev/logs"
    }

    actual fun prefersReducedMotion(): Boolean {
        // On JVM Desktop, check system properties or environment variables
        // macOS: defaults read com.apple.universalaccess reduceMotion
        // Windows: Check registry for SystemParameters.HighContrast or animations disabled
        // Linux: Check GTK settings or GNOME accessibility settings
        return try {
            val osName = getOSName().lowercase()
            when {
                osName.contains("mac") -> {
                    // Check macOS reduce motion setting via defaults command
                    val process = ProcessBuilder("defaults", "read", "com.apple.universalaccess", "reduceMotion")
                        .redirectErrorStream(true)
                        .start()
                    val result = process.inputStream.bufferedReader().readText().trim()
                    val completed = process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)
                    if (!completed) {
                        process.destroyForcibly()
                        return false
                    }
                    result == "1"
                }
                osName.contains("windows") -> {
                    // Windows: Proper implementation requires JNA/JNI to call
                    // SystemParametersInfo(SPI_GETCLIENTAREAANIMATION) or read registry
                    // For now, return false as a safe default
                    false
                }
                else -> {
                    // Linux/other: Check GTK_ENABLE_ANIMATIONS or GNOME settings
                    val gtkAnimations = System.getenv("GTK_ENABLE_ANIMATIONS")
                    gtkAnimations == "0" || gtkAnimations == "false"
                }
            }
        } catch (e: Exception) {
            // Default to false if we can't determine the setting
            false
        }
    }
}
