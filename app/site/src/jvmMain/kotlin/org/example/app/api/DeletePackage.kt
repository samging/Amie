package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * `GET /api/delete-package?package=&username=&token=`: deletes one of the signed-in user's files from
 * the package store. The backend's `DELETE /package?path=` checks that the bearer owns the path.
 */
@Api
suspend fun deletePackage(ctx: ApiContext) {
    val path = ctx.req.params["package"]?.trim().orEmpty()
    val username = ctx.req.params["username"]?.trim().orEmpty()
    val token = ctx.req.params["token"]?.trim().orEmpty()

    if (path.isEmpty() || username.isEmpty() || token.isEmpty()) {
        ctx.sendJson(400, """{"error":"missing_parameters"}""")
        return
    }

    val encoded = URLEncoder.encode(path, Charsets.UTF_8)
    val request = HttpRequest.newBuilder()
        .uri(URI.create(ServerConfig.backend("/package?path=$encoded")))
        .header("Authorization", "Bearer $token")
        .DELETE()
        .build()
    val response = try {
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    } catch (e: Exception) {
        ctx.logger.warn("Delete: backend unavailable: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
        return
    }
    when (response.statusCode()) {
        204 -> { ServerCache.invalidate("packages:"); ctx.sendJson(200, """{"ok":true}""") }
        401 -> ctx.sendJson(401, """{"error":"unauthorized"}""")
        403 -> ctx.sendJson(403, """{"error":"forbidden"}""")
        404 -> ctx.sendJson(404, """{"error":"not_found"}""")
        else -> ctx.sendJson(502, """{"error":"unavailable"}""")
    }
}
