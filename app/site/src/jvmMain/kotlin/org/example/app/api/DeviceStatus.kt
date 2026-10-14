package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private const val TTL_SECONDS = 30L

/** `GET /api/get-device-status?username=`: a user's device statuses, cached for half a minute. */
@Api("get-device-status")
suspend fun getDeviceStatus(ctx: ApiContext) {
    val username = ctx.req.params["username"]?.trim().orEmpty()
    if (username.isEmpty()) {
        ctx.sendJson(400, """{"error":"missing_username"}""")
        return
    }
    val encoded = java.net.URLEncoder.encode(username, Charsets.UTF_8)
    val entry = try {
        ServerCache.fetch("devices:$username", TTL_SECONDS, ctx.req.wantsRefresh) {
            ServerCache.fetchUrl(ServerConfig.backend("/device-status?username=$encoded"), ttlSeconds = TTL_SECONDS)
        }
    } catch (e: Exception) {
        ctx.logger.warn("Device status unavailable: ${e.message}")
        ctx.sendJson(502, "[]")
        return
    }
    ctx.send(entry, TTL_SECONDS, errorBody = "[]")
}

/** `POST /api/save-device-status?username=`: forwards the new statuses and drops the cached copy. */
@Api("save-device-status")
suspend fun saveDeviceStatus(ctx: ApiContext) {
    val username = ctx.req.params["username"]?.trim().orEmpty()
    val bodyText = ctx.req.body?.decodeToString() ?: "{}"
    val encoded = java.net.URLEncoder.encode(username, Charsets.UTF_8)

    val request = HttpRequest.newBuilder()
        .uri(URI.create(ServerConfig.backend("/device-status?username=$encoded")))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(bodyText))
        .build()

    try {
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        ServerCache.invalidate("devices:$username")
        ctx.res.status = response.statusCode()
        ctx.res.contentType = "application/json"
        ctx.res.body = response.body().toByteArray()
    } catch (e: Exception) {
        ctx.logger.warn("Device status save failed: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
    }
}
