package org.example.amiepackagerepository.shared.admin

import io.jsonwebtoken.Claims
import org.example.amiepackagerepository.shared.packagestore.requireSubject
import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.UserService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/** Who is acting as administrator: the Keycloak username and the roles the token carries. */
data class AdminIdentity(val username: String, val roles: Set<String>)

/**
 * Admin access is a Keycloak matter: the bearer must be a token of the realm whose `realm_access.roles`
 * (or `resource_access.<client>.roles`) contain [adminRole] (`amie.admin-role`, default `admin`).
 * Tokens this backend issues itself carry no roles and can never be admin. The website's server checks
 * the same claim before it forwards an admin call; this check runs again here so a call that reaches the
 * backend directly is held to the same rule.
 */
@Component
class AdminAccess(
    private val userService: UserService,
    @Value("\${amie.admin-role:admin}") val adminRole: String,
    @Value("\${amie.keycloak.client-id:amie}") private val clientId: String,
) {
    /** The admin behind [authHeader], or 401 / 403. */
    fun require(authHeader: String?): AdminIdentity {
        val subject = userService.requireSubject(authHeader)
        val claims = userService.validateToken(authHeader?.removePrefix("Bearer ")?.trim().orEmpty())
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized or expired token")
        val roles = rolesOf(claims, clientId)
        if (adminRole !in roles) throw ResponseStatusException(HttpStatus.FORBIDDEN, "The $adminRole role is required")
        return AdminIdentity(subject, roles)
    }

    /** Whether [authHeader] is an administrator's token; never throws. */
    fun isAdmin(authHeader: String?): Boolean = runCatching { require(authHeader); true }.getOrDefault(false)

    companion object {
        /** Realm roles and the client's roles, as Keycloak writes them into an access token. */
        fun rolesOf(claims: Claims, clientId: String): Set<String> {
            val roles = mutableSetOf<String>()
            (claims["realm_access"] as? Map<*, *>)?.get("roles")?.let { r -> (r as? Collection<*>)?.forEach { it?.toString()?.let(roles::add) } }
            ((claims["resource_access"] as? Map<*, *>)?.get(clientId) as? Map<*, *>)?.get("roles")?.let { r -> (r as? Collection<*>)?.forEach { it?.toString()?.let(roles::add) } }
            return roles
        }
    }
}
