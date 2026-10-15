package org.example.app.components.layouts

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.Overflow
import com.varabyte.kobweb.compose.css.OverflowWrap
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.attrsModifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.navigation.Link
import com.varabyte.kobweb.silk.components.navigation.UncoloredLinkVariant
import com.varabyte.kobweb.silk.components.navigation.UndecoratedLinkVariant
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.breakpoint.Breakpoint
import com.varabyte.kobweb.silk.style.toAttrs
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.silk.theme.colors.palette.color
import com.varabyte.kobweb.silk.theme.colors.palette.toPalette
import com.varabyte.kobwebx.markdown.LocalMarkdownContext
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.app.components.Clipboard
import org.example.app.components.widgets.BannerKind
import org.example.app.components.widgets.HintText
import org.example.app.components.widgets.LinkButton
import org.example.app.components.widgets.TextField
import org.example.app.components.widgets.Toasts
import org.example.app.toSitePalette
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList

val MarkdownStyle = CssStyle {
    base { Modifier.fillMaxWidth().minWidth(0.px) }

    cssRule(" h1") { Modifier.fontSize(22.px).fontWeight(400).margin(top = 0.px, bottom = 1.25.cssRem).lineHeight(1.5) }
    cssRule(" h2") { Modifier.fontSize(15.px).fontWeight(400).margin(top = 2.cssRem, bottom = 0.75.cssRem).lineHeight(1.6) }
    cssRule(" h3") { Modifier.fontSize(12.px).fontWeight(400).margin(top = 1.5.cssRem, bottom = 0.5.cssRem).lineHeight(1.7) }
    cssRule(" h4") { Modifier.fontSize(11.px).fontWeight(400).margin(top = 1.cssRem, bottom = 0.5.cssRem) }
    // Anchored headings land below the sticky header instead of under it
    cssRule(" :is(h1,h2,h3,h4)") { Modifier.styleModifier { property("scroll-margin-top", "4.75rem") } }
    cssRule(" p") { Modifier.margin(topBottom = 0.9.cssRem).fontSize(11.px).lineHeight(2) }
    cssRule(" li") { Modifier.fontSize(11.px).lineHeight(2) }
    cssRule(" ul") { Modifier.fillMaxWidth().overflowWrap(OverflowWrap.BreakWord) }
    cssRule(" :is(li,ol,ul)") { Modifier.margin(bottom = 0.3.cssRem) }
    cssRule(" code") {
        Modifier
            .fontFamily("SFMono-Regular", "Menlo", "Consolas", "monospace")
            .fontSize(12.px)
            .padding(0.1.cssRem, 0.35.cssRem)
            .borderRadius(6.px)
            .backgroundColor(colorMode.toSitePalette().nearBackground)
            .border(1.px, LineStyle.Solid, colorMode.toSitePalette().border)
    }
    cssRule(" pre") { Modifier.margin(top = 0.5.cssRem, bottom = 1.5.cssRem).fillMaxWidth().position(Position.Relative) }
    cssRule(" pre > code") {
        Modifier
            .display(DisplayStyle.Block)
            .fillMaxWidth()
            .padding(0.9.cssRem)
            .fontSize(12.px)
            .lineHeight(1.6)
            .borderRadius(0.px)
            .overflow { x(Overflow.Auto) }
    }
    cssRule(" pre > button.copy-code") {
        Modifier.position(Position.Absolute).top(0.4.cssRem).right(0.4.cssRem)
            .fontFamily("Press Start 2P", "monospace").fontSize(7.px).lineHeight(1).padding(0.4.cssRem, 0.5.cssRem)
            .backgroundColor(Colors.Black).color(Colors.White).border(2.px, LineStyle.Solid, Colors.White)
            .cursor(com.varabyte.kobweb.compose.css.Cursor.Pointer).opacity(0.75)
    }
    cssRule(" pre > button.copy-code:hover") { Modifier.opacity(1).backgroundColor(Colors.White).color(Colors.Black) }
    cssRule(" table") {
        Modifier.fillMaxWidth().margin(topBottom = 1.cssRem)
            .styleModifier { property("border-collapse", "collapse") }
    }
    cssRule(" th") {
        Modifier.textAlign(com.varabyte.kobweb.compose.css.TextAlign.Start).padding(0.5.cssRem, 0.7.cssRem).fontSize(10.px)
            .borderBottom(2.px, LineStyle.Solid, colorMode.toSitePalette().border)
    }
    cssRule(" td") {
        Modifier.padding(0.5.cssRem, 0.7.cssRem).fontSize(10.px).lineHeight(1.9).borderBottom(1.px, LineStyle.Solid, colorMode.toSitePalette().border)
            .styleModifier { property("vertical-align", "top") }
    }
    cssRule(" blockquote") {
        Modifier.margin(topBottom = 1.cssRem, leftRight = 0.px).padding(0.5.cssRem, 1.cssRem)
            .borderLeft(4.px, LineStyle.Solid, colorMode.toSitePalette().brand.primary)
            .backgroundColor(colorMode.toSitePalette().nearBackground)
            .borderRadius(0.px)
    }
    cssRule(" hr") { Modifier.border(0.px, LineStyle.None, colorMode.toSitePalette().border).borderTop(1.px, LineStyle.Solid, colorMode.toSitePalette().border).margin(topBottom = 2.cssRem) }
}

