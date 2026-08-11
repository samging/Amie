package org.example.amiepackagerepository

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Async
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

@Serializable // DeviceDto is Json
data class DeviceDto(
    val name: String,
    val port: String,
    val deviceEndpoint: String? = null
)

enum class DeviceActions {
    SET, GET
}

@Service
class DeviceService(
    private val deviceStatusRepository: DeviceStatusRepository,
    private val userRepository: UserRepository,
    private val simpleService: SimpleService
) {
    private val restClient = RestClient.create()
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val logger = LoggerFactory.getLogger(DeviceService::class.java)
    }

    @Async
    @Transactional
    fun repositoryDeviceController(
        action: DeviceActions,
        username: String,
        deviceMap: Map<String, DeviceDto>
    ): CompletableFuture<ResponseEntity<String>> {

        if (deviceMap.isEmpty() && action == DeviceActions.SET) {
            logger.error("deviceMap is empty")
            return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body("deviceMap is empty")
            )
        }

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
                val repoOwner = "samging"
                val repoName = "codeRepository"
                val fileName = "$username-device.json"

                val path = "uploads/$username/$fileName"
                val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$path"

                val deviceStatuses = deviceMap.map { (key, device) ->
                    DeviceStatus(
                        deviceKey = key,
                        name = device.name,
                        port = device.port,
                        deviceEndpoint = device.deviceEndpoint,
                        user = user
                    )
                }
                
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
                val repoOwner = "samging"
                val repoName = "codeRepository"
                val fileName = "$username-device.json"

                val path = "uploads/$username/$fileName"
                val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$path"

                try {
                    val response = restClient.get()
                        .uri(url)
                        .header("Authorization", "Bearer $githubToken")
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .retrieve()
                        .toEntity(String::class.java)

                    CompletableFuture.completedFuture(ResponseEntity.ok(response.body))
                } catch (e: org.springframework.web.client.HttpClientErrorException) {
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

    fun getDeviceStatuses(username: String): List<DeviceStatus> {
        return deviceStatusRepository.findByUserUsername(username)
    }
}
