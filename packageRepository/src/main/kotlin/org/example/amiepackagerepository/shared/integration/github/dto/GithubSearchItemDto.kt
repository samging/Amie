package org.example.amiepackagerepository.shared.integration.github.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class GithubSearchItemDto(
    val name: String,
    val path: String,
    val sha: String,
    @JsonProperty("html_url") val htmlUrl: String
)
