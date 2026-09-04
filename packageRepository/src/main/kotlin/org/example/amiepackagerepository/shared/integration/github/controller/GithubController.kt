package org.example.amiepackagerepository.shared.transactionalMiddleware.integration.github.controller

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.drive.Drive
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.amiepackagerepository.shared.integration.github.service.GithubService
import org.example.amiepackagerepository.shared.integration.github.dto.GithubItemDto
import org.example.amiepackagerepository.shared.integration.github.dto.GithubItemMetadataDto
import org.example.amiepackagerepository.shared.integration.github.dto.PostDeviceStatusDto
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.example.amiepackagerepository.shared.transactionalMiddleware.enumerables.DeviceActions
import org.example.amiepackagerepository.shared.transactionalMiddleware.dto.DeviceDto
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.TransactionalStatusRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * REST Controller for GitHub integration.
 * Provides endpoints for managing files and device statuses within a GitHub repository.
 *
 * Supported operations:
 * - Listing repository files and metadata.
 * - Uploading new code snippets with language classification.
 * - Updating/editing existing files.
 * - Syncing device status DTOs with GitHub storage.
 *
 * @property driveService The authorized Google Drive client.
 * @property simpleService The service handling GitHub-specific logic.
 * @property userService Authentication and user data service.
 * @property deviceService Service for handling device-related transactional logic.
 */
