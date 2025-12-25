package cc.unitmesh.agent.artifact

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Android implementation of ArtifactBundlePacker
 * Uses java.util.zip for ZIP operations (same as JVM)
 */
actual class ArtifactBundlePacker {

    actual suspend fun pack(bundle: ArtifactBundle, outputPath: String): PackResult =
        withContext(Dispatchers.IO) {
            try {
                val outputFile = File(outputPath)
                outputFile.parentFile?.mkdirs()

                ZipOutputStream(FileOutputStream(outputFile)).use { zipOut ->
                    // Add ARTIFACT.md
                    val artifactMd = bundle.generateArtifactMd()
                    addZipEntry(zipOut, ArtifactBundle.ARTIFACT_MD, artifactMd)

                    // Add package.json
                    val packageJson = bundle.generatePackageJson()
                    addZipEntry(zipOut, ArtifactBundle.PACKAGE_JSON, packageJson)

                    // Add main content
                    val mainFileName = bundle.getMainFileName()
                    addZipEntry(zipOut, mainFileName, bundle.mainContent)

                    // Add additional files
                    bundle.files.forEach { (fileName, content) ->
                        addZipEntry(zipOut, fileName, content)
                    }

                    // Add context.json
                    val contextJson = ArtifactBundleUtils.serializeBundle(bundle)
                    addZipEntry(zipOut, ArtifactBundle.CONTEXT_JSON, contextJson)
                }

                PackResult.Success(outputPath)
            } catch (e: Exception) {
                PackResult.Error("Failed to pack bundle: ${e.message}", e)
            }
        }

    actual suspend fun unpack(inputPath: String): UnpackResult =
        withContext(Dispatchers.IO) {
            try {
                val files = mutableMapOf<String, String>()

                ZipInputStream(FileInputStream(inputPath)).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val content = zipIn.readBytes().toString(Charsets.UTF_8)
                            files[entry.name] = content
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }

                val bundle = ArtifactBundleUtils.reconstructBundle(files)
                if (bundle != null) {
                    UnpackResult.Success(bundle)
                } else {
                    UnpackResult.Error("Failed to reconstruct bundle from ZIP contents")
                }
            } catch (e: Exception) {
                UnpackResult.Error("Failed to unpack bundle: ${e.message}", e)
            }
        }

    actual suspend fun extractToDirectory(inputPath: String, outputDir: String): PackResult =
        withContext(Dispatchers.IO) {
            try {
                val outputDirectory = File(outputDir)
                outputDirectory.mkdirs()

                ZipInputStream(FileInputStream(inputPath)).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val outputFile = File(outputDirectory, entry.name)
                            outputFile.parentFile?.mkdirs()
                            
                            FileOutputStream(outputFile).use { fileOut ->
                                zipIn.copyTo(fileOut)
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }

                PackResult.Success(outputDir)
            } catch (e: Exception) {
                PackResult.Error("Failed to extract bundle: ${e.message}", e)
            }
        }

    private fun addZipEntry(zipOut: ZipOutputStream, fileName: String, content: String) {
        val entry = ZipEntry(fileName)
        zipOut.putNextEntry(entry)
        zipOut.write(content.toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()
    }
}