val DocsShellStyle = CssStyle {
    base { Modifier.fillMaxWidth().display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).gap(2.cssRem) }
    Breakpoint.MD { Modifier.flexDirection(FlexDirection.Row).alignItems(AlignItems.FlexStart) }
}

val DocsNavStyle = CssStyle {
    base { Modifier.fillMaxWidth().display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.4.cssRem) }
    Breakpoint.MD {
        Modifier.width(16.cssRem).flexShrink(0).flexDirection(FlexDirection.Column).position(Position.Sticky).top(4.5.cssRem)
            .maxHeight(90.vh).overflow { y(Overflow.Auto) }
    }
}

/** The desktop app documentation, in reading order (see resources/markdown/docs). */
val DocsPages = listOf(
    "Start here" to listOf("docs" to "Overview", "docs/getting-started" to "Getting started"),
    "Working in the app" to listOf(
        "docs/sandbox" to "The sandbox", "docs/sessions" to "Running sessions", "docs/sandbox-scripting" to "Scripting API",
        "docs/models" to "Models and metrics", "docs/serving" to "Serving a model", "docs/devices" to "Devices"
    ),
    "Reference" to listOf(
        "docs/amp-folder" to "The AMP folder", "docs/sign-in" to "Signing in", "docs/authentication" to "Keycloak setup",
        "docs/backend-integration" to "Amie backend", "docs/models-page" to "Models page",
        "docs/troubleshooting" to "Troubleshooting"
    ),
)

private val flatDocs: List<Pair<String, String>> = DocsPages.flatMap { it.second }

private class Heading(val id: String, val text: String, val level: Int)

@Composable
private fun DocsNav(currentPath: String) {
    val palette = ColorMode.current.toSitePalette()
    var filter by remember { mutableStateOf("") }
    val needle = filter.trim().lowercase()
    Div(DocsNavStyle.toAttrs { attr("aria-label", "Documentation pages") }) {
        TextField(filter, { filter = it }, placeholder = "Find a page…", modifier = Modifier.height(2.cssRem).fontSize(9.px).attrsModifier { attr("data-search", ""); attr("aria-label", "Find a documentation page") })
        var shown = 0
        DocsPages.forEach { (section, pages) ->
            val matching = pages.filter { (_, title) -> needle.isEmpty() || title.lowercase().contains(needle) || section.lowercase().contains(needle) }
            if (matching.isEmpty()) return@forEach
            shown += matching.size
            Div(Modifier.fontSize(8.px).color(palette.muted)
                .margin(top = 0.8.cssRem, bottom = 0.3.cssRem).toAttrs()) {
                Text(section.uppercase())
            }
            matching.forEach { (path, title) ->
                val active = currentPath.trim('/') == path
                Link(
                    "/$path", title,
                    Modifier.padding(0.35.cssRem, 0.6.cssRem).borderRadius(0.px).fontSize(9.px).lineHeight(1.6)
                        .then(if (active) Modifier.backgroundColor(Colors.White).color(Colors.Black) else Modifier)
                        .attrsModifier { if (active) attr("aria-current", "page") },
                    variant = UndecoratedLinkVariant.then(UncoloredLinkVariant)
                )
            }
        }
        if (shown == 0) HintText("No page is called that. Try the site search for packages and authors.", Modifier.margin(top = 0.6.cssRem))
    }
}

