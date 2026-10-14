package org.example.awss3

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.net.URLEncoder
import java.time.Instant

private data class EndpointDto(
    val retailName: String,
    val descriptiveName: String
)

data class S3ItemDto(
    val name: String,
    val path: String,
    val downloadUrl: String,
    @get:JsonProperty("eTag") val eTag: String?,
    val type: String,
    val size: Long?
)

/** One object read back from the bucket. */
class StoredObject(
    val key: String,
    val bytes: ByteArray,
    val contentType: String?,
    val eTag: String?,
    val lastModified: Instant?
) {
    val name: String get() = key.substringAfterLast('/')
}

/** What `HEAD` tells about an object, without its bytes. */
data class ObjectMeta(val name: String, val path: String, val size: Long, @get:JsonProperty("eTag") val eTag: String?, val contentType: String?, val downloadUrl: String)

/** One line of a user's error log (`uploads/<user>/state/errors.jsonl`). */
data class ErrorEntry(val time: String, val error: String, val message: String)

/**
 * The package store: everything the Amie backend and website keep in the bucket below `uploads/`.
 *
 * Layout: `uploads/<user>/README.md` (dashboard marker), `uploads/<user>/<lang>/<file>` (published package
 * files), `uploads/<user>/state/<name>.json` (a user's saved states, devices among them) and
 * `uploads/<user>/state/errors.jsonl` (the user's error log). Listings skip Markdown and the `state/` folder.
 */
