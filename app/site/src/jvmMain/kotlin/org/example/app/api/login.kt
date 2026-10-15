package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.example.app.api.RateLimit.allow

/** `POST /api/login`: signs in against the backend's own accounts and returns its `{token}` answer. */
@Api
suspend fun login(ctx: ApiContext) {
    if (!ctx.allow(RateLimit.signin)) return
    val requestBody = ctx.req.body?.decodeToString() ?: "{}"
    val request = HttpRequest.newBuilder()
        .uri(URI.create(ServerConfig.backend("/login")))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .build()
    try {
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        ctx.res.status = response.statusCode()
        ctx.res.contentType = "application/json"
        ctx.res.body = (if (response.statusCode() == 200) response.body() else """{"error":"invalid_credentials"}""").toByteArray()
    } catch (e: Exception) {
        ctx.logger.warn("Login unavailable: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
    }
}
