package org.example.amiepackagerepository.shared.transactionalMiddleware.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceDto(
    val name: String,
    val port: String,
    val deviceEndpoint: String? = null,
)
