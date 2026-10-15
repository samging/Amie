package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.Overflow
import com.varabyte.kobweb.compose.css.TextAlign
import com.varabyte.kobweb.compose.css.UserSelect
import com.varabyte.kobweb.compose.css.WhiteSpace
import com.varabyte.kobweb.compose.css.Width
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.attrsModifier
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import io.ktor.http.Url
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import org.example.app.components.Catalog
import org.example.app.components.ClientCache
import org.example.app.components.Http
import org.example.app.components.JarPrecheck
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
import org.khronos.webgl.Uint8Array
import org.w3c.dom.Element
import kotlin.js.Json

@JsModule("highlight.js")
@JsNonModule
external object hljs {
    fun highlightElement(element: Element)
}

private const val CODE_FONT_SIZE = 12
private const val CODE_LINE_HEIGHT = 1.6

@Composable
fun HighlightedCode(code: String, language: String? = null, wrap: Boolean = false) {
    var codeElement by remember { mutableStateOf<Element?>(null) }
    LaunchedEffect(code, codeElement) {
        val element = codeElement ?: return@LaunchedEffect
        if (code.isEmpty()) return@LaunchedEffect
        delay(50)
        runCatching { hljs.highlightElement(element) }
    }
    Pre(Modifier.margin(0.px).fillMaxWidth().minWidth(0.px).then(if (wrap) Modifier.whiteSpace(WhiteSpace.PreWrap).styleModifier { property("word-break", "break-word") } else Modifier).toAttrs()) {
        Code(attrs = Modifier.fontSize(CODE_FONT_SIZE.px).lineHeight(CODE_LINE_HEIGHT).then(if (wrap) Modifier.whiteSpace(WhiteSpace.PreWrap) else Modifier).toAttrs {
            if (language != null) classes("language-$language")
            ref { element ->
                codeElement = element
                onDispose { codeElement = null }
            }
        }) { Text(code) }
    }
}

private fun languageOf(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
    "py" -> "python"; "kt", "kts" -> "kotlin"; "js" -> "javascript"; "ts" -> "typescript"
    "html", "xml" -> "xml"; "css" -> "css"; "json" -> "json"; "md" -> "markdown"
    "c", "h" -> "c"; "cpp", "ino" -> "cpp"; "sh" -> "bash"; "yaml", "yml" -> "yaml"
    else -> null
}

private fun decodeBase64(content: String): String? = runCatching {
    val binary = window.atob(content.replace(Regex("\\s+"), ""))
    val bytes = js("new Uint8Array(binary.length)")
    for (i in 0 until binary.length) bytes[i] = binary[i].code
    js("new TextDecoder()").decode(bytes) as String
}.getOrNull()

private fun prettyJson(text: String): String? = runCatching {
    JSON.stringify(JSON.parse<Any?>(text), null, 2)
}.getOrNull()

