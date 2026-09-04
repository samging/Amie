package org.example.amiepackagerepository.shared.integration.github.dto

import org.example.amiepackagerepository.shared.integration.shared.RepositoryInterface

/**
 * Data Transfer Object for GitHub item metadata with extended properties.
 *
 * @property name The name of the file.
 * @property downloadUrl The URL for raw content download.
 * @property id The unique identifier (SHA).
 * @property type The item type (e.g., "file").
 * @property endComp Custom property indicating if the file is a C component.
 */
data class GithubItemMetadataDto(
    override val name: String,
    override val downloadUrl: String? = null,
    override val id: String? = null,
    val type: String = "file",
    val endComp: String? = null,
) : RepositoryInterface
