package cc.unitmesh.devins.ui.desktop

import cc.unitmesh.agent.logging.AutoDevLogger

/**
 * Android implementation of FileOpenHandler
 * File opening is handled via Intent filters in AndroidManifest.xml
 */
actual class FileOpenHandler {
    actual fun install(onFileOpen: (String) -> Unit) {
        // Android handles file opening via Intent filters
        // This is a no-op as the activity's onNewIntent() handles it
        AutoDevLogger.info("FileOpenHandler") { 
            "📦 FileOpenHandler: Android uses Intent filters for file opening" 
        }
    }
    
    actual fun uninstall() {
        // No-op on Android
    }
}

