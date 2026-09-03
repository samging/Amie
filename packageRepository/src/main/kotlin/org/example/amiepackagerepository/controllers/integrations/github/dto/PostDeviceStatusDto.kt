package org.example.amiepackagerepository.controllers.integrations.github.dto

import kotlinx.serialization.Serializable
import org.example.amiepackagerepository.transactionalMiddleware.DeviceActions
import org.example.amiepackagerepository.transactionalMiddleware.DeviceDto

@Serializable
data class PostDeviceStatusDto(
    val action: DeviceActions,
    val username: String,
    val deviceMap: Map<String, DeviceDto>
)
