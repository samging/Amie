package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.HttpMethod

private const val LISTING_TTL_SECONDS = 60L
private const val JAR_TTL_SECONDS = 3600L
private val JAR_NAME = Regex("""[A-Za-z0-9._-]+\.jar""")

/**
 * `GET /api/jars`: the jars available for download, as the S3 service lists them, cached here for a
 * minute. `?refresh=1` re-reads the bucket.
 */
@Api("jars")
suspend fun listJars(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.GET) {
        ctx.res.status = 405
        return
    }
    val refresh = ctx.req.wantsRefresh
    val entry = try {
        ServerCache.fetch("jars:list", LISTING_TTL_SECONDS, refresh) {
            ServerCache.fetchUrl(ServerConfig.s3Service("/api/aws/jars"), ttlSeconds = LISTING_TTL_SECONDS)
        }
    } catch (e: Exception) {
        ctx.logger.warn("Jar listing unavailable: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
        return
    }
    if (refresh) ServerCache.invalidate("jars:file:")
    ctx.send(entry, maxAgeSeconds = if (refresh) 0 else LISTING_TTL_SECONDS, errorBody = "[]")
}

/**
 * `GET /api/jar-download?name=<file>.jar`: streams the jar as an attachment. The bytes are cached on
 * this server for an hour and the browser is told it may cache them as long.
 */
@Api("jar-download")
suspend fun downloadJar(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.GET) {
        ctx.res.status = 405
        return
    }
    val name = ctx.req.params["name"]?.trim().orEmpty()
    if (!JAR_NAME.matches(name)) {
        ctx.sendJson(400, """{"error":"invalid_name"}""")
        return
    }
    val entry = try {
        ServerCache.fetch("jars:file:$name", JAR_TTL_SECONDS, ctx.req.wantsRefresh) {
            ServerCache.fetchUrl(ServerConfig.s3Service("/api/aws/jars/$name"), ttlSeconds = JAR_TTL_SECONDS)
        }
    } catch (e: Exception) {
        ctx.logger.warn("Jar download unavailable: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
        return
    }
    if (entry.status in 200..299 && "Content-Disposition" !in entry.headers) {
        ctx.res.headers["Content-Disposition"] = "attachment; filename=\"$name\""
    }
    ctx.send(entry, maxAgeSeconds = JAR_TTL_SECONDS)
}
