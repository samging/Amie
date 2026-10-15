package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.util.concurrent.Callable
import java.util.concurrent.Executors

private const val TTL_SECONDS = 60L
private val json = Json { ignoreUnknownKeys = true }

/**
 * `GET /api/all-packages`: every file in the store as one flat list, each with its `author`, so a page can
 * show, filter and sort the whole store at once. The backend only lists the authors' folders at the top
 * level, so this route reads each author's folder (sharing the per-author cache entries of
 * `/api/repo-by-username`) and merges them. Cached for a minute; `?refresh=1` re-reads everything.
 */
@Api("all-packages")
suspend fun allPackages(ctx: ApiContext) {
    val refresh = ctx.req.wantsRefresh
    val entry = try {
        ServerCache.fetch("packages:flat", TTL_SECONDS, refresh) { buildFlatListing(refresh) }
    } catch (e: Exception) {
        ctx.logger.warn("Flat package listing unavailable: ${e.message}")
        ctx.sendJson(502, "[]")
        return
    }
    ctx.send(entry, TTL_SECONDS, errorBody = "[]")
}

private fun JsonObject.str(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun flatten(item: JsonObject, author: String): JsonObject = buildJsonObject {
    put("name", item.str("name") ?: "")
    put("path", item.str("path") ?: "")
    put("author", author)
    put("size", (item["size"] as? JsonPrimitive)?.doubleOrNull ?: 0.0)
    put("download_url", item.str("download_url") ?: item.str("downloadUrl") ?: "")
    put("html_url", item.str("html_url") ?: "")
    item.str("sha")?.let { put("sha", it) }
}

private fun userListing(author: String, refresh: Boolean): List<JsonObject> {
    val encoded = URLEncoder.encode(author, Charsets.UTF_8)
    val response = ServerCache.fetch("packages:user:$author", TTL_SECONDS, refresh) {
        ServerCache.fetchUrl(ServerConfig.backend("/user-packages?username=$encoded"), ttlSeconds = TTL_SECONDS)
    }
    if (response.status !in 200..299) return emptyList()
    val parsed = runCatching { json.parseToJsonElement(response.body.decodeToString()) }.getOrNull() as? JsonArray ?: return emptyList()
    return parsed.mapNotNull { it as? JsonObject }.filter { it.str("type") != "dir" }
}

private fun buildFlatListing(refresh: Boolean): CachedResponse {
    val root = ServerCache.fetch("packages:all", TTL_SECONDS, refresh) {
        ServerCache.fetchUrl(ServerConfig.backend("/packages"), ttlSeconds = TTL_SECONDS)
    }
    if (root.status !in 200..299) return root
    val items = (runCatching { json.parseToJsonElement(root.body.decodeToString()) }.getOrNull() as? JsonArray)
        ?.mapNotNull { it as? JsonObject }.orEmpty()

    val merged = mutableListOf<JsonObject>()
    // Files at the top level (rare) keep the author their path names, if any
    items.filter { it.str("type") == "file" }.forEach { item ->
        val path = item.str("path").orEmpty()
        val author = path.removePrefix("uploads/").substringBefore('/', "")
        merged += flatten(item, author)
    }

    val authors = items.filter { it.str("type") == "dir" }.mapNotNull { it.str("name") }.filter { it.isNotEmpty() }
    if (authors.isNotEmpty()) {
        val pool = Executors.newFixedThreadPool(authors.size.coerceIn(1, 6))
        try {
            val futures = authors.map { author -> pool.submit(Callable { author to runCatching { userListing(author, refresh) }.getOrDefault(emptyList()) }) }
            futures.forEach { future ->
                val (author, files) = future.get()
                files.forEach { merged += flatten(it, author) }
            }
        } finally {
            pool.shutdown()
        }
    }
    merged.sortWith(compareBy({ it.str("name")?.lowercase() ?: "" }, { it.str("author") ?: "" }))
    val body = JsonArray(merged).toString().toByteArray()
    return ServerCache.wrap(status = 200, body = body, contentType = "application/json", ttlSeconds = TTL_SECONDS)
}
