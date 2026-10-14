package org.example.app.api.keycloak

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.HttpMethod
import org.example.app.api.sendJson
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

/**
 * `POST /api/keycloak/device`: starts an OAuth 2.0 device authorization (RFC 8628) with the realm. The
 * page shows `verification_uri_complete` as a QR code; the person scans it with a phone, signs in there,
 * and the page polls `POST /api/keycloak/token` with the `device_code` until Keycloak hands out tokens.
 * The `amie` client needs "OAuth 2.0 Device Authorization Grant" enabled; otherwise Keycloak answers
 * 400 and the page falls back to a QR code of the hosted sign-in link.
 */
@Api("device")
suspend fun startDeviceAuthorization(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) {
        ctx.res.status = 405
        return
    }
    val params = mutableMapOf(
        "client_id" to KeycloakServerConfig.clientId,
        "scope" to "openid profile email"
    )
    if (KeycloakServerConfig.clientSecret.isNotEmpty()) params["client_secret"] = KeycloakServerConfig.clientSecret
    val body = params.entries.joinToString("&") { (k, v) ->
        URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)
    }
    try {
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(KeycloakServerConfig.deviceUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString()
        )
        if (response.statusCode() == 200) {
            ctx.sendJson(200, response.body())
        } else {
            ctx.logger.info("Device flow not available: ${response.statusCode()}")
            ctx.sendJson(400, """{"error":"device_flow_unavailable"}""")
        }
    } catch (e: Exception) {
        ctx.logger.warn("Keycloak unreachable for device flow: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
    }
}
