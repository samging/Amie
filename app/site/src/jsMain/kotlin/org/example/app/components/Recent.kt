package org.example.app.components

import kotlinx.browser.window
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.example.app.auth.jwtJson
import kotlin.js.Date

@Serializable
data class RecentItem(val path: String, val name: String, val owner: String = "", val url: String = "", val at: Double = 0.0)

/** The last few package files the person opened, kept in this browser so they are one click away. */
object Recent {
    private const val KEY = "amie.recent"
    private const val LIMIT = 8

    fun list(): List<RecentItem> = runCatching {
        window.localStorage.getItem(KEY)?.let { jwtJson.decodeFromString(ListSerializer(RecentItem.serializer()), it) }
    }.getOrNull().orEmpty()

    fun add(item: RecentItem) {
        val updated = listOf(item.copy(at = Date.now())) + list().filter { it.path != item.path }
        save(updated.take(LIMIT))
    }

    fun remove(path: String) = save(list().filter { it.path != path })

    fun clear() = save(emptyList())

    private fun save(items: List<RecentItem>) {
        runCatching { window.localStorage.setItem(KEY, jwtJson.encodeToString(ListSerializer(RecentItem.serializer()), items)) }
    }
}
