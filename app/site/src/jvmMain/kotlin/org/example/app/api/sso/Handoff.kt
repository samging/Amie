package org.example.app.api.sso

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.example.app.api.keycloak.parseForm
import org.example.app.api.sendJson
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Single sign-on from the Amie desktop app into this website.
 *
 * The app is signed in with Keycloak but the browser it opens is not, so the app hands its tokens over:
 * `POST /api/sso/handoff` with Keycloak's token JSON (`access_token`, `refresh_token`, `id_token`,
 * `expires_in`) answers `{"code": ..., "expires_in": 120}`. The app opens `/sso?code=<code>&next=<path>`
 * in the browser; that page claims the tokens once with `POST /api/sso/claim` (`code=`), verifies them
 * like any other sign-in (signature, issuer, expiry) and keeps the session. The tokens never appear in a
 * URL, a code works once and dies after [TTL_MS].
 */
private const val TTL_MS = 120_000L
private const val MAX_PENDING = 256

private class Pending(val tokens: JsonObject, val expiresAt: Long)

private val pending = ConcurrentHashMap<String, Pending>()
private val random = SecureRandom()

private fun purge() {
    val now = System.currentTimeMillis()
    pending.entries.removeIf { it.value.expiresAt <= now }
}

private fun newCode(): String {
    val bytes = ByteArray(32)
    random.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun looksLikeJwt(value: String?): Boolean =
    value != null && value.count { it == '.' } == 2 && value.length in 32..16_384 && value.none { it.isWhitespace() }

/** `POST /api/sso/handoff`: the app's tokens in, a one-time code out. */
@Api("handoff")
suspend fun handoff(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) {
        ctx.res.status = 405
        return
    }
    val body = ctx.req.body?.decodeToString().orEmpty()
    val tokens = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
    val accessToken = tokens?.get("access_token")?.jsonPrimitive?.content
    if (tokens == null || !looksLikeJwt(accessToken)) {
        ctx.sendJson(400, """{"error":"invalid_request","error_description":"send Keycloak's token JSON with an access_token"}""")
        return
    }
    purge()
    if (pending.size >= MAX_PENDING) {
        ctx.sendJson(429, """{"error":"too_many_requests"}""")
        return
    }
    // Only the token fields are kept, the way Keycloak's token endpoint answers them
    val kept = buildJsonObject {
        put("access_token", accessToken!!)
        tokens["refresh_token"]?.jsonPrimitive?.content?.takeIf { looksLikeJwt(it) }?.let { put("refresh_token", it) }
        tokens["id_token"]?.jsonPrimitive?.content?.takeIf { looksLikeJwt(it) }?.let { put("id_token", it) }
        put("expires_in", (tokens["expires_in"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 60)
        put("token_type", "Bearer")
    }
    val code = newCode()
    pending[code] = Pending(kept, System.currentTimeMillis() + TTL_MS)
    ctx.sendJson(200, """{"code":"$code","expires_in":${TTL_MS / 1000}}""")
}

/** `POST /api/sso/claim` (`code=`): the tokens behind a code, once; 404 for an unknown, used or expired code. */
@Api("claim")
suspend fun claim(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) {
        ctx.res.status = 405
        return
    }
    val body = ctx.req.body?.decodeToString().orEmpty()
    val code = parseForm(body)["code"]?.trim()
        ?: runCatching { Json.parseToJsonElement(body).jsonObject["code"]?.jsonPrimitive?.content }.getOrNull()
    if (code.isNullOrEmpty()) {
        ctx.sendJson(400, """{"error":"invalid_request","error_description":"send the code"}""")
        return
    }
    purge()
    val found = pending.remove(code)
    if (found == null || found.expiresAt <= System.currentTimeMillis()) {
        ctx.sendJson(404, """{"error":"unknown_code","error_description":"the sign-in link was already used or has expired"}""")
        return
    }
    ctx.res.headers["Cache-Control"] = "no-store"
    ctx.sendJson(200, found.tokens.toString())
}
