package org.example.amiepackagerepository

import org.springframework.stereotype.Service
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.slf4j.LoggerFactory
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.Claims
import javax.crypto.SecretKey
import java.util.Date

@Service
class UserService(private val userRepository: UserRepository) {
    private val passwordEncoder = BCryptPasswordEncoder()
    private val log = LoggerFactory.getLogger(UserService::class.java)
    private val secretKey: SecretKey = Jwts.SIG.HS512.key().build()

    fun userExists(username: String): Boolean {
        return userRepository.existsByUsername(username)
    }

    fun createUser(username: String = "", password: String) {
        val hashedPassword = passwordEncoder.encode(password) ?: throw IllegalStateException("Password encoding failed")
        val user = User(
            username = username.ifBlank { "anon" },
            password = hashedPassword
        )
        log.debug("User created: {}", user.username)
        userRepository.save(user)
    }

    fun deleteUser(username: String, password: String) {
        userRepository.findByUsername(username)?.let { user ->
            if (passwordEncoder.matches(password, user.password)) {
                userRepository.delete(user)
            } else {
                log.debug("Password did not match for user: {}", username)
            }
        }
    }

    fun loginAsUser(username: String, password: String): String? {
        userRepository.findByUsername(username)?.let { user ->
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

    fun grantGuestToken(username:String): String {
        val token = Jwts.builder()
            .subject("guest")
            .claim("userId", -1L)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 60 * 60 * 1000))
            .signWith(secretKey)
            .compact()
        
        if (!userRepository.existsByUsername(username)) {
            userRepository.save(User(username = username, password = ""))
        }
        return token
    }
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
