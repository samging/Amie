package org.example.awss3

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest

@Service
class S3Service(
    private val s3Client: S3Client,
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
}
