package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.HttpMethod
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.example.app.api.RateLimit.allow

private val uploadClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

/**
 * Passes a browser's multipart upload on to the backend untouched (same body, same `Content-Type` with its
 * boundary, same bearer), so the page talks only to this site: no second origin, no CORS, and the backend's
 * port is this server's business. A 400 carries the backend's reasons (why a jar was refused); every other
 * failure is a plain "unavailable".
 */
private fun ApiContext.forwardUpload(path: String) {
    if (req.method != HttpMethod.POST) {
        res.status = 405
        return
    }
    if (!allow(RateLimit.upload)) return
    val token = bearerToken()
    if (token == null) {
        sendJson(401, """{"error":"unauthorized"}""")
        return
    }
    val bytes = req.body
    val contentType = req.contentType
    if (bytes == null || bytes.isEmpty() || contentType == null || !contentType.startsWith("multipart/form-data")) {
        sendJson(400, """{"error":"missing_file"}""")
        return
    }
    try {
        val response = uploadClient.send(
            HttpRequest.newBuilder(URI.create(ServerConfig.backend(path)))
                .timeout(Duration.ofMinutes(10))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", contentType)
                .header("X-Forwarded-For", RateLimit.clientOf(this))
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
            HttpResponse.BodyHandlers.ofString()
        )
        res.headers["Cache-Control"] = "no-store"
        when (response.statusCode()) {
            in 200..299 -> {
                val body = response.body()
                // The backend's /edit answers 200 with an "Error ..." text when it refuses the file
                if (path.startsWith("/edit") && body.startsWith("Error", ignoreCase = true)) {
                    logger.warn("Edit refused: ${body.take(200)}")
                    sendJson(400, """{"error":"rejected"}""")
                    return
                }
                ServerCache.invalidate("packages:")
                ServerCache.invalidate("jars:")
                res.status = 200
                res.contentType = "text/plain"
                res.body = body.toByteArray()
            }
            400 -> {
                res.status = 400
                res.contentType = "text/plain"
                res.body = response.body().toByteArray()
            }
            401 -> sendJson(401, """{"error":"unauthorized"}""")
            403 -> sendJson(403, """{"error":"forbidden"}""")
            413 -> sendJson(413, """{"error":"too_large"}""")
            else -> {
                logger.warn("Upload to $path answered ${response.statusCode()}: ${response.body().take(200)}")
                sendJson(502, """{"error":"unavailable"}""")
            }
        }
    } catch (e: Exception) {
        logger.warn("Upload to $path failed: ${e.message}")
        sendJson(502, """{"error":"unavailable"}""")
    }
}

/** `POST /api/upload`: a new package (multipart `file`, `username`, `progLanguage`) for the backend's `/upload`. */
@Api("upload")
suspend fun uploadPackage(ctx: ApiContext) = ctx.forwardUpload("/upload")

/** `POST /api/edit`: a new version of a file (multipart `file`, `filename`, `username`) for the backend's `/edit`. */
@Api("edit")
suspend fun editPackage(ctx: ApiContext) = ctx.forwardUpload("/edit")
