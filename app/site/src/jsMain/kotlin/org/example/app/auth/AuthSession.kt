package org.example.app.auth

import kotlinx.browser.window
import kotlinx.serialization.Serializable
import kotlin.js.Date

/** How the person signed in: with the Keycloak realm or with one of the backend's own accounts. */
enum class SessionSource { KEYCLOAK, LOCAL }

@Serializable
data class UserSession(
    val username: String,
    val accessToken: String,
    val idToken: String = "",
    val refreshToken: String = "",
    /** When the access token stops being valid, in milliseconds since the epoch */
    val expiresAt: Double = 0.0,
    val source: SessionSource = SessionSource.KEYCLOAK,
    /** Realm and client roles from the access token (empty for local accounts) */
    val roles: List<String> = emptyList()
) {
    val expired: Boolean get() = expiresAt > 0 && expiresAt <= Date.now()
    val isAdmin: Boolean get() = ADMIN_ROLE in roles

    companion object {
        const val ADMIN_ROLE = "admin"
    }
}

/**
 * The signed-in person's tokens in localStorage. The older keys (`auth_token`, `token`, `username`) are
 * kept in step so nothing else on the site has to change.
 */
object AuthSession {
    private const val KEY = "amie.session"

    fun current(): UserSession? {
        val stored = runCatching { window.localStorage.getItem(KEY) }.getOrNull()
        if (stored != null) {
            runCatching { jwtJson.decodeFromString(UserSession.serializer(), stored) }.getOrNull()?.let { return it }
        }
        // A session written by an earlier version of the site
        val legacyToken = window.localStorage.getItem("auth_token") ?: window.localStorage.getItem("token") ?: return null
        val username = window.localStorage.getItem("username") ?: Jwt.decodePayload(legacyToken)?.username() ?: return null
        val claims = Jwt.decodePayload(legacyToken)
        val isKeycloak = claims?.string("iss")?.contains("/realms/") == true
        val session = UserSession(
            username = username,
            accessToken = legacyToken,
            expiresAt = claims?.get("exp")?.let { (it.toString().toDoubleOrNull() ?: 0.0) * 1000 } ?: 0.0,
            source = if (isKeycloak) SessionSource.KEYCLOAK else SessionSource.LOCAL
        )
        save(session)
        return session
    }

    fun save(session: UserSession) {
        runCatching {
            window.localStorage.setItem(KEY, jwtJson.encodeToString(UserSession.serializer(), session))
            window.localStorage.setItem("auth_token", session.accessToken)
            window.localStorage.setItem("token", session.accessToken)
            window.localStorage.setItem("username", session.username)
        }
    }

    fun clear() {
        runCatching {
            listOf(KEY, "auth_token", "token", "username", "USER_SESSION").forEach { window.localStorage.removeItem(it) }
        }
    }

    val username: String? get() = current()?.username
    val accessToken: String? get() = current()?.accessToken
    val isSignedIn: Boolean get() = current() != null
    val isAdmin: Boolean get() = current()?.isAdmin == true

    private fun kotlinx.serialization.json.JsonObject.username(): String? =
        string("preferred_username") ?: string("sub")
}
