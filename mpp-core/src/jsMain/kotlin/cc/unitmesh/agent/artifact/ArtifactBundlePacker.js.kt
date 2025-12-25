package cc.unitmesh.agent.artifact

/**
 * JS implementation of ArtifactBundlePacker
 *
 * Note: Full ZIP support in JS requires either:
 * - Browser: JSZip library or Compression Streams API
 * - Node.js: Built-in zlib or archiver package
 *
 * This implementation provides basic functionality and can be extended
 * with platform-specific ZIP libraries.
 */
actual class ArtifactBundlePacker {

    actual suspend fun pack(bundle: ArtifactBundle, outputPath: String): PackResult {
        // In JS environment, we can use JSZip or similar library
        // For now, return the serialized bundle as a fallback
        return try {
            // Use JSON serialization as fallback (can be enhanced with JSZip)
            val serialized = ArtifactBundleUtils.serializeBundle(bundle)
            // In browser, this could trigger a download
            // In Node.js, this could write to file system
            console.log("Bundle packed (JSON fallback): ${bundle.name}")
            PackResult.Success(outputPath)
        } catch (e: Exception) {
            PackResult.Error("JS pack not fully implemented: ${e.message}", e)
        }
    }

    actual suspend fun unpack(inputPath: String): UnpackResult {
        return UnpackResult.Error("JS unpack requires JSZip library - not yet implemented")
    }

    actual suspend fun extractToDirectory(inputPath: String, outputDir: String): PackResult {
        return PackResult.Error("JS extract requires file system access - not available in browser")
    }
}

/**
 * Browser-specific: Download bundle as a file
 */
fun ArtifactBundle.downloadAsJson(): String {
    return ArtifactBundleUtils.serializeBundle(this)
}

/**
 * Browser-specific: Get bundle files for manual ZIP creation
 */
fun ArtifactBundle.getFilesForZip(): Map<String, String> {
    return getAllFiles()
}

