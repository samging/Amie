package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import kotlinx.coroutines.launch
import org.example.app.HeadlineTextStyle
import org.example.app.SubheadlineTextStyle
import org.example.app.auth.AuthSession
import org.example.app.components.ClientCache
import org.example.app.components.Http
import org.example.app.components.layouts.PageLayout
import org.example.app.components.widgets.*
import org.example.app.config.AmieConfig
import org.example.app.config.encodeURIComponent
import org.example.app.toSitePalette
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import kotlin.js.Json

private fun parseItems(text: String): List<Json> {
    if (text.isBlank()) return emptyList()
    val json = JSON.parse<dynamic>(text)
    return when {
        js("Array.isArray(json)") as Boolean -> json.unsafeCast<Array<Json>>().toList()
        json == null -> emptyList()
        else -> listOf(json.unsafeCast<Json>())
    }
}

@NoLiveLiterals
@Page("/")
@Composable
fun IndexPage() {
    val ctx = rememberPageContext()
    val scope = rememberCoroutineScope()
    val palette = ColorMode.current.toSitePalette()
    val username = remember { AuthSession.username }

    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<Json>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun loadAll(refresh: Boolean) {
        loading = true
        error = null
        try {
            val text = ClientCache.cached("packages:all", ClientCache.MINUTE, refresh) {
                val result = Http.get(AmieConfig.api("packages" + if (refresh) "?refresh=1" else ""))
                if (!result.ok) throw IllegalStateException()
                result.text
            }
            items = parseItems(text)
        } catch (e: Throwable) {
            items = ClientCache.getStale("packages:all")?.let { parseItems(it) } ?: emptyList()
            error = "The package list could not be loaded right now."
        } finally {
            loading = false
        }
    }

    suspend fun search() {
        val q = query.trim()
        if (q.isEmpty()) return loadAll(refresh = false)
        loading = true
        error = null
        try {
            val text = ClientCache.cached("packages:search:$q", ClientCache.MINUTE) {
                val result = Http.get(AmieConfig.api("search-packages?query=${encodeURIComponent(q)}"))
                if (!result.ok) throw IllegalStateException()
                result.text
            }
            items = parseItems(text)
        } catch (e: Throwable) {
            error = "The search could not be run right now."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadAll(refresh = false) }

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

        // Search
        Div(Modifier.fillMaxWidth().display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.9.cssRem).alignItems(AlignItems.Center).margin(bottom = 2.cssRem).toAttrs()) {
            TextField(query, { query = it }, placeholder = "Search, e.g. *jar", modifier = Modifier.width(22.cssRem), onEnter = { scope.launch { search() } })
            PrimaryButton(if (loading) "Searching…" else "Search", enabled = !loading) { scope.launch { search() } }
            GhostButton("Refresh", enabled = !loading) { query = ""; scope.launch { loadAll(refresh = true) } }
        }

        StatusBanner(error, BannerKind.ERROR, Modifier.margin(bottom = 1.cssRem))

        if (loading && items.isEmpty()) {
            EmptyState("Loading packages…")
        } else {
            val authors = items.filter { it["type"] == "dir" }
            val files = items.filter { it["type"] == "file" }

            if (authors.isNotEmpty()) {
                H2(Modifier.fontSize(14.px).margin(top = 0.px, bottom = 0.8.cssRem).toAttrs()) { Text("Authors") }
                Div(Modifier.display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.6.cssRem).margin(bottom = 2.cssRem).toAttrs()) {
                    for (author in authors) {
                        val name = author["name"] as? String ?: continue
                        Card(Modifier.width(com.varabyte.kobweb.compose.css.Width.FitContent).minWidth(14.cssRem).display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.8.cssRem).padding(0.8.cssRem, 1.cssRem)) {
                            Img("/usericon.svg", attrs = Modifier.size(2.2.cssRem).toAttrs())
                            Div {
                                Div(Modifier.fontWeight(FontWeight.SemiBold).toAttrs()) { Text(name) }
                                A("/dashboard?username=${encodeURIComponent(name)}", attrs = Modifier.fontSize(10.px).color(palette.brand.primary).toAttrs()) { Text("View packages") }
                            }
                        }
                    }
                }
            }

            if (files.isNotEmpty()) {
                H2(Modifier.fontSize(14.px).margin(top = 0.px, bottom = 0.8.cssRem).toAttrs()) { Text("Packages") }
                Table(DataTableStyle.toModifier().toAttrs()) {
                    Thead { Tr { Th { Text("Name") }; Th { Text("Author") }; Th { Text("Size") }; Th { Text("") } } }
                    Tbody {
                        for (item in files) {
                            val name = item["name"] as? String ?: "Unknown"
                            val path = item["path"] as? String ?: ""
                            val owner = path.substringAfter("uploads/").substringBefore("/")
                            val size = (item["size"] as? Number)?.toDouble() ?: 0.0
                            val downloadUrl = item["download_url"] as? String ?: item["downloadUrl"] as? String ?: ""
                            Tr {
                                Td {
                                    Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.5.cssRem).toAttrs()) {
                                        when {
                                            name.endsWith(".py") -> Img("/python.svg", attrs = Modifier.size(1.4.cssRem).toAttrs())
                                            name.endsWith(".json") -> Img("/json.svg", attrs = Modifier.size(1.4.cssRem).toAttrs())
                                            name.endsWith(".jar") -> SpanText("☕")
                                            else -> SpanText("📄")
                                        }
                                        SpanText(name, Modifier.fontWeight(FontWeight.Medium))
                                    }
                                }
                                Td { Text(owner.ifEmpty { "–" }) }
                                Td { Text(formatSize(size)) }
                                Td {
                                    Div(Modifier.display(DisplayStyle.Flex).gap(0.4.cssRem).justifyContent(JustifyContent.FlexEnd).toAttrs()) {
                                        GhostButton("View") {
                                            ctx.router.navigateTo("/view?package=${encodeURIComponent(path)}&username=${encodeURIComponent(owner)}&url=${encodeURIComponent(downloadUrl)}")
                                        }
                                        if (downloadUrl.isNotEmpty()) LinkButton("Download", downloadUrl, download = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (authors.isEmpty() && files.isEmpty() && !loading) {
                EmptyState(if (query.isBlank()) "No packages have been published yet." else "Nothing matches that search.")
            }
        }
    }
}
