package cc.unitmesh.devins.ui.platform

/**
 * Android implementation of ClipboardImageReader.
 * TODO: Implement using Android ClipboardManager for image reading.
 */
class AndroidClipboardImageReader : ClipboardImageReader {
    override fun hasImage(): Boolean {
        // TODO: Implement using ClipboardManager
        return false
    }

    override fun readImage(): ClipboardImageData? {
        // TODO: Implement using ClipboardManager
        return null
    }
}

actual fun createClipboardImageReader(): ClipboardImageReader = AndroidClipboardImageReader()