/** What a jar holds, read from its central directory in the browser (the same check the upload page runs). */
@Composable
private fun JarContents(name: String, directUrl: String, size: Double) {
    var names by remember { mutableStateOf<List<String>?>(null) }
    var state by remember { mutableStateOf("loading") }
    val palette = ColorMode.current.toSitePalette()

    LaunchedEffect(name, directUrl) {
        if (size > 25.0 * 1024 * 1024) {
            state = "skipped"
            return@LaunchedEffect
        }
        val candidates = listOf(AmieConfig.downloadUrl(name, "package", size.toLong()), directUrl).filter { it.isNotEmpty() }.distinct()
        for (url in candidates) {
            val loaded = runCatching {
                val response = window.fetch(url).await()
                if (!response.ok) null else JarPrecheck.entryNames(Uint8Array(response.arrayBuffer().await()))
            }.getOrNull()
            if (loaded != null) {
                names = loaded
                state = "ready"
                return@LaunchedEffect
            }
        }
        state = "failed"
    }

    SectionHeading("Inside the jar")
    when (state) {
        "loading" -> SkeletonRows(rows = 3, label = "Reading the jar…")
        "skipped" -> Card { MutedText("This jar is too large to inspect here. Download it to look inside.") }
        "failed" -> Card { MutedText("The jar could not be read here. Download it to look inside.") }
        else -> {
            val entries = names.orEmpty()
            val classes = entries.filter { it.endsWith(".class") }.map { it.removeSuffix(".class").replace('/', '.') }
            val resources = entries.filter { !it.endsWith(".class") && !it.endsWith("/") }
            val hasManifest = entries.any { it.equals("META-INF/MANIFEST.MF", ignoreCase = true) }
            StatRow {
                StatTile("Classes", classes.size.toString())
                StatTile("Other files", resources.size.toString(), if (hasManifest) "including a manifest" else "no manifest")
                StatTile("Entries", entries.size.toString())
            }
            if (classes.isNotEmpty()) {
                Card {
                    Div(Modifier.fontSize(9.px).color(palette.muted).margin(bottom = 0.4.cssRem).toAttrs()) { Text("Classes (the package class is one of these)") }
                    Div(Modifier.maxHeight(14.cssRem).overflow(Overflow.Auto).fontFamily("SFMono-Regular", "Menlo", "Consolas", "monospace").fontSize(11.px).lineHeight(1.8).toAttrs()) {
                        for (cls in classes.take(80)) Div { Text(cls) }
                        if (classes.size > 80) MutedText("… and ${classes.size - 80} more")
                    }
                }
            }
        }
    }
}

