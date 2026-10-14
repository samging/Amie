package org.example.app.auth

import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.example.app.components.ClientCache
import org.example.app.components.Http
import org.example.app.config.AmieConfig
import kotlin.js.Date

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Int = 60,
    @SerialName("token_type") val tokenType: String = "Bearer"
)

@Serializable
data class DeviceStart(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("expires_in") val expiresIn: Int = 600,
    val interval: Int = 5
)

sealed class SignIn {
    class Success(val session: UserSession) : SignIn()
    class Failure(val message: String) : SignIn()
}

sealed class DevicePoll {
    object Pending : DevicePoll()
    class Done(val result: SignIn) : DevicePoll()
}

/**
 * Signs people in and keeps them signed in. Keycloak tokens are verified in the browser (signature,
 * issuer, expiry, client) before a session is accepted; the backend verifies them again on every call.
 */
object AuthClient {
    private val tokenUrl get() = AmieConfig.api("keycloak/token")
    private var verifiedToken: String? = null

    suspend fun signInWithPassword(username: String, password: String): SignIn {
        if (username.isBlank() || password.isEmpty()) return SignIn.Failure("Enter your username and password.")
        val result = Http.postForm(tokenUrl, mapOf("username" to username.trim(), "password" to password))
        return establish(result, wrongCredentials = "Wrong username or password.")
    }

    suspend fun exchangeCode(code: String): SignIn {
        val result = Http.postForm(tokenUrl, mapOf("code" to code, "redirect_uri" to AmieConfig.oauthRedirectUri))
        return establish(result, wrongCredentials = "The sign-in link has expired. Please try again.")
    }

    /** Starts the "scan to sign in" flow; null when the realm does not offer it. */
    suspend fun startDeviceSignIn(): DeviceStart? {
        val result = runCatching { Http.postForm(AmieConfig.api("keycloak/device"), emptyMap()) }.getOrNull() ?: return null
        if (!result.ok) return null
        return runCatching { jwtJson.decodeFromString(DeviceStart.serializer(), result.text) }.getOrNull()
    }

    suspend fun pollDeviceSignIn(deviceCode: String): DevicePoll {
        val result = runCatching { Http.postForm(tokenUrl, mapOf("device_code" to deviceCode)) }.getOrNull()
            ?: return DevicePoll.Done(SignIn.Failure("The sign-in service is unavailable."))
        if (result.ok) return DevicePoll.Done(establish(result, wrongCredentials = "Sign-in was refused."))
        val error = runCatching { jwtJson.parseToJsonElement(result.text).jsonObject.string("error") }.getOrNull()
        return when (error) {
            "authorization_pending", "slow_down" -> DevicePoll.Pending
            "expired_token" -> DevicePoll.Done(SignIn.Failure("The code expired. Show a new one."))
            "access_denied" -> DevicePoll.Done(SignIn.Failure("Sign-in was declined on the phone."))
            else -> DevicePoll.Done(SignIn.Failure("Sign-in did not complete."))
        }
    }

    /** Signs in with one of the backend's own accounts (the registration form on this site). */
    suspend fun signInLocal(username: String, password: String): SignIn {
        if (username.isBlank() || password.isEmpty()) return SignIn.Failure("Enter your username and password.")
        val body = """{"username":${quote(username.trim())},"password":${quote(password)}}"""
        val result = runCatching { Http.postJson(AmieConfig.api("login"), body) }.getOrNull()
            ?: return SignIn.Failure("The sign-in service is unavailable.")
        if (result.status == 401) return SignIn.Failure("Wrong username or password.")
        if (!result.ok) return SignIn.Failure("Sign-in is unavailable right now.")
        val token = runCatching { jwtJson.parseToJsonElement(result.text).jsonObject.string("token") }.getOrNull()
            ?: return SignIn.Failure("Sign-in is unavailable right now.")
        val claims = Jwt.decodePayload(token)
        val expiresAt = claims?.get("exp")?.jsonPrimitive?.intOrNull?.let { it * 1000.0 } ?: (Date.now() + ClientCache.HOUR)
        val session = UserSession(username.trim(), token, expiresAt = expiresAt, source = SessionSource.LOCAL)
        AuthSession.save(session)
        ClientCache.removePrefix("me:")
        return SignIn.Success(session)
    }

