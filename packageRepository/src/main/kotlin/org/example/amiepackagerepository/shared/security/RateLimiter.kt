package org.example.amiepackagerepository.shared.security

import java.util.concurrent.ConcurrentHashMap

/**
 * A token bucket per key, in memory: each key may make [capacity] calls at once and earns
 * [refillPerMinute] more a minute. Keys that have not been seen for ten minutes are forgotten.
 * One instance per rule (sign-in attempts, uploads, everything); see [RateLimitFilter].
 */
class RateLimiter(val capacity: Int, val refillPerMinute: Int, private val clock: () -> Long = System::currentTimeMillis) {
    private class Bucket(var tokens: Double, var updatedAt: Long)
    private val buckets = ConcurrentHashMap<String, Bucket>()
    @Volatile private var lastSweep = clock()

    /** Takes one token for [key]; the seconds to wait when there is none, or 0 when the call may go ahead. */
    fun tryAcquire(key: String): Long {
        sweep()
        val now = clock()
        val bucket = buckets.computeIfAbsent(key) { Bucket(capacity.toDouble(), now) }
        synchronized(bucket) {
            val refill = (now - bucket.updatedAt) / 60_000.0 * refillPerMinute
            bucket.tokens = minOf(capacity.toDouble(), bucket.tokens + refill)
            bucket.updatedAt = now
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0
                return 0
            }
            val missing = 1.0 - bucket.tokens
            return Math.ceil(missing / refillPerMinute * 60).toLong().coerceAtLeast(1)
        }
    }

    val size: Int get() = buckets.size

    private fun sweep() {
        val now = clock()
        if (now - lastSweep < 60_000) return
        lastSweep = now
        buckets.entries.removeIf { now - it.value.updatedAt > 10 * 60_000 }
    }
}
