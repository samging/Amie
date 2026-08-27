package org.example.amiepackagerepository

import com.google.api.services.drive.Drive
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import java.io.File
import io.jsonwebtoken.Claims
import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.concurrent.CompletableFuture
import org.springframework.http.ResponseEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


/**
 * REST Controller providing HTTP endpoints to interact with Google Drive and GitHub.
 * @property driveService The authorized Google Drive client.
 * @property simpleService The business logic service handling Drive and GitHub operations.
 * @property userService The business logic service handling user operations.
 */

@Serializable
data class sendDeviceStatusDto(
	val action: DeviceActions,
	val username: String,
	val deviceMap: Map<String, DeviceDto>
)

@CrossOrigin(origins = ["http://localhost:8081"])
@RestController
class SimpleController(
	private val driveService: Drive,
	private val simpleService: SimpleService,
	private val userService: UserService,
    private val deviceService: DeviceService
) {
	private val logger = org.slf4j.LoggerFactory.getLogger(SimpleController::class.java)
	/**
	 * Retrieves a formatted list of all files present in the Google Drive.
	 * @return A string representation/log of the files found in the drive.
	 */
	@GetMapping("/list-disk")
	fun getFiles(): String {
		logger.info("--- [GET /list-disk] START ---")
		return simpleService.listFiles(driveService).also { logger.info("--- [GET /list-disk] END ---") }
	}

	@GetMapping("/fetch-endpoints")
	fun fetchEndpoints(): Map<String, String>? {
		logger.info("--- [GET /fetch-endpoints] START ---")
		return simpleService.fetchEndpoints().also { logger.info("--- [GET /fetch-endpoints] END ---") }
	}

	@PostMapping("/post-endpoints")
	fun postEndpoints() {
		logger.info("--- [POST /post-endpoints] START ---")
		simpleService.postEndpoints()
		logger.info("--- [POST /post-endpoints] END ---")
	}

	/**
	 * Retrieves a list of all files present in the GitHub repository.
	 * @return A list of items found in the GitHub repository.
	 */
	@GetMapping("/list-github")
	fun getGithubFiles(): List<GithubItem> {
		logger.info("--- [GET /list-github] START ---")
		return simpleService.listFilesGithub().also { logger.info("--- [GET /list-github] END ---") }
	}

	/**
	 * @param fileName The exact name of the file to retrieve from Google Drive.
	 * @return A status message indicating whether the download succeeded or failed,
	 */
	@GetMapping("/download")
	fun downloadFile(@RequestParam fileName: String = "welcome-message"): String {
		logger.info("--- [GET /download] START (fileName: {}) ---", fileName)
		val userHome = System.getProperty("user.home")
		val destinationFile = File(userHome, "Downloads/amiePackagesDownload/$fileName")

		return try {
			simpleService.downloadFile(driveService, fileName, destinationFile)
			"Success! File downloaded to ${destinationFile.absolutePath}"
		} catch (e: Exception) {
			logger.error("Download Error: {}", e.message)
			"Failed to download file: ${e.message}"
		} finally {
			logger.info("--- [GET /download] END ---")
		}
	}

	@PostMapping("/upload")
	fun uploadFile(
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
		} catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
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
	suspend fun updateDeviceStatus(
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
	suspend fun getDeviceStatus(
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
					val decodedBytes = Base64.Default.decode(cleanedBase64)
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

	@GetMapping("list-github-metadata")
	fun listGithubMetadata(): Map<String, GithubItemMetadata> {
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

	@PostMapping("/edit")
	fun editFile(
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

	@GetMapping("/query")
	fun queryFiles(@RequestParam query: String): Any {
		logger.info("--- [GET /query] START (query: {}) ---", query)
		return simpleService.queryFilesGithub(query) ?: emptyList<Any>().also { logger.info("--- [GET /query] END ---") }
	}

	@GetMapping("/user-packages")
	fun getUserPackages(@RequestParam username: String): List<GithubContentResponse> {
		logger.info("--- [GET /user-packages] START (user: {}) ---", username)
		return simpleService.listUserPackages(username).also { logger.info("--- [GET /user-packages] END ---") }
	}

	@PostMapping("/device-repository-controller")
	fun deviceRepositoryController(@RequestParam action: DeviceActions,
	                               @RequestParam username: String = "",
	                               @RequestBody deviceMap: Map<String, DeviceDto>
	): CompletableFuture<ResponseEntity<String>> {
		logger.info("--- [POST /device-repository-controller] START (action: {}, user: {}) ---", action, username)
        println("DEBUG: Received device repository request: action=$action, username=$username, devices=${deviceMap.size}")

		return deviceService.repositoryDeviceController(action, username, deviceMap).exceptionally { ex ->
            val cause = ex.cause ?: ex
            val status = when (cause) {
                is IllegalArgumentException -> HttpStatus.BAD_REQUEST
                is NoSuchElementException -> HttpStatus.NOT_FOUND
                is SecurityException -> HttpStatus.FORBIDDEN
                is IllegalStateException -> HttpStatus.CONFLICT
                is UnsupportedOperationException -> HttpStatus.NOT_IMPLEMENTED
                is org.springframework.web.client.HttpClientErrorException -> {
                    HttpStatus.valueOf(cause.statusCode.value())
                }
                else -> HttpStatus.INTERNAL_SERVER_ERROR
            }

            val errorMessage = when {
                cause is org.springframework.web.client.HttpClientErrorException -> {
                    "Repository Provider Error: ${cause.responseBodyAsString}"
                }
                else -> cause.message ?: "An unexpected error occurred"
            }

			logger.error("Device Repository Action Failed: {}", errorMessage)
			ResponseEntity.status(status).body("Action Failed: $errorMessage")
		}.also { logger.info("--- [POST /device-repository-controller] END ---") }
	}

	@PostMapping("/register")
	fun register(@RequestBody registerRequest: Map<String, String>): String {
		val username = registerRequest["username"] ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Username required")
		logger.info("--- [POST /register] START (user: {}) ---", username)
		val password = registerRequest["password"] ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Password required")

		if (userService.userExists(username)) {
			logger.warn("Register Failed: Username '{}' already exists", username)
			throw ResponseStatusException(HttpStatus.CONFLICT, "Username is already assigned to different account")
		}

		userService.createUser(username, password)
		simpleService.createUserDashboard(username = username)

		logger.info("User registered successfully: {}", username)
		logger.info("--- [POST /register] END ---")
		return "User registered successfully"
	}

	@PostMapping("/login")
	fun login(@RequestBody loginRequest: Map<String, String>): Map<String, String> {
		val username = loginRequest["username"] ?: "guest"
		logger.info("--- [POST /login] START (user: {}) ---", username)
		val password = loginRequest["password"] ?: ""
		
		println("DEBUG: Login attempt for user: $username")

		val token = userService.loginAsUser(username, password)
            ?: if (username.startsWith("guest-") && password == "") {
				println("GUEST TOKEN GENERATED")
				userService.grantGuestToken(username)
			} else {
				logger.warn("Login Failed: Invalid credentials for '{}'", username)
				throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
			}

		println("DEBUG: Login successful for $username, generating dashboard...")
		try {
			userService.grantUserToken(username)
			println("USER TOKEN GENERATED")
			simpleService.createUserDashboard(username = username)
		} catch (e: Exception) {
			logger.error("Dashboard creation non-fatal error: {}", e.message)
		}
		
		println("DEBUG: Returning token for $username")
		logger.info("--- [POST /login] END ---")
        return mapOf("token" to token)
	}

	@PostMapping("/handle-login-error")
	fun handleError(@RequestBody request: Map<String, String>) {
        val error = request["error"] ?: "Unknown"
        val username = request["username"] ?: "unknown"
		logger.info("--- [POST /handle-login-error] (user: {}, error: {}) ---", username, error)
		simpleService.writeError(username, "LoginError", error)
	}

	@GetMapping("/dashboard")
	fun validateToken(@RequestHeader("Authorization") authHeader: String): String {
		logger.info("--- [GET /dashboard] START ---")
		val token = authHeader.removePrefix("Bearer ")

		val claims = userService.validateToken(token) 
            ?: run {
				logger.warn("Token validation failed")
				throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized or expired token")
			}
		val username = claims.subject
		val userId = (claims["userId"] as? Number)?.toLong() ?: -1L

		logger.info("Dashboard validation successful for user: {} (ID: {})", username, userId)
		logger.info("--- [GET /dashboard] END ---")
		return "Welcome to your dashboard, $username (ID: $userId)!"
	}

	@DeleteMapping("/user")
	fun deleteUser(@RequestParam username: String, @RequestParam password: String) {
		logger.info("--- [DELETE /user] (user: {}) ---", username)
		userService.deleteUser(username, password)
	}

    @PostMapping("/device-status")
    fun saveDeviceStatus(
        @RequestParam username: String,
        @RequestBody deviceMap: Map<String, DeviceDto>
    ): String {
		logger.info("--- [POST /device-status] (user: {}) ---", username)
        deviceService.saveDeviceStatuses(username, deviceMap)
        return "Device statuses saved successfully"
    }

    @GetMapping("/device-status")
    fun getDeviceStatus(@RequestParam username: String): List<DeviceStatus> {
		logger.info("--- [GET /device-status] (user: {}) ---", username)
        return deviceService.getDeviceStatuses(username)
    }
}
