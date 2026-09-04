package org.example.amiepackagerepository.shared.integration.github.dto

import org.example.amiepackagerepository.shared.integration.shared.RepositoryInterface

/**
 * Data Transfer Object representing a simplified item in a GitHub repository.
 * Implements [RepositoryInterface] for unified handling across different integrations.
 *
 * @property name The name of the file or directory.
 * @property downloadUrl The URL for raw content download from GitHub.
 * @property id The unique SHA or identifier of the item.
 * @property type The type of item (e.g., "file", "dir"). Defaults to "file".
 */
data class GithubItemDto(
    override val name: String,
    override val downloadUrl: String? = null,
    override val id: String? = null,
    val type: String = "file"
) : RepositoryInterface
