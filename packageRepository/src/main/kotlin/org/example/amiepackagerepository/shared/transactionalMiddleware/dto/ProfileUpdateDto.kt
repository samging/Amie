package org.example.amiepackagerepository.shared.transactionalMiddleware.dto

import kotlinx.serialization.Serializable

/**
 * Data Transfer Object for profile updates.
 */
@Serializable
data class ProfileUpdateDto(
    val fullName: String,
    val email: String,
    val bio: String
)
