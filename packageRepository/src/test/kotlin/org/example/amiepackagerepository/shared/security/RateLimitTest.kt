package org.example.amiepackagerepository.shared.security

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/** The token bucket and the filter in front of every route. */
class RateLimitTest {
    @Test
    fun `a bucket allows its capacity at once, then one more per refill interval`() {
        var now = 0L
        val limiter = RateLimiter(capacity = 3, refillPerMinute = 60) { now }
        repeat(3) { assertEquals(0, limiter.tryAcquire("a")) }
        assertEquals(1, limiter.tryAcquire("a"))          // empty: wait a second (60 a minute)
        now += 1_000
        assertEquals(0, limiter.tryAcquire("a"))          // one token came back
        assertEquals(0, limiter.tryAcquire("b"))          // another key has its own bucket
        now += 60 * 60_000
        repeat(3) { assertEquals(0, limiter.tryAcquire("a")) }   // never more than the capacity
        assertTrue(limiter.tryAcquire("a") > 0)
    }

    @Test
    fun `a slow rule reports a longer wait and forgotten keys are swept`() {
        var now = 0L
        val limiter = RateLimiter(capacity = 1, refillPerMinute = 10) { now }
        assertEquals(0, limiter.tryAcquire("x"))
        assertEquals(6, limiter.tryAcquire("x"))          // 10 a minute: six seconds for one token
        now += 11 * 60_000
        limiter.tryAcquire("y")
        assertEquals(1, limiter.size)                     // x was idle for over ten minutes and is gone
    }

    @Test
    fun `routes map to rules and the client is the forwarded address only behind a trusted proxy`() {
        assertEquals("signin", RateLimitFilter.ruleFor("POST", "/login"))
        assertEquals("signin", RateLimitFilter.ruleFor("POST", "/register"))
        assertEquals("upload", RateLimitFilter.ruleFor("POST", "/upload"))
        assertEquals("upload", RateLimitFilter.ruleFor("POST", "/avatar"))
        assertEquals("upload", RateLimitFilter.ruleFor("PUT", "/state/devices"))
        assertEquals("write", RateLimitFilter.ruleFor("POST", "/notes"))
        assertEquals("write", RateLimitFilter.ruleFor("DELETE", "/package"))
        assertEquals("read", RateLimitFilter.ruleFor("GET", "/login"))
        assertEquals("read", RateLimitFilter.ruleFor("GET", "/packages"))

        assertEquals("203.0.113.9", RateLimitFilter.clientOf("127.0.0.1", "203.0.113.9, 10.0.0.1", true))
        assertEquals("127.0.0.1", RateLimitFilter.clientOf("127.0.0.1", "203.0.113.9", false))
        assertEquals("198.51.100.4", RateLimitFilter.clientOf("198.51.100.4", "203.0.113.9", true))   // a public peer cannot spoof
        assertEquals("127.0.0.1", RateLimitFilter.clientOf("127.0.0.1", "   ", true))
        assertEquals("unknown", RateLimitFilter.clientOf(null, null, true))
    }

    @Test
    fun `the filter answers 429 with Retry-After and a problem document once a client is over its limit`() {
        val filter = RateLimitFilter(enabled = true, signinPerMinute = 2, uploadPerMinute = 5, writePerMinute = 5, readPerMinute = 5, trustForwarded = true)
        val chain = mock(FilterChain::class.java)
        fun login(ip: String): MockHttpServletResponse {
            val request = MockHttpServletRequest("POST", "/login").apply { remoteAddr = "127.0.0.1"; addHeader("X-Forwarded-For", ip) }
            val response = MockHttpServletResponse()
            filter.doFilter(request, response, chain)
            return response
        }
        assertEquals(200, login("203.0.113.9").status)
        assertEquals(200, login("203.0.113.9").status)
        val refused = login("203.0.113.9")
        assertEquals(429, refused.status)
        assertEquals("application/problem+json", refused.contentType?.substringBefore(';'))
        assertTrue(refused.getHeader("Retry-After")!!.toInt() >= 1)
        assertTrue(refused.contentAsString.contains("\"code\":\"too_many_requests\""), refused.contentAsString)
        assertTrue(refused.contentAsString.contains("\"instance\":\"/login\""), refused.contentAsString)
        verify(chain, times(2)).doFilter(any(), any())
        // Another address is not affected, and reads are a separate, larger bucket
        assertEquals(200, login("203.0.113.10").status)
        val read = MockHttpServletResponse()
        filter.doFilter(MockHttpServletRequest("GET", "/packages").apply { remoteAddr = "127.0.0.1"; addHeader("X-Forwarded-For", "203.0.113.9") }, read, chain)
        assertEquals(200, read.status)
    }

    @Test
    fun `a disabled filter lets everything through`() {
        val filter = RateLimitFilter(enabled = false, signinPerMinute = 1, uploadPerMinute = 1, writePerMinute = 1, readPerMinute = 1, trustForwarded = true)
        val chain = mock(FilterChain::class.java)
        repeat(5) { filter.doFilter(MockHttpServletRequest("POST", "/login"), MockHttpServletResponse(), chain) }
        verify(chain, times(5)).doFilter(any(), any())
    }
}
