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

@Serializable
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
		logger.info("--- [SimpleService: listFilesGithub] START ---")
		val githubToken = System.getenv("GITHUB_TOKEN")?.trim()
		if (githubToken != null) {
			val safeToken = if (githubToken.length > 8) "${githubToken.take(4)}...${githubToken.takeLast(4)}" else "****"
			logger.info("Using GITHUB_TOKEN (Length: {}): {}", githubToken.length, safeToken)
		} else {
			logger.warn("GITHUB_TOKEN is NULL in environment")
		}

		val repoOwner = "samging"
		val repoName = "codeRepository"
		val path = "uploads"
		val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$path"

		return try {
			logger.info("Calling GitHub contents API: {}", url)
			val response = restClient.get()
				.uri(url)
				.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.retrieve()
				.onStatus({ it.value() == 404 }, { _, _ -> 
					logger.warn("GitHub path not found (404): {}", path)
					throw IOException("Directory not found (likely empty)")
				})
				.body(object : ParameterizedTypeReference<List<GithubContentResponse>>() {})

			logger.info("GitHub API response success, found {} items", response?.size ?: 0)
			response?.map {
				GithubItem(
					name = it.name,
					downloadUrl = it.downloadUrl ?: "",
					type = it.type
				)
			} ?: emptyList()
		} catch (e: FileNotFoundException) {
			logger.info("GitHub: Path '{}' not found. This usually means the folder is empty.", path)
			emptyList()
		} catch (e: Exception) {
			logger.error("GitHub List Error: {}", e.message)
			emptyList()
		} finally {
			logger.info("--- [SimpleService: listFilesGithub] END ---")
		}
	}

	/**
	 * Recursively lists all files in a user's upload directory.
	 */
	fun listUserPackages(username: String): List<GithubContentResponse> {
		logger.info("--- [SimpleService: listUserPackages] START (user: {}) ---", username)
		val githubToken = System.getenv("GITHUB_TOKEN")?.trim()
		val repoOwner = "samging"
		val repoName = "codeRepository"
		val rootPath = "uploads/$username"
		
		val allFiles = mutableListOf<GithubContentResponse>()
		
		fun walk(path: String) {
			logger.info("Walking GitHub path: {}", path)
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
				
				logger.info("Walked path '{}' successfully, found {} items", path, response?.size ?: 0)
				response?.forEach { item ->
					if (item.type == "dir") {
						walk(item.path)
					} else if (item.type == "file" && !item.name.endsWith(".md")) {
						allFiles.add(item)
					}
				}
			} catch (e: Exception) {
				logger.error("Error walking path {}: {}", path, e.message)
			}
		}
		
		walk(rootPath)
		logger.info("Finished walk, total files found: {}", allFiles.size)
		logger.info("--- [SimpleService: listUserPackages] END ---")
		return allFiles
	}

	fun queryFilesGithub(query: String = ""): Any? {
		logger.info("--- [SimpleService: queryFilesGithub] START (query: {}) ---", query)
		val githubToken = System.getenv("GITHUB_TOKEN")?.trim()
		val repoOwner = "samging"
		val repoName = "codeRepository"

		// --- NEW: Extension-based Search (*ext) ---
		if (query.trim().startsWith("*")) {
			val extension = query.trim().removePrefix("*").lowercase()
			logger.info("GITHUB: Performing extension-based search for '.{}'", extension)
			
			val dirTreeUrl = "https://api.github.com/repos/$repoOwner/$repoName/contents/uploads/searchables.json"
			return try {
				logger.info("Fetching searchables.json from: {}", dirTreeUrl)
				val response = restClient.get()
					.uri(dirTreeUrl)
					.header("Authorization", "Bearer ${githubToken ?: ""}")
					.retrieve()
					.body(GithubContentResponse::class.java)
				
				val encodedContent = response?.content?.replace("\n", "")?.replace("\r", "") ?: ""
				val decodedContent = String(Base64.getDecoder().decode(encodedContent))
				
				val allSearchables = try {
					json.decodeFromString<List<GithubSearchable>>(decodedContent)
				} catch (e: Exception) {
					listOf(json.decodeFromString<GithubSearchable>(decodedContent))
				}

				val matches = allSearchables.filter { it.lang.lowercase() == extension || it.url.lowercase().endsWith(".$extension") }
				logger.info("GITHUB: Found {} matches for '.{}'", matches.size, extension)
				
				// Map matches to a format Frontend Index.kt Table expects
				matches.map { 
					mapOf(
						"name" to it.url.substringAfterLast("/"),
						"path" to it.url.substringAfter("repos/$repoOwner/$repoName/contents/"),
						"type" to "file",
						"download_url" to it.url.replace("api.github.com/repos", "raw.githubusercontent.com").replace("/contents/", "/main/"),
						"size" to 0
					)
				}.also { logger.info("--- [SimpleService: queryFilesGithub] END (Success) ---") }
			} catch (e: Exception) {
				logger.error("GITHUB: Extension search failed: {}", e.message)
				emptyList<Any>()
			}
		}

		// --- Existing Logic: Specific File Search ---
		val path = "uploads"
		val fileParts = query.trim().split(".")
		val fileName = fileParts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "essay1"
		val fileExtension = fileParts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "pdf"

		val fullFileName = "$fileName.$fileExtension"
		val targetPath = "$path/$fullFileName"
		
		val encodedPath = targetPath.split("/").joinToString("/") { 
			java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") 
		}

		val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$encodedPath"
		logger.info("Generated GitHub API URL for specific file: {}", url)

		return try {
			val responseEntity = restClient
				.get()
				.uri(url)
				.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.retrieve()
				.toEntity(GithubContentResponse::class.java)

			logger.info("Specific file search success for path: {}", targetPath)
			responseEntity.body
		} catch (e: Exception) {
			logger.warn("Specific file search failed for path {}: {}", targetPath, e.message)
			null
		} finally {
			logger.info("--- [SimpleService: queryFilesGithub] END ---")
		}
	}

	/**
	 * This method searches both standard and Shared Drives, returning file names and their
	 * corresponding structural IDs.
	 * @param driveService The authorized [Drive] client instance used to execute the request.
	 * @return A newline-separated string listing the available file names, or a structural
	 */
	fun listFiles(driveService: Drive): String {
		logger.info("Listing Google Drive files...")
		val result: FileList = driveService.files().list()
			.setPageSize(10)
			.setFields("nextPageToken, files(id, name)")
			.setSupportsAllDrives(true)
			.setIncludeItemsFromAllDrives(true)
			.execute()

		val files: List<DriveFile>? = result.files

		if (files.isNullOrEmpty()) {
			logger.info("No Google Drive files found.")
			return "Is null or empty."
		}
		val fileListString = StringBuilder("Available Files: \n")
			for (file in files) {
				fileListString.append(file.name).append("\n")
			}
		logger.info("Found {} files in Google Drive", files.size)
		return fileListString.toString()
	}

	fun createUserDashboard(rootRepo: String = "codeRepository", username: String) {
		if (username.isEmpty()) return
		logger.info("--- [SimpleService: createUserDashboard] START (user: {}) ---", username)

		val githubToken = System.getenv("GITHUB_TOKEN")?.trim()
		val repoOwner = "samging"
		
		val rawPath = "uploads/$username/README.md"
		val encodedPath = rawPath.split("/").joinToString("/") { 
			java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") 
		}
		val url = "https://api.github.com/repos/$repoOwner/$rootRepo/contents/$encodedPath"

		CompletableFuture.runAsync {
			try {
				logger.info("Checking if dashboard already exists for user '{}' at {}", username, url)
				val checkResponse = restClient.get()
					.uri(url)
					.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
					.header("Accept", "application/vnd.github+json")
					.header("X-GitHub-Api-Version", "2022-11-28")
					.retrieve()
					.onStatus({ it.value() == 404 }, { _, _ -> /* Expected if new user */ })
					.toEntity(String::class.java)

				if (checkResponse.statusCode.is2xxSuccessful) {
					logger.info("Dashboard for user '{}' already exists. Skipping creation.", username)
					return@runAsync
				}
			} catch (e: Exception) {
				logger.info("Dashboard not found for user '{}' (expected for new users)", username)
			}

			val contentBase64 = Base64.getEncoder().encodeToString("# Dashboard for $username".toByteArray())
			val body = mapOf(
				"message" to "Create dashboard for $username",
				"content" to contentBase64
			)

			try {
				logger.info("Creating dashboard README.md for user '{}' on GitHub...", username)
				restClient.put()
					.uri(url)
					.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
					.header("Accept", "application/vnd.github+json")
					.header("X-GitHub-Api-Version", "2022-11-28")
					.body(body)
					.retrieve()
					.toBodilessEntity()

				logger.info("GitHub User Dashboard Created Successfully for {}!", username)
			} catch (e: Exception) {
				logger.error("GitHub dashboard creation failed for user '{}': {}", username, e.message)
			} finally {
				logger.info("--- [SimpleService: createUserDashboard] ASYNC END ---")
			}
		}
	}

	fun postEndpoints() {
		val githubToken = System.getenv("GITHUB_TOKEN")?.trim()
		if (githubToken.isNullOrBlank()) {
			logger.error("GITHUB_TOKEN is missing. Cannot post endpoints.")
			return
		}
		logger.info("--- [SimpleService: postEndpoints] START ---")

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
				logger.error("endpoints.json not found locally")
				return
			}
			val contentBase64 = Base64.getEncoder().encodeToString(file.readBytes())
			val existingSha = fetchFileSha(url, githubToken)

			val body = mutableMapOf(
				"message" to "endpoints.json - search for compatible device metrics",
				"content" to contentBase64
			)

			if (existingSha != null) body["sha"] = existingSha

			logger.info("Uploading endpoints.json to GitHub...")
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
			logger.error("GitHub API error while posting endpoints: {}", e.message)
		} finally {
			logger.info("--- [SimpleService: postEndpoints] END ---")
		}
	}

	fun fetchEndpoints(): Map<String, String> {
		logger.info("--- [SimpleService: fetchEndpoints] START ---")
		val githubToken = System.getenv("GITHUB_TOKEN")?.trim()
		val repoOwner = "samging"
		val repoName = "codeRepository"
		val path = "repositoryInformations"
		val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$path"

		try {
			logger.info("Fetching endpoints from GitHub: {}", url)
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
				logger.info("Successfully parsed {} endpoints", list.size)
				list.associate { it.retailName to it.descriptiveName }
			} catch (e: Exception) {
				logger.warn("Failed to parse as List<EndpointDto>, trying Map<String, String>: {}", e.message)
				try {
					json.decodeFromString<Map<String, String>>(decodedContent).also { logger.info("Parsed as Map with {} items", it.size) }
				} catch (e2: Exception) {
					logger.error("Failed to parse endpoints JSON: {}", e2.message)
					emptyMap()
				}
			}
		} catch (e: Exception) {
			logger.error("Error fetching endpoints: {}", e.message)
			return emptyMap()
		} finally {
			logger.info("--- [SimpleService: fetchEndpoints] END ---")
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
		logger.info("Downloading file '{}' from Google Drive to {}", fileName, savePath.absolutePath)
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
			logger.error("File '{}' not found on Google Drive.", fileName)
			throw IOException("File not found on Google Drive: $fileName")
		}
		val fileId = files[0].id
		logger.info("Found fileId: {} for fileName: {}", fileId, fileName)

		FileOutputStream(savePath).use { stream ->
			driveService.files().get(fileId)
				.executeAndDownloadTo(stream)
		}
		outputStream.close()
		logger.info("File '{}' downloaded successfully.", fileName)
	}

	fun uploadFile(username: String,
				   progLanguage: String,
				   file: MultipartFile) {
		logger.info("--- [SimpleService: uploadFile] START (user: {}, lang: {}, file: {}) ---", username, progLanguage, file.originalFilename)
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

		var existingSha: String? = null
		try {
			logger.info("GITHUB: Checking for existing file to get SHA at path: {}", path)
			val checkResponse = restClient.get()
				.uri(url)
				.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.retrieve()
				.onStatus({ it.value() == 404 }, { _, _ -> /* Expected if new file */ })
				.toEntity(GithubContentResponse::class.java)

			existingSha = checkResponse.body?.sha
			if (existingSha != null) {
				logger.info("GITHUB: File exists, retrieved SHA: {}", existingSha)
			}
		} catch (e: Exception) {
			logger.info("GITHUB: Initial file check info (not necessarily an error): {}", e.message)
		}

		try {
				val dirTreeUrl: String = "https://api.github.com/repos/$repoOwner/$repoName/contents/uploads/searchables.json"

				fun patchSearchTree(body: String, sha: String? = null): ResponseEntity<String> {
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
						.onStatus({ it.is4xxClientError }, { _, _ -> /* Handled via response status */ })
						.toEntity(String::class.java)
				}

				val encodeToBase = Base64.getEncoder().encodeToString(file.bytes)
				val contentBody = mutableMapOf(
					"message" to "Update $path via Amie Repository for $username",
					"content" to encodeToBase
				)

				fun MutableMap<String, String>.updateJson(existingContentBase64: String?, updateApply: (GithubSearchable.() -> Unit)?): String {
					val existingList = if (!existingContentBase64.isNullOrBlank()) {
						try {
							val decoded = String(Base64.getDecoder().decode(existingContentBase64.replace("\n", "").replace("\r", "")))
							// Try to parse as list, if it's a single object convert it to list
							try {
								json.decodeFromString<List<GithubSearchable>>(decoded).toMutableList()
							} catch (e: Exception) {
								listOf(json.decodeFromString<GithubSearchable>(decoded)).toMutableList()
							}
						} catch (e: Exception) {
							logger.warn("GITHUB: Could not parse existing searchables.json, starting fresh list: {}", e.message)
							mutableListOf<GithubSearchable>()
						}
					} else {
						mutableListOf<GithubSearchable>()
					}

					val newEntry = GithubSearchable(url = url, lang = progLanguage)
					updateApply?.invoke(newEntry)
					
					// Avoid duplicates if same URL
					existingList.removeAll { it.url == newEntry.url }
					existingList.add(newEntry)

					val result = json.encodeToString(existingList)
					logger.info("GITHUB: Generated updated searchables JSON with {} items", existingList.size)
					return result
				}

				var searchablesSha: String? = null
				var existingContent: String? = null
				try {
					val searchCheck = restClient.get()
						.uri(dirTreeUrl)
						.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
						.header("Accept", "application/vnd.github+json")
						.header("X-GitHub-Api-Version", "2022-11-28")
						.retrieve()
						.onStatus({ it.value() == 404 }, { _, _ -> /* Handled */ })
						.toEntity(GithubContentResponse::class.java)
					
					searchablesSha = searchCheck.body?.sha
					existingContent = searchCheck.body?.content
					if (searchablesSha != null) logger.info("GITHUB: Existing searchables.json found, SHA: {}", searchablesSha)
				} catch (e: Exception) {
					logger.info("GITHUB: searchables.json not found (will create new)")
				}

				val updatedJson = contentBody.updateJson(existingContent) {}
				val patchResponse = patchSearchTree(updatedJson, searchablesSha)
				logger.info("(!)GITHUB: Patch response status: {}", patchResponse.statusCode)

				if (patchResponse.statusCode.value() == 409) {
					logger.warn("GITHUB: Conflict updating searchables.json. Someone else updated it. Skipping metadata sync for this file.")
				} else if (patchResponse.statusCode.value() == 404) {
					logger.info("GITHUB: searchables.json not found, creating new")
					val createBody = mapOf(
						"message" to "Create searchables.json",
						"content" to Base64.getEncoder().encodeToString(updatedJson.toByteArray())
					)
					restClient.put().uri(dirTreeUrl)
						.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
						.header("Accept", "application/vnd.github+json")
						.header("X-GitHub-Api-Version", "2022-11-28")
						.body(createBody).retrieve()
						.toBodilessEntity()
				}
			logger.info("GITHUB: Searchables logic completed successfully")
		} catch (e: Exception) {
			logger.warn("GITHUB: Experimental searchables logic failed (non-fatal): {}", e.message)
		}

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
		} finally {
			logger.info("--- [SimpleService: uploadFile] END ---")
		}
	}
	private fun fetchFileSha(url: String, githubToken: String?): String? {
		if (githubToken.isNullOrBlank()) return null
		return try {
			logger.info("Fetching SHA for URL: {}", url)
			val res = restClient.get().uri(url)
				.header("Authorization", "Bearer $githubToken")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.retrieve()
				.body(object : ParameterizedTypeReference<Map<String, Any>>() {})
			val sha = res?.get("sha") as? String
			logger.info("Fetched SHA: {}", sha)
			sha
		} catch(e: Exception) {
			logger.warn("Failed to fetch SHA for {}: {}", url, e.message)
			null
		}
	}

	fun writeError(username: String, error: String, message: String) {
		logger.info("Logging error for user '{}': [{}] {}", username, error, message)
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

		try {
			restClient.put()
				.uri(url)
				.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.toBodilessEntity()
			logger.info("Error successfully logged to GitHub errors.md")
		} catch (e: Exception) {
			logger.error("Failed to log error to GitHub: {}", e.message)
		}
	}

	fun sendEdit(username: String, fileName: String, updateFile: MultipartFile) {
		logger.info("--- [SimpleService: sendEdit] START (user: {}, file: {}) ---", username, fileName)
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
			logger.info("Fetching metadata for file to update: {}", url)
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

			logger.info("Executing PUT request for update...")
			val response = restClient.put()
				.uri(url)
				.header("Authorization", "Bearer ${githubToken?.trim() ?: ""}")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.body(body)
				.retrieve()
				.toEntity(String::class.java)

			logger.info("GitHub Update Success for {}! Status: {}", username, response.statusCode.value())
		} catch (e: Exception) {
			logger.error("CRITICAL: GitHub API Update Error for user '{}': {}", username, e.message)
			throw e
		} finally {
			logger.info("--- [SimpleService: sendEdit] END ---")
		}
	}

	fun uploadFileData(username: String, fileName: String, data: ByteArray) {
		logger.info("--- [SimpleService: uploadFileData] START (user: {}, file: {}) ---", username, fileName)
		val githubToken = System.getenv("GITHUB_TOKEN")?.trim()
		val repoOwner = "samging"
		val repoName = "codeRepository"
		val path = "uploads/$username/$fileName"
		val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$path"
		val contentBase64 = Base64.getEncoder().encodeToString(data)

		var existingSha: String? = null
		try {
			logger.info("Checking SHA for raw data upload: {}", url)
			val checkResponse = restClient.get()
				.uri(url)
				.header("Authorization", "Bearer ${githubToken ?: ""}")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.retrieve()
				.body(GithubContentResponse::class.java)
			existingSha = checkResponse?.sha
			logger.info("Retrieved SHA: {}", existingSha)
		} catch (e: Exception) {
			logger.info("No existing file found for raw upload (this is normal for new files)")
		}

		val body = mutableMapOf(
			"message" to "Update $fileName via Amie Device Manager",
			"content" to contentBase64
		)
		if (existingSha != null) body["sha"] = existingSha

		try {
			logger.info("Executing PUT request for raw data upload...")
			restClient.put()
				.uri(url)
				.header("Authorization", "Bearer ${githubToken ?: ""}")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.body(body)
				.retrieve()
				.toBodilessEntity()
			logger.info("GitHub raw data sync successful for '{}'", fileName)
		} catch (e: Exception) {
			logger.error("DEBUG: GitHub data sync failed for '{}': {}", fileName, e.message)
		} finally {
			logger.info("--- [SimpleService: uploadFileData] END ---")
		}
	}

}
