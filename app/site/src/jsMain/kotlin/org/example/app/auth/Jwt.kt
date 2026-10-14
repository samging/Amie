package org.example.app.auth

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.example.app.components.ClientCache
import org.example.app.components.Http
import org.example.app.config.AmieConfig
import org.khronos.webgl.Uint8Array
import kotlin.js.Date
import kotlin.js.Promise

@Serializable
data class JwkKey(val kid: String, val kty: String, val alg: String? = null, val use: String? = null, val n: String, val e: String)

@Serializable
data class JwksResponse(val keys: List<JwkKey>)

@Serializable
data class OpenIdConfiguration(val issuer: String = "")

/** The outcome of checking a token: either the claims, or why the token was refused. */
class JwtCheck(val claims: JsonObject?, val reason: String?) {
    val valid: Boolean get() = claims != null
    val username: String? get() = claims?.string("preferred_username") ?: claims?.string("email") ?: claims?.string("sub")
    val expiresAtMs: Double? get() = claims?.get("exp")?.jsonPrimitive?.longOrNull?.let { it * 1000.0 }

    /** Realm roles plus this client's roles */
    val roles: List<String> get() {
        val found = mutableListOf<String>()
        (claims?.get("realm_access") as? JsonObject)?.get("roles")?.let { r -> if (r is JsonArray) r.forEach { found += it.jsonPrimitive.content } }
        ((claims?.get("resource_access") as? JsonObject)?.get(AmieConfig.clientId) as? JsonObject)?.get("roles")
            ?.let { r -> if (r is JsonArray) r.forEach { found += it.jsonPrimitive.content } }
        return found.distinct()
    }
}

fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

val jwtJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Verifies the realm's JWTs in the browser: the RS256 signature against the realm's published keys
 * (WebCrypto), the issuer, the expiry and, for ID tokens, the audience. Keys are cached for an hour.
 */
object Jwt {
    private var announcedIssuer: String? = null

    fun decodePayload(token: String): JsonObject? = runCatching {
        val parts = token.split('.')
        if (parts.size != 3) return null
        jwtJson.parseToJsonElement(base64UrlDecodeToString(parts[1])).jsonObject
    }.getOrNull()

    /** The realm's signing keys, from the cache when possible. */
    suspend fun keys(refresh: Boolean = false): JwksResponse {
        val text = ClientCache.cached("jwks:${AmieConfig.realmUrl}", ClientCache.HOUR, refresh) {
            val result = Http.get(AmieConfig.jwksUrl)
            if (!result.ok) throw IllegalStateException("The sign-in service is unavailable")
            result.text
        }
        return jwtJson.decodeFromString(JwksResponse.serializer(), text)
    }

    private suspend fun issuers(): Set<String> {
        if (announcedIssuer == null) {
            val text = runCatching {
                ClientCache.cached("oidc:${AmieConfig.realmUrl}", ClientCache.HOUR) {
                    Http.get(AmieConfig.openIdConfigurationUrl).takeIf { it.ok }?.text ?: "{}"
                }
            }.getOrNull()
            announcedIssuer = text?.let { runCatching { jwtJson.decodeFromString(OpenIdConfiguration.serializer(), it).issuer }.getOrNull() }
        }
        return setOfNotNull(AmieConfig.realmUrl, announcedIssuer?.takeIf { it.isNotEmpty() })
    }

    /**
     * @param requireAudience when true the token must name this client in `aud` (ID tokens); access
     *   tokens carry the client in `azp` instead, which is checked then.
     */
    suspend fun verify(token: String, requireAudience: Boolean): JwtCheck {
        val parts = token.split('.')
        if (parts.size != 3) return JwtCheck(null, "malformed token")
        val header = runCatching { jwtJson.parseToJsonElement(base64UrlDecodeToString(parts[0])).jsonObject }.getOrNull()
            ?: return JwtCheck(null, "malformed header")
        if (header.string("alg") != "RS256") return JwtCheck(null, "unsupported algorithm")
        val kid = header.string("kid") ?: return JwtCheck(null, "no key id")

        var key = keys().keys.firstOrNull { it.kid == kid }
        if (key == null) key = keys(refresh = true).keys.firstOrNull { it.kid == kid }
        if (key == null) return JwtCheck(null, "unknown signing key")

        val signed = verifySignature(key, parts[0] + "." + parts[1], parts[2])
        if (!signed) return JwtCheck(null, "bad signature")

        val claims = runCatching { jwtJson.parseToJsonElement(base64UrlDecodeToString(parts[1])).jsonObject }.getOrNull()
            ?: return JwtCheck(null, "malformed claims")
        val issuer = claims.string("iss")
        if (issuer == null || issuer !in issuers()) return JwtCheck(null, "wrong issuer")
        val exp = claims["exp"]?.jsonPrimitive?.longOrNull ?: return JwtCheck(null, "no expiry")
        if (exp * 1000.0 < Date.now()) return JwtCheck(null, "expired")

        val audience = claims["aud"]?.let { aud ->
            if (aud is JsonArray) aud.map { it.jsonPrimitive.content } else listOf(aud.jsonPrimitive.content)
        }.orEmpty()
        val party = claims.string("azp")
        if (requireAudience && AmieConfig.clientId !in audience) return JwtCheck(null, "wrong audience")
        if (!requireAudience && party != AmieConfig.clientId && AmieConfig.clientId !in audience) return JwtCheck(null, "wrong client")
        return JwtCheck(claims, null)
    }

    private suspend fun verifySignature(key: JwkKey, data: String, signature: String): Boolean = runCatching {
        val subtle = window.asDynamic().crypto.subtle
        val jwk = js("{}")
        jwk.kty = key.kty
        jwk.n = key.n
        jwk.e = key.e
        jwk.alg = "RS256"
        jwk.ext = true
        val algorithm = js("({ name: 'RSASSA-PKCS1-v1_5', hash: { name: 'SHA-256' } })")
        val cryptoKey = (subtle.importKey("jwk", jwk, algorithm, false, arrayOf("verify")) as Promise<dynamic>).await()
        val encoder = js("new TextEncoder()")
        val bytes = encoder.encode(data)
        (subtle.verify(algorithm, cryptoKey, base64UrlDecode(signature), bytes) as Promise<Boolean>).await()
    }.getOrDefault(false)

    private fun base64UrlDecode(input: String): Uint8Array {
        var normalized = input.replace('-', '+').replace('_', '/')
        while (normalized.length % 4 != 0) normalized += "="
        val binary = window.atob(normalized)
        val bytes = Uint8Array(binary.length)
        for (i in binary.indices) bytes.asDynamic()[i] = binary[i].code
        return bytes
    }

    private fun base64UrlDecodeToString(input: String): String {
        val bytes = base64UrlDecode(input)
        val decoder = js("new TextDecoder()")
        return decoder.decode(bytes) as String
    }
}
