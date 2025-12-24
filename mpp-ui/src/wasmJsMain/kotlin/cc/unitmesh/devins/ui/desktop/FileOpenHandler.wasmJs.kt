package cc.unitmesh.devins.ui.desktop

import cc.unitmesh.agent.logging.AutoDevLogger

/**
 * WASM implementation of FileOpenHandler
 * Web apps don't receive file open events from the OS
 */
actual class FileOpenHandler {
    actual fun install(onFileOpen: (String) -> Unit) {
        // Web apps don't receive OS file open events
        AutoDevLogger.info("FileOpenHandler") { 
            "📦 FileOpenHandler: Not applicable for WASM apps" 
        }
    }
    
    actual fun uninstall() {
        // No-op on WASM
    }
}

