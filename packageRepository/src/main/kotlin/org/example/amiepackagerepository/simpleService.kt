package org.example.amiepackagerepository

import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.FileList
import com.google.api.services.drive.model.File as DriveFile
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.client.RestClient
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.IOException
import java.io.FileNotFoundException
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.CompletableFuture
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import kotlinx.serialization.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.decodeFromString
import org.springframework.http.ResponseEntity

/**
 * Common interface for items retrieved from different repository types.
 */
interface RepositoryItem {
	val name: String
	val id: String?
	val downloadUrl: String?
}

@Serializable
data class GithubSearchable(
	val lang: String,
	val url: String,
){
}
data class GithubItem(
	override val name: String,
	override val downloadUrl: String? = null,
	override val id: String? = null,
	val type: String = "file"
) : RepositoryItem

data class GithubItemMetadata(
	override val name: String,
	override val downloadUrl: String? = null,
	override val id: String? = null,
	val type: String = "file",
	val endComp: String? = null,
) : RepositoryItem


data class GoogleDriveItem(
	override val name: String,
	override val id: String,
	override val downloadUrl: String? = null
) : RepositoryItem

/**
 * Internal DTO for GitHub API response items.
 */
data class GithubSearchResponse(
	@JsonProperty("total_count") val totalCount: Int,
	@JsonProperty("incomplete_results") val incompleteResults: Boolean,
	val items: List<GithubSearchItem>
)

data class GithubContentResponse(
	val name: String,
	val path: String,
	val sha: String,
	val size: Long,
	val url: String,
	@JsonProperty("html_url") val htmlUrl: String,
	@JsonProperty("download_url") val downloadUrl: String?,
	val type: String,
	val content: String? = null,
	val encoding: String? = null
)

data class GithubSearchItem(
	val name: String,
	val path: String,
	val sha: String,
	@JsonProperty("html_url") val htmlUrl: String
)

data class CreateRepoRequest(
	val name: String,
	val description: String?,
	val private: Boolean
)

@Serializable
data class EndpointDto(
	@SerialName("retail_name") val retailName: String,
	@SerialName("descriptive_name") val descriptiveName: String,
)
/**
 * Provides functionality to query, list, and stream files directly from:
 * personal and shared Google Drives, and now GitHub.
 */
@Service
@Suppress("NewApi")
class SimpleService {
	private val logger = LoggerFactory.getLogger(SimpleService::class.java)
	private val json = Json { ignoreUnknownKeys = true }
	private val restClient = RestClient.builder()
		.requestFactory(org.springframework.http.client.SimpleClientHttpRequestFactory().apply {
			setConnectTimeout(5000)
			setReadTimeout(5000)
		})
		.build()

	fun listFilesGithub(): List<GithubItem> {
		val githubToken = System.getenv("GITHUB_TOKEN")?.trim()
		if (githubToken != null) {
			val safeToken = if (githubToken.length > 8) "${githubToken.take(4)}...${githubToken.takeLast(4)}" else "****"
			println("DEBUG: Using GITHUB_TOKEN (Length: ${githubToken.length}): $safeToken")
		} else {
			println("DEBUG: GITHUB_TOKEN is NULL in environment")
		}

		val repoOwner = "samging"
		val repoName = "codeRepository"
		val path = "uploads"
		val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$path"

		return try {
			val response = restClient.get()
				.uri(url)
				.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.retrieve()
				.onStatus({ it.value() == 404 }, { _, _ -> 
					// Handle 404 as empty directory
					throw IOException("Directory not found (likely empty)")
				})
				.body(object : ParameterizedTypeReference<List<GithubContentResponse>>() {})

			response?.map {
				GithubItem(
					name = it.name,
					downloadUrl = it.downloadUrl ?: "",
					type = it.type
				)
			} ?: emptyList()
		} catch (e: FileNotFoundException) {
			println("GitHub: Path '$path' not found. This usually means the folder is empty.")
			emptyList()
		} catch (e: Exception) {
			println("GitHub List Error: ${e.message}")
			emptyList()
		}
	}

