@file:Suppress("unused")

package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.forms.Button
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.forms.*
import io.ktor.client.request.get
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.browser.window
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.jetbrains.compose.web.dom.Text

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("id_token") val idToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("refresh_expires_in") val refreshExpiresIn: Int? = null,
    @SerialName("token_type") val tokenType: String
)

@Serializable
data class JwkKey(
    val kid: String,
    val kty: String,
    val alg: String? = null,
    val use: String? = null,
    val n: String,
    val e: String
)

@Serializable
data class JwksResponse(
    val keys: List<JwkKey>
)

val httpClient = HttpClient {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
}


@Serializable
data class UserSession(
    val accessToken: String,
    val idToken: String,
    val refreshToken: String
)

/**
 * Sets the user session in localStorage.
 * Note: localStorage has a much larger limit (~5MB) compared to cookies (4KB).
 */
fun setUserSessionLocalStorage(session: UserSession) {
    val jsonString = Json.encodeToString(UserSession.serializer(), session)
    window.localStorage.setItem("USER_SESSION", jsonString)
}

fun getUserSessionFromLocalStorage(): UserSession? {
    val jsonString = window.localStorage.getItem("USER_SESSION") ?: return null
    return try {
        Json.decodeFromString(UserSession.serializer(), jsonString)
    } catch (e: Exception) {
        null
    }
}
val keycloakAuthUrl = "http://localhost:8080/realms/master/protocol/openid-connect/auth" +
        "?client_id=amie" +
        "&response_type=code" +
        "&scope=openid%20profile%20email" +
        "&redirect_uri=${URLBuilder("http://localhost:8081/oauthpage").buildString()}"

@Page("oauthpage")
@Composable
fun OAuthPage() {
    val currentUrl = Url(window.location.href)
    val authorizationCode = currentUrl.parameters["code"]
    var accessToken by remember { mutableStateOf<String?>(null) }
    var validationStatus by remember { mutableStateOf("") }

    LaunchedEffect(authorizationCode) {
        if (authorizationCode != null) {
            try {
                validationStatus = "Exchanging code for tokens..."
                val tokenResponse = exchangeCodeForTokens(authorizationCode)
                accessToken = tokenResponse.accessToken

                validationStatus = "Saving session..."

                setUserSessionLocalStorage(
                    UserSession(
                        accessToken = tokenResponse.accessToken,
                        idToken = tokenResponse.idToken,
                        refreshToken = tokenResponse.refreshToken
                    )
                )

                validationStatus = "Fetching JWKS and validating token..."
                val idToken = tokenResponse.idToken
                val isValid = validateJwt(idToken)

                if (isValid) {
                    validationStatus = "Token validated successfully!"
                    println("Access Token: ${tokenResponse.accessToken}")
                } else {
                    validationStatus = "Token validation FAILED!"
                }
            } catch (e: Exception) {
                validationStatus = "Error: ${e.message}"
                println("Error exchanging code: ${e.message}")
            }
        }
    }

    Button(onClick = {
        window.location.href = keycloakAuthUrl
    }) {
        Text("Login with Keycloak SSO")
    }

    if (validationStatus.isNotEmpty()) {
        Text(validationStatus)
    }

    if (accessToken != null) {
        Text("Logged in! Access Token: $accessToken")
    } else if (authorizationCode != null) {
        Text("Auth Code: $authorizationCode")
    }
}

/**
 * Decodes and validates JWT claims: iss, aud, exp.
 * Also fetches JWKS to find the matching public key for the token's 'kid'.
 */
suspend fun validateJwt(token: String): Boolean {
    try {
        val parts = token.split(".")
        if (parts.size != 3) return false

        // 0. Decode Header to get 'kid'
        val base64Header = parts[0].replace("-", "+").replace("_", "/")
        val decodedHeader = window.atob(base64Header)
        val headerJson = Json.parseToJsonElement(decodedHeader).jsonObject
        val kid = headerJson["kid"]?.jsonPrimitive?.content
        
        if (kid != null) {
            val jwks = getJwks()
            val matchingKey = jwks.keys.find { it.kid == kid }
            if (matchingKey != null) {
                println("Matching JWK found for kid: $kid")
                // In a production app, use matchingKey.n and matchingKey.e with WebCrypto
                // to verify parts[2] (the signature).
            } else {
                println("Validation Error: No matching JWK found for kid: $kid")
                return false
            }
        }

        // 1. Decode Payload (part index 1)
        val base64Payload = parts[1].replace("-", "+").replace("_", "/")
        val decodedPayload = window.atob(base64Payload)
        val payloadJson = Json.parseToJsonElement(decodedPayload).jsonObject

        // 2. Validate Issuer (iss)
        val issuer = payloadJson["iss"]?.jsonPrimitive?.content
        if (issuer != "http://localhost:8080/realms/master") {
            println("Validation Error: Invalid Issuer ($issuer)")
            return false
        }

        // 3. Validate Audience (aud)
        val audience = payloadJson["aud"]?.let {
            if (it is JsonArray) it.map { a -> a.jsonPrimitive.content } else listOf(it.jsonPrimitive.content)
        }
        if (audience?.contains("amie") != true) {
            println("Validation Error: Invalid Audience ($audience)")
            return false
        }

        // 4. Validate Expiration (exp)
        val expiration = payloadJson["exp"]?.jsonPrimitive?.long ?: 0
        val currentTimeInSeconds = (kotlin.js.Date.now() / 1000.0).toLong()
        if (expiration < currentTimeInSeconds) {
            println("Validation Error: Token Expired")
            return false
        }

        return true
    } catch (e: Exception) {
        println("JWT Decode Error: ${e.message}")
        return false
    }
}


suspend fun getJwks(): JwksResponse {
    val jwksEndpoint = "http://localhost:8080/realms/master/protocol/openid-connect/certs"
    return httpClient.get(jwksEndpoint).body<JwksResponse>()
}

/**
 * Exchanges an authorization code for access, ID, and refresh tokens.
 *
 * @param authorizationCode The code received from the OAuth provider.
 * @return A [TokenResponse] containing the tokens.
 */
suspend fun exchangeCodeForTokens(authorizationCode: String): TokenResponse {
    val tokenEndpoint = "http://localhost:8080/realms/master/protocol/openid-connect/token"

    val response = httpClient.submitForm(
        url = tokenEndpoint,
        formParameters = parameters {
            append("grant_type", "authorization_code")
            append("client_id", "amie")
            append("client_secret", "")
            append("code", authorizationCode)
            append("redirect_uri", "http://localhost:8081/oauthpage")
        }
    )

    return response.body<TokenResponse>()
}