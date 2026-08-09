package org.example.amiepackagerepository

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeviceService(
    private val deviceStatusRepository: DeviceStatusRepository,
    private val userRepository: UserRepository
) {

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

data class DeviceDto(
    val name: String,
    val port: String,
    val deviceEndpoint: String? = null
)
