package org.example.amiepackagerepository.shared.transactionalMiddleware.dto

import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity

/** A user's public profile; never carries the password hash. */
data class ProfileDto(
    val id: Long,
    val username: String,
    val fullName: String,
    val email: String,
    val bio: String,
) {
    companion object {
        fun of(user: RestUserEntity) = ProfileDto(user.id, user.username, user.fullName, user.email, user.bio)
    }
}
