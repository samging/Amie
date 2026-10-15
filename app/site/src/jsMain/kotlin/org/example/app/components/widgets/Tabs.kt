package org.example.app.components.widgets

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.UserSelect
import com.varabyte.kobweb.compose.css.WhiteSpace
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import kotlinx.browser.window
import org.example.app.toSitePalette
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** One tab of a [TabBar]: its hash id, label, an optional count and whether the count needs attention (red). */
class Tab(val id: String, val label: String, val count: Int? = null, val alert: Boolean = false)

private val ALERT_RED: Color = Color.rgb(0xD64545)

/**
 * The selected tab, kept in the page's `#hash` so a link such as `/admin#inbox` opens that tab and a reload
 * stays where it was. [default] is used when the hash names no tab in [ids].
 */
@Composable
fun rememberTab(default: String, ids: List<String>): MutableState<String> {
    val state = remember {
        val fromHash = runCatching { window.location.hash.removePrefix("#") }.getOrDefault("")
        mutableStateOf(if (fromHash in ids) fromHash else default)
    }
    DisposableEffect(Unit) {
        val onHash: (org.w3c.dom.events.Event) -> Unit = {
            val h = window.location.hash.removePrefix("#")
            if (h in ids) state.value = h
        }
        window.addEventListener("hashchange", onHash)
        onDispose { window.removeEventListener("hashchange", onHash) }
    }
    return state
}

/**
 * A row of tabs that splits a long page into parts, one shown at a time. Keyboard: Left and Right move,
 * Enter or Space select. Selecting writes the tab's id into the hash without adding history entries.
 */
@Composable
fun TabBar(tabs: List<Tab>, selected: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val palette = ColorMode.current.toSitePalette()
    fun choose(id: String) {
        onSelect(id)
        runCatching { window.history.replaceState(null, "", "#$id") }
    }
    Div(
        Modifier.fillMaxWidth().display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.2.cssRem).margin(bottom = 1.2.cssRem)
            .borderBottom(2.px, LineStyle.Solid, palette.border).then(modifier)
            .toAttrs { attr("role", "tablist") }
    ) {
        for ((index, tab) in tabs.withIndex()) {
            val active = tab.id == selected
            Div(
                Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.4.cssRem)
                    .padding(topBottom = 0.55.cssRem, leftRight = 0.9.cssRem).fontSize(10.px).lineHeight(1)
                    .cursor(Cursor.Pointer).userSelect(UserSelect.None).whiteSpace(WhiteSpace.NoWrap)
                    .color(if (active) Colors.White else palette.muted)
                    .styleModifier { property("margin-bottom", "-2px"); property("border-bottom", "2px solid ${if (active) "#ffffff" else "transparent"}") }
                    .toAttrs {
                        attr("role", "tab")
                        attr("aria-selected", active.toString())
                        attr("tabindex", if (active) "0" else "-1")
                        attr("id", "tab-${tab.id}")
                        onClick { choose(tab.id) }
                        onKeyDown { e ->
                            when (e.key) {
                                "Enter", " " -> { e.preventDefault(); choose(tab.id) }
                                "ArrowRight" -> { e.preventDefault(); choose(tabs[(index + 1) % tabs.size].id) }
                                "ArrowLeft" -> { e.preventDefault(); choose(tabs[(index - 1 + tabs.size) % tabs.size].id) }
                            }
                        }
                    }
            ) {
                Text(tab.label)
                tab.count?.let { n ->
                    Span(
                        Modifier.fontSize(8.px).lineHeight(1.3).padding(0.1.cssRem, 0.35.cssRem).minWidth(1.1.cssRem).textAlign(com.varabyte.kobweb.compose.css.TextAlign.Center)
                            .then(if (tab.alert && n > 0) Modifier.backgroundColor(ALERT_RED).color(Colors.White) else Modifier.border(1.px, LineStyle.Solid, palette.border).color(palette.muted))
                            .toAttrs { attr("aria-label", "$n") }
                    ) { Text(if (n > 99) "99+" else n.toString()) }
                }
            }
        }
    }
}
