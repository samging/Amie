package org.example.app.api

import com.varabyte.kobweb.api.ApiContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-browser limits on this site's own routes, before anything is forwarded: sign-in attempts, uploads,
 * notes, admin actions. The client is the connection's address, or the first `X-Forwarded-For` hop when
 * `AMIE_TRUST_FORWARDED=1` (the site is behind a reverse proxy). Over a limit the answer is
 * `429 {"error":"too_many_requests"}` with `Retry-After`. The backend keeps its own limits as well.
 */
object RateLimit {
    class Rule(val name: String, val perMinute: Int) {
        private class Bucket(var tokens: Double, var updatedAt: Long)
        private val buckets = ConcurrentHashMap<String, Bucket>()
        @Volatile private var lastSweep = System.currentTimeMillis()

        /** Seconds to wait, or 0. */
        fun tryAcquire(key: String): Long {
            val now = System.currentTimeMillis()
            if (now - lastSweep > 60_000) { lastSweep = now; buckets.entries.removeIf { now - it.value.updatedAt > 10 * 60_000 } }
            val bucket = buckets.computeIfAbsent(key) { Bucket(perMinute.toDouble(), now) }
            synchronized(bucket) {
                bucket.tokens = minOf(perMinute.toDouble(), bucket.tokens + (now - bucket.updatedAt) / 60_000.0 * perMinute)
                bucket.updatedAt = now
                if (bucket.tokens >= 1.0) { bucket.tokens -= 1.0; return 0 }
                return Math.ceil((1.0 - bucket.tokens) / perMinute * 60).toLong().coerceAtLeast(1)
            }
        }
    }

    private fun env(name: String, default: Int) = System.getenv(name)?.toIntOrNull() ?: default

    /** Sign-in and registration attempts: password guessing. */
    val signin = Rule("signin", env("AMIE_LIMIT_SIGNIN", 10))
    /** Package, build and picture uploads. */
    val upload = Rule("upload", env("AMIE_LIMIT_UPLOAD", 20))
    /** Notes, profile saves, publications, admin actions, SSO handoffs. */
    val write = Rule("write", env("AMIE_LIMIT_WRITE", 60))
    /** Expensive reads: model structure, linked-account lookups. */
    val heavy = Rule("heavy", env("AMIE_LIMIT_HEAVY", 30))

    private val trustForwarded = System.getenv("AMIE_TRUST_FORWARDED") == "1"

    /** The address a request counts against. */
    fun clientOf(ctx: ApiContext): String {
        val peer = runCatching { ctx.req.connection.origin.remoteAddress.ifEmpty { ctx.req.connection.origin.remoteHost } }.getOrNull().orEmpty().ifEmpty { "unknown" }
        if (!trustForwarded) return peer
        val forwarded = ctx.req.header("X-Forwarded-For")?.split(',')?.firstOrNull()?.trim().orEmpty()
        return forwarded.takeIf { it.isNotEmpty() && it.length <= 64 && it.none { c -> c.isWhitespace() } } ?: peer
    }

    /**
     * Takes a token from [rule] for the caller; when there is none, answers 429 and returns false so the
     * route can simply `return`.
     */
    fun ApiContext.allow(rule: Rule): Boolean {
        val wait = rule.tryAcquire(clientOf(this))
        if (wait == 0L) return true
        logger.warn("Rate limit '${rule.name}' hit by ${clientOf(this)} on ${req.method} ${req.params.keys.firstOrNull() ?: ""}; retry in ${wait}s")
        res.headers["Retry-After"] = wait.toString()
        res.headers["Cache-Control"] = "no-store"
        sendJson(429, """{"error":"too_many_requests","retryAfterSeconds":$wait}""")
        return false
    }
}

/** The headers every forwarded call carries so the backend can count the real browser, not this server. */
internal fun ApiContext.forwardedHeaders(): Map<String, String> = mapOf("X-Forwarded-For" to RateLimit.clientOf(this))
