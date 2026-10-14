package org.example.app.components

import kotlinx.browser.window
import kotlin.js.Date

/**
 * The browser-side cache: answers are kept in memory for the life of the page and mirrored into
 * localStorage so a reload, or the next visit, starts from the last known data instead of a spinner.
 * Values are JSON text; each entry carries the moment it expires.
 */
object ClientCache {
    private const val PREFIX = "amie.cache:"
    private class Entry(val expiresAt: Double, val value: String)

    private val memory = HashMap<String, Entry>()

    fun get(key: String): String? {
        val now = Date.now()
        memory[key]?.let { if (it.expiresAt > now) return it.value else memory.remove(key) }
        val stored = runCatching { window.localStorage.getItem(PREFIX + key) }.getOrNull() ?: return null
        val separator = stored.indexOf('|')
        if (separator <= 0) return null
        val expiresAt = stored.substring(0, separator).toDoubleOrNull() ?: return null
        val value = stored.substring(separator + 1)
        if (expiresAt <= now) {
            remove(key)
            return null
        }
        memory[key] = Entry(expiresAt, value)
        return value
    }

    /** The cached value even when it has expired, for showing something while fresh data loads. */
    fun getStale(key: String): String? {
        memory[key]?.let { return it.value }
        val stored = runCatching { window.localStorage.getItem(PREFIX + key) }.getOrNull() ?: return null
        val separator = stored.indexOf('|')
        return if (separator > 0) stored.substring(separator + 1) else null
    }

    fun put(key: String, value: String, ttlMs: Double) {
        val entry = Entry(Date.now() + ttlMs, value)
        memory[key] = entry
        runCatching { window.localStorage.setItem(PREFIX + key, "${entry.expiresAt}|$value") }
    }

    fun remove(key: String) {
        memory.remove(key)
        runCatching { window.localStorage.removeItem(PREFIX + key) }
    }

    fun removePrefix(prefix: String) {
        memory.keys.filter { it.startsWith(prefix) }.forEach { memory.remove(it) }
        runCatching {
            val doomed = (0 until window.localStorage.length).mapNotNull { window.localStorage.key(it) }
                .filter { it.startsWith(PREFIX + prefix) }
            doomed.forEach { window.localStorage.removeItem(it) }
        }
    }

    /** [load] runs only when nothing fresh is cached, or when [refresh] is set. */
    suspend fun cached(key: String, ttlMs: Double, refresh: Boolean = false, load: suspend () -> String): String {
        if (!refresh) get(key)?.let { return it }
        val value = load()
        put(key, value, ttlMs)
        return value
    }

    const val MINUTE = 60_000.0
    const val HOUR = 3_600_000.0
}
