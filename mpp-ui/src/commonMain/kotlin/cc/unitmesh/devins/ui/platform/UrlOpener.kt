package cc.unitmesh.devins.ui.platform

/**
 * Cross-platform URL opener
 * Opens URLs in the system's default browser
 */
expect object UrlOpener {
    /**
     * Open a URL in the system's default browser
     * @param url The URL to open (e.g., "https://github.com/settings/tokens")
     */
    fun openUrl(url: String)
}

