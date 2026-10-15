package org.example.awss3

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class RateLimitFilterTest {
    @Test
    fun `writes and reads have their own buckets per client, and the excess gets 429`() {
        val filter = RateLimitFilter(enabled = true, writePerMinute = 2, readPerMinute = 3, trustForwarded = true)
        val chain = mock(FilterChain::class.java)
        fun call(method: String, path: String, ip: String): MockHttpServletResponse {
            val response = MockHttpServletResponse()
            filter.doFilter(MockHttpServletRequest(method, path).apply { remoteAddr = "127.0.0.1"; addHeader("X-Forwarded-For", ip) }, response, chain)
            return response
        }
        assertEquals(200, call("PUT", "/api/aws/files/content", "203.0.113.9").status)
        assertEquals(200, call("DELETE", "/api/aws/files/content", "203.0.113.9").status)
        val refused = call("POST", "/api/aws/upload-data", "203.0.113.9")
        assertEquals(429, refused.status)
        assertEquals("application/problem+json", refused.contentType?.substringBefore(';'))
        assertTrue(refused.getHeader("Retry-After")!!.toInt() >= 1)
        assertTrue(refused.contentAsString.contains("\"code\":\"too_many_requests\""))
        repeat(3) { assertEquals(200, call("GET", "/api/aws/files/root", "203.0.113.9").status) }
        assertEquals(429, call("GET", "/api/aws/files/root", "203.0.113.9").status)
        assertEquals(200, call("GET", "/api/aws/files/root", "203.0.113.10").status)
        verify(chain, times(6)).doFilter(any(), any())
    }

    @Test
    fun `the forwarded address counts only behind a private peer`() {
        assertEquals("203.0.113.9", RateLimitFilter.clientOf("10.0.0.5", "203.0.113.9", true))
        assertEquals("198.51.100.4", RateLimitFilter.clientOf("198.51.100.4", "203.0.113.9", true))
        assertEquals("127.0.0.1", RateLimitFilter.clientOf("127.0.0.1", "203.0.113.9", false))
        var now = 0L
        val limiter = RateLimiter(2, 60) { now }
        assertEquals(0, limiter.tryAcquire("k")); assertEquals(0, limiter.tryAcquire("k")); assertEquals(1, limiter.tryAcquire("k"))
        now += 2_000
        assertEquals(0, limiter.tryAcquire("k"))
    }
}
