package cc.unitmesh.devins.ui.platform

/**
 * iOS implementation of ClipboardImageReader.
 * TODO: Implement using UIPasteboard for image reading.
 */
class IosClipboardImageReader : ClipboardImageReader {
    override fun hasImage(): Boolean {
        // TODO: Implement using UIPasteboard
        return false
    }

    override fun readImage(): ClipboardImageData? {
        // TODO: Implement using UIPasteboard
        return null
    }
}

actual fun createClipboardImageReader(): ClipboardImageReader = IosClipboardImageReader()

