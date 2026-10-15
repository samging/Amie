package org.example.app.api

import com.varabyte.kobweb.api.Api
import com.varabyte.kobweb.api.ApiContext
import com.varabyte.kobweb.api.http.HttpMethod
import org.example.app.api.keycloak.KeycloakTokens
import java.net.URLEncoder
import org.example.app.api.RateLimit.allow

/**
 * The admin panel's people and notes, forwarded to the backend's `/admin` routes. The role is checked
 * twice: here, by verifying the Keycloak token's roles ([KeycloakTokens]), and again by the backend
 * before it acts. Everything goes with the admin's own bearer, so the backend logs who did what.
 */

private fun ApiContext.adminToken(): String? {
    val token = bearerToken() ?: run { sendJson(401, """{"error":"unauthorized"}"""); return null }
    val identity = KeycloakTokens.verify(token) ?: run { sendJson(401, """{"error":"unauthorized"}"""); return null }
    if (!identity.isAdmin) { sendJson(403, """{"error":"forbidden"}"""); return null }
    return token
}

private fun ApiContext.forwardAdmin(method: String, path: String, token: String, passBody: Boolean = false) {
    res.headers["Cache-Control"] = "no-store"
    try {
        val response = backendCall(method, path, token, extraHeaders = forwardedHeaders())
        when (response.statusCode()) {
            200 -> sendJson(200, if (passBody) response.body() else """{"ok":true}""")
            204 -> sendJson(200, """{"ok":true}""")
            400 -> sendJson(400, """{"error":"invalid_request"}""")
            401 -> sendJson(401, """{"error":"unauthorized"}""")
            403 -> sendJson(403, """{"error":"forbidden"}""")
            404 -> sendJson(404, """{"error":"not_found"}""")
            else -> { logger.warn("Admin $method $path answered ${response.statusCode()}: ${response.body().take(200)}"); sendJson(502, """{"error":"unavailable"}""") }
        }
    } catch (e: Exception) {
        logger.warn("Admin $method $path failed: ${e.message}")
        sendJson(502, """{"error":"unavailable"}""")
    }
}

private fun validUsername(name: String) = name.isNotEmpty() && name.length <= 128 && name.none { it == '/' || it == '\\' || it.isWhitespace() || it.isISOControl() }

/** `GET /api/admin-inbox`: what waits for an administrator (pending pictures, the day's notes) with counts for the badge. */
@Api("admin-inbox")
suspend fun adminInbox(ctx: ApiContext) {
    val token = ctx.adminToken() ?: return
    ctx.forwardAdmin("GET", "/admin/inbox", token, passBody = true)
}

/** `GET /api/admin-avatars`: the pictures waiting for approval (preview each with `/api/avatar-preview`). */
@Api("admin-avatars")
suspend fun adminAvatars(ctx: ApiContext) {
    val token = ctx.adminToken() ?: return
    ctx.forwardAdmin("GET", "/admin/avatars/pending", token, passBody = true)
}

/** `GET /api/admin-badges`: the badges that can be granted. */
@Api("admin-badges")
suspend fun adminBadges(ctx: ApiContext) {
    val token = ctx.adminToken() ?: return
    ctx.forwardAdmin("GET", "/admin/badges", token, passBody = true)
}

/** `POST /api/admin-badge?username=&badge=&action=grant|revoke`: gives or takes a badge. */
@Api("admin-badge")
suspend fun adminBadge(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) { ctx.res.status = 405; return }
    if (!ctx.allow(RateLimit.write)) return
    val token = ctx.adminToken() ?: return
    val username = ctx.req.params["username"]?.trim().orEmpty()
    val badge = ctx.req.params["badge"]?.trim().orEmpty()
    if (!validUsername(username) || !badge.matches(Regex("[a-z]{1,32}"))) { ctx.sendJson(400, """{"error":"invalid_request"}"""); return }
    val path = "/admin/users/${URLEncoder.encode(username, Charsets.UTF_8)}/badges/$badge"
    ServerCache.invalidate("profile:$username")
    when (ctx.req.params["action"]) {
        "grant" -> ctx.forwardAdmin("POST", path, token)
        "revoke" -> ctx.forwardAdmin("DELETE", path, token)
        else -> ctx.sendJson(400, """{"error":"invalid_request"}""")
    }
}

