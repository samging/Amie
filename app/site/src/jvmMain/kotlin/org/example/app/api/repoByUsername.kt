package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext

private const val TTL_SECONDS = 60L

/** `GET /api/repo-by-username?username=`: one author's packages, cached per author for a minute. */
@Api
suspend fun repoByUsername(ctx: ApiContext) {
    val username = ctx.req.params["username"]?.trim().orEmpty()
    if (username.isEmpty()) {
        ctx.sendJson(400, """{"error":"missing_username"}""")
        return
    }
    val encoded = java.net.URLEncoder.encode(username, Charsets.UTF_8)
    val entry = try {
        ServerCache.fetch("packages:user:$username", TTL_SECONDS, ctx.req.wantsRefresh) {
            ServerCache.fetchUrl(ServerConfig.backend("/user-packages?username=$encoded"), ttlSeconds = TTL_SECONDS)
        }
    } catch (e: Exception) {
        ctx.logger.warn("User packages unavailable: ${e.message}")
        ctx.sendJson(502, "[]")
        return
    }
    ctx.send(entry, TTL_SECONDS, errorBody = "[]")
}
