package org.example.app.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.browser.document
import kotlinx.browser.window
import org.example.app.components.widgets.Palette
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/**
 * Keys that work on every page: `⌘K` / `Ctrl+K` and `?` open the search, `/` jumps to the page's own search
 * box (or opens the search when the page has none), `Esc` closes the search.
 */
object Shortcuts {
    @Composable
    fun Install() {
        DisposableEffect(Unit) {
            val listener: (Event) -> Unit = handler@{ event ->
                val e = event as? KeyboardEvent ?: return@handler
                val target = e.target as? HTMLElement
                val typing = target != null && (
                    target.tagName == "INPUT" || target.tagName == "TEXTAREA" || target.tagName == "SELECT" || target.isContentEditable
                    )
                when {
                    (e.metaKey || e.ctrlKey) && !e.altKey && e.key.lowercase() == "k" -> {
                        e.preventDefault()
                        Palette.toggle()
                    }
                    e.key == "Escape" && Palette.isOpen -> Palette.close()
                    !typing && !e.metaKey && !e.ctrlKey && !e.altKey && e.key == "/" -> {
                        e.preventDefault()
                        if (!focusSearch()) Palette.open()
                    }
                    !typing && !e.metaKey && !e.ctrlKey && !e.altKey && e.key == "?" -> {
                        e.preventDefault()
                        Palette.open()
                    }
                }
            }
            window.addEventListener("keydown", listener)
            onDispose { window.removeEventListener("keydown", listener) }
        }
    }

    /** Focuses the page's search box, marked with `data-search`; false when the page has none. */
    fun focusSearch(): Boolean {
        val element = document.querySelector("[data-search]") as? HTMLElement ?: return false
        element.focus()
        return true
    }
}
