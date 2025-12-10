package cc.unitmesh.llm.multimodal

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Tencent COS (Cloud Object Storage) uploader for multimodal AI requests.
 * 
 * GLM-4.6V and other vision models require image URLs instead of base64,
 * so we need to upload images to cloud storage first.
 * 
 * Reference: https://cloud.tencent.com/document/product/436/7778
 */
class TencentCosUploader(
    private val secretId: String,
    private val secretKey: String,
    private val region: String = "ap-guangzhou",
    private val bucket: String = "autodev-images-1234567890" // bucket-appid format
) {
    private val client = HttpClient(CIO) {
        expectSuccess = false
    }

    private val host: String
        get() = "$bucket.cos.$region.myqcloud.com"

    /**
     * Upload an image file to Tencent COS and return the public URL.
     * 
     * @param file The image file to upload
     * @param objectKey The object key (path) in the bucket, e.g., "images/2024/01/image.png"
     * @return The public URL of the uploaded image
     */
    suspend fun uploadImage(file: File, objectKey: String? = null): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val key = objectKey ?: generateObjectKey(file)
                val content = file.readBytes()
                val contentType = getContentType(file.extension)
                
                val url = "https://$host/$key"
                val timestamp = System.currentTimeMillis() / 1000
                
                // Generate authorization header
                val authorization = generateAuthorization(
                    method = "PUT",
                    uri = "/$key",
                    headers = mapOf(
                        "host" to host,
                        "content-type" to contentType,
                        "content-length" to content.size.toString()
                    ),
                    timestamp = timestamp
                )
                
                val response = client.put(url) {
                    header("Host", host)
                    header("Authorization", authorization)
                    header("Content-Type", contentType)
                    header("Content-Length", content.size.toString())
                    header("x-cos-acl", "public-read") // Make the file publicly readable
                    setBody(content)
                }
                
                if (response.status.isSuccess()) {
                    Result.success(url)
                } else {
                    val errorBody = response.bodyAsText()
                    Result.failure(Exception("Upload failed: ${response.status} - $errorBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Upload image bytes directly to Tencent COS.
     */
    suspend fun uploadImageBytes(
        bytes: ByteArray,
        fileName: String,
        contentType: String = "image/png"
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val key = generateObjectKey(fileName)
                val url = "https://$host/$key"
                val timestamp = System.currentTimeMillis() / 1000
                
                val authorization = generateAuthorization(
                    method = "PUT",
                    uri = "/$key",
                    headers = mapOf(
                        "host" to host,
                        "content-type" to contentType,
                        "content-length" to bytes.size.toString()
                    ),
                    timestamp = timestamp
                )
                
                val response = client.put(url) {
                    header("Host", host)
                    header("Authorization", authorization)
                    header("Content-Type", contentType)
                    header("Content-Length", bytes.size.toString())
                    header("x-cos-acl", "public-read")
                    setBody(bytes)
                }
                
                if (response.status.isSuccess()) {
                    Result.success(url)
                } else {
                    val errorBody = response.bodyAsText()
                    Result.failure(Exception("Upload failed: ${response.status} - $errorBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Generate COS authorization signature.
     * Reference: https://cloud.tencent.com/document/product/436/7778
     */
    private fun generateAuthorization(
        method: String,
        uri: String,
        headers: Map<String, String>,
        timestamp: Long,
        expireSeconds: Long = 3600
    ): String {
        val keyTime = "$timestamp;${timestamp + expireSeconds}"
        
        // 1. Generate SignKey
        val signKey = hmacSha1(secretKey, keyTime)
        
        // 2. Generate HttpString
        val httpString = buildString {
            append(method.lowercase())
            append("\n")
            append(uri)
            append("\n")
            append("") // query params (empty)
            append("\n")
            // lowercase headers
            val sortedHeaders = headers.entries
                .map { it.key.lowercase() to URLEncoder.encode(it.value, "UTF-8") }
                .sortedBy { it.first }
            append(sortedHeaders.joinToString("&") { "${it.first}=${it.second}" })
            append("\n")
        }
        
        // 3. Generate StringToSign
        val sha1HttpString = sha1Hex(httpString)
        val stringToSign = "sha1\n$keyTime\n$sha1HttpString\n"
        
        // 4. Generate Signature
        val signature = hmacSha1(signKey, stringToSign)
        
        // 5. Build Authorization
        val headerList = headers.keys.map { it.lowercase() }.sorted().joinToString(";")
        
        return buildString {
            append("q-sign-algorithm=sha1")
            append("&q-ak=$secretId")
            append("&q-sign-time=$keyTime")
            append("&q-key-time=$keyTime")
            append("&q-header-list=$headerList")
            append("&q-url-param-list=")
            append("&q-signature=$signature")
        }
    }

    private fun hmacSha1(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        val result = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return result.joinToString("") { "%02x".format(it) }
    }

    private fun sha1Hex(data: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val result = digest.digest(data.toByteArray(Charsets.UTF_8))
        return result.joinToString("") { "%02x".format(it) }
    }

    private fun generateObjectKey(file: File): String {
        return generateObjectKey(file.name)
    }

    private fun generateObjectKey(fileName: String): String {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        val datePath = dateFormat.format(Date())
        val uuid = UUID.randomUUID().toString().take(8)
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return "multimodal/$datePath/${uuid}_$safeName"
    }

    private fun getContentType(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }

    fun close() {
        client.close()
    }
}

