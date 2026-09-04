package org.example.amiepackagerepository.shared.transactionalMiddleware.integration.gdrive

import com.google.api.services.drive.Drive
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.TransactionalStatusRepository
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.File

@CrossOrigin(origins = ["http://localhost:8081"])
@RestController
class DriveController(
    private val driveService: Drive,
    private val simpleService: DriveService,
    private val userService: UserService,
    private val deviceService: TransactionalStatusRepository
) {
    private val logger = LoggerFactory.getLogger(DriveController::class.java)

    /**
     * Retrieves a formatted list of all files present in the Google Drive.
     * @return A string representation/log of the files found in the drive.
     */
    @GetMapping("/list-disk")
    fun listFiles(): String {
        logger.info("--- [GET /list-disk] START ---")
        return simpleService.listFiles(driveService).also {
            logger.info("--- [GET /list-disk] END ---")
        }
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

}