@CrossOrigin(origins = ["http://localhost:8081"])
@RestController
class GithubController(
    private val driveService: Drive,
    private val simpleService: GithubService,
    private val userService: UserService,
    private val deviceService: TransactionalStatusRepository
) {
    private val logger = LoggerFactory.getLogger(GithubController::class.java)

    /**
     * Retrieves a list of all files present in the GitHub repository "uploads" folder.
     *
     * @return A list of [GithubItemDto] objects.
     */
    @GetMapping("/list-github")
    fun getRepositoryFiles(): List<GithubItemDto> {
        logger.info("--- [GET /list-github] START ---")
        return simpleService.listFilesGithub().also { logger.info("--- [GET /list-github] END ---") }
    }

    /**
     * Retrieves metadata for all files in the repository.
     * Specifically identifies if files are C components (*.c).
     *
     * @return A map where the key is the index string and the value is [GithubItemMetadataDto].
     */
    @GetMapping("list-github-metadata")
    fun getRepositoryMetadata(): Map<String, GithubItemMetadataDto> {
        logger.info("--- [GET /list-github-metadata] START ---")
        val githubItems = simpleService.listFilesGithub()
        return githubItems.mapIndexed { index, item ->
            index.toString() to GithubItemMetadataDto(
                name = item.name,
                downloadUrl = item.downloadUrl,
                id = item.id,
                type = item.type,
                endComp = item.name.endsWith(".c").toString()
            )
        }.toMap().also { logger.info("--- [GET /list-github-metadata] END ---") }
    }

    /**
     * Uploads a code snippet to GitHub. Validates the user's JWT token first.
     *
     * @param file The file to upload.
     * @param progLanguage The programming language of the snippet (for directory categorization).
     * @param username The owner of the file.
     * @param authHeader The "Authorization" Bearer token.
     * @return A success or error message string.
     * @throws ResponseStatusException 401 if unauthorized, 403 if forbidden.
     */
    @PostMapping("/upload")
    fun uploadCodeSnippet(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(value = "progLanguage", defaultValue = "unknown") progLanguage: String,
        @RequestParam("username") username: String,
        @RequestHeader("Authorization") authHeader: String
    ): String {
        logger.info("--- [POST /upload] START (user: {}, file: {}, lang: {}) ---", username, file.originalFilename, progLanguage)
        val token = authHeader.removePrefix("Bearer ")

        val claims = userService.validateToken(token) ?: run {
            logger.warn("CONTROLLER: Unauthorized upload attempt for user '{}'", username)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        }

        if (claims.subject != username) {
            logger.warn("CONTROLLER: Forbidden upload attempt. Token subject '{}' does not match request username '{}'", claims.subject, username)
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }

        return try {
            simpleService.uploadFile(username, progLanguage, file)
            logger.info("CONTROLLER: Upload successful for user '{}', file '{}'", username, file.originalFilename)
            "File uploaded successfully"
        } catch (e: GoogleJsonResponseException) {
            logger.error("CONTROLLER: Google API Error: {}", e.details?.message ?: e.message)
            "Google API Error: ${e.details?.message ?: e.message}"
        } catch (e: Exception) {
            logger.error("CONTROLLER: Unexpected error during upload: {}", e.message, e)
            "Error uploading file: ${e.message}"
        } finally {
            logger.info("--- [POST /upload] END ---")
        }
    }

    /**
     * Updates the device status for a user on GitHub (Async).
     *
     * @param deviceUpdateDto Data containing the username and new device configuration map.
     */
    @PostMapping("/update-device-status")
    suspend fun postDeviceStatusDto(
        @RequestBody deviceUpdateDto: PostDeviceStatusDto
    ){
        logger.info("--- [POST /update-device-status] START (user: {}) ---", deviceUpdateDto.username)
        logger.info("Received device update request for user: ${deviceUpdateDto.username}")

        val response = deviceService.repositoryDeviceController(
            DeviceActions.SET, deviceUpdateDto.username, deviceUpdateDto.deviceMap
        ).await()
        if (response.statusCode == HttpStatus.OK){
            logger.info("Device update successful for user: ${deviceUpdateDto.username}")
        } else {
            logger.error("Device update failed for user: ${deviceUpdateDto.username} \n Response: ${response.body} \n -with status code: ${response.statusCode}")
        }
        logger.info("--- [POST /update-device-status] END ---")
    }

    /**
     * Fetches current device statuses for a user from GitHub storage (Async).
     *
     * @param deviceUpdateDto Data containing the username.
     * @return A map of device identifiers to [DeviceDto] objects.
     */
    @OptIn(ExperimentalEncodingApi::class)
    @PostMapping("/get-device-status")
    suspend fun fetchDeviceStatuses(
        @RequestBody deviceUpdateDto: PostDeviceStatusDto
    ): Map<String, DeviceDto>? {
        logger.info("--- [POST /get-device-status] START (user: {}) ---", deviceUpdateDto.username)
        logger.info("Received device update request for user: ${deviceUpdateDto.username}")

        val response = deviceService.repositoryDeviceController(
            DeviceActions.GET, deviceUpdateDto.username, deviceUpdateDto.deviceMap
        ).await()

        if (response.statusCode == HttpStatus.OK) {
            val responseBody = response.body ?: "{}"
            val parsed = Json.parseToJsonElement(responseBody) as? JsonObject
            val base64Content = parsed?.get("content")?.jsonPrimitive?.content
            var responseString: String = ""

            try {
                if (base64Content != null) {
                    val cleanedBase64 = base64Content.replace("\n", "").replace("\r", "")
                    val decodedBytes = Base64.decode(cleanedBase64)
                    responseString = String(decodedBytes, Charsets.UTF_8)
                } else {
                    responseString = responseBody
                }
            } catch(e: Exception) {
                logger.error("Response error ${e.message}")
            }
            logger.info("Device update successful for user: ${deviceUpdateDto.username}")

            logger.info("[RESPONSE][][][][][][][][][][][][][][][][][][][][][][][][][][][][START]")
            val decod = Json.decodeFromString<Map<String, DeviceDto>>(responseString)
            logger.info(responseString)
            logger.info("[RESPONSE][][][][][][][][][][][][][][][][][][][][][][][][][][][][END]")
            logger.info("[DECOD][][][][][][][][][][][][][][][][][][][][][][][][][][][][][][][]")
            println(decod)
            logger.info("--- [POST /get-device-status] END ---")
            return decod
        } else {
            logger.error("Device update failed for user: ${deviceUpdateDto.username} \n Response: ${response.body} \n -with status code: ${response.statusCode}")
            logger.info("--- [POST /get-device-status] END ---")
            return emptyMap()
        }
    }

    /**
     * Updates/Edits an existing file on GitHub.
     *
     * @param file The new file content.
     * @param fileName The name of the file to update.
     * @param username The owner of the file.
     * @param authHeader The Bearer token for authentication.
     * @return Success or error message.
     */
    @PostMapping("/edit")
    fun updateCodeSnippet(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("filename") fileName: String,
        @RequestParam("username") username: String,
        @RequestHeader("Authorization") authHeader: String
    ): String {
        logger.info("--- [POST /edit] START (user: {}, file: {}) ---", username, fileName)
        val token = authHeader.removePrefix("Bearer ")
        val claims = userService.validateToken(token) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        if (claims.subject != username) throw ResponseStatusException(HttpStatus.FORBIDDEN)

        return try {
            simpleService.sendEdit(username, fileName, file)
            "File updated successfully"
        } catch (e: Exception) {
            logger.error("Edit Error: {}", e.message)
            "Error updating file: ${e.message}"
        } finally {
            logger.info("--- [POST /edit] END ---")
        }
    }

}