	/**
	 * Recursively lists all files in a user's upload directory.
	 */
	fun listUserPackages(username: String): List<GithubContentResponse> {
		val githubToken = System.getenv("GITHUB_TOKEN")?.trim()
		val repoOwner = "samging"
		val repoName = "codeRepository"
		val rootPath = "uploads/$username"
		
		val allFiles = mutableListOf<GithubContentResponse>()
		
		fun walk(path: String) {
			val encodedPath = path.split("/").joinToString("/") { 
				java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") 
			}
			val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$encodedPath"
			try {
				val response = restClient.get()
					.uri(url)
					.header("Authorization", "Bearer ${githubToken ?: ""}")
					.header("Accept", "application/vnd.github+json")
					.header("X-GitHub-Api-Version", "2022-11-28")
					.retrieve()
					.body(object : ParameterizedTypeReference<List<GithubContentResponse>>() {})
				
				response?.forEach { item ->
					if (item.type == "dir") {
						walk(item.path)
					} else if (item.type == "file" && !item.name.endsWith(".md")) {
						allFiles.add(item)
					}
				}
			} catch (e: Exception) {
				println("DEBUG: Error walking path $path: ${e.message}")
			}
		}
		
		walk(rootPath)
		return allFiles
	}

	fun queryFilesGithub(query: String = ""): GithubContentResponse? {
		val githubToken = System.getenv("GITHUB_TOKEN")?.trim()
		val repoOwner = "samging"
		val repoName = "codeRepository"
		val path = "uploads"

		// Parse the query string (e.g., "essay1.pdf") into name and extension
		val fileParts = query.trim().split(".")
		val fileName = fileParts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "essay1"
		val fileExtension = fileParts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "pdf"

		val fullFileName = "$fileName.$fileExtension"
		val targetPath = "$path/$fullFileName"
		
		val encodedPath = targetPath.split("/").joinToString("/") { 
			java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") 
		}

		// GitHub Contents API URL for a specific file path
		val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$encodedPath"
		println("Generated URL: $url")

		return try {
			// Since we are targeting a single file, we can map to a DTO or a generic Map/Json node
			val responseEntity = restClient
				.get()
				.uri(url)
				.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.retrieve()
				.toEntity(GithubContentResponse::class.java)

			val fileInfo = responseEntity.body
			if (fileInfo != null) {
				println("File found: ${fileInfo.path} -> ${fileInfo.htmlUrl}")
				println("Download URL: ${fileInfo.downloadUrl}")
			}

			when (responseEntity.statusCode.value()) {
				200 -> println("Ok")
				401 -> println("Unauthorized")
				else -> println("Unexpected status: ${responseEntity.statusCode}")
			}
			fileInfo
		} catch (e: Exception) {
			println("Error: ${e.message} (File may not exist or path is invalid)")
			null
		}
	}

	/**
	 * This method searches both standard and Shared Drives, returning file names and their
	 * corresponding structural IDs.
	 * @param driveService The authorized [Drive] client instance used to execute the request.
	 * @return A newline-separated string listing the available file names, or a structural
	 */
	fun listFiles(driveService: Drive): String {
		val result: FileList = driveService.files().list()
			.setPageSize(10)
			.setFields("nextPageToken, files(id, name)")
			.setSupportsAllDrives(true)
			.setIncludeItemsFromAllDrives(true)
			.execute()

		val files: List<DriveFile>? = result.files

		if (files.isNullOrEmpty()) {
			return "Is null or empty. ${files.toString()}"
		}
		val fileListString = StringBuilder("Available Files: \n")
			for (file in files) {
				fileListString.append(file.name).append("\n")
			}
		return fileListString.toString()
	}

	fun createUserDashboard(rootRepo: String = "codeRepository", username: String) {
		if (username.isEmpty()) return

		val githubToken = System.getenv("GITHUB_TOKEN")?.trim()
		val repoOwner = "samging"
		
		val rawPath = "uploads/$username/README.md"
		val encodedPath = rawPath.split("/").joinToString("/") { 
			java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") 
		}
		val url = "https://api.github.com/repos/$repoOwner/$rootRepo/contents/$encodedPath"

		CompletableFuture.runAsync {
			try {
				val checkResponse = restClient.get()
					.uri(url)
					.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
					.header("Accept", "application/vnd.github+json")
					.header("X-GitHub-Api-Version", "2022-11-28")
					.retrieve()
					.onStatus({ it.value() == 404 }, { _, _ -> /* Expected if new user */ })
					.toEntity(String::class.java)

				if (checkResponse.statusCode.is2xxSuccessful) {
					println("DEBUG: Dashboard for $username already exists. Skipping.")
					return@runAsync
				}
			} catch (e: Exception) {
				// 404 is expected here for new users
			}

			val contentBase64 = Base64.getEncoder().encodeToString("# Dashboard for $username".toByteArray())
			val body = mapOf(
				"message" to "Create dashboard for $username",
				"content" to contentBase64
			)

			try {
				restClient.put()
					.uri(url)
					.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
					.header("Accept", "application/vnd.github+json")
					.header("X-GitHub-Api-Version", "2022-11-28")
					.body(body)
					.retrieve()
					.toBodilessEntity()

				println("GitHub User Dashboard Created Successfully for $username!")
			} catch (e: Exception) {
				println("DEBUG: GitHub dashboard creation failed: ${e.message}")
			}
		}
	}

