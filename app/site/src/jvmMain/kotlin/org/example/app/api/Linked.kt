package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.example.app.api.RateLimit.allow
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private const val LINKED_TTL_SECONDS = 600L
private val GITHUB_LOGIN = Regex("[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38}")
private val HF_LOGIN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
private val linkedHttp: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()
private val linkedJson = Json { ignoreUnknownKeys = true }

/**
 * `GET /api/linked?username=`: what a person's linked GitHub and Hugging Face accounts hold, fetched here
 * (never from the browser) from the two public APIs and cached ten minutes. The handles come from the
 * person's profile on the backend and must match the services' username rules, so this server only ever
 * calls `api.github.com/users/<login>/repos` and `huggingface.co/api/models?author=<login>`: no other host,
 * no redirects, five-second connect and ten-second read timeouts, at most twelve items each, and only
 * the fields the page shows (name, description, stars or downloads, address). A failure of one service
 * leaves the other's list intact.
 */
@Api("linked")
suspend fun linked(ctx: ApiContext) {
    val username = ctx.req.params["username"]?.trim().orEmpty()
    if (username.isEmpty() || username.length > 128) { ctx.sendJson(400, """{"error":"missing_username"}"""); return }
    if (!ctx.allow(RateLimit.heavy)) return
    val entry = try {
        ServerCache.fetch("linked:$username", LINKED_TTL_SECONDS, ctx.req.wantsRefresh) {
            val profile = ServerCache.fetchUrl(ServerConfig.backend("/profile?username=${URLEncoder.encode(username, Charsets.UTF_8)}"), ttlSeconds = 60)
            val obj = if (profile.status == 200) runCatching { linkedJson.parseToJsonElement(profile.body.decodeToString()).jsonObject }.getOrNull() else null
            val github = obj?.get("github")?.jsonPrimitive?.content?.takeIf { GITHUB_LOGIN.matches(it) }.orEmpty()
            val hf = obj?.get("huggingFace")?.jsonPrimitive?.content?.takeIf { HF_LOGIN.matches(it) }.orEmpty()
            val body = buildJsonObject {
                put("github", github)
                put("huggingFace", hf)
                put("repos", if (github.isEmpty()) JsonArray(emptyList()) else githubRepos(ctx, github))
                put("models", if (hf.isEmpty()) JsonArray(emptyList()) else huggingFaceModels(ctx, hf))
            }.toString()
            ServerCache.wrap(200, body.toByteArray(), "application/json", ttlSeconds = LINKED_TTL_SECONDS)
        }
    } catch (e: Exception) {
        ctx.logger.warn("Linked accounts unavailable: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
        return
    }
    ctx.send(entry, maxAgeSeconds = 300)
}

/** A string field, or "" for a missing or JSON `null` one (GitHub sends `"language": null`). */
private fun JsonObject.str(key: String): String = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.content.orEmpty()

private fun fetchJson(url: String): String? = runCatching {
    val response = linkedHttp.send(
        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json").header("User-Agent", "amie-site").GET().build(),
        HttpResponse.BodyHandlers.ofString()
    )
    if (response.statusCode() == 200 && response.body().length < 2_000_000) response.body() else null
}.getOrNull()

private fun githubRepos(ctx: ApiContext, login: String): JsonArray {
    val text = fetchJson("https://api.github.com/users/${URLEncoder.encode(login, Charsets.UTF_8)}/repos?sort=updated&per_page=12&type=owner") ?: return JsonArray(emptyList())
    val repos = runCatching { linkedJson.parseToJsonElement(text).jsonArray }.getOrElse { return JsonArray(emptyList()) }
    return buildJsonArray {
        for (repo in repos.take(12)) {
            val r = repo as? JsonObject ?: continue
            val name = r.str("name")
            if (name.isEmpty()) continue
            val url = r.str("html_url")
            if (!url.startsWith("https://github.com/")) continue
            add(buildJsonObject {
                put("name", name.take(100))
                put("description", r.str("description").take(200))
                put("language", r.str("language").take(40))
                put("stars", r.str("stargazers_count").toIntOrNull() ?: 0)
                put("url", url)
                put("updated", r.str("pushed_at").take(30))
            })
        }
    }
}

private fun huggingFaceModels(ctx: ApiContext, login: String): JsonArray {
    val text = fetchJson("https://huggingface.co/api/models?author=${URLEncoder.encode(login, Charsets.UTF_8)}&limit=12&sort=downloads&direction=-1") ?: return JsonArray(emptyList())
    val models = runCatching { linkedJson.parseToJsonElement(text).jsonArray }.getOrElse { return JsonArray(emptyList()) }
    return buildJsonArray {
        for (model in models.take(12)) {
            val m = model as? JsonObject ?: continue
            val id = m.str("modelId").ifEmpty { m.str("id") }
            if (id.isEmpty()) continue
            if (!id.matches(Regex("[A-Za-z0-9._/-]{1,200}"))) continue
            add(buildJsonObject {
                put("id", id)
                put("pipeline", m.str("pipeline_tag").take(40))
                put("downloads", m.str("downloads").toIntOrNull() ?: 0)
                put("likes", m.str("likes").toIntOrNull() ?: 0)
                put("url", "https://huggingface.co/$id")
            })
        }
    }
}
