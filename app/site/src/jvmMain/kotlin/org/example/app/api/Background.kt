package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.HttpMethod
import org.example.app.api.RateLimit.allow
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private const val BACKGROUND_TTL_SECONDS = 300L
private const val BACKGROUND_MAX_BYTES = 2 * 1024 * 1024
private val BG_IMAGE_TYPES = setOf("image/png", "image/jpeg", "image/gif", "image/webp")
private val bgHttp: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
private fun validUsername(name: String) = name.isNotEmpty() && name.length <= 128 && name.none { it == '/' || it == '\\' || it.isWhitespace() || it.isISOControl() }

private fun ApiContext.imageHeaders() {
    res.headers["X-Content-Type-Options"] = "nosniff"
    res.headers["Content-Security-Policy"] = "default-src 'none'; sandbox"
    res.headers["Content-Disposition"] = "inline; filename=\"background\""
}

/**
 * `GET /api/background?username=`: the approved profile banner, cached like the picture and served only
 * as a raster image. `POST /api/background?username=` with the bearer and multipart `file`: a new banner,
 * up to 2 MB and 4096 px, waiting for an administrator like the picture.
 */
@Api("background")
suspend fun background(ctx: ApiContext) {
    val username = ctx.req.params["username"]?.trim().orEmpty()
    if (!validUsername(username)) { ctx.sendJson(400, """{"error":"missing_username"}"""); return }
    val encoded = URLEncoder.encode(username, Charsets.UTF_8)
    when (ctx.req.method) {
        HttpMethod.GET -> {
            val entry = try {
                ServerCache.fetch("background:$username", BACKGROUND_TTL_SECONDS, ctx.req.wantsRefresh) {
                    ServerCache.fetchUrl(ServerConfig.backend("/background?username=$encoded"), ttlSeconds = BACKGROUND_TTL_SECONDS)
                }
            } catch (e: Exception) {
                ctx.logger.warn("Background unavailable: ${e.message}")
                ctx.sendJson(502, """{"error":"unavailable"}""")
                return
            }
            val type = entry.contentType?.substringBefore(';')?.trim()?.lowercase()
            if (entry.status in 200..299 && (type !in BG_IMAGE_TYPES || entry.body.size > BACKGROUND_MAX_BYTES)) {
                ctx.sendJson(502, """{"error":"unavailable"}""")
                return
            }
            ctx.imageHeaders()
            ctx.send(entry, maxAgeSeconds = 3600)
        }
        HttpMethod.POST -> {
            if (!ctx.allow(RateLimit.upload)) return
            val token = ctx.bearerToken() ?: run { ctx.sendJson(401, """{"error":"unauthorized"}"""); return }
            val bytes = ctx.req.body
            val contentType = ctx.req.contentType
            if (bytes == null || bytes.isEmpty() || contentType == null || !contentType.startsWith("multipart/form-data")) { ctx.sendJson(400, """{"error":"missing_file"}"""); return }
            if (bytes.size > BACKGROUND_MAX_BYTES + 4096) { ctx.sendJson(413, """{"error":"too_large"}"""); return }
            try {
                val response = bgHttp.send(
                    HttpRequest.newBuilder(URI.create(ServerConfig.backend("/background?username=$encoded"))).timeout(Duration.ofSeconds(60))
                        .header("Authorization", "Bearer $token").header("Content-Type", contentType).header("X-Forwarded-For", RateLimit.clientOf(ctx))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
                    HttpResponse.BodyHandlers.ofString()
                )
                ServerCache.invalidate("background:$username"); ServerCache.invalidate("profile:$username")
                ctx.res.headers["Cache-Control"] = "no-store"
                when (response.statusCode()) {
                    200 -> ctx.sendJson(200, """{"ok":true}""")
                    400 -> ctx.sendJson(400, """{"error":"invalid_image"}""")
                    401 -> ctx.sendJson(401, """{"error":"unauthorized"}""")
                    403 -> ctx.sendJson(403, """{"error":"forbidden"}""")
                    413 -> ctx.sendJson(413, """{"error":"too_large"}""")
                    429 -> ctx.sendJson(429, """{"error":"too_many_requests"}""")
                    else -> { ctx.logger.warn("Background upload answered ${response.statusCode()}: ${response.body().take(200)}"); ctx.sendJson(502, """{"error":"unavailable"}""") }
                }
            } catch (e: Exception) {
                ctx.logger.warn("Background upload failed: ${e.message}")
                ctx.sendJson(502, """{"error":"unavailable"}""")
            }
        }
        else -> ctx.res.status = 405
    }
}

/** `GET /api/background-preview?username=` with the bearer: the banner even while it waits (owner or admin). */
@Api("background-preview")
suspend fun backgroundPreview(ctx: ApiContext) {
    val username = ctx.req.params["username"]?.trim().orEmpty()
    if (!validUsername(username)) { ctx.sendJson(400, """{"error":"missing_username"}"""); return }
    val token = ctx.bearerToken() ?: run { ctx.sendJson(401, """{"error":"unauthorized"}"""); return }
    if (!ctx.allow(RateLimit.heavy)) return
    try {
        val response = bgHttp.send(
            HttpRequest.newBuilder(URI.create(ServerConfig.backend("/background?username=${URLEncoder.encode(username, Charsets.UTF_8)}&preview=true"))).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer $token").GET().build(),
            HttpResponse.BodyHandlers.ofByteArray()
        )
        val type = response.headers().firstValue("Content-Type").orElse("").substringBefore(';').trim().lowercase()
        ctx.res.headers["Cache-Control"] = "no-store"
        when {
            response.statusCode() == 404 -> ctx.sendJson(404, """{"error":"not_found"}""")
            response.statusCode() != 200 || type !in BG_IMAGE_TYPES || response.body().size > BACKGROUND_MAX_BYTES -> ctx.sendJson(502, """{"error":"unavailable"}""")
            else -> { ctx.imageHeaders(); ctx.res.status = 200; ctx.res.contentType = type; ctx.res.body = response.body() }
        }
    } catch (e: Exception) {
        ctx.logger.warn("Background preview failed: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
    }
}

/** `POST /api/background-delete?username=` with the bearer: removes the caller's own banner. */
@Api("background-delete")
suspend fun backgroundDelete(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) { ctx.res.status = 405; return }
    val username = ctx.req.params["username"]?.trim().orEmpty()
    if (!validUsername(username)) { ctx.sendJson(400, """{"error":"missing_username"}"""); return }
    val token = ctx.bearerToken() ?: run { ctx.sendJson(401, """{"error":"unauthorized"}"""); return }
    try {
        val response = backendCall("DELETE", "/background?username=${URLEncoder.encode(username, Charsets.UTF_8)}", token)
        ServerCache.invalidate("background:$username"); ServerCache.invalidate("profile:$username")
        ctx.res.headers["Cache-Control"] = "no-store"
        when (response.statusCode()) {
            204 -> ctx.sendJson(200, """{"ok":true}""")
            401 -> ctx.sendJson(401, """{"error":"unauthorized"}""")
            403 -> ctx.sendJson(403, """{"error":"forbidden"}""")
            404 -> ctx.sendJson(404, """{"error":"not_found"}""")
            else -> ctx.sendJson(502, """{"error":"unavailable"}""")
        }
    } catch (e: Exception) {
        ctx.sendJson(502, """{"error":"unavailable"}""")
    }
}
