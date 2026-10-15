package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.example.app.api.RateLimit.allow

/** `POST /api/register`: creates a backend account; 409 when the name is taken. */
@Api
suspend fun register(ctx: ApiContext) {
    if (!ctx.allow(RateLimit.signin)) return
    val requestBody = ctx.req.body?.decodeToString() ?: "{}"
    val request = HttpRequest.newBuilder()
        .uri(URI.create(ServerConfig.backend("/register")))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .build()
    try {
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        ctx.res.status = response.statusCode()
        ctx.res.contentType = "application/json"
        ctx.res.body = when (response.statusCode()) {
            200 -> """{"ok":true}"""
            409 -> """{"error":"username_taken"}"""
            else -> """{"error":"rejected"}"""
        }.toByteArray()
    } catch (e: Exception) {
        ctx.logger.warn("Registration unavailable: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
    }
}
