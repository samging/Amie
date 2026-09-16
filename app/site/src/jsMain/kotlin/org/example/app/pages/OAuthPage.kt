@file:Suppress("unused")

package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.core.Page
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
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("id_token") val idToken: String? = null,
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

val jsonConfig = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

val httpClient = HttpClient {
    install(ContentNegotiation) {
        json(jsonConfig)
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
    val jsonString = jsonConfig.encodeToString(UserSession.serializer(), session)
    window.localStorage.setItem("USER_SESSION", jsonString)
}

fun getUserSessionFromLocalStorage(): UserSession? {
    val jsonString = window.localStorage.getItem("USER_SESSION") ?: return null
    return try {
        jsonConfig.decodeFromString(UserSession.serializer(), jsonString)
    } catch (e: Exception) {
        null
    }
}
val keycloakAuthUrl = "http://192.168.1.114:8080/realms/master/protocol/openid-connect/auth" +
        "?client_id=amie" +
        "&response_type=code" +
        "&scope=openid%20profile%20email" +
        "&redirect_uri=${URLBuilder("http://192.168.1.114:8081/oauthpage").buildString()}"

@Page("oauthpage")
@Composable
fun OAuthPage() {
    val currentUrl = Url(window.location.href)
    val authorizationCode = currentUrl.parameters["code"]
    val username = currentUrl.parameters["username"]
    val password = currentUrl.parameters["password"]

    var accessToken by remember { mutableStateOf<String?>(null) }
    var validationStatus by remember { mutableStateOf("") }

    LaunchedEffect(authorizationCode, username, password) {
        println("LaunchedEffect triggered. Checking credentials...")
        println(" - authorizationCode: $authorizationCode")
        println(" - username: $username")
        println(" - password: ${if (password != null) "****" else "null"}")

        if (authorizationCode != null || (username != null && password != null)) {
            println("Credentials found. Starting token exchange flow.")
            try {
                validationStatus = "Exchanging credentials for tokens..."
                println("Status Update: $validationStatus")
                
                println("STEP 1: Starting exchangeCodeForTokens...")
                val tokenResponse = exchangeCodeForTokens(authorizationCode, username, password)
                accessToken = tokenResponse.accessToken
                println("STEP 1 SUCCESS: Received TokenResponse")

                validationStatus = "Saving session to LocalStorage..."
                println("STEP 2: $validationStatus")

                setUserSessionLocalStorage(
                    UserSession(
                        accessToken = tokenResponse.accessToken,
                        idToken = tokenResponse.idToken ?: "",
                        refreshToken = tokenResponse.refreshToken
                    )
                )
                println("STEP 2 SUCCESS: Session saved.")

                validationStatus = "Fetching JWKS and validating ID Token..."
                println("STEP 3: $validationStatus")
                
                val idToken = tokenResponse.idToken
                if (idToken != null) {
                    println(" - ID Token to validate: ${idToken.take(20)}...")
                    val isValid = validateJwt(idToken)

                    if (isValid) {
                        validationStatus = "Token validated successfully!"
                        println("STEP 3 SUCCESS: Token validation successful.")
                    } else {
                        validationStatus = "Token validation FAILED!"
                        println("STEP 3 FAILURE: Token validation failed.")
                    }
                } else {
                    validationStatus = "No ID Token received. Skipping token validation."
                    println("STEP 3 SKIPPED: id_token was null in response.")
                }

                println("AUTHORIZATION SUCCESSFUL: Redirecting to main page (/) and storing session metadata.")
                window.localStorage.setItem("username", username ?: "sso_user")
                window.localStorage.setItem("token", tokenResponse.accessToken)
                window.localStorage.setItem("auth_token", tokenResponse.accessToken)
                
                validationStatus = "Redirecting to application home..."
                window.location.href = "/"
            } catch (e: Exception) {
                validationStatus = "Error: ${e.message}"
                println("CRITICAL ERROR during token exchange: ${e.message}")
                e.printStackTrace()
            }
        } else {
            println("No credentials found in URL.")
            val session = getUserSessionFromLocalStorage()
            if (session == null) {
                validationStatus = "No session found. Redirecting to Keycloak SSO..."
                println("Status Update: $validationStatus")
                println("Redirecting to: $keycloakAuthUrl")
                window.location.href = keycloakAuthUrl
            } else {
                validationStatus = "Active session found. Welcome back!"
                println("Status Update: $validationStatus")
                accessToken = session.accessToken
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.px),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            H1 { Text("OAuth Status") }

            /*
            Button(onClick = {
                window.location.href = keycloakAuthUrl
            }) {
                Text("Login with Keycloak SSO")
            }
            */

            if (validationStatus.isNotEmpty()) {
                Div(attrs = Modifier.margin(top = 16.px).toAttrs()) {
                    Text("Status: $validationStatus")
                }
            }

            if (accessToken != null) {
                Div(attrs = Modifier.margin(top = 16.px).toAttrs()) {
                    Text("Logged in! Access Token: ${accessToken?.take(10)}...")
                }
            } else if (authorizationCode != null) {
                Div(attrs = Modifier.margin(top = 16.px).toAttrs()) {
                    Text("Auth Code Received: $authorizationCode")
                }
            } else if (username != null) {
                Div(attrs = Modifier.margin(top = 16.px).toAttrs()) {
                    Text("Attempting login for user: $username")
                }
            }

            // Fallback button if nothing is happening
            if (validationStatus.isEmpty() && accessToken == null) {
                Button(
                    attrs = Modifier.margin(top = 24.px).toAttrs {
                        onClick { window.location.href = keycloakAuthUrl }
                    }
                ) {
                    Text("Login with Keycloak SSO")
                }
            }
        }
    }
}

/**
 * Decodes and validates JWT claims: iss, aud, exp.
 * Also fetches JWKS to find the matching public key for the token's 'kid'.
 */
suspend fun validateJwt(token: String): Boolean {
    println("validateJwt called with token: ${token.take(15)}...")
    try {
        val parts = token.split(".")
        if (parts.size != 3) {
            println("Validation Error: Invalid JWT structure (parts size = ${parts.size})")
            return false
        }

        // 0. Decode Header to get 'kid'
        val base64Header = parts[0].replace("-", "+").replace("_", "/")
        val decodedHeader = window.atob(base64Header)
        val headerJson = Json.parseToJsonElement(decodedHeader).jsonObject
        val kid = headerJson["kid"]?.jsonPrimitive?.content
        println("JWT Header decoded. Found 'kid': $kid")
        
        if (kid != null) {
            println("Fetching JWKS endpoints to match kid...")
            val jwks = getJwks()
            val matchingKey = jwks.keys.find { it.kid == kid }
            if (matchingKey != null) {
                println("Matching JWK found for kid: $kid (alg: ${matchingKey.alg}, kty: ${matchingKey.kty})")
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
        println("Validating Issuer: $issuer")
        if (issuer != "http://192.168.1.114:8080/realms/master") {
            println("Validation Error: Invalid Issuer ($issuer)")
            return false
        }

        // 3. Validate Audience (aud)
        val audience = payloadJson["aud"]?.let {
            if (it is JsonArray) it.map { a -> a.jsonPrimitive.content } else listOf(it.jsonPrimitive.content)
        }
        println("Validating Audience: $audience")
        if (audience?.contains("amie") != true) {
            println("Validation Error: Invalid Audience ($audience)")
            return false
        }

        // 4. Validate Expiration (exp)
        val expiration = payloadJson["exp"]?.jsonPrimitive?.long ?: 0
        val currentTimeInSeconds = (kotlin.js.Date.now() / 1000.0).toLong()
        println("Validating Expiration: token exp = $expiration, current time = $currentTimeInSeconds")
        if (expiration < currentTimeInSeconds) {
            println("Validation Error: Token Expired")
            return false
        }

        println("validateJwt completely successful!")
        return true
    } catch (e: Exception) {
        println("JWT Decode/Validation Error Exception: ${e.message}")
        e.printStackTrace()
        return false
    }
}


suspend fun getJwks(): JwksResponse {
    val jwksEndpoint = "http://192.168.1.114:8080/realms/master/protocol/openid-connect/certs"
    println("getJwks calling endpoint: $jwksEndpoint")
    println("\n=== [CURL LOG] ===")
    println("curl -X GET '$jwksEndpoint'")
    println("==================\n")
    try {
        val result = httpClient.get(jwksEndpoint).body<JwksResponse>()
        println("getJwks successfully retrieved ${result.keys.size} public keys.")
        return result
    } catch (e: Exception) {
        println("Error fetching JWKS from endpoint: ${e.message}")
        e.printStackTrace()
        throw e
    }
}

/**
 * Exchanges an authorization code or credentials for access, ID, and refresh tokens.
 *
 * @param authorizationCode The code received from the OAuth provider (optional if using password grant).
 * @param username The username for password grant.
 * @param password The password for password grant.
 * @return A [TokenResponse] containing the tokens.
 */
suspend fun exchangeCodeForTokens(
    authorizationCode: String?,
    username: String? = null,
    password: String? = null
): TokenResponse {
    val tokenEndpoint = "http://192.168.1.114:8080/realms/master/protocol/openid-connect/token"

    println("exchangeCodeForTokens called:")
    println(" - code: $authorizationCode")
    println(" - username: $username")
    println(" - password: $password")

    val formParams = parameters {
        if (username != null && password != null) {
            append("username", username)
            append("password", password)
            append("grant_type", "password")
            append("client_id", "amie")
            append("client_secret", "")

        //append("scope", "openid profile email")
        } else {
            // Fallback to authorization_code grant
            append("grant_type", "authorization_code")
            append("client_id", "amie")
            append("client_secret", "")
            append("code", authorizationCode ?: "")
            append("redirect_uri", "http://192.168.1.114:8081/oauthpage")
        }
    }

    println("Submitting form to $tokenEndpoint with parameters:")
    formParams.forEach { key, values ->
        println(" - $key: ${if (key == "password") "****" else values.joinToString()}")
    }

    // Convert formParams to curl body for logging
    val curlData = formParams.entries().joinToString("&") { entry ->
        "${entry.key}=${if (entry.key == "password") "****" else entry.value.joinToString(",")}"
    }
    println("\n=== [CURL LOG] ===")
    println("curl -X POST '$tokenEndpoint' -H 'Content-Type: application/x-www-form-urlencoded' -d '$curlData'")
    println("==================\n")

    val response = httpClient.submitForm(
        url = tokenEndpoint,
        formParameters = formParams
    )

    println("HTTP Response received from $tokenEndpoint")
    println(" - Status: ${response.status}")
    println(" - Headers: ${response.headers.entries().joinToString { "${it.key}=${it.value}" }}")

    val responseBody = response.body<String>()
    println(" - Body: $responseBody")

    if (!response.status.isSuccess()) {
        println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
        println("CRITICAL FAILURE IN exchangeCodeForTokens (STEP 1):")
        println(" - Status Code: ${response.status}")
        println(" - Response Body: $responseBody")
        println("")
        println("=== MOST COMMON CAUSES & SOLUTIONS DIAGNOSTIC ===")
        println("1. Mismatched client_id")
        println("   - Current Code Value: \"amie\"")
        println("   - Fix: Double-check Keycloak Console -> Clients tab and confirm the exact name (case-sensitive).")
        println("2. Incorrect or Missing client_secret (Confidential Clients)")
        println("   - Fix: If Access Type is Confidential, go to Clients -> 'amie' -> Credentials, copy current secret, and pass it as: append(\"client_secret\", \"<SECRET>\")")
        println("3. Client Secret Provided for a Public Client")
        println("   - Current Fallback Code Sends: \"\" (empty string)")
        println("   - Fix: Remove client_secret completely if the Keycloak client is configured as Public.")
        println("4. Invalid Client Authenticator Type")
        println("   - Fix: Under Clients -> 'amie' -> Credentials, ensure 'Client Authenticator' matches how your application sends credentials.")
        println("5. Unallowed Grant Type")
        println("   - Current Code Value: \"${if (username != null && password != null) "password" else "authorization_code"}\"")
        println("   - Fix: Go to Clients -> 'amie' -> Capability config and ensure the appropriate flow is toggled ON:")
        println("     * For grant_type=password: Enable 'Direct access grants'")
        println("     * For grant_type=authorization_code: Enable 'Standard flow'")
        println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
        throw Exception("Auth Server Error: $responseBody")
    }

    println("SUCCESS: Token response received. Decoding...")
    return jsonConfig.decodeFromString(TokenResponse.serializer(), responseBody)
}