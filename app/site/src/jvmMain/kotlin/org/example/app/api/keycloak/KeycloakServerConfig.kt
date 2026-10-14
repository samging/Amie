package org.example.app.api.keycloak

/**
 * Keycloak as seen from the Kobweb server (its API routes). Environment variables:
 * - `KEYCLOAK_URL`: where this server reaches Keycloak (default `http://localhost:8080`)
 * - `KEYCLOAK_CLIENT_SECRET`: the confidential `amie` client's secret, used only here, never sent to browsers
 * - `AMIE_SITE_URL`: the public origin of this site for redirect URIs (default `http://localhost:8081`)
 */
object KeycloakServerConfig {
    private fun env(name: String) = System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
    val url: String get() = (env("KEYCLOAK_URL") ?: "http://localhost:8080").trimEnd('/')
    val realm = "master"
    val clientId = "amie"
    val clientSecret: String get() = env("KEYCLOAK_CLIENT_SECRET") ?: ""
    val siteUrl: String get() = (env("AMIE_SITE_URL") ?: "http://localhost:8081").trimEnd('/')
    val realmUrl: String get() = "$url/realms/$realm"
    val tokenUrl: String get() = "$realmUrl/protocol/openid-connect/token"
    val deviceUrl: String get() = "$realmUrl/protocol/openid-connect/auth/device"
    val registrationsUrl: String get() = "$realmUrl/protocol/openid-connect/registrations"
    val redirectUri: String get() = "$siteUrl/oauthpage"
}
