package org.example.app.components

import kotlinx.browser.window

/** What the visitor is browsing with, for recommending the right download and naming the right keys. */
object Platform {
    private val userAgent: String get() = runCatching { window.navigator.userAgent }.getOrDefault("")

    /** "macOS", "Windows", "Linux", "Android", "iOS", or null when unknown. */
    val name: String? by lazy {
        val ua = userAgent
        when {
            ua.contains("Android", ignoreCase = true) -> "Android"
            ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod") -> "iOS"
            ua.contains("Mac") -> "macOS"
            ua.contains("Win") -> "Windows"
            ua.contains("Linux") || ua.contains("X11") -> "Linux"
            else -> null
        }
    }

    val isApple: Boolean get() = name == "macOS" || name == "iOS"

    /** The key that opens the search: ⌘ on Apple devices, Ctrl elsewhere. */
    val commandKey: String get() = if (isApple) "⌘" else "Ctrl"

    /** The platform a build is for, from its file name; null for jars and unknown types. */
    fun platformOf(fileName: String): String? = when (fileName.substringAfterLast('.', "").lowercase()) {
        "dmg", "pkg" -> "macOS"
        "msi", "exe" -> "Windows"
        "deb", "rpm", "appimage" -> "Linux"
        "apk" -> "Android"
        else -> null
    }

    /** True when a build matches the visitor's own system. */
    fun recommends(fileName: String): Boolean = name != null && platformOf(fileName) == name
}