/** `POST /api/admin-publication-delete?id=`: removes anyone's publication. */
@Api("admin-publication-delete")
suspend fun adminPublicationDelete(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) { ctx.res.status = 405; return }
    if (!ctx.allow(RateLimit.write)) return
    val token = ctx.adminToken() ?: return
    val id = ctx.req.params["id"]?.toLongOrNull() ?: run { ctx.sendJson(400, """{"error":"invalid_request"}"""); return }
    ServerCache.invalidate("publications:")
    ctx.forwardAdmin("DELETE", "/admin/publications/$id", token)
}

/** `GET /api/admin-users`: every account with its profile and note count. */
@Api("admin-users")
suspend fun adminUsers(ctx: ApiContext) {
    val token = ctx.adminToken() ?: return
    ctx.forwardAdmin("GET", "/admin/users", token, passBody = true)
}

/** `GET /api/admin-notes?limit=`: the latest notes across all packages. */
@Api("admin-notes")
suspend fun adminNotes(ctx: ApiContext) {
    val token = ctx.adminToken() ?: return
    val limit = ctx.req.params["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 100
    ctx.forwardAdmin("GET", "/admin/notes?limit=$limit", token, passBody = true)
}

/** `POST /api/admin-note-delete?id=`: removes any note. */
@Api("admin-note-delete")
suspend fun adminNoteDelete(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) { ctx.res.status = 405; return }
    if (!ctx.allow(RateLimit.write)) return
    val token = ctx.adminToken() ?: return
    val id = ctx.req.params["id"]?.toLongOrNull() ?: run { ctx.sendJson(400, """{"error":"invalid_request"}"""); return }
    ServerCache.invalidate("notes:")
    ctx.forwardAdmin("DELETE", "/admin/notes/$id", token)
}

/** `POST /api/admin-user-action?username=&action=clear-nickname|clear-avatar|delete`: moderates an account. */
@Api("admin-user-action")
suspend fun adminUserAction(ctx: ApiContext) {
    if (ctx.req.method != HttpMethod.POST) { ctx.res.status = 405; return }
    if (!ctx.allow(RateLimit.write)) return
    val token = ctx.adminToken() ?: return
    val username = ctx.req.params["username"]?.trim().orEmpty()
    if (!validUsername(username)) { ctx.sendJson(400, """{"error":"missing_username"}"""); return }
    val encoded = URLEncoder.encode(username, Charsets.UTF_8)
    when (ctx.req.params["action"]) {
        "approve-avatar" -> { ServerCache.invalidate("avatar:$username"); ServerCache.invalidate("profile:$username"); ctx.forwardAdmin("POST", "/admin/users/$encoded/avatar/approve", token); return }
        "approve-background" -> { ServerCache.invalidate("background:$username"); ServerCache.invalidate("profile:$username"); ctx.forwardAdmin("POST", "/admin/users/$encoded/avatar/approve?kind=background", token); return }
    }
    val path = when (ctx.req.params["action"]) {
        "clear-nickname" -> "/admin/users/$encoded/nickname"
        "clear-background" -> { ServerCache.invalidate("background:$username"); "/admin/users/$encoded/background" }
        "clear-avatar" -> { ServerCache.invalidate("avatar:$username"); "/admin/users/$encoded/avatar" }
        "delete" -> { ServerCache.invalidate("avatar:$username"); ServerCache.invalidate("notes:"); "/admin/users/$encoded" }
        else -> { ctx.sendJson(400, """{"error":"invalid_request"}"""); return }
    }
    ServerCache.invalidate("profile:$username")
    ctx.forwardAdmin("DELETE", path, token)
}
