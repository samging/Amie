package org.example.app.components.layouts

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.Overflow
import com.varabyte.kobweb.compose.css.OverflowWrap
import com.varabyte.kobweb.compose.ui.Modifier
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
import org.example.app.toSitePalette
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

val MarkdownStyle = CssStyle {
    base { Modifier.fillMaxWidth().minWidth(0.px) }

    cssRule(" h1") { Modifier.fontSize(22.px).fontWeight(400).margin(top = 0.px, bottom = 1.25.cssRem).lineHeight(1.5) }
    cssRule(" h2") { Modifier.fontSize(15.px).fontWeight(400).margin(top = 2.cssRem, bottom = 0.75.cssRem).lineHeight(1.6) }
    cssRule(" h3") { Modifier.fontSize(12.px).fontWeight(400).margin(top = 1.5.cssRem, bottom = 0.5.cssRem).lineHeight(1.7) }
    cssRule(" h4") { Modifier.fontSize(11.px).fontWeight(400).margin(top = 1.cssRem, bottom = 0.5.cssRem) }
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
    cssRule(" pre") { Modifier.margin(top = 0.5.cssRem, bottom = 1.5.cssRem).fillMaxWidth() }
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

@Composable
private fun DocsNav(currentPath: String) {
    val palette = ColorMode.current.toSitePalette()
    Div(DocsNavStyle.toAttrs()) {
        DocsPages.forEach { (section, pages) ->
            Div(Modifier.fontSize(8.px).color(palette.muted)
                .margin(top = 0.8.cssRem, bottom = 0.3.cssRem).toAttrs()) {
                Text(section.uppercase())
            }
            pages.forEach { (path, title) ->
                val active = currentPath.trim('/') == path
                Link(
                    "/$path", title,
                    Modifier.padding(0.35.cssRem, 0.6.cssRem).borderRadius(0.px).fontSize(9.px).lineHeight(1.6)
                        .then(if (active) Modifier.backgroundColor(Colors.White).color(Colors.Black) else Modifier),
                    variant = UndecoratedLinkVariant.then(UncoloredLinkVariant)
                )
            }
        }
    }
}

@Composable
fun MarkdownLayout(content: @Composable () -> Unit) {
    val markdown = LocalMarkdownContext.current
    val title = markdown?.frontMatter?.get("title")?.firstOrNull() ?: "Docs"
    val path = rememberPageContext().route.path
    val isDocs = path.trim('/').let { it == "docs" || it.startsWith("docs/") }

    PageLayout(title) {
        if (isDocs) {
            Div(DocsShellStyle.toAttrs()) {
                DocsNav(path)
                Div(MarkdownStyle.toAttrs()) { content() }
            }
        } else {
            Div(MarkdownStyle.toAttrs()) { content() }
        }
    }
}
