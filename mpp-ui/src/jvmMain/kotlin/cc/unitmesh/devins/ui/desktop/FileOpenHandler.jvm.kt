package cc.unitmesh.devins.ui.desktop

import cc.unitmesh.agent.artifact.ArtifactBundle
import cc.unitmesh.agent.logging.AutoDevLogger
import java.awt.Desktop
import java.awt.desktop.OpenFilesEvent
import java.awt.desktop.OpenFilesHandler
import java.io.File

/**
 * JVM implementation of FileOpenHandler using Desktop API
 * Supports macOS, Windows, and Linux file associations
 */
actual class FileOpenHandler {
    private var handler: OpenFilesHandler? = null
    
    actual fun install(onFileOpen: (String) -> Unit) {
        runCatching {
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                handler = object : OpenFilesHandler {
                    override fun openFiles(e: OpenFilesEvent) {
                        val files = e.files
                        AutoDevLogger.info("FileOpenHandler") {
                            "📦 OpenFilesHandler received files: ${files.joinToString { it.absolutePath }}"
                        }

                        val unitFile = files.firstOrNull { 
                            it.name.endsWith(ArtifactBundle.BUNDLE_EXTENSION, ignoreCase = true) 
                        }
                        if (unitFile == null) {
                            AutoDevLogger.info("FileOpenHandler") { 
                                "📦 OpenFilesHandler: no .unit file in open request" 
                            }
                            return
                        }

                        val path = unitFile.absolutePath
                        AutoDevLogger.info("FileOpenHandler") { 
                            "📦 OpenFilesHandler: opening .unit file: $path" 
                        }
                        onFileOpen(path)
                    }
                }
                desktop.setOpenFileHandler(handler)
                AutoDevLogger.info("FileOpenHandler") { 
                    "📦 OpenFilesHandler installed (Desktop supported)" 
                }
            } else {
                AutoDevLogger.info("FileOpenHandler") { 
                    "📦 Desktop API not supported; OpenFilesHandler not installed" 
                }
            }
        }.onFailure { t ->
            AutoDevLogger.error("FileOpenHandler") { 
                "Failed to install OpenFilesHandler: ${t.message}" 
            }
        }
    }
    
    actual fun uninstall() {
        runCatching {
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                desktop.setOpenFileHandler(null)
                handler = null
                AutoDevLogger.info("FileOpenHandler") { 
                    "📦 OpenFilesHandler uninstalled" 
                }
            }
        }.onFailure { t ->
            AutoDevLogger.error("FileOpenHandler") { 
                "Failed to uninstall OpenFilesHandler: ${t.message}" 
            }
        }
    }
}

