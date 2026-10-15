package org.example.awss3

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** A token bucket per key: [capacity] calls at once, [refillPerMinute] more a minute; idle keys are forgotten. */
class RateLimiter(val capacity: Int, val refillPerMinute: Int, private val clock: () -> Long = System::currentTimeMillis) {
    private class Bucket(var tokens: Double, var updatedAt: Long)
    private val buckets = ConcurrentHashMap<String, Bucket>()
    @Volatile private var lastSweep = clock()

    /** Seconds to wait, or 0 when the call may go ahead. */
    fun tryAcquire(key: String): Long {
        val now = clock()
        if (now - lastSweep > 60_000) { lastSweep = now; buckets.entries.removeIf { now - it.value.updatedAt > 10 * 60_000 } }
        val bucket = buckets.computeIfAbsent(key) { Bucket(capacity.toDouble(), now) }
        synchronized(bucket) {
            bucket.tokens = minOf(capacity.toDouble(), bucket.tokens + (now - bucket.updatedAt) / 60_000.0 * refillPerMinute)
            bucket.updatedAt = now
            if (bucket.tokens >= 1.0) { bucket.tokens -= 1.0; return 0 }
            return Math.ceil((1.0 - bucket.tokens) / refillPerMinute * 60).toLong().coerceAtLeast(1)
        }
    }
}

/**
 * Per-client limits in front of the S3 service: writes (uploads, puts, deletes) [writePerMinute] a minute,
 * reads [readPerMinute] a minute, counted per address (the `X-Forwarded-For` hop when the caller is the
 * backend or the website on this machine or a private network). Over the limit: `429`, `Retry-After`,
 * a problem document. `amie.ratelimit.enabled=false` turns it off.
 */
@Component
class RateLimitFilter(
    @Value("\${amie.ratelimit.enabled:true}") private val enabled: Boolean,
    @Value("\${amie.ratelimit.write-per-minute:120}") writePerMinute: Int,
    @Value("\${amie.ratelimit.read-per-minute:600}") readPerMinute: Int,
    @Value("\${amie.ratelimit.trust-forwarded:true}") private val trustForwarded: Boolean,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(RateLimitFilter::class.java)
    val write = RateLimiter(writePerMinute, writePerMinute)
    val read = RateLimiter(readPerMinute, readPerMinute)

    companion object {
        fun clientOf(remoteAddr: String?, forwardedFor: String?, trustForwarded: Boolean): String {
            val peer = remoteAddr?.trim().orEmpty().ifEmpty { "unknown" }
            if (!trustForwarded || forwardedFor.isNullOrBlank() || !isPrivate(peer)) return peer
            val first = forwardedFor.split(',').first().trim()
            return first.takeIf { it.isNotEmpty() && it.length <= 64 && it.none { c -> c.isWhitespace() } } ?: peer
        }
        private fun isPrivate(a: String) = a == "127.0.0.1" || a == "0:0:0:0:0:0:0:1" || a == "::1" || a.startsWith("10.") || a.startsWith("192.168.") || Regex("""172\.(1[6-9]|2\d|3[01])\..*""").matches(a) || a.startsWith("fd")
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (!enabled) { chain.doFilter(request, response); return }
        val isWrite = request.method in setOf("POST", "PUT", "DELETE", "PATCH")
        val client = clientOf(request.remoteAddr, request.getHeader("X-Forwarded-For"), trustForwarded)
        val wait = (if (isWrite) write else read).tryAcquire(client)
        if (wait == 0L) { chain.doFilter(request, response); return }
        log.warn("Rate limit hit by {} on {} {}; retry in {}s", client, request.method, request.requestURI, wait)
        val uri = request.requestURI.replace("\\", "\\\\").replace("\"", "\\\"")
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.setHeader("Retry-After", wait.toString())
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = "UTF-8"
        response.writer.write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Too many requests; try again in $wait seconds\",\"instance\":\"$uri\",\"code\":\"too_many_requests\",\"retryAfterSeconds\":$wait,\"timestamp\":\"${Instant.now()}\"}")
    }
}
