package org.example.amiepackagerepository.website

import com.google.api.services.drive.Drive
import org.example.amiepackagerepository.shared.integration.github.dto.GithubContentResponseDto
import org.example.amiepackagerepository.shared.integration.github.service.GithubService
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.TransactionalStatusRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * REST Controller for user-facing website interactions.
 * Handles registration, login, dashboard validation, and package querying.
 *
 * Provides endpoints for:
 * - User registration and dashboard initialization.
 * - JWT-based login for standard and guest users.
 * - Token validation for protected resources.
 * - Searching and listing user packages from GitHub.
 */
@CrossOrigin(origins = ["\${amie.cors.allowed-origins}"])
@RestController
class UserController(
    private val driveService: Drive,
    private val simpleService: GithubService,
    private val userService: UserService,
    private val deviceService: TransactionalStatusRepository
) {
    private val logger = LoggerFactory.getLogger(UserController::class.java)

    /**
     * Registers a new user and creates their GitHub dashboard.
     *
     * @param registerRequest A map containing "username" and "password".
     * @return A success message.
     * @throws ResponseStatusException 409 if username exists, 400 if fields missing.
     */
    @PostMapping("/register")
    fun register(@RequestBody registerRequest: Map<String, String>): String {
        val username = registerRequest["username"] ?: throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Username required"
        )
        logger.info("--- [POST /register] START (user: {}) ---", username)
        val password = registerRequest["password"] ?: throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Password required"
        )

        if (userService.userExists(username)) {
            logger.warn("Register Failed: Username '{}' already exists", username)
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Username is already assigned to different account"
            )
        }

        userService.createUser(username, password)
        simpleService.createUserDashboard(username = username)

        logger.info("User registered successfully: {}", username)
        logger.info("--- [POST /register] END ---")
        return "User registered successfully"
    }

    /**
     * Authenticates a user or guest and returns a JWT token.
     *
     * @param loginRequest A map containing "username" and "password".
     * @return A map containing the "token".
     * @throws ResponseStatusException 401 if authentication fails.
     */
    @PostMapping("/login")
    fun login(@RequestBody loginRequest: Map<String, String>): Map<String, String> {
        logger.info("--- [POST /login] START ---")
        val username = loginRequest["username"] ?: "guest"
        val password = loginRequest["password"] ?: ""
        logger.info("DEBUG: Login attempt for user: $loginRequest | username: $username | password: $password")
        println("DEBUG: Login attempt for user: $username")

        val token = userService.loginAsUser(username, password)
            ?: if (username.startsWith("guest-") && password == "") {
                println("GUEST TOKEN GENERATED")
                userService.grantGuestToken(username)
            } else {
                logger.warn("Login Failed: Invalid credentials for '{}'", username)
                throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
            }

        println("DEBUG: Login successful for $username, generating dashboard...")
        try {
            userService.grantUserToken(username)
            println("USER TOKEN GENERATED")
            simpleService.createUserDashboard(username = username)
        } catch (e: Exception) {
            logger.error("Dashboard creation non-fatal error: {}", e.message)
        }

        println("DEBUG: Returning token for $username")
        logger.info("--- [POST /login] END ---")
        return mapOf("token" to token)
    }

    /**
     * Logs a login error to GitHub for a specific user.
     *
     * @param request A map containing "error" and "username".
     */
    @PostMapping("/handle-login-error")
    fun handleError(@RequestBody request: Map<String, String>) {
        val error = request["error"] ?: "Unknown"
        val username = request["username"] ?: "unknown"
        logger.info("--- [POST /handle-login-error] (user: {}, error: {}) ---", username, error)
        simpleService.writeError(username, "LoginError", error)
    }

    /**
     * Validates a JWT token and returns a welcome message.
     *
     * @param authHeader The "Authorization" header containing the Bearer token.
     * @return A welcome message string.
     * @throws ResponseStatusException 401 if token is invalid.
     */
    @GetMapping("/dashboard")
    fun validateToken(@RequestHeader("Authorization") authHeader: String): String {
        logger.info("--- [GET /dashboard] START ---")
        val token = authHeader.removePrefix("Bearer ")

        val claims = userService.validateToken(token)
            ?: run {
                logger.warn("Token validation failed")
                throw ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Unauthorized or expired token"
                )
            }
        val username = claims.subject
        val userId = (claims["userId"] as? Number)?.toLong() ?: -1L

        logger.info("Dashboard validation successful for user: {} (ID: {})", username, userId)
        logger.info("--- [GET /dashboard] END ---")
        return "Welcome to your dashboard, $username (ID: $userId)!"
    }

    /**
     * Deletes a user account.
     *
     * @param username The username of the account to delete.
     * @param password The account password for verification.
     */
    @DeleteMapping("/user")
    fun deleteUser(@RequestParam username: String, @RequestParam password: String) {
        logger.info("--- [DELETE /user] (user: {}) ---", username)
        userService.deleteUser(username, password)
    }

    /**
     * Searches for files in the GitHub repository based on a query.
     *
     * @param query The search term (supports '*ext' for extension search).
     * @return A result object matching the query.
     */
    @GetMapping("/query")
    fun searchFiles(@RequestParam query: String): Any {
        logger.info("--- [GET /query] START (query: {}) ---", query)
        return simpleService.queryFilesGithub(query) ?: emptyList<Any>().also { logger.info("--- [GET /query] END ---") }
    }

    /**
     * Lists all packages (non-markdown files) for a specific user from GitHub.
     *
     * @param username The username whose packages are requested.
     * @return A list of package metadata objects.
     */
    @GetMapping("/user-packages")
    fun getUserPackages(@RequestParam username: String): List<GithubContentResponseDto> {
        logger.info("--- [GET /user-packages] START (user: {}) ---", username)
        return simpleService.listUserPackages(username).also { logger.info("--- [GET /user-packages] END ---") }
    }
}