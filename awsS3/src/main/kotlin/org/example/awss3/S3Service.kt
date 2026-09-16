package org.example.awss3

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception

private data class EndpointDto(
    val retailName: String,
    val descriptiveName: String
)

data class S3ItemDto(
    val name: String,
    val path: String,
    val downloadUrl: String,
    val eTag: String?,
    val type: String,
    val size: Long?
)

@Service
class S3Service(
    private val s3Client: S3Client,
    private val objectMapper: ObjectMapper,

    @Value("\${aws.s3.bucket-name}") private val bucketName: String
) {
    private val logger = LoggerFactory.getLogger(S3Service::class.java)

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
            // Using the low-level S3Client as per your implementation pattern
            val response = s3Client.putObject(request, RequestBody.fromInputStream(file.inputStream, file.size))
            
            logger.info("Successfully uploaded {} to {}. ETag: {}", key, bucketName, response.eTag())
            "File uploaded successfully. ETag: ${response.eTag()}"
        } catch (e: Exception) {
            logger.error("Failed to upload file to S3. Bucket: {}, Key: {}", bucketName, key, e)
            
            val errorMessage = "S3 Upload Failed: ${e.message}"
            throw RuntimeException(errorMessage, e)
        }
    }

    suspend fun uploadFileData(username: String, fileName: String, data: ByteArray): String =
        withContext(Dispatchers.IO) {
            require(username.isNotBlank()) { "Username must not be blank" }
            require(fileName.isNotBlank()) { "File name must not be blank" }

            val key = "uploads/$username/$fileName"
            logger.info(
                "--- [S3Service: uploadFileData] START (user: {}, file: {}) ---",
                username,
                fileName
            )

            try {
                val request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
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
            require(fileName.isNotBlank()) { "File name must not be blank" }

            val key = if (username.isNotBlank()) "uploads/$username/$fileName" else "uploads/$fileName"
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

    suspend fun writeError(username: String, error: String, message: String): Unit = withContext(Dispatchers.IO) {
        require(username.isNotBlank()) { "Username must not be blank" }

        val key = "uploads/$username/errors.md"
        val content = "[$error]: $message"
        logger.info("Logging error for user '{}': [{}] {}", username, error, message)

        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType("text/markdown; charset=utf-8")
                    .build(),
                RequestBody.fromBytes(content.toByteArray(Charsets.UTF_8))
            )
            logger.info("Error successfully logged to S3 object {}", key)
        } catch (e: Exception) {
            logger.error("Failed to log error to S3 for user '{}': {}", username, e.message, e)
        }
    }

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
                    ListObjectsV2Request.builder().bucket(bucketName).prefix("uploads/").build()
                ).contents().asSequence()
                    .filter { it.key().lowercase().endsWith(".$extension") }
                    .map { s3Object -> searchableObject(s3Object.key(), s3Object.size()) }
                    .toList()

                logger.info("Found {} S3 objects with extension .{}", matches.size, extension)
                matches
            } else {
                val fileName = query.trim().substringBeforeLast('.', query.trim()).ifBlank { "essay1" }
                val fileExtension = query.trim().substringAfterLast('.', "pdf").ifBlank { "pdf" }
                val key = "uploads/$fileName.$fileExtension"

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

        val key = "uploads/$username/README.md"
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
                    .prefix("uploads/")
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

    /** Recursively lists all non-Markdown objects below uploads/{username}/. */
    suspend fun listUserPackages(username: String): List<S3ItemDto> = withContext(Dispatchers.IO) {
        if (username.isBlank()) return@withContext emptyList()

        val prefix = "uploads/$username/"
        logger.info("--- [S3Service: listUserPackages] START (user: {}) ---", username)

        try {
            val files = s3Client.listObjectsV2Paginator(
                ListObjectsV2Request.builder().bucket(bucketName).prefix(prefix).build()
            ).contents().asSequence()
                .filterNot { it.key().endsWith(".md", ignoreCase = true) }
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

    private fun searchableObject(key: String, size: Long): Map<String, Any> = mapOf(
        "name" to key.substringAfterLast('/'),
        "path" to key,
        "type" to "file",
        "download_url" to "s3://$bucketName/$key",
        "size" to size
    )

    private fun s3Item(key: String, type: String, size: Long? = null, eTag: String? = null): S3ItemDto =
        S3ItemDto(
            name = key.removeSuffix("/").substringAfterLast('/'),
            path = key,
            downloadUrl = "s3://$bucketName/$key",
            eTag = eTag,
            type = type,
            size = size
        )

    private fun calKb(bytes: Long): Long = bytes / 1024

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
