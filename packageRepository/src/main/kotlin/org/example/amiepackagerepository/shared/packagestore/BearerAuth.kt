package org.example.amiepackagerepository.shared.packagestore

import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** The subject of a valid `Authorization: Bearer` header, or a 401. */
fun UserService.requireSubject(authHeader: String?): String {
    val token = authHeader?.removePrefix("Bearer ")?.trim().orEmpty()
    if (token.isEmpty()) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token")
    val claims = validateToken(token) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized or expired token")
    return claims.subject ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token has no subject")
}

/** The subject of the bearer token, which must be [username]; 401 or 403 otherwise. */
fun UserService.requireUser(authHeader: String?, username: String): String {
    val subject = requireSubject(authHeader)
    if (subject != username) throw ResponseStatusException(HttpStatus.FORBIDDEN, "The token belongs to another user")
    return subject
}
