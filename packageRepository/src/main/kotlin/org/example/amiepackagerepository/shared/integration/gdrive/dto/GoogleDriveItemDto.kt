package org.example.amiepackagerepository.shared.integration.gdrive.dto
import org.example.amiepackagerepository.shared.integration.shared.RepositoryInterface

data class GoogleDriveItemDto(
    override val name: String,
    override val id: String,
    override val downloadUrl: String? = null
) : RepositoryInterface

