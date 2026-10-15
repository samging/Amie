package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.HttpMethod
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.example.app.api.RateLimit.allow

private const val AVATAR_TTL_SECONDS = 300L
private const val AVATAR_MAX_BYTES = 512 * 1024
/** The only types a picture may be served as; the backend detected it from the bytes, this is the second check. */
private val IMAGE_TYPES = setOf("image/png", "image/jpeg", "image/gif", "image/webp")
private val avatarHttp: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

private fun validUsername(name: String) = name.isNotEmpty() && name.length <= 128 && name.none { it == '/' || it == '\\' || it.isWhitespace() || it.isISOControl() }

/**
 * `GET /api/avatar?username=`: the person's picture, cached here for five minutes and in the browser for
 * an hour (the profile's `avatarVersion` goes into the URL, so a new picture shows at once). Served only
 * as one of the raster image types, with `nosniff` and a sandboxing CSP, so nothing uploaded can run.
 *
 * `POST /api/avatar?username=` with the bearer and the multipart `file`: sets the caller's own picture.
 * The backend refuses anything but PNG, JPEG, GIF or WebP up to 512 KB and 2048 px.
 */
@Api("avatar")
suspend fun avatar(ctx: ApiContext) {
    val username = ctx.req.params["username"]?.trim().orEmpty()
    if (!validUsername(username)) { ctx.sendJson(400, """{"error":"missing_username"}"""); return }
    val encoded = URLEncoder.encode(username, Charsets.UTF_8)
    when (ctx.req.method) {
        HttpMethod.GET -> {
            val entry = try {
                ServerCache.fetch("avatar:$username", AVATAR_TTL_SECONDS, ctx.req.wantsRefresh) {
                    ServerCache.fetchUrl(ServerConfig.backend("/avatar?username=$encoded"), ttlSeconds = AVATAR_TTL_SECONDS)
                }
            } catch (e: Exception) {
                ctx.logger.warn("Avatar unavailable: ${e.message}")
                ctx.sendJson(502, """{"error":"unavailable"}""")
                return
            }
            val type = entry.contentType?.substringBefore(';')?.trim()?.lowercase()
            if (entry.status in 200..299 && (type !in IMAGE_TYPES || entry.body.size > AVATAR_MAX_BYTES)) {
                ctx.logger.warn("Avatar of $username has an unexpected type $type or size ${entry.body.size}; not served")
                ctx.sendJson(502, """{"error":"unavailable"}""")
                return
            }
            ctx.res.headers["X-Content-Type-Options"] = "nosniff"
            ctx.res.headers["Content-Security-Policy"] = "default-src 'none'; sandbox"
            ctx.res.headers["Content-Disposition"] = "inline; filename=\"avatar\""
            ctx.send(entry, maxAgeSeconds = 3600)
        }
        HttpMethod.POST -> {
            if (!ctx.allow(RateLimit.upload)) return
            val token = ctx.bearerToken() ?: run { ctx.sendJson(401, """{"error":"unauthorized"}"""); return }
            val bytes = ctx.req.body
            val contentType = ctx.req.contentType
            if (bytes == null || bytes.isEmpty() || contentType == null || !contentType.startsWith("multipart/form-data")) {
                ctx.sendJson(400, """{"error":"missing_file"}""")
                return
            }
            if (bytes.size > AVATAR_MAX_BYTES + 4096) { ctx.sendJson(413, """{"error":"too_large"}"""); return }
            try {
                val response = avatarHttp.send(
                    HttpRequest.newBuilder(URI.create(ServerConfig.backend("/avatar?username=$encoded"))).timeout(Duration.ofSeconds(60))
                        .header("Authorization", "Bearer $token").header("Content-Type", contentType).header("X-Forwarded-For", RateLimit.clientOf(ctx))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
                    HttpResponse.BodyHandlers.ofString()
                )
                ServerCache.invalidate("avatar:$username"); ServerCache.invalidate("profile:$username")
                ctx.res.headers["Cache-Control"] = "no-store"
                when (response.statusCode()) {
                    200 -> ctx.sendJson(200, """{"ok":true}""")
                    400 -> ctx.sendJson(400, """{"error":"invalid_image"}""")
                    401 -> ctx.sendJson(401, """{"error":"unauthorized"}""")
                    403 -> ctx.sendJson(403, """{"error":"forbidden"}""")
                    413 -> ctx.sendJson(413, """{"error":"too_large"}""")
                    else -> { ctx.logger.warn("Avatar upload answered ${response.statusCode()}: ${response.body().take(200)}"); ctx.sendJson(502, """{"error":"unavailable"}""") }
                }
            } catch (e: Exception) {
                ctx.logger.warn("Avatar upload failed: ${e.message}")
                ctx.sendJson(502, """{"error":"unavailable"}""")
            }
        }
        else -> ctx.res.status = 405
    }
}