@Service
class S3Service(
    private val s3Client: S3Client,
    private val objectMapper: ObjectMapper,
    @Value("\${aws.s3.bucket-name}") private val bucketName: String,
    /** Where clients reach this service, used to build the download URLs in listings. */
    @Value("\${amie.public-url:http://localhost:8088}") private val publicUrl: String = "http://localhost:8088"
) {
    private val logger = LoggerFactory.getLogger(S3Service::class.java)

    companion object {
        const val UPLOADS = "uploads/"
        const val STATE_FOLDER = "state/"
        const val ERROR_LOG = "errors.jsonl"
        const val MAX_ERROR_LINES = 200

        /** Only keys below `uploads/` that cannot escape it are served, written or deleted by key. */
        fun isAllowedKey(key: String): Boolean =
            key.startsWith(UPLOADS) && key.length > UPLOADS.length && !key.endsWith("/") &&
                key.split('/').none { it.isEmpty() || it == "." || it == ".." }

        /** A file name as sent to `upload-data`: one optional folder, no escaping. */
        fun isAllowedFileName(fileName: String): Boolean =
            fileName.isNotBlank() && !fileName.startsWith("/") && !fileName.endsWith("/") &&
                fileName.split('/').none { it.isBlank() || it == "." || it == ".." }

        /** Package listings leave out dashboards and the state folder. */
        fun isPackageKey(key: String): Boolean =
            !key.endsWith(".md", ignoreCase = true) && !key.substringAfter(UPLOADS).substringAfter('/').startsWith(STATE_FOLDER)

        fun errorLogKey(username: String) = "$UPLOADS$username/$STATE_FOLDER$ERROR_LOG"
    }

    /** The HTTP address a client downloads [key] from. */
    fun downloadUrl(key: String): String =
        "${publicUrl.trimEnd('/')}/api/aws/files/content?key=${URLEncoder.encode(key, Charsets.UTF_8)}"

    suspend fun uploadFile(file: MultipartFile): String = withContext(Dispatchers.IO) {
        val key = file.originalFilename ?: "unknown_${System.currentTimeMillis()}"
        logger.info("Attempting to upload file: {} to bucket: {}", key, bucketName)

        val metadataVal = mutableMapOf<String, String>()
        metadataVal["myVal"] = "test"

        val request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .metadata(metadataVal)
            .build()

        try {
            val response = s3Client.putObject(request, RequestBody.fromInputStream(file.inputStream, file.size))

            logger.info("Successfully uploaded {} to {}. ETag: {}", key, bucketName, response.eTag())
            "File uploaded successfully. ETag: ${response.eTag()}"
        } catch (e: Exception) {
            logger.error("Failed to upload file to S3. Bucket: {}, Key: {}", bucketName, key, e)

            val errorMessage = "S3 Upload Failed: ${e.message}"
            throw RuntimeException(errorMessage, e)
        }
    }

    /**
     * Stores [data] at `uploads/<username>/<fileName>`; [fileName] may carry one folder, such as
     * `jar/echo.jar`, which is how the backend files a package under its language.
     */
    suspend fun uploadFileData(username: String, fileName: String, data: ByteArray, contentType: String? = null): String =
        withContext(Dispatchers.IO) {
            require(username.isNotBlank()) { "Username must not be blank" }
            require(isAllowedFileName(fileName)) { "File name must not be blank or leave the user's folder" }

            val key = "$UPLOADS$username/$fileName"
            logger.info("--- [S3Service: uploadFileData] START (user: {}, file: {}) ---", username, fileName)

            try {
                val request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .apply { if (!contentType.isNullOrBlank()) contentType(contentType) }
                    .build()

                logger.info("Uploading raw data to bucket: {}, key: {}", bucketName, key)
                val response = s3Client.putObject(request, RequestBody.fromBytes(data))
                logger.info("S3 raw data upload successful for '{}'. ETag: {}", fileName, response.eTag())
                "File uploaded successfully. ETag: ${response.eTag()}"
            } catch (e: Exception) {
                logger.error("S3 raw data upload failed for '{}': {}", fileName, e.message, e)
                throw RuntimeException("S3 upload failed for $fileName", e)
            } finally {
                logger.info("--- [S3Service: uploadFileData] END ---")
            }
        }

    suspend fun sendEdit(username: String, fileName: String, updateFile: MultipartFile): String =
        withContext(Dispatchers.IO) {
            require(isAllowedFileName(fileName)) { "File name must not be blank or leave the user's folder" }

            val key = if (username.isNotBlank()) "$UPLOADS$username/$fileName" else "$UPLOADS$fileName"
            logger.info("--- [S3Service: sendEdit] START (user: {}, file: {}) ---", username, fileName)

            try {
                logger.info("Checking that object exists before update: {}", key)
                s3Client.headObject(
                    HeadObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build()
                )

                val request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(updateFile.contentType)
                    .build()

                logger.info("Replacing S3 object: {}", key)
                val response = updateFile.inputStream.use { inputStream ->
                    s3Client.putObject(request, RequestBody.fromInputStream(inputStream, updateFile.size))
                }

                logger.info("S3 update successful for user {}. ETag: {}", username, response.eTag())
                "File updated successfully. ETag: ${response.eTag()}"
            } catch (e: Exception) {
                logger.error("S3 update failed for user '{}': {}", username, e.message, e)
                throw RuntimeException("S3 update failed for $key", e)
            } finally {
                logger.info("--- [S3Service: sendEdit] END ---")
            }
        }

    // ---- objects by key ----------------------------------------------------------------------------

    /** The object at [key], or null when there is none. */
    suspend fun getObject(key: String): StoredObject? = withContext(Dispatchers.IO) {
        require(isAllowedKey(key)) { "Key must be below uploads/" }
        try {
            val response = s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucketName).key(key).build())
            StoredObject(
                key = key,
                bytes = response.asByteArray(),
                contentType = response.response().contentType(),
                eTag = response.response().eTag(),
                lastModified = response.response().lastModified()
            )
        } catch (e: NoSuchKeyException) {
            null
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) null else throw e
        }
    }

    /** The object's size and tags without downloading it, or null when there is none. */
    suspend fun headObject(key: String): ObjectMeta? = withContext(Dispatchers.IO) {
        require(isAllowedKey(key)) { "Key must be below uploads/" }
        try {
            val head = s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key).build())
            ObjectMeta(key.substringAfterLast('/'), key, head.contentLength() ?: 0L, head.eTag(), head.contentType(), downloadUrl(key))
        } catch (e: NoSuchKeyException) {
            null
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) null else throw e
        }
    }

    /** Writes [bytes] at [key]; returns the ETag. */
    suspend fun putObject(key: String, bytes: ByteArray, contentType: String? = null): String = withContext(Dispatchers.IO) {
        require(isAllowedKey(key)) { "Key must be below uploads/" }
        val request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .apply { if (!contentType.isNullOrBlank()) contentType(contentType) }
            .build()
        val response = s3Client.putObject(request, RequestBody.fromBytes(bytes))
        logger.info("Stored {} ({} bytes)", key, bytes.size)
        response.eTag()
    }

    /** Removes the object at [key]; false when it was not there. */
    suspend fun deleteObject(key: String): Boolean = withContext(Dispatchers.IO) {
        require(isAllowedKey(key)) { "Key must be below uploads/" }
        if (!exists(key)) return@withContext false
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build())
        logger.info("Deleted {} from bucket {}", key, bucketName)
        true
    }

    private fun exists(key: String): Boolean = try {
        s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key).build())
        true
    } catch (e: NoSuchKeyException) {
        false
    } catch (e: S3Exception) {
        if (e.statusCode() == 404) false else throw e
    }

    // ---- error log ---------------------------------------------------------------------------------

    /**
     * Appends one entry to the user's error log, keeping the last [MAX_ERROR_LINES]. Best effort: a
     * failure is logged here and never reaches the caller.
     */
    suspend fun writeError(username: String, error: String, message: String): Unit = withContext(Dispatchers.IO) {
        require(username.isNotBlank()) { "Username must not be blank" }

        val key = errorLogKey(username)
        logger.info("Logging error for user '{}': [{}] {}", username, error, message)

        try {
            val existing = try {
                s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucketName).key(key).build()).asUtf8String()
            } catch (e: NoSuchKeyException) {
                ""
            } catch (e: S3Exception) {
                if (e.statusCode() == 404) "" else throw e
            }
            val entry = ErrorEntry(Instant.now().toString(), error.take(200), message.take(2000))
            val lines = existing.lineSequence().filter { it.isNotBlank() }.toMutableList()
            lines += objectMapper.writeValueAsString(entry)
            val kept = lines.takeLast(MAX_ERROR_LINES)
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType("application/x-ndjson; charset=utf-8")
                    .build(),
                RequestBody.fromBytes((kept.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8))
            )
            logger.info("Error appended to S3 object {} ({} entries)", key, kept.size)
        } catch (e: Exception) {
            logger.error("Failed to log error to S3 for user '{}': {}", username, e.message, e)
        }
    }

    /** The user's error log, newest first; empty when there is none. */
    suspend fun listErrors(username: String): List<ErrorEntry> = withContext(Dispatchers.IO) {
        if (username.isBlank()) return@withContext emptyList()
        val key = errorLogKey(username)
        val text = try {
            s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucketName).key(key).build()).asUtf8String()
        } catch (e: NoSuchKeyException) {
            return@withContext emptyList()
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) return@withContext emptyList() else throw e
        }
        text.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
            try {
                val node = objectMapper.readTree(line)
                ErrorEntry(node.path("time").asText(""), node.path("error").asText(""), node.path("message").asText(""))
            } catch (e: Exception) {
                null
            }
        }.toList().asReversed()
    }

    /** Removes the user's error log; false when there was none. */
    suspend fun clearErrors(username: String): Boolean {
        if (username.isBlank()) return false
        return deleteObject(errorLogKey(username))
    }

    // ---- endpoints ---------------------------------------------------------------------------------

    suspend fun fetchEndpoints(): Map<String, String> = withContext(Dispatchers.IO) {
        val key = "repositoryInformations"
        logger.info("--- [S3Service: fetchEndpoints] START ---")

        try {
            logger.info("Fetching endpoints from S3 object: {}", key)
            val content = s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build()
            ).asUtf8String()
            val root = objectMapper.readTree(content)

            val endpoints = when {
                root.isArray -> root.associate { endpoint ->
                    val retailName = endpoint.path("retailName").asText()
                    val descriptiveName = endpoint.path("descriptiveName").asText()
                    require(retailName.isNotBlank() && descriptiveName.isNotBlank()) {
                        "Each endpoint must contain retailName and descriptiveName"
                    }
                    retailName to descriptiveName
                }
                root.isObject -> root.fields().asSequence().associate { (name, value) ->
                    name to value.asText()
                }
                else -> emptyMap()
            }

            logger.info("Successfully parsed {} endpoints", endpoints.size)
            endpoints
        } catch (e: Exception) {
            logger.error("Error fetching endpoints from S3: {}", e.message, e)
            emptyMap()
        } finally {
            logger.info("--- [S3Service: fetchEndpoints] END ---")
        }
    }

    suspend fun postEndpoints(): Unit = withContext(Dispatchers.IO) {
        val key = "repositoryInformations"
        val endpoints = listOf(
            EndpointDto("Arduino Uno", "Arduino uno more descriptive"),
            EndpointDto("Arduino Uno2", "Arduino uno more descriptive"),
            EndpointDto("Arduino Uno3", "Arduino uno more descriptive")
        )
        logger.info("--- [S3Service: postEndpoints] START ---")

        try {
            val json = objectMapper.writeValueAsBytes(endpoints)
            logger.info("Uploading {} endpoints to S3 object: {}", endpoints.size, key)
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType("application/json")
                    .build(),
                RequestBody.fromBytes(json)
            )
            logger.info("Endpoints uploaded successfully to S3")
        } catch (e: Exception) {
            logger.error("S3 error while posting endpoints: {}", e.message, e)
        } finally {
            logger.info("--- [S3Service: postEndpoints] END ---")
        }
    }

    // ---- queries and listings ----------------------------------------------------------------------

    /**
     * Searches S3 objects in the uploads prefix. A query beginning with `*` searches by extension;
     * any other query looks up the exact key `uploads/{fileName}.{extension}`.
     */
    suspend fun queryFiles(query: String = ""): Any? = withContext(Dispatchers.IO) {
        logger.info("--- [S3Service: queryFiles] START (query: {}) ---", query)

        try {
            if (query.trim().startsWith("*")) {
                val extension = query.trim().removePrefix("*").lowercase()
                if (extension.isBlank()) return@withContext emptyList<Map<String, Any>>()

                val matches = s3Client.listObjectsV2Paginator(
                    ListObjectsV2Request.builder().bucket(bucketName).prefix(UPLOADS).build()
                ).contents().asSequence()
                    .filter { it.key().lowercase().endsWith(".$extension") && isPackageKey(it.key()) }
                    .map { s3Object -> searchableObject(s3Object.key(), s3Object.size()) }
                    .toList()

                logger.info("Found {} S3 objects with extension .{}", matches.size, extension)
                matches
            } else {
                val fileName = query.trim().substringBeforeLast('.', query.trim()).ifBlank { "essay1" }
                val fileExtension = query.trim().substringAfterLast('.', "pdf").ifBlank { "pdf" }
                val key = "$UPLOADS$fileName.$fileExtension"

                val metadata = s3Client.headObject(
                    HeadObjectRequest.builder().bucket(bucketName).key(key).build()
                )
                searchableObject(key, metadata.contentLength())
            }
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) {
                logger.info("No S3 object found for query: {}", query)
                null
            } else {
                logger.error("S3 file search failed for query {}: {}", query, e.message, e)
                null
            }
        } catch (e: Exception) {
            logger.error("S3 file search failed for query {}: {}", query, e.message, e)
            null
        } finally {
            logger.info("--- [S3Service: queryFiles] END ---")
        }
    }

    /** Creates uploads/{username}/README.md only when it does not already exist. */
    suspend fun createUserDashboard(username: String): Unit = withContext(Dispatchers.IO) {
        if (username.isBlank()) return@withContext

        val key = "$UPLOADS$username/README.md"
        logger.info("--- [S3Service: createUserDashboard] START (user: {}) ---", username)

        try {
            try {
                s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key).build())
                logger.info("Dashboard already exists for user '{}'. Skipping creation.", username)
                return@withContext
            } catch (e: S3Exception) {
                if (e.statusCode() != 404) throw e
            }

            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType("text/markdown; charset=utf-8")
                    .build(),
                RequestBody.fromBytes("# Dashboard for $username".toByteArray(Charsets.UTF_8))
            )
            logger.info("S3 user dashboard created successfully for {}", username)
        } catch (e: Exception) {
            logger.error("S3 dashboard creation failed for user '{}': {}", username, e.message, e)
        } finally {
            logger.info("--- [S3Service: createUserDashboard] END ---")
        }
    }

    /** Lists files and virtual directories directly below the uploads prefix. */
    suspend fun listFiles(): List<S3ItemDto> = withContext(Dispatchers.IO) {
        logger.info("--- [S3Service: listFiles] START ---")

        try {
            val items = s3Client.listObjectsV2Paginator(
                ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(UPLOADS)
                    .delimiter("/")
                    .build()
            ).asSequence().flatMap { page ->
                sequence {
                    page.commonPrefixes().forEach { prefix ->
                        yield(s3Item(prefix.prefix(), "dir"))
                    }
                    page.contents().forEach { s3Object ->
                        yield(s3Item(s3Object.key(), "file", s3Object.size(), s3Object.eTag()))
                    }
                }
            }.toList()

            logger.info("Found {} items directly below uploads/", items.size)
            items
        } catch (e: Exception) {
            logger.error("S3 list files error: {}", e.message, e)
            emptyList()
        } finally {
            logger.info("--- [S3Service: listFiles] END ---")
        }
    }

    /** Recursively lists all package files below uploads/{username}/ (no Markdown, nothing under state/). */
    suspend fun listUserPackages(username: String): List<S3ItemDto> = withContext(Dispatchers.IO) {
        if (username.isBlank()) return@withContext emptyList()

        val prefix = "$UPLOADS$username/"
        logger.info("--- [S3Service: listUserPackages] START (user: {}) ---", username)

        try {
            val files = s3Client.listObjectsV2Paginator(
                ListObjectsV2Request.builder().bucket(bucketName).prefix(prefix).build()
            ).contents().asSequence()
                .filter { isPackageKey(it.key()) }
                .map { s3Object -> s3Item(s3Object.key(), "file", s3Object.size(), s3Object.eTag()) }
                .toList()

            logger.info("Finished S3 package scan for user {}, found {} files", username, files.size)
            files
        } catch (e: Exception) {
            logger.error("S3 package scan failed for user '{}': {}", username, e.message, e)
            emptyList()
        } finally {
            logger.info("--- [S3Service: listUserPackages] END ---")
        }
    }

    /** The names of the user's saved states (`uploads/<user>/state/<name>.json`). */
    suspend fun listStates(username: String): List<String> = withContext(Dispatchers.IO) {
        if (username.isBlank()) return@withContext emptyList()
        val prefix = "$UPLOADS$username/$STATE_FOLDER"
        s3Client.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucketName).prefix(prefix).build())
            .contents().asSequence()
            .map { it.key().removePrefix(prefix) }
            .filter { it.endsWith(".json") && !it.contains('/') }
            .map { it.removeSuffix(".json") }
            .sorted()
            .toList()
    }

    private fun searchableObject(key: String, size: Long): Map<String, Any> = mapOf(
        "name" to key.substringAfterLast('/'),
        "path" to key,
        "type" to "file",
        "download_url" to downloadUrl(key),
        "size" to size
    )

    private fun s3Item(key: String, type: String, size: Long? = null, eTag: String? = null): S3ItemDto =
        S3ItemDto(
            name = key.removeSuffix("/").substringAfterLast('/'),
            path = key,
            downloadUrl = if (type == "dir") "" else downloadUrl(key),
            eTag = eTag,
            type = type,
            size = size
        )

    suspend fun listBucketObjects(bucketName: String = this.bucketName): List<String> = withContext(Dispatchers.IO) {
        val request = ListObjectsRequest.builder()
            .bucket(bucketName)
            .build()

        val response = s3Client.listObjects(request)
        response.contents().map { it.key() }
    }

    suspend fun downloadAmie(keyName: String, bucketName: String): ResponseInputStream<GetObjectResponse> {
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(keyName)
            .build()

        return s3Client.getObject(getObjectRequest)
    }

}
