package org.example.app.api

import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.Request
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/** A response fetched from another service, kept in memory until it expires. */
class CachedResponse(
    val status: Int,
    val body: ByteArray,
    val contentType: String?,
    val headers: Map<String, String>,
    val eTag: String,
    val fetchedAt: Long,
    val expiresAt: Long
) {
    val fresh: Boolean get() = System.currentTimeMillis() < expiresAt
}

/**
 * The site's server-side cache: every proxied answer (package listings, jar bytes, device statuses) is
 * kept here for its TTL so repeated page loads never hit the backend or S3 twice. Entries carry an ETag
 * so browsers can revalidate with `If-None-Match` and get `304 Not Modified`.
 */
object ServerCache {
    private val entries = ConcurrentHashMap<String, CachedResponse>()
    private val http: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    fun get(key: String): CachedResponse? {
        val entry = entries[key] ?: return null
        if (entry.fresh) return entry
        entries.remove(key, entry)
        return null
    }

    fun put(key: String, entry: CachedResponse) {
        entries[key] = entry
    }

    fun invalidate(prefix: String) {
        entries.keys.removeIf { it.startsWith(prefix) }
    }

    fun clear() = entries.clear()

    val size: Int get() = entries.size

    /**
     * Answers [key] from the cache, or runs [load] and caches a successful (2xx) answer for [ttlSeconds].
     * [refresh] skips the cache. Failed answers are returned but never cached.
     */
    fun fetch(key: String, ttlSeconds: Long, refresh: Boolean = false, load: () -> CachedResponse): CachedResponse {
        if (!refresh) get(key)?.let { return it }
        val loaded = load()
        if (loaded.status in 200..299) put(key, loaded)
        return loaded
    }

    /** GET [url] with [headers] and wrap the answer for caching. */
    fun fetchUrl(url: String, headers: Map<String, String> = emptyMap(), ttlSeconds: Long): CachedResponse {
        val builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET()
        headers.forEach { (name, value) -> builder.header(name, value) }
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
        return wrap(
            status = response.statusCode(),
            body = response.body(),
            contentType = response.headers().firstValue("Content-Type").orElse(null),
            headers = buildMap {
                response.headers().firstValue("Content-Disposition").ifPresent { put("Content-Disposition", it) }
                response.headers().firstValue("Last-Modified").ifPresent { put("Last-Modified", it) }
            },
            eTag = response.headers().firstValue("ETag").orElse(null),
            ttlSeconds = ttlSeconds
        )
    }

    fun wrap(
        status: Int,
        body: ByteArray,
        contentType: String?,
        headers: Map<String, String> = emptyMap(),
        eTag: String? = null,
        ttlSeconds: Long
    ): CachedResponse {
        val now = System.currentTimeMillis()
        return CachedResponse(
            status = status,
            body = body,
            contentType = contentType,
            headers = headers,
            eTag = eTag?.takeIf { it.startsWith("\"") || it.startsWith("W/") } ?: "\"${sha256(body)}\"",
            fetchedAt = now,
            expiresAt = now + ttlSeconds * 1000
        )
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(32)

    /** True when the request's `If-None-Match` names [eTag]. */
    fun notModified(req: Request, eTag: String): Boolean {
        val wanted = eTag.removePrefix("W/")
        return req.header("If-None-Match")?.split(',')?.map { it.trim() }
            ?.any { it == "*" || it.removePrefix("W/") == wanted } == true
    }
}

fun Request.header(name: String): String? =
    headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

/** Whether the browser asked to bypass the caches (`?refresh=1`). */
val Request.wantsRefresh: Boolean get() = params["refresh"]?.let { it == "1" || it == "true" } == true

/**
 * Sends [entry] to the browser with `ETag` and `Cache-Control`, or `304 Not Modified` when the browser
 * already holds it. Errors from the other service are passed on without their body, so no detail about
 * the servers behind this site reaches the page.
 */
fun ApiContext.send(entry: CachedResponse, maxAgeSeconds: Long, errorBody: String = """{"error":"unavailable"}""") {
    res.headers["ETag"] = entry.eTag
    res.headers["Cache-Control"] = if (maxAgeSeconds > 0) "public, max-age=$maxAgeSeconds" else "no-store"
    if (entry.status !in 200..299) {
        res.status = if (entry.status == 404) 404 else 502
        res.contentType = "application/json"
        res.body = (if (entry.status == 404 && errorBody.startsWith("{")) """{"error":"not_found"}""" else errorBody).toByteArray()
        return
    }
    if (ServerCache.notModified(req, entry.eTag)) {
        res.status = 304
        res.body = ByteArray(0)
        return
    }
    res.status = entry.status
    entry.contentType?.let { res.contentType = it }
    entry.headers.forEach { (name, value) -> res.headers[name] = value }
    res.body = entry.body
}

fun ApiContext.sendJson(status: Int, json: String) {
    res.status = status
    res.contentType = "application/json"
    res.body = json.toByteArray()
}