	fun postEndpoints() {
		val githubToken = System.getenv("GITHUB_TOKEN")?.trim()
		if (githubToken.isNullOrBlank()) {
			logger.error("GITHUB_TOKEN is missing. Cannot post endpoints.")
			return
		}

		val repoOwner = "samging"
		val repoName = "codeRepository"
		val path = "repositoryInformations"
		val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$path"

		val encodedJson = json.encodeToJsonElement(listOf(
			EndpointDto("Arduino Uno", "Arduino uno more descriptive"),
			EndpointDto("Arduino Uno2", "Arduino uno more descriptive"),
			EndpointDto("Arduino Uno3", "Arduino uno more descriptive")
		))
		val file = File("endpoints.json")
		file.writeText(encodedJson.toString())

		try {
			if (!file.exists()) {
				logger.error("endpoints.json not found")
				return
			}
			val contentBase64 = Base64.getEncoder().encodeToString(file.readBytes())
			val existingSha = fetchFileSha(url, githubToken)

			val body = mutableMapOf(
				"message" to "endpoints.json - search for compatible device metrics",
				"content" to contentBase64
			)

			if (existingSha != null) body["sha"] = existingSha

			restClient.put()
				.uri(url)
				.header("Authorization", "Bearer $githubToken")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.body(body)
				.retrieve()
				.toBodilessEntity()
			
			logger.info("Endpoints uploaded successfully to GitHub")
		} catch (e: Exception) {
			logger.error("GitHub API error: ${e.message}")
		}
	}

	fun fetchEndpoints(): Map<String, String> {
		val githubToken = System.getenv("GITHUB_TOKEN")?.trim()
		val repoOwner = "samging"
		val repoName = "codeRepository"
		val path = "repositoryInformations"
		val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$path"

		try {
			val response = restClient.get()
				.uri(url)
				.header("Authorization", "Bearer ${githubToken ?: ""}")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.retrieve()
				.body(GithubContentResponse::class.java)

			val encodedContent = response?.content?.replace("\n", "") ?: ""
			val decodedContent = String(Base64.getDecoder().decode(encodedContent))
			
			// Use the class-level 'json' (Kotlinx Serialization) to handle @SerialName
			return try {
				val list = json.decodeFromString<List<EndpointDto>>(decodedContent)
				list.associate { it.retailName to it.descriptiveName }
			} catch (e: Exception) {
				logger.warn("Failed to parse as List<EndpointDto>, trying Map<String, String>: ${e.message}")
				try {
					json.decodeFromString<Map<String, String>>(decodedContent)
				} catch (e2: Exception) {
					logger.error("Failed to parse endpoints JSON: ${e2.message}")
					emptyMap()
				}
			}
		} catch (e: Exception) {
			logger.error("Error fetching endpoints: ${e.message}")
			return emptyMap()
		}
	}

	/**
	 * The method queries all non-trashed files matching the provided name. If multiple files
	 * match, it defaults to downloading the first match discovered.
	 * @param driveService The authorized [Drive] client instance used to execute the request.
	 * @param fileName The exact name string of the file targeted for download.
	 * @param savePath The local [File] destination target where data will be written.
	 * @throws IOException If the file does not exist on Google Drive, or if a local I/O error occurs.
	 */
	fun downloadFile(driveService: Drive, fileName: String, savePath: File) {
		val outputStream: OutputStream = FileOutputStream(savePath)

		val result = driveService.files().list()
			.setQ("name = '$fileName' and trashed = false")
			.setSpaces("drive")
			.setFields("files(id, name)")
			.setSupportsAllDrives(true)
			.setIncludeItemsFromAllDrives(true)
			.execute()

		val files = result.files
		if (files.isNullOrEmpty()) {
			throw IOException("File not found on Google Drive: $fileName")
		}
		val fileId = files[0].id

		FileOutputStream(savePath).use { stream ->
			driveService.files().get(fileId)
				.executeAndDownloadTo(stream)
		}
		outputStream.close()
	}

