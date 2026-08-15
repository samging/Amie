package org.example.amiepackagerepository

import com.google.api.services.drive.Drive
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import java.io.File
import io.jsonwebtoken.Claims
import java.util.concurrent.CompletableFuture
import org.springframework.http.ResponseEntity

/**
 * REST Controller providing HTTP endpoints to interact with Google Drive and GitHub.
 * @property driveService The authorized Google Drive client.
 * @property simpleService The business logic service handling Drive and GitHub operations.
 * @property userService The business logic service handling user operations.
 */
@CrossOrigin(origins = ["http://localhost:8081"])
@RestController
class SimpleController(
	private val driveService: Drive,
	private val simpleService: SimpleService,
	private val userService: UserService,
    private val deviceService: DeviceService
) {

	/**
	 * Retrieves a formatted list of all files present in the Google Drive.
	 * @return A string representation/log of the files found in the drive.
	 */
	@GetMapping("/list-disk")
	fun getFiles(): String {
		return simpleService.listFiles(driveService)
	}

	/**
	 * Retrieves a list of all files present in the GitHub repository.
	 * @return A list of items found in the GitHub repository.
	 */
	@GetMapping("/list-github")
	fun getGithubFiles(): List<GithubItem> {
		return simpleService.listFilesGithub()
	}

	/**
	 * @param fileName The exact name of the file to retrieve from Google Drive.
	 * @return A status message indicating whether the download succeeded or failed,
	 */
	@GetMapping("/download")
	fun downloadFile(@RequestParam fileName: String = "welcome-message"): String {

		val userHome = System.getProperty("user.home")
		val destinationFile = File(userHome, "Downloads/amiePackagesDownload/$fileName")

		return try {
			simpleService.downloadFile(driveService, fileName, destinationFile)
			"Success! File downloaded to ${destinationFile.absolutePath}"
		} catch (e: Exception) {
			"Failed to download file: ${e.message}"
		}
	}

	@PostMapping("/upload")
	fun uploadFile(
		@RequestParam("file") file: MultipartFile,
		@RequestParam(value = "progLanguage", defaultValue = "unknown") progLanguage: String,
		@RequestParam("username") username: String,
		@RequestHeader("Authorization") authHeader: String
	): String {
		val token = authHeader.removePrefix("Bearer ")
		val claims = userService.validateToken(token) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
		if (claims.subject != username) throw ResponseStatusException(HttpStatus.FORBIDDEN)

		println("DEBUG: Upload request received - file: ${file.originalFilename}, progLanguage: $progLanguage, username: $username")
		return try {
			simpleService.uploadFile(username, progLanguage, file)
			"File uploaded successfully"
		} catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
			"Google API Error: ${e.details?.message ?: e.message}"
		} catch (e: Exception) {
			"Error uploading file: ${e.message}"
		}
	}

	@PostMapping("/edit")
	fun editFile(
		@RequestParam("file") file: MultipartFile,
		@RequestParam("filename") fileName: String,
		@RequestParam("username") username: String,
		@RequestHeader("Authorization") authHeader: String
	): String {
		val token = authHeader.removePrefix("Bearer ")
		val claims = userService.validateToken(token) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
		if (claims.subject != username) throw ResponseStatusException(HttpStatus.FORBIDDEN)

		return try {
			simpleService.sendEdit(username, fileName, file)
			"File updated successfully"
		} catch (e: Exception) {
			"Error updating file: ${e.message}"
		}
	}

	@GetMapping("/query")
	fun queryFiles(@RequestParam query: String): Any {
		return simpleService.queryFilesGithub(query) ?: emptyList<Any>()
	}

	@GetMapping("/user-packages")
	fun getUserPackages(@RequestParam username: String): List<GithubContentResponse> {
		return simpleService.listUserPackages(username)
	}

	@PostMapping("/device-repository-controller")
	fun deviceRepositoryController(@RequestParam action: DeviceActions,
	                               @RequestParam username: String = "",
	                               @RequestBody deviceMap: Map<String, DeviceDto>
	): CompletableFuture<ResponseEntity<String>> {
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

			ResponseEntity.status(status).body("Action Failed: $errorMessage")
		}
	}

	@PostMapping("/register")
	fun register(@RequestBody registerRequest: Map<String, String>): String {
		val username = registerRequest["username"] ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Username required")
		val password = registerRequest["password"] ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Password required")

		if (userService.userExists(username)) {
			throw ResponseStatusException(HttpStatus.CONFLICT, "Username is already assigned to different account")
		}

		userService.createUser(username, password)
		simpleService.createUserDashboard(username = username)

		return "User registered successfully"
	}

	@PostMapping("/login")
	fun login(@RequestBody loginRequest: Map<String, String>): Map<String, String> {
		val username = loginRequest["username"] ?: "guest"
		val password = loginRequest["password"] ?: ""
		
		println("DEBUG: Login attempt for user: $username")

		val token = userService.loginAsUser(username, password)
            ?: if (username.startsWith("guest-") && password == "") {
				userService.grantGuestToken(username)
			} else {
				throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
			}

		println("DEBUG: Login successful for $username, generating dashboard...")
		try {
			simpleService.createUserDashboard(username = username)
		} catch (e: Exception) {
			println("DEBUG: Dashboard creation non-fatal error: ${e.message}")
		}
		
		println("DEBUG: Returning token for $username")
        return mapOf("token" to token)
	}

	@PostMapping("/handle-login-error")
	fun handleError(@RequestBody request: Map<String, String>) {
        val error = request["error"] ?: "Unknown"
        val username = request["username"] ?: "unknown"
		simpleService.writeError(username, "LoginError", error)
	}

	@GetMapping("/dashboard")
	fun validateToken(@RequestHeader("Authorization") authHeader: String): String {
		val token = authHeader.removePrefix("Bearer ")

		val claims = userService.validateToken(token) 
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized or expired token")
		val username = claims.subject
		val userId = claims["userId", Long::class.java]

		return "Welcome to your dashboard, $username (ID: $userId)!"
	}

	@DeleteMapping("/user")
	fun deleteUser(@RequestParam username: String, @RequestParam password: String) {
		userService.deleteUser(username, password)
	}

    @PostMapping("/device-status")
    fun saveDeviceStatus(
        @RequestParam username: String,
        @RequestBody deviceMap: Map<String, DeviceDto>
    ): String {
        deviceService.saveDeviceStatuses(username, deviceMap)
        return "Device statuses saved successfully"
    }

    @GetMapping("/device-status")
    fun getDeviceStatus(@RequestParam username: String): List<DeviceStatus> {
        return deviceService.getDeviceStatuses(username)
    }
}
