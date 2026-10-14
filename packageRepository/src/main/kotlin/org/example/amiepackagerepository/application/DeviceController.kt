package org.example.amiepackagerepository.application

import org.example.amiepackagerepository.shared.packagestore.PackageStoreClient
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.example.amiepackagerepository.shared.transactionalMiddleware.enumerables.DeviceActions
import org.example.amiepackagerepository.shared.transactionalMiddleware.dto.DeviceDto
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.TransactionalStatusRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.entities.DeviceStatus
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.HttpClientErrorException
import java.util.concurrent.CompletableFuture

/**
 * REST Controller for managing device status and compatibility endpoints.
 * Provides endpoints for saving, retrieving, and syncing device configurations.
 *
 * @property store The package store (the S3 bucket), where the compatibility endpoints live.
 * @property userService User management service.
 * @property deviceService Repository for managing transactional device statuses.
 */
@CrossOrigin(origins = ["\${amie.cors.allowed-origins}"])
@RestController
class DeviceController(
    private val store: PackageStoreClient,
    private val userService: UserService,
    private val deviceService: TransactionalStatusRepository
) {
    private val logger = LoggerFactory.getLogger(DeviceController::class.java)

    /**
     * Saves device statuses to the local database for a specific user.
     *
     * @param username The username for whom statuses are being saved.
     * @param deviceMap A map of device identifiers to their status details.
     * @return A status message confirming the save operation.
     */
    @PostMapping("/device-status")
    fun saveDeviceStatus(
        @RequestParam username: String,
        @RequestBody deviceMap: Map<String, DeviceDto>
    ): String {
        logger.info("--- [POST /device-status] (user: {}) ---", username)
        deviceService.saveDeviceStatuses(username, deviceMap)
        return "Device statuses saved successfully"
    }

    /**
     * Retrieves the current device statuses for a specific user from the local database.
     *
     * @param username The username whose device statuses are requested.
     * @return A list of [DeviceStatus] entities.
     */
    @GetMapping("/device-status")
    fun getDeviceStatus(@RequestParam username: String): List<DeviceStatus> {
        logger.info("--- [GET /device-status] (user: {}) ---", username)
        return deviceService.getDeviceStatuses(username)
    }

    /**
     * Fetches compatible device endpoints from the package store.
     *
     * @return A map of retail names to descriptive names for compatible devices.
     */
    @GetMapping("/fetch-endpoints")
    fun getDeviceCompatibility(): Map<String, String>? {
        logger.info("--- [GET /fetch-endpoints] START ---")
        return store.fetchEndpoints().also {
            logger.info("--- [GET /fetch-endpoints] END ---")
        }
    }

    /**
     * Publishes the built-in device compatibility endpoints to the package store.
     */
    @PostMapping("/post-endpoints")
    fun putDeviceCompatibility() {
        logger.info("--- [POST /post-endpoints] START ---")
        try {
            store.postEndpoints()
        } catch (e: Exception) {
            logger.error("Endpoints could not be published: {}", e.message)
        }
        logger.info("--- [POST /post-endpoints] END ---")
    }

    /**
     * Main entry point for complex device repository actions (GET/SET).
     * Orchestrates synchronization between local storage and remote repositories.
     *
     * @param action The desired repository action (SET or GET).
     * @param username The username associated with the request.
     * @param deviceMap A map of device keys to status DTOs.
     * @return A CompletableFuture containing the operation result.
     */
    @PostMapping("/device-repository-controller")
    fun byActionDeviceController(@RequestParam action: DeviceActions,
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
                is HttpClientErrorException -> {
                    HttpStatus.valueOf(cause.statusCode.value())
                }
                else -> HttpStatus.INTERNAL_SERVER_ERROR
            }

            val errorMessage = when {
                cause is HttpClientErrorException -> {
                    "Repository Provider Error: ${cause.responseBodyAsString}"
                }
                else -> cause.message ?: "An unexpected error occurred"
            }

            logger.error("Device Repository Action Failed: {}", errorMessage)
            ResponseEntity.status(status).body("Action Failed: $errorMessage")
        }.also { logger.info("--- [POST /device-repository-controller] END ---") }
    }
}