package org.example.awss3.jar

import com.github.benmanes.caffeine.cache.Ticker
import java.util.concurrent.TimeUnit

class FakeTicker : Ticker {
    private var nanos = 0L
    override fun read(): Long = nanos
    fun advance(seconds: Long) { nanos += TimeUnit.SECONDS.toNanos(seconds) }
}
