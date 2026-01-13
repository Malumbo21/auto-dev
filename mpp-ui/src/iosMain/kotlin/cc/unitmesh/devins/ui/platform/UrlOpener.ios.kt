package cc.unitmesh.devins.ui.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * iOS implementation of UrlOpener
 * Uses UIApplication.sharedApplication.openURL to open URLs in Safari
 */
actual object UrlOpener {
    actual fun openUrl(url: String) {
        try {
            val nsUrl = NSURL.URLWithString(url)
            if (nsUrl != null) {
                UIApplication.sharedApplication.openURL(nsUrl)
            }
        } catch (e: Exception) {
            println("Failed to open URL: $url - ${e.message}")
        }
    }
}

