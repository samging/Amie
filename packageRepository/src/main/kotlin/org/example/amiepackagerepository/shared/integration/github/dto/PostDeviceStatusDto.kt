package org.example.amiepackagerepository.shared.integration.github.dto

import kotlinx.serialization.Serializable
import org.example.amiepackagerepository.shared.transactionalMiddleware.enumerables.DeviceActions
import org.example.amiepackagerepository.shared.transactionalMiddleware.dto.DeviceDto

/**
 * Data Transfer Object for incoming device status update requests.
 * Used to transmit status changes from the client to the repository services.
 *
 * @property action The action to be performed (SET or GET).
 * @property username The user who owns the devices.
 * @property deviceMap A map of device identifiers to their updated status DTOs.
 */
@Serializable
data class PostDeviceStatusDto(
    val action: DeviceActions,
    val username: String,
    val deviceMap: Map<String, DeviceDto>
)
