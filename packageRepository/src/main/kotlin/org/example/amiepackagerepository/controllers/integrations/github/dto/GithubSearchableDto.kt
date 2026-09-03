package org.example.amiepackagerepository.controllers.integrations.github.dto

import kotlinx.serialization.Serializable

@Serializable
data class GithubSearchableDto(
    val lang: String,
    val url: String,
)

