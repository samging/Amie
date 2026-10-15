package org.example.app.components.widgets

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.Overflow
import com.varabyte.kobweb.compose.css.TextTransform
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.animation.toAnimation
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.toAttrs
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.example.app.auth.AuthClient
import org.example.app.auth.AuthSession
import org.example.app.components.Catalog
import org.example.app.components.CatalogEntry
import org.example.app.components.ClientCache
import org.example.app.components.DocsIndex
import org.example.app.components.Clipboard
import org.example.app.components.Platform
import org.example.app.components.Recent
import org.example.app.components.layouts.DocsPages
import org.example.app.components.layouts.FadeInUp
import org.example.app.config.encodeURIComponent
import org.example.app.toSitePalette
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLInputElement

/** The site-wide search: open from the header, with ⌘K / Ctrl+K, or with `?`. */
object Palette {
    var isOpen by mutableStateOf(false)
        private set
    var initialQuery: String = ""
        private set

    fun open(query: String = "") {
        initialQuery = query
        isOpen = true
    }

    fun close() {
        isOpen = false
    }

    fun toggle() = if (isOpen) close() else open()
}

/** One thing the search can take the person to. */
class PaletteEntry(
    val group: String,
    val title: String,
    val subtitle: String? = null,
    val keywords: String = "",
    val icon: String = "›",
    val action: () -> Unit
)

val PaletteBackdropStyle = CssStyle.base {
    Modifier
        .position(Position.Fixed).top(0.px).left(0.px).width(100.vw).height(100.vh).zIndex(100)
        .backgroundColor(rgba(0, 0, 0, 0.72))
        .display(DisplayStyle.Flex).justifyContent(JustifyContent.Center).alignItems(AlignItems.FlexStart)
        .padding(top = 8.vh, leftRight = 1.cssRem)
        .styleModifier { property("backdrop-filter", "blur(3px)") }
}

val PalettePanelStyle = CssStyle {
    base {
        Modifier
            .width(100.percent).maxWidth(42.cssRem)
            .backgroundColor(Color.rgb(0x0A0A0A))
            .border(3.px, LineStyle.Solid, Colors.White)
            .boxShadow(8.px, 8.px, 0.px, 0.px, Colors.White)
            .animation(FadeInUp.toAnimation(duration = 160.ms, timingFunction = AnimationTimingFunction.EaseOut))
    }
    cssRule(CSSMediaQuery.MediaFeature("prefers-reduced-motion", StylePropertyValue("reduce"))) {
        Modifier.styleModifier { property("animation", "none") }
    }
}

private fun score(query: String, entry: PaletteEntry): Int {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return 1
    val title = entry.title.lowercase()
    val haystack = (entry.title + " " + entry.subtitle.orEmpty() + " " + entry.keywords + " " + entry.group).lowercase()
    val words = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (!words.all { haystack.contains(it) }) return 0
    return when {
        title == q -> 100
        title.startsWith(q) -> 80
        title.contains(q) -> 60
        else -> 30
    }
}

private fun pageEntries(username: String?, isAdmin: Boolean, go: (String) -> Unit): List<PaletteEntry> = buildList {
    add(PaletteEntry("Pages", "Packages", "Everything published to Amie", "home store browse index", "▤") { go("/") })
    add(PaletteEntry("Pages", "Downloads", "The desktop app and package jars", "app dmg msi deb jar install", "⇩") { go("/downloads") })
    add(PaletteEntry("Pages", "Docs", "How to use the Amie desktop app", "documentation help manual guide", "¶") { go("/docs") })
    add(PaletteEntry("Pages", "Upload a package", "Publish a jar under your name", "publish new navigateto", "↑") { go("/navigateto") })
    if (username != null) {
        add(PaletteEntry("Pages", "Your packages", "Dashboard for $username", "dashboard mine devices errors", "☰") { go("/dashboard?username=${encodeURIComponent(username)}") })
        add(PaletteEntry("Pages", "Profile", "Name, email and a few words about you", "account settings", "◯") { go("/profile") })
        if (isAdmin) add(PaletteEntry("Pages", "Admin", "Builds and the whole package store", "administration publish release", "★") { go("/admin") })
    } else {
        add(PaletteEntry("Pages", "Sign in", "Password, browser or phone", "login account register", "→") { go("/loginpage") })
    }
    add(PaletteEntry("Pages", "About Amie", "What this site and the app are for", "about info", "i") { go("/about") })
}

