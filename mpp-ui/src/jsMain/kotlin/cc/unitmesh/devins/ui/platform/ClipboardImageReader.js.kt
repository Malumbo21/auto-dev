package cc.unitmesh.devins.ui.platform

/**
 * JS implementation of ClipboardImageReader.
 * TODO: Implement using Clipboard API for image reading.
 */
class JsClipboardImageReader : ClipboardImageReader {
    override fun hasImage(): Boolean {
        // TODO: Implement using Clipboard API
        return false
    }

    override fun readImage(): ClipboardImageData? {
        // TODO: Implement using Clipboard API
        return null
    }
}

actual fun createClipboardImageReader(): ClipboardImageReader = JsClipboardImageReader()

