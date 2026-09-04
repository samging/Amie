package org.example.amiepackagerepository.shared.integration.github.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

data class GithubContentResponseDto(
    @JsonIgnoreProperties(ignoreUnknown = true)
    val name: String,
    val path: String,
    val sha: String,
    val size: Long,
    val url: String,
    @JsonProperty("html_url") val htmlUrl: String,
    @JsonProperty("download_url") val downloadUrl: String?,
    val type: String,
    val content: String? = null,
    val encoding: String? = null
)
