package org.example.amiepackagerepository.shared.transactionalMiddleware.dto

import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity

/** A user's public profile; never carries the password hash. */
data class ProfileDto(
    val id: Long,
    val username: String,
    val fullName: String,
    val email: String,
    val bio: String,
    /** The chosen display name, or empty. */
    val nickname: String = "",
    /** Whether `GET /avatar?username=` has an approved picture; [avatarVersion] changes with every new picture. */
    val hasAvatar: Boolean = false,
    val avatarVersion: String = "",
    /** The person uploaded a picture that an administrator has not approved yet (only they see it). */
    val avatarPending: Boolean = false,
    /** Linked accounts by handle, or empty. */
    val github: String = "",
    val huggingFace: String = "",
    /** Badges administrators granted (keys of `BadgeCatalog`). */
    val badges: List<String> = emptyList(),
    /** An approved banner for the profile header (`GET /background?username=`), and one still waiting. */
    val hasBackground: Boolean = false,
    val backgroundVersion: String = "",
    val backgroundPending: Boolean = false,
) {
    /** What to call this person: the nickname, or the username. */
    val displayName: String get() = nickname.ifBlank { username }

    companion object {
        fun of(user: RestUserEntity, badges: List<String> = emptyList()) = ProfileDto(
            user.id, user.username, user.fullName.orEmpty(), user.email.orEmpty(), user.bio.orEmpty(), user.nickname.orEmpty(),
            user.avatarShown, if (user.avatarShown) user.avatarVersion else "", user.avatarPending,
            user.githubLogin.orEmpty(), user.huggingFaceLogin.orEmpty(), badges,
            user.backgroundShown, if (user.backgroundShown) user.backgroundVersion else "", user.backgroundPending,
        )
    }
}
