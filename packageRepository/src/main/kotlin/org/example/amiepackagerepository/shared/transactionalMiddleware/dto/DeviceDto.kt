package org.example.amiepackagerepository.shared.transactionalMiddleware.dto

import kotlinx.serialization.Serializable

/**
 * Data Transfer Object for device information.
 * Encapsulates device attributes such as name, port, and endpoint for serialization.
 */
@Serializable
data class DeviceDto(
    val name: String,
    val port: String,
    val deviceEndpoint: String? = null,
)
