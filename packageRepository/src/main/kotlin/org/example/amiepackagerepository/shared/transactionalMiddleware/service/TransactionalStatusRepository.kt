package org.example.amiepackagerepository.shared.transactionalMiddleware.service

import org.springframework.beans.factory.annotation.Value
import kotlinx.serialization.json.Json
import org.example.amiepackagerepository.shared.integration.github.service.GithubService
import org.example.amiepackagerepository.shared.transactionalMiddleware.dto.DeviceDto
import org.example.amiepackagerepository.shared.transactionalMiddleware.entities.DeviceStatus
import org.example.amiepackagerepository.shared.transactionalMiddleware.enumerables.DeviceActions
import org.example.amiepackagerepository.shared.transactionalMiddleware.repository.DeviceStatusRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.util.concurrent.CompletableFuture

/**
 * Service that handles transactional repository operations for device statuses.
 * Manages the orchestration between local database persistence and GitHub repository synchronization.
 *
 * @property deviceStatusRepository The local JPA repository for DeviceStatus entities.
 * @property userRepository The local JPA repository for RestUserEntity entities.
 * @property simpleService The GitHub service for remote file synchronization.
 */
@Service
class TransactionalStatusRepository(
    private val deviceStatusRepository: DeviceStatusRepository,
    private val userRepository: UserRepository,
    private val simpleService: GithubService
) {
    private val restClient = RestClient.create()
    private val json = Json { ignoreUnknownKeys = true }

    @Value("\${amie.github.owner}")
    private lateinit var owner: String

    @Value("\${amie.github.repo}")
    private lateinit var name: String


    @Value("\${amie.github.github-api-base}")
    private lateinit var githubApiBase: String

    @Value("\${amie.github.url-segment}")
    private lateinit var urlSegment: String

    companion object {
        private val logger = LoggerFactory.getLogger(TransactionalStatusRepository::class.java)
    }

    /**
     * Orchestrates device status updates via a specified action (SET or GET).
     * Handles local DB operations and remote GitHub synchronization.
     *
     * @param action The operation to perform (SET to save, GET to retrieve).
     * @param username The username associated with the device update.
     * @param deviceMap A map of device keys to their status DTOs.
     * @return A CompletableFuture containing the result of the operation as a ResponseEntity.
     */
    @Async
    @Transactional
    fun repositoryDeviceController(
        action: DeviceActions,
        username: String,
        deviceMap: Map<String, DeviceDto>
    ): CompletableFuture<ResponseEntity<String>> {

//        if (deviceMap.isEmpty() && action == DeviceActions.SET) {
//            logger.error("deviceMap is empty")
//            return CompletableFuture.completedFuture(
//                ResponseEntity.status(HttpStatus.BAD_REQUEST).body("deviceMap is empty")
//            )
//        }

        val user = if (username.isNotEmpty()) userRepository.findByUsername(username) else null
        if (user == null) {
            logger.error("User not found or is Unidentifiable: '$username'")
            return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body("User not found or is Unidentifiable: '$username'")
            )
        }

        val existing = deviceStatusRepository.findByUser(user)
        deviceStatusRepository.deleteAll(existing)

        return when (action) {
            DeviceActions.SET -> {
                val githubToken = System.getenv("GITHUB_TOKEN")
                val fileName = "$username-device.json"

                val path = "uploads/$username/$fileName"
                val url = "$githubApiBase/$owner/$name/$urlSegment/$path"

                val deviceStatuses = deviceMap.map { (key, device) ->
                    DeviceStatus(
                        deviceKey = key,
                        name = device.name,
                        port = device.port,
                        deviceEndpoint = device.deviceEndpoint,
                        user = user
                    )
                }
                println("${deviceStatuses.size} -> ${deviceStatuses}")
                val savedEntities = deviceStatusRepository.saveAll(deviceStatuses)

                try {
                    val jsonContent = json.encodeToString(deviceMap)
                    simpleService.uploadFileData(username, fileName, jsonContent.toByteArray())

                    CompletableFuture.completedFuture(
                        ResponseEntity.ok("Device statuses saved and synced for $username")
                    )
                } catch (e: Exception) {
                    logger.error("Error syncing to GitHub: ${e.message}")
                    CompletableFuture.completedFuture(
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Local save OK, but GitHub sync failed: ${e.message}")
                    )
                }
            }

            DeviceActions.GET -> {
                val githubToken = System.getenv("GITHUB_TOKEN")
                val fileName = "$username-device.json"

                val path = "uploads/$username/$fileName"
                val url = "$githubApiBase/$owner/$name/$urlSegment/$path"

                try {
                    val response = restClient.get()
                        .uri(url)
                        .header("Authorization", "Bearer $githubToken")
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .retrieve()
                        .toEntity(String::class.java)

                    CompletableFuture.completedFuture(ResponseEntity.ok(response.body))
                } catch (e: HttpClientErrorException) {
                    CompletableFuture.completedFuture(
                        ResponseEntity.status(e.statusCode).body("Repository Error: ${e.message}")
                    )
                } catch (e: Exception) {
                    CompletableFuture.completedFuture(
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: ${e.message}")
                    )
                }
            }
        }
    }

    /**
     * Persists device statuses to the local database for a given user.
     * Clears existing statuses before saving new ones.
     *
     * @param username The username for whom statuses are saved.
     * @param deviceMap A map containing the device data to persist.
     */
    @Transactional
    fun saveDeviceStatuses(username: String, deviceMap: Map<String, DeviceDto>) {
        val user = userRepository.findByUsername(username) ?: return
        val existing = deviceStatusRepository.findByUser(user)
        deviceStatusRepository.deleteAll(existing)

        val newStatuses = deviceMap.map { (key, device) ->
            DeviceStatus(
                deviceKey = key,
                name = device.name,
                port = device.port,
                deviceEndpoint = device.deviceEndpoint,
                user = user
            )
        }
        deviceStatusRepository.saveAll(newStatuses)
    }

    /**
     * Retrieves all device statuses for a specific username from the local database.
     *
     * @param username The username to query.
     * @return A list of [DeviceStatus] entities.
     */
    fun getDeviceStatuses(username: String): List<DeviceStatus> {
        return deviceStatusRepository.findByUserUsername(username)
    }
}