	fun uploadFile(username: String,
				   progLanguage: String,
				   file: MultipartFile) {
		val githubToken = System.getenv("GITHUB_TOKEN")?.trim()
		
		val repoOwner = "samging"
		val repoName = "codeRepository"
		val fileName = file.originalFilename ?: "unnamed_file"
		
		val safeUsername = URLEncoder.encode(username.trim(), "UTF-8").replace("+", "%20")
		val safeLang = URLEncoder.encode(progLanguage.trim(), "UTF-8").replace("+", "%20")
		val safeFileName = URLEncoder.encode(fileName.trim(), "UTF-8").replace("+", "%20")
		
		val path = if (username.isNotBlank()) "uploads/$safeUsername/$safeLang/$safeFileName" else "uploads/$safeLang/$safeFileName"
		val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$path"
		val contentBase64 = Base64.getEncoder().encodeToString(file.bytes)

		// --- SHA Supplier: Isolated retrieval of existing file's SHA ---
		var existingSha: String? = null
		try {
			logger.info("GITHUB: Checking for existing file to get SHA at path: {}", path)
			val checkResponse = restClient.get()
				.uri(url)
				.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.retrieve()
				.body(GithubContentResponse::class.java)
			
			existingSha = checkResponse?.sha
			if (existingSha != null) {
				logger.info("GITHUB: File exists, retrieved SHA: {}", existingSha)
			}

			val dirTreeUrl: String = "https://api.github.com/repos/$repoOwner/$repoName/contents/uploads/searchables.json"
			
			fun patchSearchTree(body: String, sha: String? = null): ResponseEntity<GithubContentResponse> {
				logger.info("GITHUB: Attempting to update search tree at {}", dirTreeUrl)
				val searchBody = mutableMapOf(
					"message" to "Update searchables for $path",
					"content" to Base64.getEncoder().encodeToString(body.toByteArray())
				)
				if (sha != null) searchBody["sha"] = sha

				return restClient.put() // Using PUT as per GitHub API for creating/updating
					.uri(dirTreeUrl)
					.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
					.header("Accept", "application/vnd.github+json")
					.header("X-GitHub-Api-Version", "2022-11-28")
					.body(searchBody)
					.retrieve()
					.toEntity(GithubContentResponse::class.java)
			}

			val encodeToBase = Base64.getEncoder().encodeToString(file.bytes)
			val contentBody = mutableMapOf(
				"message" to "Update $path via Amie Repository for $username",
				"content" to encodeToBase
			)

			fun MutableMap<String, String>.updateJson(updateApply: GithubSearchable?.() -> Unit): String {
				val dummySearchable = GithubSearchable(url = url, lang = progLanguage)
				dummySearchable.updateApply()
				return json.encodeToString(dummySearchable)
			}

			var searchablesSha: String? = null
			try {
				val searchCheck = restClient.get()
					.uri(dirTreeUrl)
					.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
					.header("Accept", "application/vnd.github+json")
					.header("X-GitHub-Api-Version", "2022-11-28")
					.retrieve()
					.body(GithubContentResponse::class.java)
				searchablesSha = searchCheck?.sha
				logger.info("GITHUB: Existing searchables.json found, SHA: {}", searchablesSha)
			} catch (e: Exception) {
				logger.info("GITHUB: searchables.json not found (will create new)")
			}

			val patchResponse = patchSearchTree(contentBody.updateJson{}, searchablesSha)

			if (patchResponse.statusCode.is4xxClientError) {
				val createSearch = restClient.post().uri(dirTreeUrl)
					.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
					.header("Accept", "application/vnd.github+json")
					.header("X-GitHub-Api-Version", "2022-11-28")
					.body(contentBody).retrieve()
					.toEntity(GithubContentResponse::class.java)

				if (createSearch.statusCode.is2xxSuccessful) {
					patchSearchTree(contentBody.updateJson {
						GithubSearchable(url = url, lang = progLanguage)
					}, searchablesSha)
				}
			}
		} catch (e: Exception) {
			logger.warn("GITHUB: Experimental searchables logic failed (non-fatal): {}", e.message)
		}

		// --- Final Upload Logic ---
		val body = mutableMapOf(
			"message" to "Upload $fileName via Amie Repository for $username ($progLanguage)",
			"content" to contentBase64
		)
		
		if (existingSha != null) {
			body["sha"] = existingSha
		}

		try {
			logger.info("GITHUB: Executing final PUT request for '{}'", path)
			val response = restClient.put()
				.uri(url)
				.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.body(body)
				.retrieve()
				.toEntity(String::class.java)

			logger.info("GITHUB: Upload successful for user '{}' at path '{}'! Status: {}", username, path, response.statusCode.value())
		} catch (e: Exception) {
			logger.error("GITHUB: CRITICAL upload error at path {}: {}", path, e.message)
			throw e
		}
	}
	private fun fetchFileSha(url: String, githubToken: String?): String? {
		if (githubToken.isNullOrBlank()) return null
		return try {
			val res = restClient.get().uri(url)
				.header("Authorization", "Bearer $githubToken")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.retrieve()
				.body(object : ParameterizedTypeReference<Map<String, Any>>() {})
			res?.get("sha") as? String
		} catch(e: Exception) {
			null
		}
	}

