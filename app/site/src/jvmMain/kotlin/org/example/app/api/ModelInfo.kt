package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.app.api.onnx.OnnxFormatException
import org.example.app.api.onnx.OnnxReader
import org.example.app.api.onnx.OnnxSummary
import java.net.URLEncoder
import java.util.Base64
import org.example.app.api.RateLimit.allow

private const val TTL_SECONDS = 300L
private val json = Json { ignoreUnknownKeys = true }

/**
 * `GET /api/model-info?package=<path>`: the structure of an ONNX model in the store (metadata, inputs, every
 * node with its attributes, initializers, outputs, totals) as JSON, read on this server with [OnnxReader] and
 * cached for five minutes. The bytes come from the backend's `/package` answer (content for small files) or
 * its download URL. `404` for a file that is not there, `422` for a file that is not ONNX, `502` when the
 * store cannot be reached.
 */
@Api("model-info")
suspend fun modelInfo(ctx: ApiContext) {
    if (!ctx.allow(RateLimit.heavy)) return
    val path = ctx.req.params["package"]?.trim().orEmpty()
    if (path.isEmpty()) {
        ctx.sendJson(400, """{"error":"missing_path"}""")
        return
    }
    val key = "model-info:$path"
    if (!ctx.req.wantsRefresh) ServerCache.get(key)?.let { ctx.send(it, TTL_SECONDS); return }
    val encoded = URLEncoder.encode(path, Charsets.UTF_8)
    val meta = try {
        ServerCache.fetchUrl(ServerConfig.backend("/package?path=$encoded"), ttlSeconds = TTL_SECONDS)
    } catch (e: Exception) {
        ctx.logger.warn("Model info unavailable: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
        return
    }
    if (meta.status != 200) {
        ctx.sendJson(if (meta.status == 404) 404 else 502, if (meta.status == 404) """{"error":"not_found"}""" else """{"error":"unavailable"}""")
        return
    }
    val bytes = try {
        modelBytes(String(meta.body, Charsets.UTF_8))
    } catch (e: Exception) {
        ctx.logger.warn("Model bytes unavailable for $path: ${e.message}")
        ctx.sendJson(502, """{"error":"unavailable"}""")
        return
    }
    if (bytes == null) {
        ctx.sendJson(404, """{"error":"not_found"}""")
        return
    }
    val summary: OnnxSummary = try {
        OnnxReader.summarize(bytes)
    } catch (e: OnnxFormatException) {
        ctx.sendJson(422, """{"error":"not_onnx","error_description":${Json.encodeToString(kotlinx.serialization.serializer<String>(), e.message ?: "")}}""")
        return
    }
    val entry = ServerCache.wrap(200, Json.encodeToString(OnnxSummary.serializer(), summary).toByteArray(), "application/json", ttlSeconds = TTL_SECONDS)
    ServerCache.put(key, entry)
    ctx.send(entry, TTL_SECONDS)
}

/** The file's bytes from the backend's `/package` answer: inline for small files, from the download URL otherwise */
private fun modelBytes(packageJson: String): ByteArray? {
    val obj = json.parseToJsonElement(packageJson).jsonObject
    val content = obj["content"]?.jsonPrimitive?.contentOrNull
    if (!content.isNullOrBlank()) return Base64.getMimeDecoder().decode(content.replace(Regex("\\s+"), ""))
    val url = obj["download_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
    val size = obj["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
    if (size > OnnxReader.MAX_BYTES) throw OnnxFormatException("the file is larger than ${OnnxReader.MAX_BYTES / (1024 * 1024)} MB")
    val fetched = ServerCache.fetchUrl(url, ttlSeconds = TTL_SECONDS)
    if (fetched.status == 404) return null
    if (fetched.status != 200) throw IllegalStateException("download answered ${fetched.status}")
    return fetched.body
}
