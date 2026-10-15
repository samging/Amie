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

private const val PROFILE_TTL_SECONDS = 300L
private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

/** The incoming bearer token, without the scheme; null when the request carries none. */
internal fun ApiContext.bearerToken(): String? =
    req.header("Authorization")?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotEmpty() }

private fun backendGet(path: String, token: String?): HttpResponse<String> {
    val builder = HttpRequest.newBuilder(URI.create(ServerConfig.backend(path))).timeout(Duration.ofSeconds(30)).GET()
    if (token != null) builder.header("Authorization", "Bearer $token")
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
}

private fun backendPostJson(path: String, json: String, token: String): HttpResponse<String> =
    http.send(
        HttpRequest.newBuilder(URI.create(ServerConfig.backend(path))).timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json)).build(),
        HttpResponse.BodyHandlers.ofString()
    )

/**
 * `GET /api/profile?username=`: the person's profile, cached here for five minutes. A single sign-on account
 * has no row on the backend until its first contact, so when the backend knows nothing yet and the request
 * carries the person's bearer, the backend's `/me` creates the row and answers the profile instead.
 *
 * `POST /api/profile?username=` with the bearer and `{fullName, email, bio}`: saves the profile. The row is
 * created first in the same way, so a fresh single sign-on account can save right away.
 */
@Api("profile")
suspend fun profile(ctx: ApiContext) {
    val username = ctx.req.params["username"]?.trim().orEmpty()
    if (username.isEmpty()) {
        ctx.sendJson(400, """{"error":"missing_username"}""")
        return
    }
    val encoded = URLEncoder.encode(username, Charsets.UTF_8)
    val cacheKey = "profile:$username"
    when (ctx.req.method) {
        HttpMethod.GET -> {
            val token = ctx.bearerToken()
            val entry = try {
                ServerCache.fetch(cacheKey, PROFILE_TTL_SECONDS, ctx.req.wantsRefresh) {
                    var response = backendGet("/profile?username=$encoded", null)
                    if (response.statusCode() == 404 && token != null) {
                        val me = backendGet("/me", token)
                        if (me.statusCode() == 200) response = me
                    }
                    ServerCache.wrap(response.statusCode(), response.body().toByteArray(), "application/json", ttlSeconds = PROFILE_TTL_SECONDS)
                }
            } catch (e: Exception) {
                ctx.logger.warn("Profile unavailable: ${e.message}")
                ctx.sendJson(502, """{"error":"unavailable"}""")
                return
            }
            ctx.res.headers["Cache-Control"] = "no-store"
            if (entry.status == 404) ctx.sendJson(404, """{"error":"not_found"}""")
            else if (entry.status !in 200..299) ctx.sendJson(502, """{"error":"unavailable"}""")
            else ctx.sendJson(200, entry.body.decodeToString())
        }
        HttpMethod.POST -> {
            val token = ctx.bearerToken()
            if (token == null) {
                ctx.sendJson(401, """{"error":"unauthorized"}""")
                return
            }
            val body = ctx.req.body?.decodeToString()?.takeIf { it.isNotBlank() } ?: "{}"
            try {
                // Make sure the backend has a row for this account before the update looks it up
                val me = backendGet("/me", token)
                if (me.statusCode() == 401) {
                    ctx.sendJson(401, """{"error":"unauthorized"}""")
                    return
                }
                val response = backendPostJson("/profile?username=$encoded", body, token)
                ServerCache.invalidate(cacheKey)
                ctx.res.headers["Cache-Control"] = "no-store"
                when (response.statusCode()) {
                    200 -> ctx.sendJson(200, """{"ok":true}""")
                    401 -> ctx.sendJson(401, """{"error":"unauthorized"}""")
                    403 -> ctx.sendJson(403, """{"error":"forbidden"}""")
                    400 -> ctx.sendJson(400, """{"error":"invalid_profile"}""")
                    else -> {
                        ctx.logger.warn("Profile save for $username answered ${response.statusCode()}: ${response.body().take(200)}")
                        ctx.sendJson(502, """{"error":"unavailable"}""")
                    }
                }
            } catch (e: Exception) {
                ctx.logger.warn("Profile save unavailable: ${e.message}")
                ctx.sendJson(502, """{"error":"unavailable"}""")
            }
        }
        else -> ctx.res.status = 405
    }
}

/**
 * `GET /api/session` with a bearer: whether the backend still accepts the token (its `/dashboard` check), so
 * the browser never has to reach the backend itself. 200 when it does, 401 when it does not.
 */
@Api("session")
suspend fun session(ctx: ApiContext) {
    val token = ctx.bearerToken()
    if (token == null) {
        ctx.sendJson(401, """{"error":"unauthorized"}""")
        return
    }
    ctx.res.headers["Cache-Control"] = "no-store"
    try {
        val response = backendGet("/dashboard", token)
        when (response.statusCode()) {
            200 -> ctx.sendJson(200, """{"ok":true}""")
            401 -> ctx.sendJson(401, """{"error":"unauthorized"}""")
            else -> ctx.sendJson(502, """{"error":"unavailable"}""")
        }
    } catch (e: Exception) {
        ctx.logger.warn("Session check unavailable: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
    }
}
