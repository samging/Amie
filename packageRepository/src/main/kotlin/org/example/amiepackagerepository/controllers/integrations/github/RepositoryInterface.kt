package org.example.amiepackagerepository.controllers.integrations.github

/**
 * Common interface for items retrieved from different repository types.
 */
interface RepositoryInterface {
    val name: String
    val id: String?
    val downloadUrl: String?
}