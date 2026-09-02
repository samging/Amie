package org.example.amiepackagerepository.controllers.integrations.github

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.drive.Drive
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.amiepackagerepository.dto.sendDeviceStatusDto
import org.example.amiepackagerepository.service.GithubItem
import org.example.amiepackagerepository.service.GithubItemMetadata
import org.example.amiepackagerepository.service.SimpleService
import org.example.amiepackagerepository.service.UserService
import org.example.amiepackagerepository.transactionalMiddleware.DeviceActions
import org.example.amiepackagerepository.transactionalMiddleware.DeviceDto
import org.example.amiepackagerepository.transactionalMiddleware.TransactionalStatusRepository
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

@CrossOrigin(origins = ["http://localhost:8081"])
@RestController
class GithubController(
    private val driveService: Drive,
    private val simpleService: SimpleService,
    private val userService: UserService,
    private val deviceService: TransactionalStatusRepository
) {
    private val logger = LoggerFactory.getLogger(GithubController::class.java)

    /**
     * Retrieves a list of all files present in the GitHub repository.
     * @return A list of items found in the GitHub repository.
     */
    @GetMapping("/list-github")
    fun getRepositoryFiles(): List<GithubItem> {
        logger.info("--- [GET /list-github] START ---")
        return simpleService.listFilesGithub().also { logger.info("--- [GET /list-github] END ---") }
    }

    @GetMapping("list-github-metadata")
    fun getRepositoryMetadata(): Map<String, GithubItemMetadata> {
        logger.info("--- [GET /list-github-metadata] START ---")
        val githubItems = simpleService.listFilesGithub()
        return githubItems.mapIndexed { index, item ->
            index.toString() to GithubItemMetadata(
                name = item.name,
                downloadUrl = item.downloadUrl,
                id = item.id,
                type = item.type,
                endComp = item.name.endsWith(".c").toString()
            )
        }.toMap().also { logger.info("--- [GET /list-github-metadata] END ---") }
    }

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

    @PostMapping("/update-device-status")
    suspend fun postDeviceStatusDto(
        @RequestBody deviceUpdateDto: sendDeviceStatusDto
    ){
        logger.info("--- [POST /update-device-status] START (user: {}) ---", deviceUpdateDto.username)
        println("[][][][][][][][][][][][][][][][][][][][][][][][][][][][][][][][][][]")
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

    @OptIn(ExperimentalEncodingApi::class)
    @PostMapping("/get-device-status")
    suspend fun fetchDeviceStatuses(
        @RequestBody deviceUpdateDto: sendDeviceStatusDto
    ): Map<String, DeviceDto>? {
        logger.info("--- [POST /get-device-status] START (user: {}) ---", deviceUpdateDto.username)
        println("[G][][][][][][][][][][][][][][][][][][][][][][][][][][][][][][][][][]")
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

            println("[RESPONSE][][][][][][][][][][][][][][][][][][][][][][][][][][][][START]")
            val decod = Json.decodeFromString<Map<String, DeviceDto>>(responseString)
            println(responseString)
            println("[RESPONSE][][][][][][][][][][][][][][][][][][][][][][][][][][][][END]")
            println("[DECOD][][][][][][][][][][][][][][][][][][][][][][][][][][][][][][][]")
            println(decod)
            logger.info("--- [POST /get-device-status] END ---")
            return decod
        } else {
            logger.error("Device update failed for user: ${deviceUpdateDto.username} \n Response: ${response.body} \n -with status code: ${response.statusCode}")
            logger.info("--- [POST /get-device-status] END ---")
            return emptyMap()
        }
    }

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