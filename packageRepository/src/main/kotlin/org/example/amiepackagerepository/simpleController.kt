package org.example.amiepackagerepository

import com.google.api.services.drive.Drive
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.File
/**
 * REST Controller providing HTTP endpoints to interact with Google Drive.
 * Exposes endpoints for listing available files and downloading specific
 * assets to the local file system via the [SimpleService].
 * @property driveService The authorized Google Drive client.
 * @property simpleService The business logic service handling Drive operations.
 */
@RestController
open class SimpleController(
	private val driveService: Drive,
	private val simpleService: SimpleService
) {

	/**
	 * Retrieves a formatted list of all files present in the Google Drive.
	 * @return A string representation/log of the files found in the drive.
	 */
	@GetMapping("/list-files")
	open fun getFiles(): String {
		return simpleService.listFiles(driveService)
	}

	/**
	 * Downloads a specific file from Google Drive to the user's local Downloads folder.
	 * The file is saved locally under the path:
	 * `~/Downloads/amiePackagesDownload/{fileName}`
	 * @param fileName The exact name of the file to retrieve from Google Drive.
	 * @return A status message indicating whether the download succeeded or failed,
	 * including the absolute path on success.
	 */
	@GetMapping("/download")
	open fun downloadFile(@RequestParam fileName: String): String {
		// Customize this path to where you want it saved on your computer
		val userHome = System.getProperty("user.home")
		val destinationFile = java.io.File(userHome, "Downloads/amiePackagesDownload/$fileName")

		return try {
			simpleService.downloadFile(driveService, fileName, destinationFile)
			"Success! File downloaded to ${destinationFile.absolutePath}"
		} catch (e: Exception) {
			"Failed to download file: ${e.message}"
		}
	}
}