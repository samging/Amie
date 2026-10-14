package org.example.app.api.keycloak

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.readBodyText
import com.varabyte.kobweb.api.http.setBodyText
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

/**
 * `POST /api/keycloak/token`: exchanges an authorization code (`code`, `redirect_uri`) or a username and
 * password (`username`, `password`) for Keycloak tokens, adding the confidential client's secret on the
 * server so it never ships in browser JavaScript. The body is form-urlencoded; the answer is Keycloak's
 * JSON with Keycloak's status code.
 */
@Api("token")
suspend fun exchangeToken(ctx: ApiContext) {
    try {
        val form = parseForm(ctx.req.readBodyText() ?: "")
        val params = mutableMapOf("client_id" to KeycloakServerConfig.clientId)
        if (KeycloakServerConfig.clientSecret.isNotEmpty()) params["client_secret"] = KeycloakServerConfig.clientSecret
        when {
            form["code"] != null -> {
                params["grant_type"] = "authorization_code"
                params["code"] = form.getValue("code")
                params["redirect_uri"] = form["redirect_uri"] ?: KeycloakServerConfig.redirectUri
            }
            form["username"] != null -> {
                params["grant_type"] = "password"
                params["username"] = form.getValue("username")
                params["password"] = form["password"] ?: ""
                params["scope"] = form["scope"] ?: "openid profile email"
            }
            form["refresh_token"] != null -> {
                params["grant_type"] = "refresh_token"
                params["refresh_token"] = form.getValue("refresh_token")
            }
            form["device_code"] != null -> {
                params["grant_type"] = "urn:ietf:params:oauth:grant-type:device_code"
                params["device_code"] = form.getValue("device_code")
            }
            else -> {
                ctx.res.status = 400
                ctx.res.setBodyText("""{"error":"invalid_request","error_description":"send code, username, refresh_token or device_code"}""")
                return
            }
        }
        val body = params.entries.joinToString("&") { (k, v) -> enc(k) + "=" + enc(v) }
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(KeycloakServerConfig.tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString()
        )
        ctx.res.status = response.statusCode()
        ctx.res.contentType = "application/json"
        ctx.res.setBodyText(response.body())
    } catch (e: Exception) {
        ctx.res.status = 502
        ctx.res.contentType = "application/json"
        ctx.res.setBodyText("""{"error":"keycloak_unreachable","error_description":"The sign-in service could not be reached"}""")
    }
}

private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

internal fun parseForm(body: String): Map<String, String> =
    body.split('&').filter { it.contains('=') }.associate {
        URLDecoder.decode(it.substringBefore('='), StandardCharsets.UTF_8) to
            URLDecoder.decode(it.substringAfter('='), StandardCharsets.UTF_8)
    }
