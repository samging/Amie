package org.example.amiepackagerepository.shared.integration.github.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object for device compatibility endpoints.
 * Used for mapping retail hardware names to their descriptive profiles.
 *
 * @property retailName The commercial name of the device (e.g., "Arduino Uno").
 * @property descriptiveName A detailed description or technical profile of the device.
 */
data class GithubEndpointDto(
    @Serializable
    @SerialName("retail_name") val retailName: String,
    @SerialName("descriptive_name") val descriptiveName: String,
)
