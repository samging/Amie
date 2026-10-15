package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.Width
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.attrsModifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.breakpoint.Breakpoint
import com.varabyte.kobweb.silk.style.breakpoint.displayIfAtLeast
import com.varabyte.kobweb.silk.style.breakpoint.displayUntil
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.example.app.HeadlineTextStyle
import org.example.app.SubheadlineTextStyle
import org.example.app.auth.AuthSession
import org.example.app.components.Catalog
import org.example.app.components.CatalogEntry
import org.example.app.components.ClientCache
import org.example.app.components.Http
import org.example.app.components.PackageKind
import org.example.app.components.Recent
import org.example.app.components.RecentItem
import org.example.app.components.layouts.PageLayout
import org.example.app.components.widgets.*
import org.example.app.config.AmieConfig
import org.example.app.config.encodeURIComponent
import org.example.app.toSitePalette
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import kotlin.js.Json

/** The store's own search answers `{name, path, type, download_url, size}`; the author is the folder in the path. */
private fun parseSearch(text: String): List<CatalogEntry> {
    if (text.isBlank()) return emptyList()
    val json = JSON.parse<dynamic>(text)
    val items: List<Json> = when {
        js("Array.isArray(json)") as Boolean -> json.unsafeCast<Array<Json>>().toList()
        json == null -> emptyList()
        else -> listOf(json.unsafeCast<Json>())
    }
    return items.filter { it["type"] != "dir" }.mapNotNull { item ->
        val name = item["name"] as? String ?: return@mapNotNull null
        val path = item["path"] as? String ?: ""
        CatalogEntry(
            name = name,
            path = path,
            author = path.substringAfter("uploads/", "").substringBefore("/"),
            size = (item["size"] as? Number)?.toDouble() ?: 0.0,
            downloadUrl = item["download_url"] as? String ?: item["downloadUrl"] as? String ?: ""
        )
    }
}

/** The icon for a file kind, matching the one the table always used. */
@Composable
fun KindIcon(kind: PackageKind) {
    val file = kind.iconFile
    if (file != null) Img(file, kind.label, Modifier.size(1.4.cssRem).toAttrs())
    else SpanText(kind.icon, Modifier.width(1.4.cssRem).textAlign(com.varabyte.kobweb.compose.css.TextAlign.Center).display(DisplayStyle.InlineBlock))
}

private val sortOptions = listOf("name" to "Sort: name", "size" to "Sort: largest first", "author" to "Sort: author")

