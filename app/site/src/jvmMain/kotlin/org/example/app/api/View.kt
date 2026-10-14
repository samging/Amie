package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import kotlinx.serialization.json.*

private const val TTL_SECONDS = 300L

/**
 * `GET /api/view?package=<path>`: a file's metadata and content (base64) from the package store, cached
 * for five minutes. Files over GitHub's inline limit are fetched from their download URL.
 */
@Api
suspend fun viewPackage(ctx: ApiContext) {
    val path = ctx.req.params["package"]?.trim().orEmpty()
    if (path.isEmpty()) {
        ctx.sendJson(400, """{"error":"missing_path"}""")
        return
    }
    val entry = try {
        ServerCache.fetch("packages:view:$path", TTL_SECONDS, ctx.req.wantsRefresh) { loadFile(path) }
    } catch (e: Exception) {
        ctx.logger.warn("Package view unavailable: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
        return
    }
    ctx.send(entry, TTL_SECONDS, errorBody = """{"error":"not_found"}""")
}

private fun loadFile(path: String): CachedResponse {
    val githubToken = (System.getenv("GITHUB_TOKEN") ?: System.getProperty("GITHUB_TOKEN"))?.trim()
    val encodedPath = path.split("/").joinToString("/") {
        java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }
    val url = "https://api.github.com/repos/samging/codeRepository/contents/$encodedPath"
    val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()

    fun request(target: String) = HttpRequest.newBuilder()
        .uri(URI.create(target))
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .apply { if (!githubToken.isNullOrBlank()) header("Authorization", "Bearer $githubToken") }
        .GET()
        .build()

    val response = client.send(request(url), HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() != 200) {
        return ServerCache.wrap(response.statusCode(), ByteArray(0), "application/json", ttlSeconds = 0)
    }

    val element = Json.parseToJsonElement(response.body())
    if (element is JsonArray) {
        return ServerCache.wrap(200, response.body().toByteArray(), "application/json", ttlSeconds = TTL_SECONDS)
    }

    val json = element.jsonObject.toMutableMap()
    val content = json["content"]?.jsonPrimitive?.contentOrNull
    val downloadUrl = json["download_url"]?.jsonPrimitive?.contentOrNull
    if (content.isNullOrBlank() && downloadUrl != null) {
        val raw = client.send(request(downloadUrl), HttpResponse.BodyHandlers.ofByteArray())
        if (raw.statusCode() == 200) {
            json["content"] = JsonPrimitive(Base64.getEncoder().encodeToString(raw.body()))
            json["encoding"] = JsonPrimitive("base64")
        }
    }
    return ServerCache.wrap(200, JsonObject(json).toString().toByteArray(), "application/json", ttlSeconds = TTL_SECONDS)
}
