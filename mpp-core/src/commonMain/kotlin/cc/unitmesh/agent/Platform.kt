package cc.unitmesh.agent

/**
 * Platform-specific functionality for different targets
 */
expect object Platform {
    val name: String
    val isJvm: Boolean
    val isJs: Boolean
    val isWasm: Boolean
    val isAndroid: Boolean
    val isIOS: Boolean

    fun getOSName(): String
    fun getDefaultShell(): String
    fun getCurrentTimestamp(): Long
    fun getOSInfo(): String
    fun getOSVersion(): String

    /**
     * Get user home directory
     */
    fun getUserHomeDir(): String

    /**
     * Get platform-specific log directory
     * Default: ~/.autodev/logs
     */
    fun getLogDir(): String

    /**
     * Check if user prefers reduced motion (accessibility setting)
     * Used to disable or simplify animations for users who are sensitive to motion
     */
    fun prefersReducedMotion(): Boolean
}

/**
 * Get current platform information
 */
fun getPlatformInfo(): String {
    return "Running on ${Platform.name}"
}
