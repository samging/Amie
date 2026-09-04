package org.example.amiepackagerepository.shared.integration.github.dto

import org.example.amiepackagerepository.shared.integration.shared.RepositoryInterface

data class GithubItemMetadataDto(
    override val name: String,
    override val downloadUrl: String? = null,
    override val id: String? = null,
    val type: String = "file",
    val endComp: String? = null,
) : RepositoryInterface
