package cc.unitmesh.agent.logging

/**
 * JavaScript implementation of platform-specific logging initialization
 * JS uses console logging, no file storage needed
 *
 * IMPORTANT: For ACP agent mode, all logs should go to stderr (console.error)
 * to avoid polluting stdout which is used for JSON-RPC communication.
 */
actual fun initializePlatformLogging(config: LoggingConfig) {
    // Redirect all console.log/info/warn to console.error for stderr output
    // This ensures that only JSON-RPC messages go to stdout
    redirectConsoleToStderr()
}

/**
 * JavaScript implementation of platform-specific log directory
 * JS doesn't support file logging, return a placeholder
 */
actual fun getPlatformLogDirectory(): String {
    return "console-only" // JS platform doesn't support file logging
}

/**
 * Redirect console logging to stderr
 * This is critical for ACP agent mode where stdout is used for JSON-RPC
 */
@Suppress("UNUSED_VARIABLE")
private fun redirectConsoleToStderr() {
    // Use js() to execute raw JavaScript code
    val result = js("""
        (function() {
            // Redirect all console methods to console.error (stderr)
            console.log = console.error;
            console.info = console.error;
            console.warn = console.error;
            console.debug = console.error;
            return true;
        })()
    """)
}
