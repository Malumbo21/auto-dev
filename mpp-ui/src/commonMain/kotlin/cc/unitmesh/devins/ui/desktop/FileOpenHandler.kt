package cc.unitmesh.devins.ui.desktop

/**
 * Cross-platform interface for handling file open events (e.g., double-click on .unit files)
 * 
 * Platform-specific implementations:
 * - JVM: Uses Desktop.getDesktop().setOpenFileHandler() for macOS/Windows/Linux
 * - Android: Uses Intent handling
 * - iOS: Uses URL scheme handling
 * - JS/WASM: Not applicable (web apps don't receive file open events)
 */
expect class FileOpenHandler {
    /**
     * Install the file open handler
     * @param onFileOpen Callback when a file is opened, receives the file path
     */
    fun install(onFileOpen: (String) -> Unit)
    
    /**
     * Uninstall the file open handler
     */
    fun uninstall()
}

