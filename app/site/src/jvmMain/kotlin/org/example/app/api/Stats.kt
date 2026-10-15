package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.HttpMethod
import org.example.app.api.RateLimit.allow
import java.net.URLEncoder

private const val STATS_TTL_SECONDS = 30L
private fun validPath(path: String) = path.startsWith("uploads/") && path.length < 512 && path.split('/').none { it.isEmpty() || it == "." || it == ".." }

/** `GET /api/stats?username=`: downloads and likes of an author's packages, cached half a minute. */
@Api("stats")
suspend fun stats(ctx: ApiContext) {
    val username = ctx.req.params["username"]?.trim().orEmpty()
    if (username.isEmpty() || username.length > 128 || username.contains('/')) { ctx.sendJson(400, """{"error":"missing_username"}"""); return }
    val entry = try {
        ServerCache.fetch("stats:$username", STATS_TTL_SECONDS, ctx.req.wantsRefresh) {
            ServerCache.fetchUrl(ServerConfig.backend("/stats?username=${URLEncoder.encode(username, Charsets.UTF_8)}"), ttlSeconds = STATS_TTL_SECONDS)
        }
    } catch (e: Exception) {
        ctx.sendJson(502, """{"error":"unavailable"}""")
        return
    }
    ctx.send(entry, maxAgeSeconds = 0)
}

/** `POST /api/track-download?package=`: counts a download; the page sends it as the file starts. */
@Api("track-download")
suspend fun trackDownload(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) { ctx.res.status = 405; return }
    if (!ctx.allow(RateLimit.write)) return
    val path = ctx.req.params["package"]?.trim().orEmpty()
    if (!validPath(path)) { ctx.sendJson(400, """{"error":"invalid_path"}"""); return }
    try {
        val response = backendCall("POST", "/stats/download?path=${URLEncoder.encode(path, Charsets.UTF_8)}", null, extraHeaders = ctx.forwardedHeaders())
        ServerCache.invalidate("stats:")
        ctx.res.headers["Cache-Control"] = "no-store"
        if (response.statusCode() == 200) ctx.sendJson(200, """{"ok":true}""") else ctx.sendJson(502, """{"error":"unavailable"}""")
    } catch (e: Exception) {
        ctx.sendJson(502, """{"error":"unavailable"}""")
    }
}

/** `POST /api/like?package=` with the bearer: likes or unlikes a package; answers `{liked, likes}`. */
@Api("like")
suspend fun like(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) { ctx.res.status = 405; return }
    if (!ctx.allow(RateLimit.write)) return
    val token = ctx.bearerToken() ?: run { ctx.sendJson(401, """{"error":"unauthorized"}"""); return }
    val path = ctx.req.params["package"]?.trim().orEmpty()
    if (!validPath(path)) { ctx.sendJson(400, """{"error":"invalid_path"}"""); return }
    try {
        val response = backendCall("POST", "/likes?path=${URLEncoder.encode(path, Charsets.UTF_8)}", token, extraHeaders = ctx.forwardedHeaders())
        ServerCache.invalidate("stats:")
        ctx.res.headers["Cache-Control"] = "no-store"
        when (response.statusCode()) {
            200 -> ctx.sendJson(200, response.body())
            401 -> ctx.sendJson(401, """{"error":"unauthorized"}""")
            404 -> ctx.sendJson(404, """{"error":"not_found"}""")
            400 -> ctx.sendJson(400, """{"error":"invalid_request"}""")
            else -> ctx.sendJson(502, """{"error":"unavailable"}""")
        }
    } catch (e: Exception) {
        ctx.sendJson(502, """{"error":"unavailable"}""")
    }
}

/** `GET /api/likes` with the bearer: the paths the caller liked. */
@Api("likes")
suspend fun likes(ctx: ApiContext) {
    val token = ctx.bearerToken() ?: run { ctx.sendJson(401, """{"error":"unauthorized"}"""); return }
    try {
        val response = backendCall("GET", "/likes", token)
        ctx.res.headers["Cache-Control"] = "no-store"
        when (response.statusCode()) {
            200 -> ctx.sendJson(200, response.body())
            401 -> ctx.sendJson(401, """{"error":"unauthorized"}""")
            else -> ctx.sendJson(502, """{"error":"unavailable"}""")
        }
    } catch (e: Exception) {
        ctx.sendJson(502, """{"error":"unavailable"}""")
    }
}