@NoLiveLiterals
@Page("/")
@Composable
fun IndexPage() {
    val ctx = rememberPageContext()
    val scope = rememberCoroutineScope()
    val palette = ColorMode.current.toSitePalette()
    val username = remember { AuthSession.username }

    var query by remember { mutableStateOf("") }
    var catalog by remember { mutableStateOf(Catalog.stale()) }
    var storeResults by remember { mutableStateOf<List<CatalogEntry>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var kind by remember { mutableStateOf<PackageKind?>(null) }
    var author by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf("name") }
    var recent by remember { mutableStateOf(Recent.list()) }

    suspend fun loadAll(refresh: Boolean) {
        loading = true
        error = null
        try {
            catalog = Catalog.load(refresh)
        } catch (e: Throwable) {
            catalog = Catalog.stale()
            error = "The package list could not be loaded right now." + if (catalog.isNotEmpty()) " This is the last list this browser saw." else ""
        } finally {
            loading = false
        }
    }

    /** Asks the store itself, which understands wildcards such as `*jar`. */
    suspend fun searchStore() {
        val q = query.trim()
        if (q.isEmpty()) {
            storeResults = null
            return
        }
        searching = true
        error = null
        try {
            val text = ClientCache.cached("packages:search:$q", ClientCache.MINUTE) {
                val result = Http.get(AmieConfig.api("search-packages?query=${encodeURIComponent(q)}"))
                if (!result.ok) throw IllegalStateException()
                result.text
            }
            storeResults = parseSearch(text)
        } catch (e: Throwable) {
            error = "The search could not be run right now."
        } finally {
            searching = false
        }
    }

    fun clearFilters() {
        query = ""
        storeResults = null
        kind = null
        author = null
    }

    LaunchedEffect(Unit) { loadAll(refresh = false) }

    val source = storeResults ?: catalog
    val filtering = query.isNotBlank() || kind != null || author != null || storeResults != null
    val shown = remember(source, query, kind, author, sort, storeResults) {
        val q = query.trim().lowercase()
        source.filter { entry ->
            (storeResults != null || q.isEmpty() || entry.name.lowercase().contains(q) || entry.author.lowercase().contains(q) || entry.kind.label.lowercase().contains(q)) &&
                (kind == null || entry.kind == kind) &&
                (author == null || entry.author == author)
        }.let { list ->
            when (sort) {
                "size" -> list.sortedByDescending { it.size }
                "author" -> list.sortedWith(compareBy({ it.author.lowercase() }, { it.name.lowercase() }))
                else -> list.sortedBy { it.name.lowercase() }
            }
        }
    }
    val authors: List<Pair<String, List<CatalogEntry>>> = remember(catalog) { catalog.filter { it.author.isNotEmpty() }.groupBy { it.author }.toList().sortedBy { it.first.lowercase() } }
    val kindCounts = remember(source) { source.groupingBy { it.kind }.eachCount() }

    PageLayout("Packages") {
        // Hero
        Div(Modifier.fillMaxWidth().margin(bottom = 2.cssRem).display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap)
            .alignItems(AlignItems.Center).justifyContent(JustifyContent.SpaceBetween).gap(1.5.cssRem).toAttrs()) {
          Div(Modifier.maxWidth(44.cssRem).flexGrow(1).toAttrs()) {
            H1(HeadlineTextStyle.toModifier().margin(0.px).toAttrs()) { Text("Packages for your boards") }
            P(SubheadlineTextStyle.toModifier().margin(top = 0.8.cssRem, bottom = 1.4.cssRem).maxWidth(40.cssRem).toAttrs()) {
                Text("Amie keeps firmware and tooling packages in one place: browse what others published, download ready-made jars, or upload your own.")
            }
            Div(Modifier.display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.9.cssRem).toAttrs()) {
                LinkButton("Download jars", "/downloads")
                LinkButton("Read the docs", "/docs", primary = false)
                if (username != null) LinkButton("Upload a package", "/navigateto?username=$username", primary = false)
                else LinkButton("Sign in to upload", "/loginpage", primary = false)
            }
          }
          Mascot(variant = 0, size = 200)
        }

        // The store at a glance
        if (catalog.isNotEmpty()) {
            StatRow {
                StatTile("Packages", catalog.size.toString(), "in the store")
                StatTile("Authors", authors.size.toString(), "people publishing")
                StatTile("Jars", (catalog.count { it.kind == PackageKind.JAR }).toString(), "ready for the Amie app")
                StatTile("Total size", formatSize(catalog.sumOf { it.size }))
            }
        }

        // Search
        Div(Modifier.fillMaxWidth().display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.9.cssRem).alignItems(AlignItems.Center).margin(bottom = 0.4.cssRem).toAttrs()) {
            TextField(
                query,
                { query = it; if (storeResults != null) storeResults = null },
                placeholder = "Filter by name, author or kind…",
                modifier = Modifier.width(26.cssRem).maxWidth(100.percent).attrsModifier { attr("data-search", ""); attr("aria-label", "Filter packages") },
                onEnter = { scope.launch { searchStore() } }
            )
            PrimaryButton(if (searching) "Searching…" else "Search the store", enabled = !searching && query.isNotBlank()) { scope.launch { searchStore() } }
            if (filtering) GhostButton("Clear") { clearFilters() }
            GhostButton(if (loading) "Refreshing…" else "Refresh", enabled = !loading) { clearFilters(); scope.launch { loadAll(refresh = true) } }
        }
        HintText("Typing filters the list at once; press / from anywhere on the page to get here. Enter asks the whole store, which understands wildcards such as *jar.", Modifier.margin(bottom = 1.cssRem))

        // Kind chips and sort
        if (source.isNotEmpty()) {
            Div(Modifier.fillMaxWidth().display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.6.cssRem).alignItems(AlignItems.Center)
                .justifyContent(JustifyContent.SpaceBetween).margin(bottom = 1.2.cssRem).toAttrs()) {
                Div(Modifier.display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.4.cssRem).toAttrs { attr("role", "group"); attr("aria-label", "Filter by kind") }) {
                    Chip("All", kind == null, source.size) { kind = null }
                    PackageKind.entries.forEach { k ->
                        val count = kindCounts[k] ?: 0
                        if (count > 0) Chip(k.plural, kind == k, count) { kind = if (kind == k) null else k }
                    }
                }
                SelectField(sort, sortOptions, { sort = it }, ariaLabel = "Sort packages")
            }
        }

        StatusBanner(error, BannerKind.ERROR, Modifier.margin(bottom = 1.cssRem))
        storeResults?.let { results ->
            StatusBanner("The store found ${plural(results.size, "match", "matches")} for “${query.trim()}”. Clear the box to see everything again.", BannerKind.INFO, Modifier.margin(bottom = 1.cssRem))
        }

        // Recently viewed
        if (recent.isNotEmpty() && !filtering) {
            SectionHeading("Recently viewed") { GhostButton("Clear") { Recent.clear(); recent = emptyList() } }
            Div(Modifier.display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.6.cssRem).margin(bottom = 2.cssRem).toAttrs()) {
                for (item in recent) {
                    Card(
                        Modifier.width(Width.FitContent).minWidth(12.cssRem).maxWidth(20.cssRem).padding(0.7.cssRem, 1.cssRem).cursor(Cursor.Pointer)
                            .onClick { ctx.router.navigateTo("/view?package=${encodeURIComponent(item.path)}&username=${encodeURIComponent(item.owner)}&url=${encodeURIComponent(item.url)}") }
                            .attrsModifier { attr("role", "link"); attr("tabindex", "0") }
                            .onKeyDown { if (it.key == "Enter") ctx.router.navigateTo("/view?package=${encodeURIComponent(item.path)}&username=${encodeURIComponent(item.owner)}&url=${encodeURIComponent(item.url)}") }
                    ) {
                        Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.6.cssRem).toAttrs()) {
                            KindIcon(PackageKind.of(item.name))
                            Div(Modifier.minWidth(0.px).toAttrs()) {
                                Div(Modifier.fontSize(11.px).styleModifier { property("word-break", "break-all") }.toAttrs()) { Text(item.name) }
                                MutedText(listOfNotNull(item.owner.takeIf { it.isNotEmpty() }?.let { "by $it" }, formatRelative(kotlin.js.Date(item.at).toISOString())).joinToString(" · "), Modifier.fontSize(8.px))
                            }
                        }
                    }
                }
            }
        }

        if (loading && catalog.isEmpty()) {
            SkeletonRows(rows = 5, label = "Loading packages…")
        } else {
            // Authors
            if (authors.isNotEmpty() && storeResults == null) {
                SectionHeading("Authors") {
                    if (author != null) GhostButton("Show every author") { author = null }
                }
                Div(Modifier.display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.6.cssRem).margin(bottom = 2.cssRem).toAttrs()) {
                    for ((name, files) in authors) {
                        val selected = author == name
                        Card(
                            Modifier.width(Width.FitContent).minWidth(14.cssRem).display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.8.cssRem).padding(0.8.cssRem, 1.cssRem)
                                .cursor(Cursor.Pointer)
                                .then(if (selected) Modifier.boxShadow(6.px, 6.px, 0.px, 0.px, Colors.White).translate((-2).px, (-2).px) else Modifier)
                                .onClick { author = if (selected) null else name }
                                .attrsModifier { attr("role", "button"); attr("aria-pressed", selected.toString()); attr("tabindex", "0"); attr("title", if (selected) "Show every author" else "Show only $name's packages") }
                                .onKeyDown { if (it.key == "Enter" || it.key == " ") { it.preventDefault(); author = if (selected) null else name } }
                        ) {
                            Img("/usericon.svg", attrs = Modifier.size(2.2.cssRem).toAttrs())
                            Div {
                                Div(Modifier.fontWeight(FontWeight.SemiBold).toAttrs()) { Text(name) }
                                MutedText(plural(files.size, "package") + " · " + formatSize(files.sumOf { it.size }), Modifier.fontSize(8.px).display(DisplayStyle.Block))
                                A("/dashboard?username=${encodeURIComponent(name)}", attrs = Modifier.fontSize(9.px).color(palette.brand.primary).toAttrs { onClick { it.stopPropagation() } }) { Text("Open profile") }
                            }
                        }
                    }
                }
            }

            // Packages
            SectionHeading(if (author != null) "Packages by $author" else "Packages") {
                if (shown.isNotEmpty()) MutedText("${shown.size} of ${source.size}", Modifier.fontSize(8.px))
            }
            if (shown.isEmpty()) {
                when {
                    catalog.isEmpty() && storeResults == null -> EmptyAction(
                        "No packages have been published yet.",
                        "The first jar uploaded shows up right here.",
                        action = {
                            if (username != null) LinkButton("Upload a package", "/navigateto") else LinkButton("Sign in to upload", "/loginpage")
                            LinkButton("See the downloads", "/downloads", primary = false)
                        }
                    )
                    else -> EmptyAction(
                        "Nothing matches.",
                        listOfNotNull(
                            query.trim().takeIf { it.isNotEmpty() }?.let { "No package, author or kind contains “$it”." },
                            kind?.let { "Only ${it.plural.lowercase()} are being shown." },
                            author?.let { "Only $it's packages are being shown." }
                        ).joinToString(" "),
                        action = {
                            GhostButton("Clear the filters") { clearFilters() }
                            if (query.isNotBlank() && storeResults == null) PrimaryButton("Search the whole store") { scope.launch { searchStore() } }
                        }
                    )
                }
            } else {
                // Narrow screens get one card per package instead of a table that has to scroll sideways
                Div(Modifier.displayUntil(Breakpoint.MD).fillMaxWidth().toAttrs()) {
                    Div(Modifier.display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).gap(0.6.cssRem).toAttrs()) {
                        for (entry in shown) {
                            Card {
                                Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.5.cssRem).toAttrs()) {
                                    KindIcon(entry.kind)
                                    A(entry.viewUrl, attrs = Modifier.fontWeight(FontWeight.Medium).textDecorationLine(com.varabyte.kobweb.compose.css.TextDecorationLine.None).styleModifier { property("word-break", "break-all") }.toAttrs()) { Text(entry.name) }
                                }
                                MutedText(listOfNotNull(entry.author.takeIf { it.isNotEmpty() }?.let { "by $it" }, entry.kind.label, formatSize(entry.size)).joinToString(" · "), Modifier.display(DisplayStyle.Block).margin(top = 0.3.cssRem))
                                Div(Modifier.display(DisplayStyle.Flex).gap(0.4.cssRem).flexWrap(FlexWrap.Wrap).margin(top = 0.7.cssRem).toAttrs()) {
                                    GhostButton("View") { ctx.router.navigateTo(entry.viewUrl) }
                                    if (entry.downloadUrl.isNotEmpty()) LinkButton("Download", entry.downloadUrl, download = true)
                                    CopyButton(window.location.origin + entry.viewUrl, label = "Copy link", toast = "Link to ${entry.name} copied.")
                                }
                            }
                        }
                    }
                }
                Div(Modifier.displayIfAtLeast(Breakpoint.MD).fillMaxWidth().toAttrs()) {
                TableScroll {
                    Table(DataTableStyle.toModifier().toAttrs()) {
                        Thead { Tr { Th { Text("Name") }; Th { Text("Author") }; Th { Text("Kind") }; Th { Text("Size") }; Th { Text("") } } }
                        Tbody {
                            for (entry in shown) {
                                Tr {
                                    Td {
                                        Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.5.cssRem).toAttrs()) {
                                            KindIcon(entry.kind)
                                            A(entry.viewUrl, attrs = Modifier.fontWeight(FontWeight.Medium).textDecorationLine(com.varabyte.kobweb.compose.css.TextDecorationLine.None).toAttrs { attr("title", "Open ${entry.name}") }) { Text(entry.name) }
                                        }
                                    }
                                    Td {
                                        if (entry.author.isNotEmpty()) A("/dashboard?username=${encodeURIComponent(entry.author)}", attrs = Modifier.textDecorationLine(com.varabyte.kobweb.compose.css.TextDecorationLine.None).toAttrs()) { Text(entry.author) }
                                        else Text("–")
                                    }
                                    Td { Badge(entry.kind.label) }
                                    Td { Text(formatSize(entry.size)) }
                                    Td {
                                        Div(Modifier.display(DisplayStyle.Flex).gap(0.4.cssRem).justifyContent(JustifyContent.FlexEnd).flexWrap(FlexWrap.Wrap).toAttrs()) {
                                            GhostButton("View") { ctx.router.navigateTo(entry.viewUrl) }
                                            if (entry.downloadUrl.isNotEmpty()) LinkButton("Download", entry.downloadUrl, download = true)
                                            CopyButton(window.location.origin + entry.viewUrl, label = "Copy link", toast = "Link to ${entry.name} copied.")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
