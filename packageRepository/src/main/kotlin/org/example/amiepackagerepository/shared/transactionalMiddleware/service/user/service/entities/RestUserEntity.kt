package org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table


/**
 * JPA Entity representing a user in the system.
 * Contains core authentication data including username and encrypted password.
 */
@Entity
@Table(name = "users")
data class RestUserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val username: String = "",
    val password: String = "",
    // Rows that predate a column hold NULL there, so these are nullable and read as "" by [ProfileDto]
    val fullName: String? = "",
    val email: String? = "",
    val bio: String? = "",
    /** The name shown instead of the username; empty or null when none was chosen. */
    @Column(length = 64)
    val nickname: String? = "",
    /** The profile picture as it was uploaded, already validated (PNG, JPEG, GIF or WebP, at most 512 KB). */
    @Column(columnDefinition = "bytea")
    val avatar: ByteArray? = null,
    /** The picture's real type, detected from its bytes. */
    @Column(length = 32)
    val avatarType: String? = null,
    /** Pictures show only after an administrator approved them; null or false is "waiting". */
    val avatarApproved: Boolean? = null,
    /** Linked accounts, by handle (the person says who they are elsewhere; nothing is verified). */
    @Column(length = 64)
    val githubLogin: String? = "",
    @Column(length = 64)
    val huggingFaceLogin: String? = "",
) {
    val avatarShown: Boolean get() = avatar != null && avatarApproved == true
    val avatarPending: Boolean get() = avatar != null && avatarApproved != true

    /** A short tag of the current picture, for cache-busting URLs; empty when there is none. */
    val avatarVersion: String get() = avatar?.let { java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(it)).take(12) } ?: ""

    // ByteArray has no structural equality; identity by row id keeps data-class semantics sane
    override fun equals(other: Any?): Boolean = other is RestUserEntity && other.id == id && other.username == username
    override fun hashCode(): Int = 31 * id.hashCode() + username.hashCode()
    override fun toString(): String = "RestUserEntity(id=$id, username=$username)"
}

