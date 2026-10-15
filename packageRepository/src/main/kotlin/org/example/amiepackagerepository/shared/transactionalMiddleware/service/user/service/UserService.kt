package org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import org.example.amiepackagerepository.shared.errors.ConflictException
import org.example.amiepackagerepository.shared.notes.PackageNoteRepository
import org.example.amiepackagerepository.shared.badges.UserBadgeRepository
import org.example.amiepackagerepository.shared.publications.PublicationRepository
import org.example.amiepackagerepository.shared.stats.PackageLikeRepository
import org.example.amiepackagerepository.shared.profile.Avatar
import org.example.amiepackagerepository.shared.profile.AvatarValidator
import org.example.amiepackagerepository.shared.profile.ProfileRules
import org.example.amiepackagerepository.shared.transactionalMiddleware.dto.ProfileUpdateDto
import org.example.amiepackagerepository.shared.transactionalMiddleware.entities.UserActivity
import org.example.amiepackagerepository.shared.transactionalMiddleware.repository.DeviceStatusRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.repository.UserActivityRepository
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.keycloak.KeycloakTokenVerifier
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.repository.RestUserRepository
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.Date
import javax.crypto.SecretKey

/**
 * Service for managing user accounts and authentication.
 * Handles user creation, deletion, login verification, and JWT token issuance/validation.
 *
 * @property restUserRepository The JPA repository for user data persistence.
 */
