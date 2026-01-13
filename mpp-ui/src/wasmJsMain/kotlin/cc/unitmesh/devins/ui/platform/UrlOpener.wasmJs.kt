package cc.unitmesh.devins.ui.platform

/**
 * WASM-JS implementation of UrlOpener
 * Uses external JS function to open URLs
 */
actual object UrlOpener {
    actual fun openUrl(url: String) {
        try {
            openUrlExternal(url)
        } catch (e: Exception) {
            println("Failed to open URL: $url - ${e.message}")
        }
    }
}

/**
 * External JS function to open URL in new tab
 */
private fun openUrlExternal(url: String) {
    js("window.open(url, '_blank')")
}

