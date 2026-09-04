package org.example.amiepackagerepository.shared.transactionalMiddleware.integration.gdrive

import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.FileList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.collections.isNullOrEmpty

/**
 * Service for interacting with Google Drive.
 * Provides functionality for listing files and downloading specific content from Drive.
 */
@Service
@Suppress("NewApi")
class DriveService {
    private val logger = LoggerFactory.getLogger(DriveService::class.java)
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

        val files: List<com.google.api.services.drive.model.File>? = result.files

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
}