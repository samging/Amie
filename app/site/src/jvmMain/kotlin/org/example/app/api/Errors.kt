package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.HttpMethod
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** The incoming bearer, or a 401 answer when there is none. */
private fun ApiContext.bearerOrReject(): String? {
    val token = req.header("Authorization")?.removePrefix("Bearer ")?.trim()
    if (token.isNullOrEmpty()) {
        sendJson(401, """{"error":"unauthorized"}""")
        return null
    }
    return token
}

/** `GET /api/errors?username=` with the user's bearer: the errors the app reported, newest first; never cached. */
@Api("errors")
suspend fun listErrors(ctx: ApiContext) {
    val token = ctx.bearerOrReject() ?: return
    val username = ctx.req.params["username"]?.trim().orEmpty()
    if (username.isEmpty()) {
        ctx.sendJson(400, """{"error":"missing_username"}""")
        return
    }
    val encoded = URLEncoder.encode(username, Charsets.UTF_8)
    val request = HttpRequest.newBuilder()
        .uri(URI.create(ServerConfig.backend("/errors?username=$encoded")))
        .header("Authorization", "Bearer $token")
        .GET()
        .build()
    try {
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        ctx.res.headers["Cache-Control"] = "no-store"
        ctx.sendJson(response.statusCode(), response.body().ifBlank { "[]" })
    } catch (e: Exception) {
        ctx.logger.warn("Error log unavailable: ${e.message}")
        ctx.sendJson(502, "[]")
    }
}

/** `POST /api/errors-clear?username=` with the user's bearer: removes the user's error log. */
@Api("errors-clear")
suspend fun clearErrors(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) { ctx.res.status = 405; return }
    val token = ctx.bearerOrReject() ?: return
    val username = ctx.req.params["username"]?.trim().orEmpty()
    if (username.isEmpty()) {
        ctx.sendJson(400, """{"error":"missing_username"}""")
        return
    }
    val encoded = URLEncoder.encode(username, Charsets.UTF_8)
    val request = HttpRequest.newBuilder()
        .uri(URI.create(ServerConfig.backend("/errors?username=$encoded")))
        .header("Authorization", "Bearer $token")
        .DELETE()
        .build()
    try {
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        ctx.res.headers["Cache-Control"] = "no-store"
        when (response.statusCode()) {
            204 -> ctx.sendJson(200, """{"ok":true}""")
            401 -> ctx.sendJson(401, """{"error":"unauthorized"}""")
            403 -> ctx.sendJson(403, """{"error":"forbidden"}""")
            else -> ctx.sendJson(502, """{"error":"unavailable"}""")
        }
    } catch (e: Exception) {
        ctx.logger.warn("Error log clear failed: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
    }
}
