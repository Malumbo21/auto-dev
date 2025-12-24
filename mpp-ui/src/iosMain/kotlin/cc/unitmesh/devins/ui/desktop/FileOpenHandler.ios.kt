package cc.unitmesh.devins.ui.desktop

import cc.unitmesh.agent.logging.AutoDevLogger

/**
 * iOS implementation of FileOpenHandler
 * File opening is handled via URL scheme or document picker
 */
actual class FileOpenHandler {
    actual fun install(onFileOpen: (String) -> Unit) {
        // iOS handles file opening via URL schemes or document picker
        // This would need to be implemented using Swift interop
        AutoDevLogger.info("FileOpenHandler") { 
            "📦 FileOpenHandler: iOS file opening not yet implemented" 
        }
    }
    
    actual fun uninstall() {
        // No-op on iOS
    }
}