/** The h2 headings of the page, read from the rendered article, as "On this page". */
@Composable
private fun OnThisPage(headings: List<Heading>) {
    if (headings.size < 2) return
    val palette = ColorMode.current.toSitePalette()
    Div(Modifier.fillMaxWidth().margin(bottom = 1.5.cssRem).padding(0.8.cssRem, 1.cssRem).border(2.px, LineStyle.Solid, palette.border)
        .backgroundColor(palette.nearBackground.toRgb().copyf(alpha = 0.85f)).toAttrs { attr("aria-label", "On this page") }) {
        Div(Modifier.fontSize(8.px).color(palette.muted).margin(bottom = 0.4.cssRem).toAttrs()) { Text("ON THIS PAGE") }
        Div(Modifier.display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.3.cssRem, 1.cssRem).toAttrs()) {
            headings.forEach { heading ->
                A("#${heading.id}", attrs = Modifier.fontSize(9.px).lineHeight(1.9).color(Colors.White).toAttrs()) { Text(heading.text) }
            }
        }
    }
}

/** The page before and after this one in reading order. */
@Composable
private fun PrevNext(currentPath: String) {
    val index = flatDocs.indexOfFirst { it.first == currentPath.trim('/') }
    if (index < 0) return
    val previous = flatDocs.getOrNull(index - 1)
    val next = flatDocs.getOrNull(index + 1)
    val palette = ColorMode.current.toSitePalette()
    Div(Modifier.fillMaxWidth().display(DisplayStyle.Flex).justifyContent(JustifyContent.SpaceBetween).flexWrap(FlexWrap.Wrap).gap(0.8.cssRem)
        .margin(top = 3.cssRem).padding(top = 1.5.cssRem).borderTop(1.px, LineStyle.Solid, palette.border).toAttrs { attr("aria-label", "Previous and next page") }) {
        Div { if (previous != null) LinkButton("← ${previous.second}", "/${previous.first}", primary = false) }
        Div { if (next != null) LinkButton("${next.second} →", "/${next.first}") }
    }
}

/** Puts a Copy button on every code block of the article; the markdown is static, so this runs once per page. */
private suspend fun decorateCodeBlocks() {
    delay(60)
    val blocks = document.querySelectorAll("#main-content pre").asList().mapNotNull { it as? HTMLElement }
    for (pre in blocks) {
        if (pre.getAttribute("data-copy") != null) continue
        pre.setAttribute("data-copy", "ready")
        val button = document.createElement("button") as HTMLButtonElement
        button.className = "copy-code"
        button.type = "button"
        button.textContent = "COPY"
        button.setAttribute("aria-label", "Copy this code")
        button.onclick = { event ->
            event.preventDefault()
            val code = (pre.querySelector("code") as? HTMLElement)?.innerText ?: pre.innerText.removeSuffix("COPY")
            docsScope.launch {
                val ok = Clipboard.copy(code.trimEnd())
                button.textContent = if (ok) "COPIED" else "FAILED"
                if (ok) Toasts.show("Code copied.", BannerKind.SUCCESS) else Toasts.show("Copying is blocked in this browser.", BannerKind.ERROR)
                delay(1500)
                button.textContent = "COPY"
            }
            null
        }
        pre.appendChild(button)
    }
}

/** For the copy buttons, which live outside the composition. */
private val docsScope = MainScope()

@Composable
fun MarkdownLayout(content: @Composable () -> Unit) {
    val markdown = LocalMarkdownContext.current
    val title = markdown?.frontMatter?.get("title")?.firstOrNull() ?: "Docs"
    val path = rememberPageContext().route.path
    val isDocs = path.trim('/').let { it == "docs" || it.startsWith("docs/") }
    var headings by remember(path) { mutableStateOf<List<Heading>>(emptyList()) }

    LaunchedEffect(path) {
        delay(30)
        headings = document.querySelectorAll("#main-content h2").asList().mapNotNull { node ->
            val element = node as? HTMLElement ?: return@mapNotNull null
            val id = element.id.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Heading(id, element.innerText.trim(), 2)
        }
        // A link to a section (from the search or a shared URL) lands on that heading once the page is drawn
        val fragment = window.location.hash.removePrefix("#")
        if (fragment.isNotEmpty()) {
            delay(30)
            (document.getElementById(fragment) as? HTMLElement)?.scrollIntoView(js("({ block: 'start' })"))
        }
        decorateCodeBlocks()
    }

    PageLayout(title) {
        if (isDocs) {
            Div(DocsShellStyle.toAttrs()) {
                DocsNav(path)
                Div(MarkdownStyle.toAttrs()) {
                    OnThisPage(headings)
                    content()
                    PrevNext(path)
                }
            }
        } else {
            Div(MarkdownStyle.toAttrs()) { content() }
        }
    }
}