/**
 * `GET /api/avatar-preview?username=` with the bearer: the picture even while it waits for approval, for
 * its owner (and administrators). Never cached, same image-only headers as `/api/avatar`.
 */
@Api("avatar-preview")
suspend fun avatarPreview(ctx: ApiContext) {
    val username = ctx.req.params["username"]?.trim().orEmpty()
    if (!validUsername(username)) { ctx.sendJson(400, """{"error":"missing_username"}"""); return }
    val token = ctx.bearerToken() ?: run { ctx.sendJson(401, """{"error":"unauthorized"}"""); return }
    if (!ctx.allow(RateLimit.heavy)) return
    try {
        val response = avatarHttp.send(
            HttpRequest.newBuilder(URI.create(ServerConfig.backend("/avatar?username=${URLEncoder.encode(username, Charsets.UTF_8)}&preview=true"))).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer $token").GET().build(),
            HttpResponse.BodyHandlers.ofByteArray()
        )
        val type = response.headers().firstValue("Content-Type").orElse("").substringBefore(';').trim().lowercase()
        ctx.res.headers["Cache-Control"] = "no-store"
        when {
            response.statusCode() == 404 -> ctx.sendJson(404, """{"error":"not_found"}""")
            response.statusCode() != 200 || type !in IMAGE_TYPES || response.body().size > AVATAR_MAX_BYTES -> ctx.sendJson(502, """{"error":"unavailable"}""")
            else -> {
                ctx.res.headers["X-Content-Type-Options"] = "nosniff"
                ctx.res.headers["Content-Security-Policy"] = "default-src 'none'; sandbox"
                ctx.res.headers["Content-Disposition"] = "inline; filename=\"avatar\""
                ctx.res.status = 200
                ctx.res.contentType = type
                ctx.res.body = response.body()
            }
        }
    } catch (e: Exception) {
        ctx.logger.warn("Avatar preview failed: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
    }
}

/** `POST /api/avatar-delete?username=` with the bearer: removes the caller's own picture. */
@Api("avatar-delete")
suspend fun avatarDelete(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) { ctx.res.status = 405; return }
    val username = ctx.req.params["username"]?.trim().orEmpty()
    if (!validUsername(username)) { ctx.sendJson(400, """{"error":"missing_username"}"""); return }
    val token = ctx.bearerToken() ?: run { ctx.sendJson(401, """{"error":"unauthorized"}"""); return }
    try {
        val response = backendCall("DELETE", "/avatar?username=${URLEncoder.encode(username, Charsets.UTF_8)}", token)
        ServerCache.invalidate("avatar:$username"); ServerCache.invalidate("profile:$username")
        ctx.res.headers["Cache-Control"] = "no-store"
        when (response.statusCode()) {
            204 -> ctx.sendJson(200, """{"ok":true}""")
            401 -> ctx.sendJson(401, """{"error":"unauthorized"}""")
            403 -> ctx.sendJson(403, """{"error":"forbidden"}""")
            404 -> ctx.sendJson(404, """{"error":"not_found"}""")
            else -> ctx.sendJson(502, """{"error":"unavailable"}""")
        }
    } catch (e: Exception) {
        ctx.logger.warn("Avatar removal failed: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
    }
}
