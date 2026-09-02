package org.example.amiepackagerepository.dto

import kotlinx.serialization.Serializable
import org.example.amiepackagerepository.transactionalMiddleware.DeviceActions
import org.example.amiepackagerepository.transactionalMiddleware.DeviceDto

@Serializable
data class sendDeviceStatusDto(
    val action: DeviceActions,
    val username: String,
    val deviceMap: Map<String, DeviceDto>
)