private fun docEntries(go: (String) -> Unit): List<PaletteEntry> =
    DocsPages.flatMap { (section, pages) ->
        pages.map { (path, title) -> PaletteEntry("Docs", title, section, "documentation docs $path", "¶") { go("/$path") } }
    }

/** Every heading of the docs, so "terminate" finds the section about closing sessions. */
private fun docSectionEntries(go: (String) -> Unit): List<PaletteEntry> =
    DocsIndex.map { section ->
        PaletteEntry("Docs sections", section.heading, "in ${section.page}", "documentation docs section ${section.route}", "§") {
            go("${section.route}#${section.anchor}")
        }
    }

private fun catalogEntries(catalog: List<CatalogEntry>, go: (String) -> Unit): List<PaletteEntry> {
    val authors = catalog.map { it.author }.filter { it.isNotEmpty() }.distinct().sorted()
    val authorEntries = authors.map { author ->
        val count = catalog.count { it.author == author }
        PaletteEntry("Authors", author, plural(count, "package"), "author user profile dashboard", "◯") { go("/dashboard?username=${encodeURIComponent(author)}") }
    }
    val packageEntries = catalog.sortedBy { it.name.lowercase() }.map { entry ->
        PaletteEntry(
            "Packages", entry.name,
            listOfNotNull(entry.author.takeIf { it.isNotEmpty() }?.let { "by $it" }, formatSize(entry.size).takeIf { entry.size > 0 }, entry.kind.label).joinToString(" · "),
            "package file ${entry.kind.label} ${entry.kind.plural} ${entry.path}", entry.kind.icon
        ) { go(entry.viewUrl) }
    }
    return authorEntries + packageEntries
}

private fun recentEntries(go: (String) -> Unit): List<PaletteEntry> = Recent.list().map { item ->
    PaletteEntry("Recently viewed", item.name, item.owner.takeIf { it.isNotEmpty() }?.let { "by $it" }, "recent history ${item.path}", "↺") {
        go("/view?package=${encodeURIComponent(item.path)}&username=${encodeURIComponent(item.owner)}&url=${encodeURIComponent(item.url)}")
    }
}

private fun actionEntries(username: String?, scope: kotlinx.coroutines.CoroutineScope): List<PaletteEntry> = buildList {
    add(PaletteEntry("Actions", "Copy link to this page", window.location.href, "share url clipboard", "⎘") {
        Palette.close()
        scope.launch {
            if (Clipboard.copy(window.location.href)) Toasts.show("Link copied to the clipboard.", BannerKind.SUCCESS)
            else Toasts.show("Copying is blocked in this browser.", BannerKind.ERROR)
        }
    })
    add(PaletteEntry("Actions", "Reload with fresh data", "Forgets what this browser cached and loads the page again", "refresh cache clear", "↻") {
        ClientCache.removePrefix("packages:")
        ClientCache.removePrefix("jars:")
        ClientCache.removePrefix("devices:")
        Palette.close()
        window.location.reload()
    })
    if (username != null) {
        add(PaletteEntry("Actions", "Sign out", "Ends the session for $username", "logout leave", "⏻") { Palette.close(); AuthClient.signOut() })
    }
}