@Service
class UserService(
    private val restUserRepository: RestUserRepository,
    private val userActivityRepository: UserActivityRepository,
    private val keycloakTokenVerifier: KeycloakTokenVerifier,
    private val deviceStatusRepository: DeviceStatusRepository,
    private val packageNoteRepository: PackageNoteRepository,
    private val userBadgeRepository: UserBadgeRepository,
    private val publicationRepository: PublicationRepository,
    private val packageLikeRepository: PackageLikeRepository,
) {
    private val passwordEncoder = BCryptPasswordEncoder()
    private val log = LoggerFactory.getLogger(UserService::class.java)
    private val secretKey: SecretKey = Jwts.SIG.HS512.key().build()

    /**
     * Checks if a user exists in the database by their username.
     *
     * @param username The username to check.
     * @return True if the user exists, false otherwise.
     */
    fun userExists(username: String): Boolean {
        return restUserRepository.existsByUsername(username) ?: false
    }

    /**
     * Creates a new user with an encrypted password.
     *
     * @param username The chosen username (defaults to "anon").
     * @param password The raw password string to be encrypted.
     */
    fun createUser(username: String = "", password: String) {
        val hashedPassword = passwordEncoder.encode(password) ?: throw IllegalStateException("Password encoding failed")
        val user = RestUserEntity(
            username = username.ifBlank { "anon" },
            password = hashedPassword
        )
        log.debug("User created: {}", user.username)
        restUserRepository.save(user)
    }

    /**
     * Deletes a user from the database if the provided password matches.
     *
     * @param username The username of the account to delete.
     * @param password The raw password to verify against the stored hash.
     */
    @Transactional
    fun deleteUser(username: String, password: String) {
        restUserRepository.findByUsername(username)?.let { user ->
            if (passwordEncoder.matches(password, user.password)) {
                // Rows that point at the user go first, or Postgres refuses the delete
                deviceStatusRepository.deleteAll(deviceStatusRepository.findByUser(user))
                userActivityRepository.deleteAll(userActivityRepository.findByUserUsername(username))
                packageNoteRepository.deleteAll(packageNoteRepository.findByAuthor(user))
                userBadgeRepository.deleteAll(userBadgeRepository.findByUser(user))
                publicationRepository.deleteAll(publicationRepository.findByUserOrderByYearDescCreatedAtDesc(user))
                packageLikeRepository.deleteAll(packageLikeRepository.findByUser(user))
                restUserRepository.delete(user)
            } else {
                log.debug("Password did not match for user: {}", username)
            }
        }
    }

    /**
     * Authenticates a user and generates a JWT token if credentials are valid.
     *
     * @param username The username attempting to log in.
     * @param password The raw password to verify.
     * @return A signed JWT token string if successful, or null if authentication fails.
     */
    fun loginAsUser(username: String, password: String): String? {
        restUserRepository.findByUsername(username)?.let { user ->
            if (passwordEncoder.matches(password, user.password)) {
                log.debug("User logged in: {}", username)

                val token = Jwts.builder()
                    .subject(user.username)
                    .claim("userId", user.id)
                    .issuedAt(Date())
                    .expiration(Date(System.currentTimeMillis() + 60 * 60 * 1000))
                    .signWith(secretKey)
                    .compact()
                return token
            } else {
                log.debug("Password did not match for user: {}", username)
            }
        }
        return null
    }

    /**
     * Generates a temporary JWT token for a guest user.
     * Automatically creates a user entry in the database if it doesn't exist.
     *
     * @param username The unique guest identifier.
     * @return A signed JWT token for the guest.
     */
    fun grantGuestToken(username:String): String {
        val token = Jwts.builder()
            .subject("guest")
            .claim("userId", -1L)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 60 * 60 * 1000))
            .signWith(secretKey)
            .compact()

        if (!(restUserRepository.existsByUsername(username) ?: false)) {
            restUserRepository.save(RestUserEntity(username = username, password = ""))
        }
        return token
    }

    /**
     * Ensures a user exists and returns a JWT token for their session.
     *
     * @param username The username for whom the token is issued.
     * @return A signed JWT token.
     */
    fun grantUserToken(username: String): String {
        val user = if (restUserRepository.existsByUsername(username) ?: false) {
            restUserRepository.findByUsername(username)
        } else {
            restUserRepository.save(RestUserEntity(username = username, password = ""))
        }

        val token = Jwts.builder()
            .subject(username)
            .claim("userId", -1L)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 60 * 60 * 2000))
            .signWith(secretKey)
            .compact()

        if (!(restUserRepository.existsByUsername(username) ?: false)) {
            restUserRepository.save(RestUserEntity(username = username, password = ""))
        }
        return token
    }

    /**
     * Validates a bearer token and returns its claims.
     *
     * Two kinds of token are accepted: the HS512 tokens this service issues from `/login`, and access
     * tokens issued by the Keycloak realm (what the website's `oauthpage` and the desktop app hold after
     * signing in). For a Keycloak token the subject is the Keycloak username and a `users` row is created
     * on first sight so profiles and uploads work without a separate backend registration.
     *
     * @param token The JWT token string to validate.
     * @return The extracted [Claims] if the token is valid, or null if invalid/expired.
     */
    fun validateToken(token: String): Claims? {
        keycloakTokenVerifier.verify(token)?.let { claims ->
            ensureUser(claims.subject)
            return claims
        }
        try {
            return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: Exception) {
            log.warn("Token is invalid: {}", e.message)
            return null
        }
    }

    /** Creates the `users` row for an account that exists only in Keycloak. */
    fun ensureUser(username: String) {
        if (username.isBlank()) return
        if (!(restUserRepository.existsByUsername(username) ?: false)) {
            restUserRepository.save(RestUserEntity(username = username, password = ""))
            log.info("Created backend user '{}' for a Keycloak account", username)
        }
    }

    /**
     * Updates the profile after cleaning every field ([ProfileRules]). A nickname that is another
     * account's username or nickname is refused with [ConflictException]; a nickname or email that
     * breaks the rules with [IllegalArgumentException].
     */
    fun updateProfile(username: String, profileDto: ProfileUpdateDto) {
        val user = restUserRepository.findByUsername(username) ?: throw IllegalArgumentException("User not found")
        val nickname = ProfileRules.nickname(profileDto.nickname).orEmpty()
        if (nickname.isNotEmpty() && !ProfileRules.sameName(nickname, username)) {
            restUserRepository.findByUsernameIgnoreCase(nickname)?.takeIf { it.id != user.id }?.let { throw ConflictException("That nickname is another account's name") }
            restUserRepository.findByNicknameIgnoreCase(nickname)?.takeIf { it.id != user.id }?.let { throw ConflictException("That nickname is taken") }
        }
        val updatedUser = user.copy(
            fullName = ProfileRules.line(profileDto.fullName, ProfileRules.FULL_NAME_MAX),
            email = ProfileRules.email(profileDto.email),
            bio = ProfileRules.text(profileDto.bio, ProfileRules.BIO_MAX),
            nickname = nickname,
            githubLogin = ProfileRules.githubLogin(profileDto.github).orEmpty(),
            huggingFaceLogin = ProfileRules.huggingFaceLogin(profileDto.huggingFace).orEmpty(),
        )
        restUserRepository.save(updatedUser)
        logActivity(username, "UPDATE_PROFILE", "Updated profile for $username")
    }

    /** Every account, for the admin panel. */
    fun allUsers(): List<RestUserEntity> = restUserRepository.findAll().sortedBy { it.username.lowercase() }

    /** An administrator takes a nickname away; false when the account has none or does not exist. */
    fun adminClearNickname(username: String): Boolean {
        val user = restUserRepository.findByUsername(username) ?: return false
        if (user.nickname.isNullOrEmpty()) return false
        restUserRepository.save(user.copy(nickname = ""))
        logActivity(username, "ADMIN_CLEAR_NICKNAME", "An administrator removed the nickname '${user.nickname}'")
        return true
    }

    /** An administrator removes an account with everything that points at it; false when there is none. */
    @Transactional
    fun adminDeleteUser(username: String): Boolean {
        val user = restUserRepository.findByUsername(username) ?: return false
        deviceStatusRepository.deleteAll(deviceStatusRepository.findByUser(user))
        userActivityRepository.deleteAll(userActivityRepository.findByUserUsername(username))
        packageNoteRepository.deleteAll(packageNoteRepository.findByAuthor(user))
        userBadgeRepository.deleteAll(userBadgeRepository.findByUser(user))
        publicationRepository.deleteAll(publicationRepository.findByUserOrderByYearDescCreatedAtDesc(user))
        packageLikeRepository.deleteAll(packageLikeRepository.findByUser(user))
        restUserRepository.delete(user)
        return true
    }

    /** Stores a profile picture after [AvatarValidator] accepted it. */
    fun setAvatar(username: String, bytes: ByteArray): Avatar {
        val user = restUserRepository.findByUsername(username) ?: throw IllegalArgumentException("User not found")
        val avatar = AvatarValidator.validate(bytes)
        // A new picture waits for an administrator; until then only its owner and the admins see it
        restUserRepository.save(user.copy(avatar = avatar.bytes, avatarType = avatar.contentType, avatarApproved = false))
        logActivity(username, "UPDATE_AVATAR", "Uploaded a profile picture (${avatar.contentType}, ${avatar.bytes.size} bytes), awaiting approval")
        return avatar
    }

    /** The stored picture when it is approved, or, with [includePending], also one still waiting; null otherwise. */
    fun getAvatar(username: String, includePending: Boolean = false): Avatar? {
        val user = restUserRepository.findByUsername(username) ?: return null
        val bytes = user.avatar ?: return null
        if (user.avatarApproved != true && !includePending) return null
        return Avatar(bytes, user.avatarType ?: AvatarValidator.detect(bytes) ?: "application/octet-stream")
    }

    /** An administrator approves the waiting picture; false when there is none waiting. */
    fun approveAvatar(username: String): Boolean {
        val user = restUserRepository.findByUsername(username) ?: return false
        if (user.avatar == null || user.avatarApproved == true) return false
        restUserRepository.save(user.copy(avatarApproved = true))
        logActivity(username, "AVATAR_APPROVED", "An administrator approved the profile picture")
        return true
    }

    /** Stores a profile background (up to 2 MB, 4096 px), waiting for approval like the picture. */
    fun setBackground(username: String, bytes: ByteArray): Avatar {
        val user = restUserRepository.findByUsername(username) ?: throw IllegalArgumentException("User not found")
        val image = AvatarValidator.validate(bytes, AvatarValidator.BACKGROUND_MAX_BYTES, AvatarValidator.BACKGROUND_MAX_SIDE)
        restUserRepository.save(user.copy(background = image.bytes, backgroundType = image.contentType, backgroundApproved = false))
        logActivity(username, "UPDATE_BACKGROUND", "Uploaded a profile background (${image.contentType}, ${image.bytes.size} bytes), awaiting approval")
        return image
    }

    fun getBackground(username: String, includePending: Boolean = false): Avatar? {
        val user = restUserRepository.findByUsername(username) ?: return null
        val bytes = user.background ?: return null
        if (user.backgroundApproved != true && !includePending) return null
        return Avatar(bytes, user.backgroundType ?: AvatarValidator.detect(bytes) ?: "application/octet-stream")
    }

    fun clearBackground(username: String): Boolean {
        val user = restUserRepository.findByUsername(username) ?: throw IllegalArgumentException("User not found")
        if (user.background == null) return false
        restUserRepository.save(user.copy(background = null, backgroundType = null, backgroundApproved = null))
        logActivity(username, "UPDATE_BACKGROUND", "Removed the profile background")
        return true
    }

    fun approveBackground(username: String): Boolean {
        val user = restUserRepository.findByUsername(username) ?: return false
        if (user.background == null || user.backgroundApproved == true) return false
        restUserRepository.save(user.copy(backgroundApproved = true))
        logActivity(username, "BACKGROUND_APPROVED", "An administrator approved the profile background")
        return true
    }

    /** The accounts whose background waits for approval. */
    fun pendingBackgrounds(): List<RestUserEntity> = restUserRepository.findAll().filter { it.backgroundPending }.sortedBy { it.username.lowercase() }

    /** The accounts whose picture waits for approval. */
    fun pendingAvatars(): List<RestUserEntity> = restUserRepository.findAll().filter { it.avatarPending }.sortedBy { it.username.lowercase() }

    /** Removes the picture; false when there was none. */
    fun clearAvatar(username: String): Boolean {
        val user = restUserRepository.findByUsername(username) ?: throw IllegalArgumentException("User not found")
        if (user.avatar == null) return false
        restUserRepository.save(user.copy(avatar = null, avatarType = null, avatarApproved = null))
        logActivity(username, "UPDATE_AVATAR", "Removed the profile picture")
        return true
    }

    /**
     * Logs a user activity.
     */
    fun logActivity(username: String, actionType: String, details: String = "") {
        val user = restUserRepository.findByUsername(username)
        val activity = UserActivity(
            actionType = actionType,
            details = details,
            timestamp = LocalDateTime.now(),
            user = user
        )
        userActivityRepository.save(activity)
    }

    /**
     * Retrieves the profile of a user.
     */
    fun getProfile(username: String): RestUserEntity? {
        return restUserRepository.findByUsername(username)
    }
}
