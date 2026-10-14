package org.example.amiepackagerepository.shared.transactionalMiddleware.service

import kotlinx.serialization.json.Json
import org.example.amiepackagerepository.shared.packagestore.PackageStoreClient
import org.example.amiepackagerepository.shared.packagestore.PackageStoreException
import org.example.amiepackagerepository.shared.transactionalMiddleware.dto.DeviceDto
import org.example.amiepackagerepository.shared.transactionalMiddleware.entities.DeviceStatus
import org.example.amiepackagerepository.shared.transactionalMiddleware.enumerables.DeviceActions
import org.example.amiepackagerepository.shared.transactionalMiddleware.repository.DeviceStatusRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CompletableFuture

/**
 * The devices a user configured in the desktop app: kept in Postgres ([DeviceStatusRepository]) and, so
 * the app can fetch them back on any machine, as the saved state `devices` in the package store
 * (`uploads/<user>/state/devices.json`).
 *
 * @property deviceStatusRepository The local JPA repository for DeviceStatus entities.
 * @property userRepository The local JPA repository for RestUserEntity entities.
 * @property store The package store.
 */
@Service
class TransactionalStatusRepository(
    private val deviceStatusRepository: DeviceStatusRepository,
    private val userRepository: UserRepository,
    private val store: PackageStoreClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val logger = LoggerFactory.getLogger(TransactionalStatusRepository::class.java)
        const val DEVICES_STATE = "devices"
    }

    /**
     * Saves (SET) or reads back (GET) a user's devices. SET replaces the rows in Postgres and writes the
     * state file; GET answers the state file's JSON (`{}` when there is none yet).
     *
     * @return 200 with a message (SET) or the devices JSON (GET); 400 for an unknown user; 502 when the store is down.
     */
    @Async
    @Transactional
    fun repositoryDeviceController(
        action: DeviceActions,
        username: String,
        deviceMap: Map<String, DeviceDto>
    ): CompletableFuture<ResponseEntity<String>> {
        val user = if (username.isNotEmpty()) userRepository.findByUsername(username) else null
        if (user == null) {
            logger.error("User not found or is Unidentifiable: '$username'")
            return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body("User not found or is Unidentifiable: '$username'")
            )
        }

        return when (action) {
            DeviceActions.SET -> {
                val existing = deviceStatusRepository.findByUser(user)
                deviceStatusRepository.deleteAll(existing)
                val deviceStatuses = deviceMap.map { (key, device) ->
                    DeviceStatus(
                        deviceKey = key,
                        name = device.name,
                        port = device.port,
                        deviceEndpoint = device.deviceEndpoint,
                        user = user
                    )
                }
                deviceStatusRepository.saveAll(deviceStatuses)

                try {
                    store.put(
                        PackageStoreClient.stateKey(username, DEVICES_STATE),
                        json.encodeToString(deviceMap).toByteArray(),
                        MediaType.APPLICATION_JSON_VALUE
                    )
                    CompletableFuture.completedFuture(ResponseEntity.ok("Device statuses saved and synced for $username"))
                } catch (e: PackageStoreException) {
                    logger.error("Devices of {} saved locally but not in the store: {}", username, e.message)
                    CompletableFuture.completedFuture(
                        ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Local save OK, but the package store failed: ${e.message}")
                    )
                }
            }

            DeviceActions.GET -> try {
                val stored = store.get(PackageStoreClient.stateKey(username, DEVICES_STATE))
                CompletableFuture.completedFuture(ResponseEntity.ok(stored?.bytes?.toString(Charsets.UTF_8) ?: "{}"))
            } catch (e: PackageStoreException) {
                logger.error("Devices of {} could not be read from the store: {}", username, e.message)
                CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Error: ${e.message}"))
            }
        }
    }

    /**
     * Persists device statuses to the local database for a given user.
     * Clears existing statuses before saving new ones.
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

    /** All device statuses of a user from the local database. */
    fun getDeviceStatuses(username: String): List<DeviceStatus> {
        return deviceStatusRepository.findByUserUsername(username)
    }
}