@Composable
fun CommandPalette() {
    if (!Palette.isOpen) return
    val ctx = rememberPageContext()
    val scope = rememberCoroutineScope()
    val palette = ColorMode.current.toSitePalette()
    var query by remember { mutableStateOf(Palette.initialQuery) }
    var selected by remember { mutableStateOf(0) }
    var catalog by remember { mutableStateOf(Catalog.stale()) }
    val username = remember { AuthSession.username }
    val isAdmin = remember { AuthSession.isAdmin }

    LaunchedEffect(Unit) {
        catalog = runCatching { Catalog.load() }.getOrDefault(catalog)
    }
    DisposableEffect(Unit) {
        val body = document.body
        val previous = body?.style?.getPropertyValue("overflow") ?: ""
        body?.style?.setProperty("overflow", "hidden")
        onDispose { body?.style?.setProperty("overflow", previous) }
    }

    val go: (String) -> Unit = { path ->
        Palette.close()
        ctx.router.navigateTo(path)
    }

    val entries = remember(catalog, username, isAdmin) {
        pageEntries(username, isAdmin, go) + recentEntries(go) + docEntries(go) + catalogEntries(catalog, go) + docSectionEntries(go) + actionEntries(username, scope)
    }
    val results = remember(query, entries) {
        if (query.isBlank()) {
            entries.filter { it.group == "Pages" || it.group == "Recently viewed" || it.group == "Actions" } + entries.filter { it.group == "Docs" }.take(4)
        } else {
            entries.map { it to score(query, it) }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }.take(40)
        }
    }
    LaunchedEffect(query) { selected = 0 }
    LaunchedEffect(selected) {
        document.getElementById("palette-option-$selected")?.scrollIntoView(js("({ block: 'nearest' })"))
    }

    Div(PaletteBackdropStyle.toAttrs {
        onClick { Palette.close() }
        attr("role", "dialog"); attr("aria-modal", "true"); attr("aria-label", "Search Amie")
    }) {
        Div(PalettePanelStyle.toAttrs { onClick { it.stopPropagation() } }) {
            Div(Modifier.padding(1.cssRem).borderBottom(2.px, LineStyle.Solid, palette.border).toAttrs()) {
                Input(InputType.Text, attrs = TextInputStyle.toModifier().toAttrs {
                    placeholder("Search packages, authors, docs and pages…")
                    value(query)
                    attr("aria-label", "Search")
                    attr("autocomplete", "off")
                    ref { element ->
                        (element as HTMLInputElement).focus()
                        onDispose { }
                    }
                    onInput { query = it.value }
                    onKeyDown { event ->
                        when (event.key) {
                            "ArrowDown" -> { event.preventDefault(); if (results.isNotEmpty()) selected = (selected + 1).coerceAtMost(results.lastIndex) }
                            "ArrowUp" -> { event.preventDefault(); selected = (selected - 1).coerceAtLeast(0) }
                            "Enter" -> { event.preventDefault(); results.getOrNull(selected)?.action?.invoke() }
                            "Escape" -> Palette.close()
                        }
                    }
                })
            }

            Div(Modifier.maxHeight(55.vh).overflow(Overflow.Auto).toAttrs { attr("role", "listbox") }) {
                if (results.isEmpty()) {
                    EmptyState(if (catalog.isEmpty()) "Nothing matches. The package list is still loading, or the store is empty." else "Nothing matches “$query”.")
                }
                var lastGroup: String? = null
                results.forEachIndexed { index, entry ->
                    if (entry.group != lastGroup) {
                        lastGroup = entry.group
                        Div(Modifier.fontSize(8.px).color(palette.muted).textTransform(TextTransform.Uppercase)
                            .padding(top = 0.8.cssRem, bottom = 0.3.cssRem, leftRight = 1.cssRem).toAttrs()) { Text(entry.group) }
                    }
                    val active = index == selected
                    Div(
                        Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.8.cssRem)
                            .padding(0.55.cssRem, 1.cssRem).cursor(Cursor.Pointer)
                            .then(if (active) Modifier.backgroundColor(Colors.White).color(Colors.Black) else Modifier)
                            .onClick { entry.action() }
                            .onMouseEnter { selected = index }
                            .toAttrs {
                                id("palette-option-$index")
                                attr("role", "option"); attr("aria-selected", active.toString())
                            }
                    ) {
                        SpanText(entry.icon, Modifier.width(1.6.cssRem).flexShrink(0).fontSize(10.px).textAlign(com.varabyte.kobweb.compose.css.TextAlign.Center))
                        Div(Modifier.minWidth(0.px).toAttrs()) {
                            Div(Modifier.fontSize(10.px).lineHeight(1.6).toAttrs()) { Text(entry.title) }
                            if (entry.subtitle != null) {
                                Div(Modifier.fontSize(8.px).lineHeight(1.6).color(if (active) Color.rgb(0x444444) else palette.muted)
                                    .styleModifier { property("word-break", "break-word") }.toAttrs()) { Text(entry.subtitle) }
                            }
                        }
                    }
                }
            }

            Div(Modifier.display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).alignItems(AlignItems.Center).gap(0.6.cssRem)
                .padding(0.6.cssRem, 1.cssRem).borderTop(2.px, LineStyle.Solid, palette.border).fontSize(8.px).color(palette.muted).toAttrs()) {
                KeyHint("↑↓"); Text("move")
                KeyHint("↵"); Text("open")
                KeyHint("esc"); Text("close")
                KeyHint("${Platform.commandKey} K"); Text("open this search anywhere")
                KeyHint("/"); Text("jump to a page's search box")
            }
        }
    }
}
