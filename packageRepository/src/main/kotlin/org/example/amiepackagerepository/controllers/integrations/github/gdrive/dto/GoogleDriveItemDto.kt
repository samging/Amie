package org.example.amiepackagerepository.controllers.integrations.github.gdrive.dto

import org.example.amiepackagerepository.controllers.integrations.github.RepositoryInterface

data class GoogleDriveItemDto(
    override val name: String,
    override val id: String,
    override val downloadUrl: String? = null
) : RepositoryInterface

