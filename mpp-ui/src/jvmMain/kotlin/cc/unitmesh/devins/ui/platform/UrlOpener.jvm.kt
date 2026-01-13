package cc.unitmesh.devins.ui.platform

import java.awt.Desktop
import java.net.URI

/**
 * JVM implementation of UrlOpener
 * Uses java.awt.Desktop to open URLs in the system's default browser
 */
actual object UrlOpener {
    actual fun openUrl(url: String) {
        try {
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(URI(url))
                }
            }
        } catch (e: Exception) {
            println("Failed to open URL: $url - ${e.message}")
        }
    }
}

