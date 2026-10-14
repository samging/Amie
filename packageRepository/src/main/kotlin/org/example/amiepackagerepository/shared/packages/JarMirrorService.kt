package org.example.amiepackagerepository.shared.packages

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * Copies a validated package jar into the S3 bucket through the Amie S3 service, so it shows up on the
 * website's downloads page. Best effort: a failure is logged and never fails the upload itself.
 * Configure with `amie.s3.service-url` (empty disables mirroring).
 */
@Service
class JarMirrorService(
    @Value("\${amie.s3.service-url:}") private val serviceUrl: String,
    restClientBuilder: RestClient.Builder
) {
    private val log = LoggerFactory.getLogger(JarMirrorService::class.java)
    private val restClient: RestClient = restClientBuilder.build()

    val enabled: Boolean get() = serviceUrl.isNotBlank()

    /** True when the jar now sits in the bucket. */
    fun mirror(username: String, fileName: String, bytes: ByteArray): Boolean {
        if (!enabled) return false
        return try {
            restClient.post()
                .uri("${serviceUrl.trimEnd('/')}/api/aws/upload-data?username={u}&fileName={f}", username, fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes)
                .retrieve()
                .toBodilessEntity()
            log.info("Mirrored {} for {} to the S3 service", fileName, username)
            true
        } catch (e: Exception) {
            log.warn("Could not mirror {} to the S3 service: {}", fileName, e.message)
            false
        }
    }
}
