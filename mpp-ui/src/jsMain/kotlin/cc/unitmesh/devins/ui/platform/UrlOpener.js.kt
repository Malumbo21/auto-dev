package cc.unitmesh.devins.ui.platform

import kotlinx.browser.window

/**
 * JS implementation of UrlOpener
 * Uses window.open() to open URLs in a new browser tab
 */
actual object UrlOpener {
    actual fun openUrl(url: String) {
        try {
            window.open(url, "_blank")
        } catch (e: Exception) {
            println("Failed to open URL: $url - ${e.message}")
        }
    }
}

