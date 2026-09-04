package org.example.amiepackagerepository.shared.integration.github.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Data Transfer Object representing a single item in a GitHub search result.
 *
 * @property name The name of the file.
 * @property path The repository path to the file.
 * @property sha The unique git SHA identifier for the file content.
 * @property htmlUrl The URL to view the file in a web browser.
 */
data class GithubSearchItemDto(
    val name: String,
    val path: String,
    val sha: String,
    @JsonProperty("html_url") val htmlUrl: String
)