	fun writeError(username: String, error: String, message: String) {
		val githubToken = System.getenv("GITHUB_TOKEN")?.trim()
		val repoOwner = "samging"
		val repoName = "codeRepository"
		val path = "uploads/$username/errors.md"
		val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$path"
		val contentBase64 = Base64.getEncoder().encodeToString("[$error]: $message".toByteArray())
		val existingSha = fetchFileSha(url, githubToken)

		val body = mutableMapOf(
			"message" to "Update $path via Amie Repository for $username",
			"content" to contentBase64
		).apply {
			if (existingSha != null) {
				put("sha", existingSha)
			}
		}

		restClient.put()
			.uri(url)
			.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.retrieve()
			.toBodilessEntity()
	}

	fun sendEdit(username: String, fileName: String, updateFile: MultipartFile) {
		val githubToken = System.getenv("GITHUB_TOKEN")?.trim()
		val repoOwner = "samging"
		val repoName = "codeRepository"
		
		val path = if (username.isNotBlank()) "uploads/$username/$fileName" else "uploads/$fileName"
		
		// Properly encode the path segments
		val encodedPath = path.split("/").joinToString("/") { 
			java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") 
		}
		val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$encodedPath"

		try {
			val currentFile = restClient.get()
				.uri(url)
				.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.retrieve()
				.body(GithubContentResponse::class.java) ?: throw RuntimeException("File not found for update: $path")

			val contentBase64 = Base64.getEncoder().encodeToString(updateFile.bytes)
			val body = mapOf(
				"message" to "Update $fileName via Amie Repository for $username",
				"content" to contentBase64,
				"sha" to currentFile.sha
			)

			val response = restClient.put()
				.uri(url)
				.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.body(body)
				.retrieve()
				.toEntity(String::class.java)

			println("GitHub Update Success for $username! Status: ${response.statusCode.value()}")
		} catch (e: Exception) {
			println("CRITICAL: GitHub API Update Error: ${e.message}")
			throw e
		}
	}

	fun uploadFileData(username: String, fileName: String, data: ByteArray) {
		val githubToken = System.getenv("GITHUB_TOKEN")?.trim()
		val repoOwner = "samging"
		val repoName = "codeRepository"
		val path = "uploads/$username/$fileName"
		val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$path"
		val contentBase64 = Base64.getEncoder().encodeToString(data)

		var existingSha: String? = null
		try {
			val checkResponse = restClient.get()
				.uri(url)
				.header("Authorization", "Bearer ${githubToken ?: ""}")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.retrieve()
				.body(GithubContentResponse::class.java)
			existingSha = checkResponse?.sha
		} catch (e: Exception) {}

		val body = mutableMapOf(
			"message" to "Update $fileName via Amie Device Manager",
			"content" to contentBase64
		)
		if (existingSha != null) body["sha"] = existingSha

		try {
			restClient.put()
				.uri(url)
				.header("Authorization", "Bearer ${githubToken ?: ""}")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.body(body)
				.retrieve()
				.toBodilessEntity()
		} catch (e: Exception) {
			println("DEBUG: GitHub data sync failed: ${e.message}")
		}
	}

}
