package cc.unitmesh.agent.artifact

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * JVM implementation of ArtifactBundlePacker
 * Uses java.util.zip for ZIP operations
 */
actual class ArtifactBundlePacker {

    actual suspend fun pack(bundle: ArtifactBundle, outputPath: String): PackResult =
        withContext(Dispatchers.IO) {
            try {
                val outputFile = File(outputPath)
                outputFile.parentFile?.mkdirs()

                ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zos ->
                    // Add all bundle files
                    bundle.getAllFiles().forEach { (path, content) ->
                        val entry = ZipEntry(path)
                        zos.putNextEntry(entry)
                        zos.write(content.toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                    }
                }

                PackResult.Success(outputFile.absolutePath)
            } catch (e: Exception) {
                PackResult.Error("Failed to pack bundle: ${e.message}", e)
            }
        }

    actual suspend fun unpack(inputPath: String): UnpackResult =
        withContext(Dispatchers.IO) {
            try {
                val inputFile = File(inputPath)
                if (!inputFile.exists()) {
                    return@withContext UnpackResult.Error("File not found: $inputPath")
                }

                val files = mutableMapOf<String, String>()

                ZipInputStream(BufferedInputStream(FileInputStream(inputFile))).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val content = zis.readBytes().toString(Charsets.UTF_8)
                            files[entry.name] = content
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                // Validate bundle structure
                when (val validation = ArtifactBundleUtils.validateBundle(files)) {
                    is ValidationResult.Valid -> {
                        val bundle = ArtifactBundleUtils.reconstructBundle(files)
                        if (bundle != null) {
                            UnpackResult.Success(bundle)
                        } else {
                            UnpackResult.Error("Failed to reconstruct bundle from files")
                        }
                    }
                    is ValidationResult.Invalid -> {
                        UnpackResult.Error("Invalid bundle: ${validation.errors.joinToString(", ")}")
                    }
                }
            } catch (e: Exception) {
                UnpackResult.Error("Failed to unpack bundle: ${e.message}", e)
            }
        }

    actual suspend fun extractToDirectory(inputPath: String, outputDir: String): PackResult =
        withContext(Dispatchers.IO) {
            try {
                val inputFile = File(inputPath)
                if (!inputFile.exists()) {
                    return@withContext PackResult.Error("File not found: $inputPath")
                }

                val outputDirectory = File(outputDir)
                outputDirectory.mkdirs()

                ZipInputStream(BufferedInputStream(FileInputStream(inputFile))).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val outputFile = File(outputDirectory, entry.name)

                        // Security check: prevent zip slip attack
                        if (!outputFile.canonicalPath.startsWith(outputDirectory.canonicalPath)) {
                            throw SecurityException("Zip entry outside target directory: ${entry.name}")
                        }

                        if (entry.isDirectory) {
                            outputFile.mkdirs()
                        } else {
                            outputFile.parentFile?.mkdirs()
                            FileOutputStream(outputFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                PackResult.Success(outputDirectory.absolutePath)
            } catch (e: Exception) {
                PackResult.Error("Failed to extract bundle: ${e.message}", e)
            }
        }
}

/**
 * JVM-specific extension to save bundle to a file
 */
suspend fun ArtifactBundle.saveToFile(outputPath: String): PackResult {
    val packer = ArtifactBundlePacker()
    val finalPath = if (outputPath.endsWith(ArtifactBundle.BUNDLE_EXTENSION)) {
        outputPath
    } else {
        "$outputPath${ArtifactBundle.BUNDLE_EXTENSION}"
    }
    return packer.pack(this, finalPath)
}

/**
 * JVM-specific extension to load bundle from a file
 */
suspend fun loadArtifactBundle(inputPath: String): UnpackResult {
    val packer = ArtifactBundlePacker()
    return packer.unpack(inputPath)
}

