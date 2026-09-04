package org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.entities.RestUserEntity
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.repository.RestUserRepository
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.util.Date
import javax.crypto.SecretKey

/**
 * Service for managing user accounts and authentication.
 * Handles user creation, deletion, login verification, and JWT token issuance/validation.
 *
 * @property restUserRepository The JPA repository for user data persistence.
 */
@Service
class UserService(private val restUserRepository: RestUserRepository) {
    private val passwordEncoder = BCryptPasswordEncoder()
    private val log = LoggerFactory.getLogger(UserService::class.java)
    private val secretKey: SecretKey = Jwts.SIG.HS512.key().build() // Keep existing for now, but will improve logging

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
        restUserRepository.save(RestUserEntity(username = user.username, password = user.password))
    }

    /**
     * Deletes a user from the database if the provided password matches.
     *
     * @param username The username of the account to delete.
     * @param password The raw password to verify against the stored hash.
     */
    fun deleteUser(username: String, password: String) {
        restUserRepository.findByUsername(username)?.let { user ->
            if (passwordEncoder.matches(password, user.password)) {
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
     * Validates a JWT token and returns its claims.
     *
     * @param token The JWT token string to validate.
     * @return The extracted [Claims] if the token is valid, or null if invalid/expired.
     */
    fun validateToken(token: String): Claims? {
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
}
