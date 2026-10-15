package org.example.app.components

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.HTMLTextAreaElement
import kotlin.js.Promise

/** Puts text on the clipboard: the async clipboard API when the page may use it, a hidden textarea otherwise. */
object Clipboard {
    suspend fun copy(text: String): Boolean {
        val modern = runCatching {
            val clipboard = window.navigator.asDynamic().clipboard
            if (clipboard == null) return@runCatching false
            (clipboard.writeText(text) as Promise<Unit>).await()
            true
        }.getOrDefault(false)
        return modern || legacy(text)
    }

    private fun legacy(text: String): Boolean = runCatching {
        val area = document.createElement("textarea") as HTMLTextAreaElement
        area.value = text
        area.setAttribute("readonly", "")
        area.style.position = "fixed"
        area.style.left = "-9999px"
        document.body?.appendChild(area)
        area.select()
        val ok = document.execCommand("copy")
        document.body?.removeChild(area)
        ok
    }.getOrDefault(false)
}
