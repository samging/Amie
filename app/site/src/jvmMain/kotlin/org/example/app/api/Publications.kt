package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.HttpMethod
import org.example.app.api.RateLimit.allow
import java.net.URLEncoder

private const val PUBLICATIONS_TTL_SECONDS = 60L
private const val MAX_PUBLICATION_BODY_BYTES = 8 * 1024
private fun validUsername(name: String) = name.isNotEmpty() && name.length <= 128 && name.none { it == '/' || it == '\\' || it.isWhitespace() || it.isISOControl() }

/**
 * `GET /api/publications?username=`: a person's publications, cached a minute.
 * `POST /api/publications` with the bearer and `{title, venue, year, url, doi}`: adds one (the backend cleans
 * every field and accepts only `http(s)` links). `POST /api/publications-delete?id=`: removes one of yours.
 */
@Api("publications")
suspend fun publications(ctx: ApiContext) {
    when (ctx.req.method) {
        HttpMethod.GET -> {
            val username = ctx.req.params["username"]?.trim().orEmpty()
            if (!validUsername(username)) { ctx.sendJson(400, """{"error":"missing_username"}"""); return }
            val entry = try {
                ServerCache.fetch("publications:$username", PUBLICATIONS_TTL_SECONDS, ctx.req.wantsRefresh) {
                    ServerCache.fetchUrl(ServerConfig.backend("/publications?username=${URLEncoder.encode(username, Charsets.UTF_8)}"), ttlSeconds = PUBLICATIONS_TTL_SECONDS)
                }
            } catch (e: Exception) {
                ctx.logger.warn("Publications unavailable: ${e.message}")
                ctx.sendJson(502, """{"error":"unavailable"}""")
                return
            }
            ctx.send(entry, maxAgeSeconds = 0)
        }
        HttpMethod.POST -> {
            if (!ctx.allow(RateLimit.write)) return
            val token = ctx.bearerToken() ?: run { ctx.sendJson(401, """{"error":"unauthorized"}"""); return }
            val body = ctx.req.body
            if (body == null || body.isEmpty()) { ctx.sendJson(400, """{"error":"invalid_request"}"""); return }
            if (body.size > MAX_PUBLICATION_BODY_BYTES) { ctx.sendJson(413, """{"error":"too_large"}"""); return }
            try {
                val response = backendCall("POST", "/publications", token, body.decodeToString(), extraHeaders = ctx.forwardedHeaders())
                ServerCache.invalidate("publications:")
                ctx.res.headers["Cache-Control"] = "no-store"
                when (response.statusCode()) {
                    201 -> ctx.sendJson(200, response.body())
                    400 -> ctx.sendJson(400, """{"error":"invalid_request"}""")
                    401 -> ctx.sendJson(401, """{"error":"unauthorized"}""")
                    429 -> ctx.sendJson(429, """{"error":"too_many_requests"}""")
                    else -> { ctx.logger.warn("Publication add answered ${response.statusCode()}: ${response.body().take(200)}"); ctx.sendJson(502, """{"error":"unavailable"}""") }
                }
            } catch (e: Exception) {
                ctx.logger.warn("Publication add failed: ${e.message}")
                ctx.sendJson(502, """{"error":"unavailable"}""")
            }
        }
        else -> ctx.res.status = 405
    }
}

@Api("publications-delete")
suspend fun publicationsDelete(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) { ctx.res.status = 405; return }
    if (!ctx.allow(RateLimit.write)) return
    val token = ctx.bearerToken() ?: run { ctx.sendJson(401, """{"error":"unauthorized"}"""); return }
    val id = ctx.req.params["id"]?.toLongOrNull() ?: run { ctx.sendJson(400, """{"error":"invalid_request"}"""); return }
    try {
        val response = backendCall("DELETE", "/publications/$id", token, extraHeaders = ctx.forwardedHeaders())
        ServerCache.invalidate("publications:")
        ctx.res.headers["Cache-Control"] = "no-store"
        when (response.statusCode()) {
            204 -> ctx.sendJson(200, """{"ok":true}""")
            401 -> ctx.sendJson(401, """{"error":"unauthorized"}""")
            403 -> ctx.sendJson(403, """{"error":"forbidden"}""")
            404 -> ctx.sendJson(404, """{"error":"not_found"}""")
            else -> ctx.sendJson(502, """{"error":"unavailable"}""")
        }
    } catch (e: Exception) {
        ctx.logger.warn("Publication delete failed: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
    }
}
