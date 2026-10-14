package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * `GET /api/delete-package?package=&username=&token=`: deletes one of the signed-in user's files from
 * the package store after the backend confirms the token belongs to that user.
 */
@Api
suspend fun deletePackage(ctx: ApiContext) {
    val githubToken = System.getenv("GITHUB_TOKEN")
    val path = ctx.req.params["package"]?.trim().orEmpty()
    val username = ctx.req.params["username"]?.trim().orEmpty()
    val token = ctx.req.params["token"]?.trim().orEmpty()

    if (path.isEmpty() || username.isEmpty() || token.isEmpty()) {
        ctx.sendJson(400, """{"error":"missing_parameters"}""")
        return
    }

    val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()
    val authRequest = HttpRequest.newBuilder()
        .uri(URI.create(ServerConfig.backend("/dashboard")))
        .header("Authorization", "Bearer $token")
        .GET()
        .build()
    val authResponse = try {
        client.send(authRequest, HttpResponse.BodyHandlers.ofString())
    } catch (e: Exception) {
        ctx.logger.warn("Delete: backend unavailable: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
        return
    }
    if (authResponse.statusCode() != 200) {
        ctx.sendJson(401, """{"error":"unauthorized"}""")
        return
    }
    val loggedInUser = authResponse.body().substringAfter("dashboard, ").substringBefore(" (ID:")
    if (loggedInUser != username) {
        ctx.sendJson(403, """{"error":"forbidden"}""")
        return
    }

    when (val status = deleteFromPackageStore(path)) {
        200 -> { ServerCache.invalidate("packages:"); ctx.sendJson(200, """{"ok":true}""") }
        404 -> ctx.sendJson(404, """{"error":"not_found"}""")
        else -> ctx.sendJson(if (status == 0) 502 else 502, """{"error":"unavailable"}""")
    }
}

/**
 * Deletes [path] from the GitHub package store. Returns 200 when gone, 404 when it was not there,
 * 502 for anything else.
 */
internal fun deleteFromPackageStore(path: String): Int {
    val githubToken = System.getenv("GITHUB_TOKEN")
    val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()
    val encodedPath = path.split("/").joinToString("/") {
        java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }
    val url = "https://api.github.com/repos/samging/codeRepository/contents/$encodedPath"
    fun github() = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", "Bearer $githubToken")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
    return try {
        val metadata = client.send(github().GET().build(), HttpResponse.BodyHandlers.ofString())
        if (metadata.statusCode() != 200) return if (metadata.statusCode() == 404) 404 else 502
        val sha = "\"sha\":\"([a-f0-9]+)\"".toRegex().find(metadata.body())?.groupValues?.get(1) ?: return 502
        val deleteBody = """{"message":"Delete file via Amie","sha":"$sha"}"""
        val deleted = client.send(
            github().method("DELETE", HttpRequest.BodyPublishers.ofString(deleteBody)).build(),
            HttpResponse.BodyHandlers.ofString()
        )
        if (deleted.statusCode() in 200..299) 200 else 502
    } catch (e: Exception) {
        502
    }
}
