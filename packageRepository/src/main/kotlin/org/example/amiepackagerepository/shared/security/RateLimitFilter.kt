package org.example.amiepackagerepository.shared.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Per-client rate limits, before any route runs, against brute force and flooding:
 *
 * | rule | routes | per client |
 * |---|---|---|
 * | `signin` | `POST /login`, `POST /register` | [signinPerMinute] a minute (default 10): password guessing |
 * | `upload` | `POST /upload`, `/edit`, `/avatar`, `PUT /files` | [uploadPerMinute] a minute (default 20): big bodies |
 * | `write` | any other `POST`/`PUT`/`DELETE` | [writePerMinute] a minute (default 60) |
 * | `read` | everything else | [readPerMinute] a minute (default 300) |
 *
 * The client is the `X-Forwarded-For` address when the request comes from a trusted proxy (the website's
 * server on the same machine or a private network, `amie.ratelimit.trust-forwarded`), otherwise the
 * remote address. Over the limit the answer is `429` with `Retry-After` and the usual problem document.
 * Limits are in memory per backend instance; `amie.ratelimit.enabled=false` turns them off (tests).
 */
@Component
class RateLimitFilter(
    @Value("\${amie.ratelimit.enabled:true}") private val enabled: Boolean,
    @Value("\${amie.ratelimit.signin-per-minute:10}") signinPerMinute: Int,
    @Value("\${amie.ratelimit.upload-per-minute:20}") uploadPerMinute: Int,
    @Value("\${amie.ratelimit.write-per-minute:60}") writePerMinute: Int,
    @Value("\${amie.ratelimit.read-per-minute:300}") readPerMinute: Int,
    @Value("\${amie.ratelimit.trust-forwarded:true}") private val trustForwarded: Boolean,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(RateLimitFilter::class.java)

    val signin = RateLimiter(signinPerMinute, signinPerMinute)
    val upload = RateLimiter(uploadPerMinute, uploadPerMinute)
    val write = RateLimiter(writePerMinute, writePerMinute)
    val read = RateLimiter(readPerMinute, readPerMinute)

    companion object {
        private val SIGNIN = setOf("/login", "/register", "/handle-login-error", "/api/sso-register")
        private val UPLOAD = setOf("/upload", "/edit", "/avatar", "/background")

        fun ruleFor(method: String, path: String): String = when {
            method == "POST" && path in SIGNIN -> "signin"
            (method == "POST" || method == "PUT") && (path in UPLOAD || path.startsWith("/state/")) -> "upload"
            method == "POST" || method == "PUT" || method == "DELETE" || method == "PATCH" -> "write"
            else -> "read"
        }

        /** The address to count against: the first `X-Forwarded-For` hop behind a trusted proxy, else the peer. */
        fun clientOf(remoteAddr: String?, forwardedFor: String?, trustForwarded: Boolean): String {
            val peer = remoteAddr?.trim().orEmpty().ifEmpty { "unknown" }
            if (!trustForwarded || forwardedFor.isNullOrBlank() || !isPrivate(peer)) return peer
            val first = forwardedFor.split(',').first().trim()
            return first.takeIf { it.isNotEmpty() && it.length <= 64 && it.none { c -> c.isWhitespace() } } ?: peer
        }

        private fun isPrivate(address: String): Boolean =
            address == "127.0.0.1" || address == "0:0:0:0:0:0:0:1" || address == "::1" || address.startsWith("10.") ||
                address.startsWith("192.168.") || Regex("""172\.(1[6-9]|2\d|3[01])\..*""").matches(address) || address.startsWith("fd")
    }

    fun limiterFor(rule: String): RateLimiter = when (rule) { "signin" -> signin; "upload" -> upload; "write" -> write; else -> read }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (!enabled) { chain.doFilter(request, response); return }
        val rule = ruleFor(request.method, request.requestURI)
        val client = clientOf(request.remoteAddr, request.getHeader("X-Forwarded-For"), trustForwarded)
        val wait = limiterFor(rule).tryAcquire("$client|$rule")
        if (wait == 0L) { chain.doFilter(request, response); return }
        log.warn("Rate limit '{}' hit by {} on {} {}; retry in {}s", rule, client, request.method, request.requestURI, wait)
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.setHeader("Retry-After", wait.toString())
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = "UTF-8"
        response.writer.write(problemJson(request.requestURI, wait))
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.requestURI.startsWith("/actuator")

    /** The same shape as the ApiExceptionHandler's documents, written without a JSON library. */
    private fun problemJson(uri: String, wait: Long): String {
        val safeUri = uri.replace("\\", "\\\\").replace("\"", "\\\"")
        return "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429," +
            "\"detail\":\"Too many requests; try again in $wait seconds\",\"instance\":\"$safeUri\"," +
            "\"code\":\"too_many_requests\",\"retryAfterSeconds\":$wait,\"timestamp\":\"${java.time.Instant.now()}\"}"
    }
}
