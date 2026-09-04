package org.example.amiepackagerepository.shared.integration.github.dto

import kotlinx.serialization.Serializable

@Serializable
data class GithubSearchableDto(
    val lang: String,
    val url: String,
)

