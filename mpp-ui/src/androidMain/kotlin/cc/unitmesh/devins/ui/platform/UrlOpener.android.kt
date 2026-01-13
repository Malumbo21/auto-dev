package cc.unitmesh.devins.ui.platform

import android.content.Intent
import android.net.Uri
import cc.unitmesh.config.ConfigManager

/**
 * Android implementation of UrlOpener
 * Uses Intent.ACTION_VIEW to open URLs in the system's default browser
 */
actual object UrlOpener {
    actual fun openUrl(url: String) {
        try {
            val context = ConfigManager.appContext
            if (context != null) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                println("Failed to open URL: Context not available")
            }
        } catch (e: Exception) {
            println("Failed to open URL: $url - ${e.message}")
        }
    }
}

