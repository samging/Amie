package org.example.amiepackagerepository.keycloak

import org.example.amiepackagerepository.shared.transactionalMiddleware.service.user.service.keycloak.KeycloakTokenVerifier
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs against a live Keycloak when KEYCLOAK_TEST_USER / KEYCLOAK_TEST_PASSWORD / KEYCLOAK_CLIENT_SECRET are set,
 * otherwise only checks that foreign tokens are rejected.
 */
class KeycloakTokenVerifierTest {
    private val base = System.getenv("KEYCLOAK_URL") ?: "http://localhost:8080"
    private val issuer = System.getenv("KEYCLOAK_ISSUER") ?: "$base/realms/master"
    private val verifier = KeycloakTokenVerifier("$issuer, http://127.0.0.1:8080/realms/master/", "$base/realms/master/protocol/openid-connect/certs")

    @Test
    fun `the issuer list is split and trimmed`() {
        assertEquals(setOf(issuer, "http://127.0.0.1:8080/realms/master"), verifier.issuers)
        assertEquals(issuer, verifier.issuer)
    }

    @Test
    fun `a backend style token is not mistaken for a Keycloak token`() {
        assertNull(verifier.verify("eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ4In0.c2ln"))
        assertNull(verifier.verify("garbage"))
    }

    @Test
    fun `a real Keycloak access token verifies and carries the username as subject`() {
        val user = System.getenv("KEYCLOAK_TEST_USER")
        val password = System.getenv("KEYCLOAK_TEST_PASSWORD")
        val secret = System.getenv("KEYCLOAK_CLIENT_SECRET")
        assumeTrue(user != null && password != null && secret != null, "no Keycloak test credentials")
        val form = "grant_type=password&client_id=amie&client_secret=$secret&username=$user&password=$password"
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("$base/realms/master/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
            HttpResponse.BodyHandlers.ofString()
        )
        assumeTrue(response.statusCode() == 200, "Keycloak answered ${response.statusCode()}: ${response.body()}")
        val token = Regex("\"access_token\":\"([^\"]+)\"").find(response.body())!!.groupValues[1]

        val claims = assertNotNull(verifier.verify(token))
        assertEquals(user, claims.subject)
        assertTrue(claims.issuer in verifier.issuers, "issuer ${claims.issuer} is not accepted")

        val tampered = token.dropLast(2) + "xx"
        assertNull(verifier.verify(tampered))
    }
}
