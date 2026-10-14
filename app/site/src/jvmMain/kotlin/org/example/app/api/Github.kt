package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext

private const val TTL_SECONDS = 60L

/** `GET /api/list-github`: everything under the package store's `uploads/` folder, cached for a minute. */
@Api("list-github")
suspend fun listGithub(ctx: ApiContext) {
    val entry = try {
        ServerCache.fetch("packages:all", TTL_SECONDS, ctx.req.wantsRefresh) {
            ServerCache.fetchUrl(ServerConfig.backend("/list-github"), ttlSeconds = TTL_SECONDS)
        }
    } catch (e: Exception) {
        ctx.logger.warn("Package listing unavailable: ${e.message}")
        ctx.sendJson(502, "[]")
        return
    }
    ctx.send(entry, TTL_SECONDS, errorBody = "[]")
}

/** `GET /api/search-packages?query=`: the package store's search, cached per query for a minute. */
@Api("search-packages")
suspend fun searchPackages(ctx: ApiContext) {
    val query = ctx.req.params["query"]?.trim().orEmpty()
    if (query.isEmpty()) {
        ctx.sendJson(200, "[]")
        return
    }
    val encoded = java.net.URLEncoder.encode(query, Charsets.UTF_8)
    val entry = try {
        ServerCache.fetch("packages:search:$query", TTL_SECONDS, ctx.req.wantsRefresh) {
            ServerCache.fetchUrl(ServerConfig.backend("/query?query=$encoded"), ttlSeconds = TTL_SECONDS)
        }
    } catch (e: Exception) {
        ctx.logger.warn("Package search unavailable: ${e.message}")
        ctx.sendJson(502, "[]")
        return
    }
    ctx.send(entry, TTL_SECONDS, errorBody = "[]")
}
