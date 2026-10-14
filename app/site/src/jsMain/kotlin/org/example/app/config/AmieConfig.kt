package org.example.app.config

import kotlinx.browser.window

/**
 * Where the website finds the Amie backend and Keycloak.
 *
 * Both default to the host the page was loaded from (so the site works on localhost and on a LAN address
 * without edits). To point them elsewhere, set these keys in the browser's localStorage, for example from the
 * console: `localStorage.setItem("amie.backendUrl", "http://192.168.1.50:8090")`.
 *
 * - `amie.backendUrl`: the Spring Boot packageRepository (default `http://<host>:8080`)
 * - `amie.keycloakUrl`: the Keycloak server (default `http://<host>:8080`)
 *
 * Jar downloads and package listings go through this site's own server (`/api/...`), which talks to the
 * S3 service and the backend itself and caches their answers.
 */
object AmieConfig {
    private fun saved(key: String): String? =
        runCatching { window.localStorage.getItem(key)?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()

    val host: String get() = window.location.hostname.ifEmpty { "localhost" }

    /** This site's own origin, for redirect URIs */
    val siteUrl: String get() = window.location.origin

    val backendUrl: String get() = (saved("amie.backendUrl") ?: "http://$host:8080").trimEnd('/')

    /** The S3 service, used directly only for large application builds (`amie.s3ServiceUrl`, default `http://<host>:8088`) */
    val s3ServiceUrl: String get() = (saved("amie.s3ServiceUrl") ?: "http://$host:8088").trimEnd('/')

    /** Where to download a file from the bucket: large application builds stream straight from the S3 service. */
    fun downloadUrl(name: String, kind: String, size: Long): String =
        if (kind == "application" && size > 64L * 1024 * 1024) "$s3ServiceUrl/api/aws/jars/${encodeURIComponent(name)}"
        else api("jar-download?name=${encodeURIComponent(name)}")
    val keycloakUrl: String get() = (saved("amie.keycloakUrl") ?: "http://$host:8080").trimEnd('/')

    const val realm = "master"
    const val clientId = "amie"

    val realmUrl: String get() = "$keycloakUrl/realms/$realm"
    val jwksUrl: String get() = "$realmUrl/protocol/openid-connect/certs"
    val openIdConfigurationUrl: String get() = "$realmUrl/.well-known/openid-configuration"
    val oauthRedirectUri: String get() = "$siteUrl/oauthpage"

    /** The hosted login (or registration) page, coming back to /oauthpage with a code */
    fun authUrl(register: Boolean = false, scope: String = "openid profile email", extra: String = ""): String =
        "$realmUrl/protocol/openid-connect/${if (register) "registrations" else "auth"}" +
            "?client_id=$clientId&response_type=code&scope=${scope.replace(" ", "%20")}" +
            "&redirect_uri=${encodeURIComponent(oauthRedirectUri)}$extra"

    /** Keycloak's own end-session page, which then returns to this site */
    fun logoutUrl(idToken: String?): String =
        "$realmUrl/protocol/openid-connect/logout?client_id=$clientId" +
            "&post_logout_redirect_uri=${encodeURIComponent("$siteUrl/")}" +
            (idToken?.takeIf { it.isNotEmpty() }?.let { "&id_token_hint=$it" } ?: "")

    fun backend(path: String): String = "$backendUrl/${path.trimStart('/')}"

    /** This site's own API routes */
    fun api(path: String): String = "/api/${path.trimStart('/')}"
}

external fun encodeURIComponent(str: String): String