/** One package file: its details and, for text files, its content with syntax highlighting. */
@NoLiveLiterals
@Page("view")
@Composable
fun ViewPage() {
    val ctx = rememberPageContext()
    val palette = ColorMode.current.toSitePalette()
    val params = Url(window.location.href).parameters
    val packagePath = params["package"] ?: ""
    val username = params["username"] ?: ""
    val directUrl = params["url"] ?: ""

    var metadata by remember { mutableStateOf<Json?>(null) }
    var content by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var lineNumbers by remember { mutableStateOf(true) }
    var wrap by remember { mutableStateOf(false) }
    var pretty by remember { mutableStateOf(false) }
    var related by remember { mutableStateOf(Catalog.stale().filter { it.author == username && it.path != packagePath }) }

    LaunchedEffect(packagePath) {
        if (packagePath.isEmpty()) {
            loading = false
            error = "No file was given."
            return@LaunchedEffect
        }
        try {
            val encodedPath = packagePath.split("/").joinToString("/") { encodeURIComponent(it) }
            val text = ClientCache.cached("packages:view:$packagePath", 5 * ClientCache.MINUTE) {
                val result = Http.get(AmieConfig.api("view?package=$encodedPath"))
                if (!result.ok) throw IllegalStateException()
                result.text
            }
            val json = JSON.parse<Json>(text)
            metadata = json
            val binary = PackageKind.of(packagePath.substringAfterLast('/')).let { it == PackageKind.JAR || it == PackageKind.MODEL }
            if (!binary) {
                content = (json["content"] as? String)?.takeIf { it.isNotBlank() }?.let { decodeBase64(it) }
                if (content == null && directUrl.isNotEmpty()) {
                    content = runCatching { Http.get(directUrl).takeIf { it.ok }?.text }.getOrNull()
                }
            }
        } catch (e: Throwable) {
            error = "This file could not be loaded."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(username) {
        if (username.isNotEmpty()) {
            related = runCatching { Catalog.load() }.getOrDefault(Catalog.stale()).filter { it.author == username && it.path != packagePath }
        }
    }

    val name = metadata?.get("name") as? String ?: packagePath.substringAfterLast("/")
    val size = (metadata?.get("size") as? Number)?.toDouble() ?: 0.0
    val downloadUrl = (metadata?.get("download_url") as? String) ?: directUrl
    val sha = (metadata?.get("sha") as? String)?.trim('"')
    val kind = PackageKind.of(name)
    val isBinary = kind == PackageKind.JAR || kind == PackageKind.MODEL || name.endsWith(".bin", true)
    val language = languageOf(name)
    val text = content
    val displayed = if (pretty && text != null) prettyJson(text) ?: text else text
    val lineCount = if (isBinary) 0 else displayed?.let { if (it.isEmpty()) 0 else it.count { c -> c == '\n' } + 1 } ?: 0

    LaunchedEffect(name, packagePath) {
        if (name.isNotEmpty() && packagePath.isNotEmpty() && error == null) Recent.add(RecentItem(packagePath, name, username, downloadUrl))
    }

    PageLayout(name.ifEmpty { "Package" }) {
        Breadcrumbs(
            Crumb("Packages", "/"),
            if (username.isNotEmpty()) Crumb(username, "/dashboard?username=${encodeURIComponent(username)}") else Crumb("Store"),
            Crumb(name.ifEmpty { "Package" })
        )
        PageHeading(
            name.ifEmpty { "Package" },
            listOfNotNull(username.takeIf { it.isNotEmpty() }?.let { "by $it" }, kind.label, formatSize(size).takeIf { size > 0 }).joinToString(" · ")
        ) {
            if (downloadUrl.isNotEmpty()) LinkButton("Download", downloadUrl, download = true)
            CopyButton(window.location.href, label = "Copy link", toast = "Link to $name copied.")
            if (username.isNotEmpty()) LinkButton("All by $username", "/dashboard?username=${encodeURIComponent(username)}", primary = false)
        }

        if (!loading && error == null) {
            StatRow {
                StatTile("Kind", kind.label, kind.extensions.firstOrNull()?.let { ".${name.substringAfterLast('.', "").lowercase()}" })
                StatTile("Size", formatSize(size))
                if (lineCount > 0) StatTile("Lines", lineCount.toString(), language?.let { "highlighted as $it" })
                if (!sha.isNullOrEmpty()) StatTile("Checksum", sha.take(10) + "…", "MD5, copy below")
            }
        }

        when {
            loading -> SkeletonRows(rows = 6, label = "Loading the file…")
            error != null -> EmptyAction(
                error!!,
                if (packagePath.isEmpty()) "Open a package from the list to see it here." else "It may have been deleted, or the store is unreachable right now.",
                action = {
                    LinkButton("Back to the packages", "/")
                    if (username.isNotEmpty()) LinkButton("All by $username", "/dashboard?username=${encodeURIComponent(username)}", primary = false)
                }
            )
            displayed != null && !isBinary -> {
                Div(Modifier.display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).alignItems(AlignItems.Center).gap(0.5.cssRem).margin(bottom = 0.6.cssRem).toAttrs { attr("role", "toolbar"); attr("aria-label", "Viewer options") }) {
                    Chip("Line numbers", lineNumbers) { lineNumbers = !lineNumbers }
                    Chip("Wrap long lines", wrap) { wrap = !wrap }
                    if (kind == PackageKind.JSON) Chip("Pretty print", pretty) { pretty = !pretty }
                    CopyButton(displayed, label = "Copy content", done = "Copied", toast = "The file content was copied.")
                    if (downloadUrl.isNotEmpty()) A(downloadUrl, attrs = Modifier.fontSize(9.px).toAttrs { attr("target", "_blank"); attr("rel", "noopener") }) { Text("Open raw") }
                }
                Div(
                    Modifier.fillMaxWidth().maxHeight(40.cssRem).overflow(Overflow.Auto).padding(0.75.cssRem)
                        .backgroundColor(Color.rgb(13, 17, 23)).borderRadius(12.px).display(DisplayStyle.Flex).alignItems(AlignItems.FlexStart).toAttrs()
                ) {
                    if (lineNumbers) {
                        Pre(
                            Modifier.margin(0.px).padding(top = 1.em, bottom = 1.em, right = 0.9.cssRem).fontSize(CODE_FONT_SIZE.px).lineHeight(CODE_LINE_HEIGHT)
                                .color(Color.rgb(0x6E7681)).textAlign(TextAlign.End).userSelect(UserSelect.None).flexShrink(0)
                                .toAttrs { attr("aria-hidden", "true") }
                        ) { Text((1..lineCount).joinToString("\n")) }
                    }
                    key(displayed, wrap) { HighlightedCode(displayed, language, wrap) }
                }
            }
            kind == PackageKind.JAR -> {
                Card(Modifier.margin(bottom = 1.5.cssRem)) {
                    Div(Modifier.fontSize(12.px).margin(bottom = 0.5.cssRem).toAttrs()) { Text("Use it in the Amie app") }
                    MutedText("Open the package in the sandbox; the app fetches the jar from here on first use.")
                    Div(Modifier.margin(topBottom = 0.6.cssRem).toAttrs()) {
                        key(name) { HighlightedCode("val p = initMod(\"$name\")\np.inference(\"hello\").forEach { log(it) }\np.close()", "kotlin") }
                    }
                    Div(Modifier.display(DisplayStyle.Flex).gap(0.5.cssRem).flexWrap(FlexWrap.Wrap).toAttrs()) {
                        CopyButton("val p = initMod(\"$name\")\np.inference(\"hello\").forEach { log(it) }\np.close()", label = "Copy snippet", done = "Copied", toast = "Snippet copied.")
                        LinkButton("How sessions work", "/docs/sessions", primary = false)
                    }
                }
                JarContents(name, downloadUrl, size)
            }
            kind == PackageKind.MODEL -> Card {
                Div(Modifier.fontSize(12.px).margin(bottom = 0.5.cssRem).toAttrs()) { Text("This is a model file") }
                MutedText("Models are published by the Amie desktop app and opened from its Models page; there is no preview here. Use Download to get the file.")
                Div(Modifier.margin(top = 0.6.cssRem).toAttrs()) { LinkButton("Models in the app", "/docs/models", primary = false) }
            }
            isBinary -> Card { MutedText("This is a binary package. Use the download button to get it.") }
            size > 1024 * 1024 -> Card { MutedText("This file is too large to show here. Use the download button to get it.") }
            else -> Card { MutedText("There is no preview for this file.") }
        }

        if (!sha.isNullOrEmpty() && !loading && error == null) {
            Div(Modifier.margin(top = 1.cssRem).fontSize(8.px).display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.5.cssRem).flexWrap(FlexWrap.Wrap).toAttrs()) {
                MutedText("MD5", Modifier.fontSize(8.px))
                Code(attrs = Modifier.fontSize(8.px).styleModifier { property("word-break", "break-all") }.toAttrs()) { Text(sha) }
                CopyButton(sha, label = "Copy", done = "Copied", toast = "Checksum copied.")
            }
        }

        if (related.isNotEmpty()) {
            SectionHeading("More by $username", Modifier.margin(top = 2.cssRem))
            Div(Modifier.display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.6.cssRem).toAttrs()) {
                for (entry in related.take(6)) {
                    Card(
                        Modifier.width(Width.FitContent).minWidth(12.cssRem).maxWidth(20.cssRem).padding(0.7.cssRem, 1.cssRem).cursor(Cursor.Pointer)
                            .onClick { ctx.router.navigateTo(entry.viewUrl) }
                            .attrsModifier { attr("role", "link"); attr("tabindex", "0") }
                            .onKeyDown { if (it.key == "Enter") ctx.router.navigateTo(entry.viewUrl) }
                    ) {
                        Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.6.cssRem).toAttrs()) {
                            KindIcon(entry.kind)
                            Div(Modifier.minWidth(0.px).toAttrs()) {
                                Div(Modifier.fontSize(11.px).styleModifier { property("word-break", "break-all") }.toAttrs()) { Text(entry.name) }
                                MutedText(listOf(entry.kind.label, formatSize(entry.size)).joinToString(" · "), Modifier.fontSize(8.px))
                            }
                        }
                    }
                }
            }
        }
    }
}
