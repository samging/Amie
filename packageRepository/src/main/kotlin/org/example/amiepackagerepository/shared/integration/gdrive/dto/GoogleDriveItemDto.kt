package org.example.amiepackagerepository.shared.integration.gdrive.dto
import org.example.amiepackagerepository.shared.integration.shared.RepositoryInterface

/**
 * Data Transfer Object representing an item in Google Drive.
 * Implements [RepositoryInterface] for unified handling of repository items.
 *
 * @property name The display name of the file or folder.
 * @property id The unique identifier for the item in Google Drive.
 * @property downloadUrl The URL used to download the file content, if applicable.
 */
data class GoogleDriveItemDto(
    override val name: String,
    override val id: String,
    override val downloadUrl: String? = null
) : RepositoryInterface