    private suspend fun establish(result: org.example.app.components.HttpResult, wrongCredentials: String): SignIn {
        if (result.status == 401 || result.status == 400) {
            // Keycloak refusing the *client* (no or wrong client secret on the site server) is not the person's fault
            val error = runCatching { jwtJson.parseToJsonElement(result.text).jsonObject.string("error") }.getOrNull()
            if (error == "unauthorized_client" || error == "invalid_client") {
                console.error("sign-in: Keycloak refused the amie client ($error); set KEYCLOAK_CLIENT_SECRET on the site server")
                return SignIn.Failure("Sign-in is not available right now.")
            }
            return SignIn.Failure(wrongCredentials)
        }
        if (!result.ok) return SignIn.Failure("The sign-in service is unavailable.")
        val tokens = runCatching { jwtJson.decodeFromString(TokenResponse.serializer(), result.text) }.getOrNull()
            ?: return SignIn.Failure("The sign-in service gave an unexpected answer.")

        val access = Jwt.verify(tokens.accessToken, requireAudience = false)
        if (!access.valid) return SignIn.Failure("The sign-in could not be verified (${access.reason}).")
        tokens.idToken?.let { idToken ->
            val id = Jwt.verify(idToken, requireAudience = true)
            if (!id.valid) return SignIn.Failure("The sign-in could not be verified (${id.reason}).")
        }
        val username = access.username ?: return SignIn.Failure("The sign-in did not say who you are.")
        val session = UserSession(
            username = username,
            accessToken = tokens.accessToken,
            idToken = tokens.idToken.orEmpty(),
            refreshToken = tokens.refreshToken.orEmpty(),
            expiresAt = access.expiresAtMs ?: (Date.now() + tokens.expiresIn * 1000.0),
            source = SessionSource.KEYCLOAK,
            roles = access.roles
        )
        AuthSession.save(session)
        verifiedToken = session.accessToken
        ClientCache.removePrefix("me:")
        return SignIn.Success(session)
    }

    /** A fresh access token for a Keycloak session, using the refresh token; null when that fails. */
    suspend fun refresh(session: UserSession): UserSession? {
        if (session.refreshToken.isEmpty()) return null
        val result = runCatching { Http.postForm(tokenUrl, mapOf("refresh_token" to session.refreshToken)) }.getOrNull() ?: return null
        return when (val outcome = establish(result, wrongCredentials = "")) {
            is SignIn.Success -> outcome.session
            is SignIn.Failure -> null
        }
    }

    /**
     * The signed-in person's name, or null. A Keycloak token is verified once per page load and
     * refreshed when it ran out; a backend token is checked against the backend (cached for a minute).
     */
    suspend fun currentUser(): String? {
        var session = AuthSession.current() ?: return null
        if (session.source == SessionSource.KEYCLOAK) {
            if (session.expired || (verifiedToken != session.accessToken && !Jwt.verify(session.accessToken, false).valid)) {
                session = refresh(session) ?: run { signOutLocally(); return null }
            }
            verifiedToken = session.accessToken
            return session.username
        }
        val check = runCatching {
            ClientCache.cached("me:${session.accessToken.takeLast(24)}", ClientCache.MINUTE) {
                Http.get(AmieConfig.backend("dashboard"), Http.bearer(session.accessToken), noStore = true).status.toString()
            }
        }.getOrNull()
        if (check == "401") {
            signOutLocally()
            return null
        }
        return session.username
    }

    /** A token to send to the backend, refreshed first when it ran out. */
    suspend fun accessToken(): String? {
        currentUser() ?: return null
        return AuthSession.accessToken
    }

    fun signOutLocally() {
        verifiedToken = null
        AuthSession.clear()
        ClientCache.removePrefix("me:")
        ClientCache.removePrefix("profile:")
    }

    /** Ends the session here and, for Keycloak, on the realm too (which sends the browser back home). */
    fun signOut() {
        val session = AuthSession.current()
        signOutLocally()
        if (session?.source == SessionSource.KEYCLOAK) {
            window.location.href = AmieConfig.logoutUrl(session.idToken)
        } else {
            window.location.href = "/"
        }
    }

    private fun quote(value: String): String = JSON.stringify(value)

    /** Waits the polling interval the realm asked for. */
    suspend fun waitInterval(seconds: Int) = delay(seconds.coerceAtLeast(1) * 1000L)
}
