package org.example.amiepackagerepository.controllers.integrations.github.dto

import org.example.amiepackagerepository.controllers.integrations.github.RepositoryInterface

data class GithubItemDto(
    override val name: String,
    override val downloadUrl: String? = null,
    override val id: String? = null,
    val type: String = "file"
) : RepositoryInterface
