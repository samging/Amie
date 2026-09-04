package org.example.amiepackagerepository.shared.integration.github.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class GithubEndpointDto(
    @Serializable
    @SerialName("retail_name") val retailName: String,
    @SerialName("descriptive_name") val descriptiveName: String,
)
