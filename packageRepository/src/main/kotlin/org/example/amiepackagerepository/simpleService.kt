package org.example.amiepackagerepository

import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.FileList
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.client.http.InputStreamContent
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.client.RestClient
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.util.Base64

/**
 * Common interface for items retrieved from different repository types.
 */
interface RepositoryItem {
	val name: String
	val id: String?
	val downloadUrl: String?
}

data class GithubItem(
	override val name: String,
	override val downloadUrl: String? = null,
	override val id: String? = null
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
	val total_count: Int,
	val incomplete_results: Boolean,
	val items: List<GithubSearchItem>
)

data class GithubContentResponse(
	val name: String,
	val path: String,
	val sha: String,
	val size: Long,
	val url: String,
	val html_url: String,
	val download_url: String?,
	val type: String
)

data class GithubSearchItem(
	val name: String,
	val path: String,
	val sha: String,
	val html_url: String
)

data class CreateRepoRequest(
	val name: String,
	val description: String?,
	val private: Boolean
)

/**
 * Provides functionality to query, list, and stream files directly from:
 * personal and shared Google Drives, and now GitHub.
 */
@Service
open class SimpleService {

	private val restClient = RestClient.create()

	/**
	 * Lists files from the GitHub repository 'samging/codeRepository' in the 'uploads/' folder.
	 */
	fun listFilesGithub(): List<GithubItem> {
		val githubToken = System.getenv("GITHUB_TOKEN")

		val repoOwner = "samging"
		val repoName = "codeRepository"
		val path = "uploads"
		val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$path"

		if (githubToken.isNullOrBlank()) {
			println("WARNING: GITHUB_TOKEN is not set. Private repos will return 404.")
		}

		return try {
			val response = restClient.get()
				.uri(url)
				.let { spec ->
					if (!githubToken.isNullOrBlank()) spec.header("Authorization", "Bearer $githubToken")
					else spec
				}
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.retrieve()
				.onStatus({ it.value() == 404 }, { _, _ -> 
					// Handle 404 as empty directory
					throw java.io.FileNotFoundException("Directory not found (likely empty)")
				})
				.body(object : ParameterizedTypeReference<List<GithubContentResponse>>() {})

			response?.map {
				GithubItem(
					name = it.name,
					downloadUrl = it.download_url ?: ""
				)
			} ?: emptyList()
		} catch (e: java.io.FileNotFoundException) {
			println("GitHub: Path '$path' not found. This usually means the folder is empty.")
			emptyList()
		} catch (e: Exception) {
			println("GitHub List Error: ${e.message}")
			emptyList()
		}
	}

	fun queryFilesGithub(query: String = ""): GithubContentResponse? {
		val githubToken = System.getenv("GITHUB_TOKEN")
		val repoOwner = "samging"
		val repoName = "codeRepository"
		val path = "uploads"

		// Parse the query string (e.g., "essay1.pdf") into name and extension
		val fileParts = query.trim().split(".")
		val fileName = fileParts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "essay1"
		val fileExtension = fileParts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "pdf"

		val fullFileName = "$fileName.$fileExtension"
		val targetPath = "$path/$fullFileName"

		// GitHub Contents API URL for a specific file path
		val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$targetPath"
		println("Generated URL: $url")

		val restClient = RestClient.create()
		return try {
			// Since we are targeting a single file, we can map to a DTO or a generic Map/Json node
			val responseEntity = restClient
				.get()
				.uri(url)
				.header("Authorization", "Bearer $githubToken")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.retrieve()
				.toEntity(GithubContentResponse::class.java)

			val fileInfo = responseEntity.body
			if (fileInfo != null) {
				println("File found: ${fileInfo.path} -> ${fileInfo.html_url}")
				println("Download URL: ${fileInfo.download_url}")
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

		if (username.isEmpty()) {
			throw java.lang.IllegalArgumentException("Username cannot be empty")
		}

		val githubToken = System.getenv("GITHUB_TOKEN")
			?: throw java.lang.IllegalArgumentException("Environment variable 'GITHUB_TOKEN' not set.")

		val repoOwner = "samging"
		val path = "uploads"

		val url = "https://api.github.com/repos/$repoOwner/$rootRepo/contents/$username"
		val restClient = RestClient.create()

		val requestBody = CreateRepoRequest(
			name = rootRepo,
			description = "Dashboard for $username",
			private = true
		)
		try {
			val response = restClient
				.post()
				.uri(url)
				.header("Authorization", "Bearer $githubToken")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.body(requestBody)
				.retrieve()
				.toEntity(String::class.java)

			if (response.statusCode.is2xxSuccessful) {
				println("GitHub Repository Created Successfully!")
			}
		} catch(e: Exception) {
			println("CRITICAL: GitHub API Error: ${e.message}")
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
			throw java.io.IOException("File not found on Google Drive: $fileName")
		}
		val fileId = files[0].id

		java.io.FileOutputStream(savePath).use { outputStream ->
			driveService.files().get(fileId)
				.executeAndDownloadTo(outputStream)
		}
		outputStream.close()
	}

	fun uploadFile(driveService: Drive, file: MultipartFile) {
		val githubToken = System.getenv("GITHUB_TOKEN") 
			?: throw java.io.IOException("Environment variable 'GITHUB_TOKEN' not set.")
		
		val repoOwner = "samging"
		val repoName = "codeRepository"
		val fileName = file.originalFilename ?: "unnamed_file"
		val path = "uploads/$fileName"
		
		val contentBase64 = Base64.getEncoder().encodeToString(file.bytes)
		
		val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$path"
		
		val body = mapOf(
			"message" to "Upload $fileName via Amie Repository",
			"content" to contentBase64
		)

		try {
			val response = restClient.put()
				.uri(url)
				.header("Authorization", "Bearer $githubToken")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.body(body)
				.retrieve()
				.toEntity(String::class.java)

			println("GitHub Upload Success! Status: ${response.statusCode.value()}")
		} catch (e: Exception) {
			println("CRITICAL: GitHub API Error: ${e.message}")
			throw e
		}
	}
}
