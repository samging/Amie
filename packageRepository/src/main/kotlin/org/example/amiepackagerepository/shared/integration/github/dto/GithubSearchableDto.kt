package org.example.amiepackagerepository.shared.integration.github.dto

import kotlinx.serialization.Serializable

/**
 * Data Transfer Object for searchable items in the GitHub repository.
 * Used for maintaining the "searchables.json" index for efficient file discovery.
 *
 * @property lang The programming language or category of the file.
 * @property url The GitHub API URL pointing to the file content.
 */
@Serializable
data class GithubSearchableDto(
    val lang: String,
    val url: String,
)

