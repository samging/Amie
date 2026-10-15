package org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.keycloak

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Header
import io.jsonwebtoken.JwsHeader
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.Locator
import io.jsonwebtoken.security.Jwks
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.Key
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

/**
 * Verifies access tokens issued by the Keycloak realm the Amie website and desktop app sign in against.
 *
 * The realm's signing keys are read from its JWKS endpoint ([jwksUri]) and cached; an unknown key id
 * triggers one refresh so key rotation works without a restart. A token is accepted when its signature
 * verifies, its issuer equals [issuer] and it has not expired. The returned [Claims] carry the Keycloak
 * `preferred_username` as subject so the controllers can keep comparing `claims.subject` with the
 * `username` they were given.
 *
 * Configuration (application.properties):
 * - `amie.keycloak.issuer`: the `iss` claims accepted, comma-separated. A realm without a fixed frontend URL
 *   writes the address the client reached it on, so the desktop app (localhost) and a browser on the LAN
 *   (the machine's LAN address) hold tokens with different issuers for the same realm; list every address
 *   the realm is reached on.
 * - `amie.keycloak.jwks-uri`: where this server can reach the realm's certs endpoint (may differ from the
 *   issuer host, for example `localhost` while the issuer is a LAN address)
 */
@Component
class KeycloakTokenVerifier(
    @Value("\${amie.keycloak.issuer}") issuerList: String,
    @Value("\${amie.keycloak.jwks-uri}") val jwksUri: String,
) {
    private val log = LoggerFactory.getLogger(KeycloakTokenVerifier::class.java)

    /** Every issuer a token may carry (trailing slashes ignored) */
    val issuers: Set<String> = issuerList.split(',').map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }.toSet()

    /** The first configured issuer, for messages */
    val issuer: String get() = issuers.firstOrNull() ?: ""

    init {
        require(issuers.isNotEmpty()) { "amie.keycloak.issuer must name at least one issuer" }
        log.info("Accepting Keycloak tokens from {}", issuers.joinToString())
    }
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val keys = AtomicReference<Map<String, Key>>(emptyMap())

    /** The claims of a valid Keycloak token, or null with the reason logged. */
    fun verify(token: String): Claims? {
        if (!looksLikeKeycloakToken(token)) return null
        return try {
            val payload = Jwts.parser()
                .keyLocator(KeyLocator())
                .build()
                .parseSignedClaims(token)
                .payload
            val tokenIssuer = payload.issuer?.trimEnd('/')
            if (tokenIssuer !in issuers) {
                log.warn("Keycloak token rejected: issuer {} is none of {}", tokenIssuer, issuers.joinToString())
                return null
            }
            val username = payload["preferred_username"] as? String
                ?: payload["email"] as? String
                ?: payload.subject
            Jwts.claims()
                .add(payload)
                .subject(username)
                .add("keycloakSubject", payload.subject)
                .add("userId", -1L)
                .build()
        } catch (e: Exception) {
            log.warn("Keycloak token rejected: {}", e.message)
            null
        }
    }

    /** Keycloak signs with RS256 and names the key; the backend's own tokens are HS512 without a kid. */
    private fun looksLikeKeycloakToken(token: String): Boolean {
        val header = token.substringBefore('.', "")
        if (header.isEmpty()) return false
        return runCatching {
            val json = String(java.util.Base64.getUrlDecoder().decode(header))
            json.contains("\"kid\"") && json.contains("RS")
        }.getOrDefault(false)
    }

    private inner class KeyLocator : Locator<Key> {
        override fun locate(header: Header): Key? {
            val kid = (header as? JwsHeader)?.keyId ?: return null
            keys.get()[kid]?.let { return it }
            refresh()
            return keys.get()[kid] ?: throw IllegalStateException("no signing key '$kid' at $jwksUri")
        }
    }

    @Synchronized
    private fun refresh() {
        val request = HttpRequest.newBuilder(URI(jwksUri)).timeout(Duration.ofSeconds(5)).GET().build()
        val body = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (body.statusCode() != 200) throw IllegalStateException("JWKS at $jwksUri answered ${body.statusCode()}")
        // JwkSet is also a Map, so Kotlin's `.keys` would be the map keys; call the accessor explicitly
        val jwks: Set<io.jsonwebtoken.security.Jwk<*>> = Jwks.setParser().build().parse(body.body()).getKeys()
        val loaded: Map<String, Key> = jwks.associate { jwk -> jwk.id to (jwk.toKey() as Key) }
        keys.set(loaded)
        log.info("Loaded {} Keycloak signing key(s) from {}", keys.get().size, jwksUri)
    }
}
