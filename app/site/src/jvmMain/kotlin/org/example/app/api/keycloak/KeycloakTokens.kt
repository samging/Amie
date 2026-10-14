package org.example.app.api.keycloak

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.time.Duration
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

/** Who a verified Keycloak token belongs to. */
data class Identity(val username: String, val subject: String, val roles: Set<String>) {
    val isAdmin: Boolean get() = KeycloakTokens.adminRole in roles
}

/**
 * Verifies the realm's RS256 access tokens on this server: signature against the realm's JWKS (cached,
 * refreshed once on an unknown key id), issuer and expiry. Roles come from `realm_access` and from
 * `resource_access.<client>`; the admin role name is `AMIE_ADMIN_ROLE` (default `admin`, the master
 * realm's administrator role).
 */
object KeycloakTokens {
    private val json = Json { ignoreUnknownKeys = true }
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val keys = AtomicReference<Map<String, RSAPublicKey>>(emptyMap())
    private val announcedIssuer = AtomicReference<String?>(null)

    val adminRole: String get() = System.getenv("AMIE_ADMIN_ROLE")?.trim()?.takeIf { it.isNotEmpty() } ?: "admin"

    fun verify(token: String): Identity? = runCatching {
        val parts = token.split('.')
        if (parts.size != 3) return null
        val header = json.parseToJsonElement(decodeToString(parts[0])).jsonObject
        if (header.str("alg") != "RS256") return null
        val kid = header.str("kid") ?: return null
        val key = keys.get()[kid] ?: run { refreshKeys(); keys.get()[kid] } ?: return null

        val signature = Signature.getInstance("SHA256withRSA")
        signature.initVerify(key)
        signature.update((parts[0] + "." + parts[1]).toByteArray(Charsets.US_ASCII))
        if (!signature.verify(decode(parts[2]))) return null

        val claims = json.parseToJsonElement(decodeToString(parts[1])).jsonObject
        val issuer = claims.str("iss") ?: return null
        if (issuer !in acceptedIssuers()) return null
        val exp = claims["exp"]?.jsonPrimitive?.longOrNull ?: return null
        if (exp * 1000 < System.currentTimeMillis()) return null

        val roles = mutableSetOf<String>()
        claims["realm_access"]?.jsonObject?.get("roles")?.jsonArray?.forEach { roles += it.jsonPrimitive.content }
        claims["resource_access"]?.jsonObject?.get(KeycloakServerConfig.clientId)?.jsonObject?.get("roles")?.jsonArray
            ?.forEach { roles += it.jsonPrimitive.content }
        Identity(
            username = claims.str("preferred_username") ?: claims.str("email") ?: claims.str("sub") ?: return null,
            subject = claims.str("sub") ?: "",
            roles = roles
        )
    }.getOrNull()

    private fun acceptedIssuers(): Set<String> {
        if (announcedIssuer.get() == null) {
            runCatching {
                val body = get("${KeycloakServerConfig.realmUrl}/.well-known/openid-configuration")
                json.parseToJsonElement(body).jsonObject.str("issuer")?.let { announcedIssuer.set(it) }
            }
        }
        return setOfNotNull(
            KeycloakServerConfig.realmUrl,
            System.getenv("KEYCLOAK_ISSUER")?.trim()?.takeIf { it.isNotEmpty() },
            announcedIssuer.get()
        )
    }

    @Synchronized
    private fun refreshKeys() {
        val body = get("${KeycloakServerConfig.realmUrl}/protocol/openid-connect/certs")
        val loaded = json.parseToJsonElement(body).jsonObject["keys"]?.jsonArray.orEmpty()
            .mapNotNull { element ->
                val key = element.jsonObject
                val kid = key.str("kid") ?: return@mapNotNull null
                if (key.str("kty") != "RSA") return@mapNotNull null
                val n = BigInteger(1, decode(key.str("n") ?: return@mapNotNull null))
                val e = BigInteger(1, decode(key.str("e") ?: return@mapNotNull null))
                kid to (KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(n, e)) as RSAPublicKey)
            }.toMap()
        keys.set(loaded)
    }

    private fun get(url: String): String {
        val response = http.send(
            HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )
        check(response.statusCode() == 200) { "Keycloak answered ${response.statusCode()} for $url" }
        return response.body()
    }

    private fun decode(input: String): ByteArray = Base64.getUrlDecoder().decode(input)
    private fun decodeToString(input: String): String = String(decode(input), Charsets.UTF_8)
    private fun JsonObject.str(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
}
