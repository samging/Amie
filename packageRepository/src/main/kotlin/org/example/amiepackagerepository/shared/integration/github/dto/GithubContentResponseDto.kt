package org.example.amiepackagerepository.shared.integration.github.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Data Transfer Object for detailed content information from the GitHub API.
 * Maps directly to the GitHub "contents" API response.
 *
 * @property name File name.
 * @property path Full path in the repository.
 * @property sha Git SHA identifier.
 * @property size File size in bytes.
 * @property url API URL for the item.
 * @property htmlUrl Browser URL for the item.
 * @property downloadUrl URL for raw file download.
 * @property type Item type ("file", "dir").
 * @property content Base64 encoded content (only for files).
 * @property encoding Content encoding (usually "base64").
 */
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
