package org.example.amiepackagerepository.application

import com.google.api.services.drive.Drive
import org.example.amiepackagerepository.controllers.integrations.github.GithubService
import org.example.amiepackagerepository.service.user.service.UserService
import org.example.amiepackagerepository.transactionalMiddleware.DeviceActions
import org.example.amiepackagerepository.transactionalMiddleware.DeviceDto
import org.example.amiepackagerepository.transactionalMiddleware.TransactionalStatusRepository
import org.example.amiepackagerepository.transactionalMiddleware.entities.entities.DeviceStatus
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

@CrossOrigin(origins = ["http://localhost:8081"])
@RestController
class DeviceController(
    private val driveService: Drive,
    private val simpleService: GithubService,
    private val userService: UserService,
    private val deviceService: TransactionalStatusRepository
) {
    private val logger = LoggerFactory.getLogger(DeviceController::class.java)

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

    @GetMapping("/fetch-endpoints")
    fun getDeviceCompatibility(): Map<String, String>? {
        logger.info("--- [GET /fetch-endpoints] START ---")
        return simpleService.fetchEndpoints().also {
            logger.info("--- [GET /fetch-endpoints] END ---")
        }
    }

    @PostMapping("/post-endpoints")
    fun putDeviceCompatibility() {
        logger.info("--- [POST /post-endpoints] START ---")
        simpleService.postEndpoints()
        logger.info("--- [POST /post-endpoints] END ---")
    }

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