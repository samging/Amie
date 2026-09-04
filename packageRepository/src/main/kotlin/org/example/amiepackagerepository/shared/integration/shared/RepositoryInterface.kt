package org.example.amiepackagerepository.shared.integration.shared

/**
 * Common interface for items retrieved from different repository types.
 */
interface RepositoryInterface {
    val name: String
    val id: String?
    val downloadUrl: String?
}