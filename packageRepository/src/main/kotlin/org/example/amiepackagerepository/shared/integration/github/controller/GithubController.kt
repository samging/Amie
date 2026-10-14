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
import org.example.amiepackagerepository.shared.packages.AmiePackage
import org.example.amiepackagerepository.shared.packages.JarInspector
import org.example.amiepackagerepository.shared.packages.JarMirrorService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
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
@CrossOrigin(origins = ["\${amie.cors.allowed-origins}"])
@RestController
class GithubController(
    private val driveService: Drive,
    private val simpleService: GithubService,
    private val userService: UserService,
    private val deviceService: TransactionalStatusRepository,
    private val jarInspector: JarInspector,
    private val jarMirror: JarMirrorService
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
     * Uploads a package to GitHub. Validates the user's JWT token first. A `.jar` upload (or any upload
     * sent with `progLanguage=jar`, which is what the website's upload page sends) must be a valid Amie
     * package: a jar with a class exposing `run(String): List` and `close()` (see [AmiePackage]); it is
     * then also mirrored to the S3 bucket for the downloads page. Other file types (for example the
     * desktop app's ONNX models) are stored as before.
     *
     * @param file The file to upload.
     * @param progLanguage The programming language of the snippet (for directory categorization).
     * @param username The owner of the file.
     * @param authHeader The "Authorization" Bearer token.
     * @return 200 with a message, 400 with the reasons a jar was refused.
     * @throws ResponseStatusException 401 if unauthorized, 403 if forbidden.
     */
    @PostMapping("/upload")
    fun uploadCodeSnippet(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(value = "progLanguage", defaultValue = "unknown") progLanguage: String,
        @RequestParam("username") username: String,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<String> {
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

        val mustBeJar = progLanguage.equals("jar", ignoreCase = true) || JarInspector.isJarName(file.originalFilename)
        val bytes = if (mustBeJar) file.bytes else null
        if (bytes != null) {
            val inspection = jarInspector.inspect(file.originalFilename, bytes)
            if (!inspection.valid) {
                logger.info("CONTROLLER: Refused jar '{}' from '{}': {}", file.originalFilename, username, inspection.problems)
                return ResponseEntity.badRequest().body(inspection.problems.joinToString("; "))
            }
            logger.info("CONTROLLER: Jar '{}' exposes package classes {}", file.originalFilename, inspection.entryClasses)
        }

        return try {
            simpleService.uploadFile(username, progLanguage, file)
            if (bytes != null) jarMirror.mirror(username, file.originalFilename ?: "package.jar", bytes)
            userService.logActivity(username, "UPLOAD_PACKAGE", "Uploaded package: ${file.originalFilename}")
            logger.info("CONTROLLER: Upload successful for user '{}', file '{}'", username, file.originalFilename)
            ResponseEntity.ok("File uploaded successfully")
        } catch (e: GoogleJsonResponseException) {
            logger.error("CONTROLLER: Google API Error: {}", e.details?.message ?: e.message)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("The package could not be stored")
        } catch (e: Exception) {
            logger.error("CONTROLLER: Unexpected error during upload: {}", e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("The package could not be stored")
        } finally {
            logger.info("--- [POST /upload] END ---")
        }
    }

    /**
     * Removes a file the user uploaded, the reverse of /upload: the desktop app's "remove publish".
     *
     * @param fileName The name of the file to remove.
     * @param progLanguage The language folder it was uploaded under.
     * @param username The owner of the file; must be the token's subject.
     * @return 200 with "removed" or "not there", 401 or 403 otherwise.
     */
    @DeleteMapping("/upload")
    fun removeCodeSnippet(
        @RequestParam("filename") fileName: String,
        @RequestParam(value = "progLanguage", defaultValue = "unknown") progLanguage: String,
        @RequestParam("username") username: String,
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<String> {
        logger.info("--- [DELETE /upload] START (user: {}, file: {}, lang: {}) ---", username, fileName, progLanguage)
        val token = authHeader.removePrefix("Bearer ")
        val claims = userService.validateToken(token) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        if (claims.subject != username) throw ResponseStatusException(HttpStatus.FORBIDDEN)
        return try {
            val removed = simpleService.deleteFile(username, progLanguage, fileName)
            if (removed) userService.logActivity(username, "REMOVE_PACKAGE", "Removed package: $fileName")
            ResponseEntity.ok(if (removed) "removed" else "not there")
        } catch (e: Exception) {
            logger.error("CONTROLLER: Unexpected error during delete: {}", e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("The package could not be removed")
        } finally {
            logger.info("--- [DELETE /upload] END ---")
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
            userService.logActivity(deviceUpdateDto.username, "UPDATE_DEVICE_STATUS", "Updated device statuses")
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
            userService.logActivity(username, "EDIT_PACKAGE", "Edited package: $fileName")
            "File updated successfully"
        } catch (e: Exception) {
            logger.error("Edit Error: {}", e.message)
            "Error updating file: ${e.message}"
        } finally {
            logger.info("--- [POST /edit] END ---")
        }
    }

}