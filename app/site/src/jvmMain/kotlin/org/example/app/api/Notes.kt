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

private const val NOTES_TTL_SECONDS = 30L
private const val MAX_NOTE_BODY_BYTES = 16 * 1024
private val notesHttp: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

private fun validPackagePath(path: String): Boolean =
    path.startsWith("uploads/") && path.length < 512 && path.split('/').none { it.isEmpty() || it == "." || it == ".." }

/** Maps the backend's status onto this site's error codes; the backend's text never reaches the page. */
private fun ApiContext.relay(response: HttpResponse<String>, okBody: String = """{"ok":true}""") {
    res.headers["Cache-Control"] = "no-store"
    when (response.statusCode()) {
        200, 201, 204 -> sendJson(200, okBody)
        400 -> sendJson(400, """{"error":"invalid_request"}""")
        401 -> sendJson(401, """{"error":"unauthorized"}""")
        403 -> sendJson(403, """{"error":"forbidden"}""")
        404 -> sendJson(404, """{"error":"not_found"}""")
        413 -> sendJson(413, """{"error":"too_large"}""")
        429 -> sendJson(429, """{"error":"too_many_requests"}""")
        else -> {
            logger.warn("Notes ${req.method} answered ${response.statusCode()}: ${response.body().take(200)}")
            sendJson(502, """{"error":"unavailable"}""")
        }
    }
}

internal fun backendCall(method: String, path: String, token: String?, body: String? = null, contentType: String = "application/json", extraHeaders: Map<String, String> = emptyMap()): HttpResponse<String> {
    val builder = HttpRequest.newBuilder(URI.create(ServerConfig.backend(path))).timeout(Duration.ofSeconds(30))
    if (token != null) builder.header("Authorization", "Bearer $token")
    extraHeaders.forEach { (k, v) -> builder.header(k, v) }
    if (body != null) builder.header("Content-Type", contentType).method(method, HttpRequest.BodyPublishers.ofString(body))
    else builder.method(method, HttpRequest.BodyPublishers.noBody())
    return notesHttp.send(builder.build(), HttpResponse.BodyHandlers.ofString())
}

/**
 * `GET /api/notes?package=`: the notes on a package, newest first, cached here for half a minute.
 * `POST /api/notes?package=` with the bearer and `{"text": "..."}`: adds a note (the backend cleans and
 * limits the text, refuses more than ten a minute per person). Notes are plain text; the page renders
 * them as text, so markup in a note is shown, never run.
 */
@Api("notes")
suspend fun notes(ctx: ApiContext) {
    val path = ctx.req.params["package"]?.trim().orEmpty()
    if (!validPackagePath(path)) {
        ctx.sendJson(400, """{"error":"invalid_path"}""")
        return
    }
    val encoded = URLEncoder.encode(path, Charsets.UTF_8)
    when (ctx.req.method) {
        HttpMethod.GET -> {
            val entry = try {
                ServerCache.fetch("notes:$path", NOTES_TTL_SECONDS, ctx.req.wantsRefresh) {
                    ServerCache.fetchUrl(ServerConfig.backend("/notes?path=$encoded"), ttlSeconds = NOTES_TTL_SECONDS)
                }
            } catch (e: Exception) {
                ctx.logger.warn("Notes unavailable: ${e.message}")
                ctx.sendJson(502, """{"error":"unavailable"}""")
                return
            }
            ctx.send(entry, maxAgeSeconds = 0)
        }
        HttpMethod.POST -> {
            if (!ctx.allow(RateLimit.write)) return
            val token = ctx.bearerToken() ?: run { ctx.sendJson(401, """{"error":"unauthorized"}"""); return }
            val body = ctx.req.body
            if (body == null || body.isEmpty() || body.size > MAX_NOTE_BODY_BYTES) {
                ctx.sendJson(if (body != null && body.size > MAX_NOTE_BODY_BYTES) 413 else 400, """{"error":"${if (body != null && body.size > MAX_NOTE_BODY_BYTES) "too_large" else "invalid_request"}"}""")
                return
            }
            // Only the text is taken from the page; the path comes from the query, checked above
            val text = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(body.decodeToString()).let { (it as kotlinx.serialization.json.JsonObject)["text"] }?.let { (it as kotlinx.serialization.json.JsonPrimitive).content } }.getOrNull()
            if (text == null) { ctx.sendJson(400, """{"error":"invalid_request"}"""); return }
            val json = kotlinx.serialization.json.buildJsonObject {
                put("path", kotlinx.serialization.json.JsonPrimitive(path))
                put("text", kotlinx.serialization.json.JsonPrimitive(text))
            }.toString()
            try {
                val response = backendCall("POST", "/notes", token, json, extraHeaders = ctx.forwardedHeaders())
                ServerCache.invalidate("notes:$path")
                ctx.relay(response, okBody = if (response.statusCode() == 201) response.body() else """{"ok":true}""")
            } catch (e: Exception) {
                ctx.logger.warn("Note could not be added: ${e.message}")
                ctx.sendJson(502, """{"error":"unavailable"}""")
            }
        }
        else -> ctx.res.status = 405
    }
}

/** `POST /api/notes-delete?id=&package=` with the bearer: removes a note (author or package owner). */
@Api("notes-delete")
suspend fun notesDelete(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) { ctx.res.status = 405; return }
    if (!ctx.allow(RateLimit.write)) return
    val token = ctx.bearerToken() ?: run { ctx.sendJson(401, """{"error":"unauthorized"}"""); return }
    val id = ctx.req.params["id"]?.toLongOrNull() ?: run { ctx.sendJson(400, """{"error":"invalid_request"}"""); return }
    val path = ctx.req.params["package"]?.trim().orEmpty()
    try {
        val response = backendCall("DELETE", "/notes/$id", token, extraHeaders = ctx.forwardedHeaders())
        if (path.isNotEmpty()) ServerCache.invalidate("notes:$path") else ServerCache.invalidate("notes:")
        ctx.relay(response)
    } catch (e: Exception) {
        ctx.logger.warn("Note could not be removed: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
    }
}
