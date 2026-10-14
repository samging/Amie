package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import java.net.URLEncoder

private const val TTL_SECONDS = 300L

/**
 * `GET /api/view?package=<path>`: a file's metadata and content (base64) from the package store, cached
 * for five minutes. Proxies the backend's `GET /package?path=`, which answers `{name, path, size,
 * download_url, content, encoding}` (content only for files up to 1 MiB).
 */
@Api
suspend fun viewPackage(ctx: ApiContext) {
    val path = ctx.req.params["package"]?.trim().orEmpty()
    if (path.isEmpty()) {
        ctx.sendJson(400, """{"error":"missing_path"}""")
        return
    }
    val encoded = URLEncoder.encode(path, Charsets.UTF_8)
    val entry = try {
        ServerCache.fetch("packages:view:$path", TTL_SECONDS, ctx.req.wantsRefresh) {
            ServerCache.fetchUrl(ServerConfig.backend("/package?path=$encoded"), ttlSeconds = TTL_SECONDS)
        }
    } catch (e: Exception) {
        ctx.logger.warn("Package view unavailable: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
        return
    }
    ctx.send(entry, TTL_SECONDS, errorBody = """{"error":"not_found"}""")
}
