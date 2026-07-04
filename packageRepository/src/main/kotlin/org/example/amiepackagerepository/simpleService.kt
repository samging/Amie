package org.example.amiepackagerepository

import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.FileList
import com.google.api.services.drive.model.File as DriveFile
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Provides functionality to query, list, and stream files directly from:
 * personal and shared Google Drives.
 */
@Service
open class SimpleService {

	/**
	 * This method searches both standard and Shared Drives, returning file names and their
	 * corresponding structural IDs.
	 * @param driveService The authorized [Drive] client instance used to execute the request.
	 * @return A newline-separated string listing the available file names, or a structural
	 */
	open fun listFiles(driveService: Drive): String {
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

	/**
	 * The method queries all non-trashed files matching the provided name. If multiple files
	 * match, it defaults to downloading the first match discovered.
	 * @param driveService The authorized [Drive] client instance used to execute the request.
	 * @param fileName The exact name string of the file targeted for download.
	 * @param savePath The local [File] destination target where data will be written.
	 * @throws IOException If the file does not exist on Google Drive, or if a local I/O error occurs.
	 */
	open fun downloadFile(driveService: Drive, fileName: String, savePath: File) {

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
}