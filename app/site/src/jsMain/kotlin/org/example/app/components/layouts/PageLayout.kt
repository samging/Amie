package org.example.app.components.layouts

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.ColumnScope
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.animation.Keyframes
import com.varabyte.kobweb.silk.style.animation.toAnimation
import com.varabyte.kobweb.compose.ui.styleModifier
import org.jetbrains.compose.web.css.*
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.breakpoint.Breakpoint
import com.varabyte.kobweb.silk.style.toAttrs
import kotlinx.browser.document
import kotlinx.browser.window
import org.example.app.components.Shortcuts
import org.example.app.components.sections.AnimatedBackground
import org.example.app.components.sections.Footer
import org.example.app.components.sections.NavHeader
import org.example.app.components.widgets.BannerKind
import org.example.app.components.widgets.CommandPalette
import org.example.app.components.widgets.GhostButton
import org.example.app.components.widgets.ToastHost
import org.example.app.components.widgets.Toasts
import org.jetbrains.compose.web.dom.Div
import org.w3c.dom.events.Event

val FadeInUp = Keyframes {
    from { Modifier.opacity(0).translateY(14.px) }
    to { Modifier.opacity(1).translateY(0.px) }
}

val PageContentStyle = CssStyle {
    base {
        Modifier.fillMaxSize().padding(leftRight = 1.cssRem, top = 2.cssRem, bottom = 3.cssRem)
            .animation(FadeInUp.toAnimation(duration = 500.ms, timingFunction = AnimationTimingFunction.EaseOut))
    }
    cssRule(CSSMediaQuery.MediaFeature("prefers-reduced-motion", StylePropertyValue("reduce"))) {
        Modifier.styleModifier { property("animation", "none") }
    }
    Breakpoint.MD { Modifier.maxWidth(64.cssRem).padding(leftRight = 2.cssRem, top = 2.5.cssRem, bottom = 4.cssRem) }
}

val BackToTopStyle = CssStyle.base {
    Modifier.position(Position.Fixed).bottom(1.25.cssRem).left(1.25.cssRem).zIndex(40)
}

/** A way back up on long pages; appears once the page has been scrolled a screen or so. */
@Composable
private fun BackToTop() {
    var visible by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { visible = window.scrollY > 600 }
        window.addEventListener("scroll", listener)
        onDispose { window.removeEventListener("scroll", listener) }
    }
    if (!visible) return
    Div(BackToTopStyle.toAttrs()) {
        GhostButton("↑ Top") { window.asDynamic().scrollTo(js("({ top: 0, behavior: 'smooth' })")) }
    }
}

/** Says when the browser loses and regains its connection, since every list on the site comes from the network. */
@Composable
private fun ConnectionNotices() {
    DisposableEffect(Unit) {
        val offline: (Event) -> Unit = { Toasts.show("You are offline. The pages show what this browser saved last.", BannerKind.ERROR, durationMs = 6000) }
        val online: (Event) -> Unit = { Toasts.show("Back online.", BannerKind.SUCCESS) }
        window.addEventListener("offline", offline)
        window.addEventListener("online", online)
        onDispose {
            window.removeEventListener("offline", offline)
            window.removeEventListener("online", online)
        }
    }
}

/** Every page: the header with navigation, the content column, the footer, plus the search and the toasts. */
@Composable
fun PageLayout(title: String, content: @Composable ColumnScope.() -> Unit) {
    LaunchedEffect(title) {
        document.title = "Amie – $title"
    }
    Shortcuts.Install()
    ConnectionNotices()

    Box(
        Modifier
            .fillMaxWidth()
            .minHeight(100.vh)
            .gridTemplateRows { size(1.fr); size(minContent) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxSize().gridRow(1),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NavHeader()
            Div(PageContentStyle.toAttrs { id("main-content") }) {
                Column(Modifier.fillMaxWidth()) { content() }
            }
        }
        Footer(Modifier.fillMaxWidth().gridRow(2))
    }
    BackToTop()
    ToastHost()
    CommandPalette()
}
