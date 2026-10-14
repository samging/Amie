package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.HttpMethod
import org.example.app.api.keycloak.Identity
import org.example.app.api.keycloak.KeycloakTokens
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Administration routes: only for a Keycloak account that carries the admin role (see
 * [KeycloakTokens.adminRole]). Every call needs `Authorization: Bearer <access token>`.
 */

private fun ApiContext.requireAdmin(): Identity? {
    val token = req.header("Authorization")?.removePrefix("Bearer ")?.trim()
    if (token.isNullOrEmpty()) {
        sendJson(401, """{"error":"unauthorized"}""")
        return null
    }
    val identity = KeycloakTokens.verify(token)
    if (identity == null) {
        sendJson(401, """{"error":"unauthorized"}""")
        return null
    }
    if (!identity.isAdmin) {
        sendJson(403, """{"error":"forbidden"}""")
        return null
    }
    return identity
}

private fun jsonString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/** `GET /api/admin-me`: who the admin is; 401/403 otherwise. */
@Api("admin-me")
suspend fun adminMe(ctx: ApiContext) {
    val identity = ctx.requireAdmin() ?: return
    ctx.sendJson(200, """{"username":${jsonString(identity.username)},"admin":true,"roles":[${identity.roles.joinToString(",") { jsonString(it) }}]}""")
}

/** `POST /api/admin-delete-jar?name=`: removes a package jar or application build from the bucket. */
@Api("admin-delete-jar")
suspend fun adminDeleteJar(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) { ctx.res.status = 405; return }
    val identity = ctx.requireAdmin() ?: return
    val name = ctx.req.params["name"]?.trim().orEmpty()
    if (!Regex("""[A-Za-z0-9._-]+\.[A-Za-z]+""").matches(name)) {
        ctx.sendJson(400, """{"error":"invalid_name"}""")
        return
    }
    try {
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(ServerConfig.s3Service("/api/aws/jars/$name"))).DELETE().build(),
            HttpResponse.BodyHandlers.ofString()
        )
        ServerCache.invalidate("jars:")
        ctx.logger.info("Admin ${identity.username} deleted $name: ${response.statusCode()}")
        when (response.statusCode()) {
            204 -> ctx.sendJson(200, """{"ok":true}""")
            404 -> ctx.sendJson(404, """{"error":"not_found"}""")
            else -> ctx.sendJson(502, """{"error":"unavailable"}""")
        }
    } catch (e: Exception) {
        ctx.logger.warn("Admin delete jar failed: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
    }
}

/** `POST /api/admin-delete-package?package=`: removes any file from the package store. */
@Api("admin-delete-package")
suspend fun adminDeletePackage(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) { ctx.res.status = 405; return }
    val identity = ctx.requireAdmin() ?: return
    val path = ctx.req.params["package"]?.trim().orEmpty()
    if (path.isEmpty() || !path.startsWith("uploads/") || path.contains("..")) {
        ctx.sendJson(400, """{"error":"invalid_path"}""")
        return
    }
    val status = deleteFromPackageStore(path)
    ctx.logger.info("Admin ${identity.username} deleted package $path: $status")
    when (status) {
        200 -> { ServerCache.invalidate("packages:"); ctx.sendJson(200, """{"ok":true}""") }
        404 -> ctx.sendJson(404, """{"error":"not_found"}""")
        else -> ctx.sendJson(502, """{"error":"unavailable"}""")
    }
}

/** `POST /api/admin-release-upload?fileName=` with the file as the body: publishes an application build. */
@Api("admin-release-upload")
suspend fun adminReleaseUpload(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) { ctx.res.status = 405; return }
    val identity = ctx.requireAdmin() ?: return
    val fileName = ctx.req.params["fileName"]?.trim().orEmpty()
    val bytes = ctx.req.body
    if (fileName.isEmpty() || bytes == null || bytes.isEmpty()) {
        ctx.sendJson(400, """{"error":"missing_file"}""")
        return
    }
    try {
        val encoded = URLEncoder.encode(fileName, Charsets.UTF_8)
        val response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().send(
            HttpRequest.newBuilder(URI.create(ServerConfig.s3Service("/api/aws/jars/releases?fileName=$encoded")))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
            HttpResponse.BodyHandlers.ofString()
        )
        ServerCache.invalidate("jars:")
        ctx.logger.info("Admin ${identity.username} published $fileName (${bytes.size} bytes): ${response.statusCode()}")
        when (response.statusCode()) {
            200 -> ctx.sendJson(200, response.body())
            400 -> ctx.sendJson(400, """{"error":${jsonString(response.body())}}""")
            else -> ctx.sendJson(502, """{"error":"unavailable"}""")
        }
    } catch (e: Exception) {
        ctx.logger.warn("Admin release upload failed: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
    }
}
