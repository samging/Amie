package org.example.app.components.sections

import androidx.compose.runtime.*
import com.varabyte.kobweb.browser.dom.ElementTarget
import com.varabyte.kobweb.compose.css.functions.clamp
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.foundation.layout.Spacer
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.graphics.Image
import com.varabyte.kobweb.silk.components.icons.CloseIcon
import com.varabyte.kobweb.silk.components.icons.HamburgerIcon
import com.varabyte.kobweb.silk.components.navigation.Link
import com.varabyte.kobweb.silk.components.navigation.UncoloredLinkVariant
import com.varabyte.kobweb.silk.components.navigation.UndecoratedLinkVariant
import com.varabyte.kobweb.silk.components.overlay.Overlay
import com.varabyte.kobweb.silk.components.overlay.OverlayVars
import com.varabyte.kobweb.silk.components.overlay.PopupPlacement
import com.varabyte.kobweb.silk.components.overlay.Tooltip
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.animation.Keyframes
import com.varabyte.kobweb.silk.style.animation.toAnimation
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.breakpoint.Breakpoint
import com.varabyte.kobweb.silk.style.breakpoint.displayIfAtLeast
import com.varabyte.kobweb.silk.style.breakpoint.displayUntil
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import org.example.app.auth.AuthClient
import org.example.app.auth.AuthSession
import org.example.app.components.Platform
import org.example.app.components.AdminInboxLink
import org.example.app.components.RedCount
import org.example.app.components.loadAdminInbox
import org.example.app.components.widgets.Avatar
import org.example.app.components.widgets.PublicProfile
import org.example.app.components.widgets.loadPublicProfile
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.example.app.components.widgets.GhostButton
import org.example.app.components.widgets.IconButton
import org.example.app.components.widgets.InlineSvg
import org.example.app.components.widgets.LinkButton
import org.example.app.components.widgets.Palette
import org.example.app.components.widgets.SEARCH_SVG
import org.example.app.toSitePalette
import org.jetbrains.compose.web.css.*

val NavHeaderStyle = CssStyle.base {
    Modifier
        .fillMaxWidth()
        .height(3.75.cssRem)
        .padding(topBottom = 0.px, leftRight = 1.25.cssRem)
        .backgroundColor(colorMode.toSitePalette().nearBackground.toRgb().copyf(alpha = 0.75f))
        .borderBottom(1.px, LineStyle.Solid, colorMode.toSitePalette().border)
        .styleModifier { property("backdrop-filter", "blur(12px)") }
        .position(Position.Sticky).top(0.px).zIndex(10)
}

/** Which top-level section a path belongs to, so the header can mark it. */
private fun sectionOf(path: String): String = when {
    path == "/" || path.startsWith("/view") -> "/"
    path.startsWith("/downloads") -> "/downloads"
    path.startsWith("/docs") -> "/docs"
    path.startsWith("/navigateto") || path.startsWith("/edit") -> "/navigateto"
    path.startsWith("/dashboard") -> "/dashboard"
    path.startsWith("/admin") -> "/admin"
    path.startsWith("/profile") -> "/profile"
    else -> path
}

@Composable
private fun NavLink(path: String, text: String, active: Boolean) {
    Link(
        path, text,
        Modifier.fontSize(10.px).lineHeight(1).color(Colors.White).padding(topBottom = 0.4.cssRem)
            .borderBottom(2.px, LineStyle.Solid, if (active) Colors.White else Colors.Transparent),
        variant = UndecoratedLinkVariant.then(UncoloredLinkVariant)
    ) 
}

/** The three places everyone goes; everything personal lives in the account menu. */
@Composable
private fun MenuItems(username: String?, current: String, full: Boolean = false) {
    NavLink("/", "Packages", current == "/")
    NavLink("/downloads", "Downloads", current == "/downloads")
    NavLink("/docs", "Docs", current == "/docs")
    if (full) {
        NavLink("/navigateto", "Upload", current == "/navigateto")
        if (username != null) NavLink("/dashboard?username=$username", "Dashboard", current == "/dashboard")
        if (username != null) NavLink("/profile", "Profile", current == "/profile")
        if (AuthSession.isAdmin) NavLink("/admin", "Admin", current == "/admin")
        AdminInboxLink(active = false)
    }
}

@Composable
private fun AccountItems(username: String?, compact: Boolean) {
    if (username != null) {
        if (compact) GhostButton("Sign out") { AuthClient.signOut() } else AccountMenu(username)
    } else {
        LinkButton("Sign in", "/loginpage")
    }
}

/**
 * The signed-in person's menu: their picture or initials and name open a list with Dashboard, Profile,
 * Upload, and for administrators Admin and Inbox (with the red count), then Sign out. Closes on a click
 * outside, on Escape, or after choosing. The red count also sits on the button so it is seen while closed.
 */
@Composable
private fun AccountMenu(username: String) {
    var open by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf(PublicProfile(username = username)) }
    var inbox by remember { mutableStateOf(0L) }
    val palette = ColorMode.current.toSitePalette()
    LaunchedEffect(username) {
        profile = loadPublicProfile(username)
        inbox = loadAdminInbox()?.total ?: 0L
    }
    DisposableEffect(open) {
        if (!open) return@DisposableEffect onDispose { }
        val close: (org.w3c.dom.events.Event) -> Unit = { e -> if (e !is org.w3c.dom.events.KeyboardEvent || e.key == "Escape") open = false }
        kotlinx.browser.window.addEventListener("click", close)
        kotlinx.browser.window.addEventListener("keydown", close)
        onDispose { kotlinx.browser.window.removeEventListener("click", close); kotlinx.browser.window.removeEventListener("keydown", close) }
    }
    Div(Modifier.position(Position.Relative).toAttrs { onClick { it.stopPropagation() } }) {
        Div(
            Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.5.cssRem).cursor(com.varabyte.kobweb.compose.css.Cursor.Pointer)
                .padding(0.25.cssRem, 0.5.cssRem).border(2.px, LineStyle.Solid, if (open) Colors.White else Colors.Transparent)
                .toAttrs {
                    attr("role", "button"); attr("tabindex", "0"); attr("aria-haspopup", "menu"); attr("aria-expanded", open.toString()); attr("aria-label", "Account menu for $username")
                    onClick { open = !open }
                    onKeyDown { if (it.key == "Enter" || it.key == " ") { it.preventDefault(); open = !open } }
                }
        ) {
            Avatar(profile, size = 1.6.cssRem)
            Span(Modifier.fontSize(10.px).color(Colors.White).toAttrs()) { Text(profile.displayName) }
            RedCount(inbox, "items in the inbox")
            Span(Modifier.fontSize(8.px).color(palette.muted).toAttrs { attr("aria-hidden", "true") }) { Text(if (open) "▴" else "▾") }
        }
        if (open) {
            Div(
                Modifier.position(Position.Absolute).top(100.percent).right(0.px).margin(top = 0.4.cssRem).minWidth(13.cssRem).zIndex(30)
                    .backgroundColor(palette.nearBackground).border(2.px, LineStyle.Solid, Colors.White).padding(0.4.cssRem)
                    .display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).gap(0.1.cssRem)
                    .toAttrs { attr("role", "menu") }
            ) {
                MenuEntry("/dashboard?username=$username", "Dashboard", "your packages, devices and errors")
                MenuEntry("/profile", "Profile", "picture, nickname, links, publications")
                MenuEntry("/navigateto?username=$username", "Upload a package", "publish a jar")
                if (AuthSession.isAdmin) {
                    MenuEntry("/admin", "Administration", "people, notes, store, builds")
                    MenuEntry("/admin#inbox", "Inbox", if (inbox > 0) "$inbox waiting" else "nothing waiting", count = inbox)
                }
                Div(Modifier.borderTop(1.px, LineStyle.Solid, palette.border).margin(topBottom = 0.3.cssRem).toAttrs())
                Div(
                    Modifier.padding(0.45.cssRem, 0.6.cssRem).fontSize(10.px).cursor(com.varabyte.kobweb.compose.css.Cursor.Pointer).color(Colors.White)
                        .toAttrs { attr("role", "menuitem"); attr("tabindex", "0"); onClick { AuthClient.signOut() }; onKeyDown { if (it.key == "Enter") AuthClient.signOut() } }
                ) { Text("Sign out") }
            }
        }
    }
}

@Composable
private fun MenuEntry(path: String, label: String, hint: String, count: Long = 0) {
    val palette = ColorMode.current.toSitePalette()
    Link(path, Modifier.display(DisplayStyle.Block).padding(0.45.cssRem, 0.6.cssRem).color(Colors.White).styleModifier { property("text-decoration", "none") }, variant = UndecoratedLinkVariant.then(UncoloredLinkVariant)) {
        Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.4.cssRem).fontSize(10.px).styleModifier { property("text-decoration", "none") }.toAttrs()) {
            Text(label)
            RedCount(count, "waiting")
        }
        Div(Modifier.fontSize(8.px).color(palette.muted).styleModifier { property("text-decoration", "none") }.toAttrs()) { Text(hint) }
    }
}

@Composable
private fun SearchButton() {
    IconButton(onClick = { Palette.open() }) { InlineSvg(SEARCH_SVG, size = 18, label = "Search") }
    Tooltip(ElementTarget.PreviousSibling, "Search packages, authors and docs (${Platform.commandKey} K)", placement = PopupPlacement.Bottom)
}

val SideMenuSlideInAnim = Keyframes {
    from { Modifier.translateX(100.percent) }
    to { Modifier }
}

enum class SideMenuState {
    CLOSED, OPEN, CLOSING;

    fun close() = when (this) {
        CLOSED -> CLOSED
        OPEN -> CLOSING
        CLOSING -> CLOSING
    }
}

@Composable
fun NavHeader() {
    val username = remember { AuthSession.username }
    val current = sectionOf(rememberPageContext().route.path)

    Row(NavHeaderStyle.toModifier(), verticalAlignment = Alignment.CenterVertically) {
        Link("/", Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.6.cssRem), variant = UndecoratedLinkVariant.then(UncoloredLinkVariant)) {
            Image("/svglogo.svg", "Amie", Modifier.height(2.cssRem).display(DisplayStyle.Block))
        }

        Spacer()

        Row(Modifier.gap(1.4.cssRem).displayIfAtLeast(Breakpoint.MD), verticalAlignment = Alignment.CenterVertically) {
            MenuItems(username, current)
            SearchButton()
            AccountItems(username, compact = false)
        }

        Row(
            Modifier.fontSize(1.5.cssRem).gap(1.cssRem).displayUntil(Breakpoint.MD),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var menuState by remember { mutableStateOf(SideMenuState.CLOSED) }

            SearchButton()
            IconButton(onClick = { menuState = SideMenuState.OPEN }) { HamburgerIcon() }

            if (menuState != SideMenuState.CLOSED) {
                SideMenu(
                    menuState,
                    username,
                    current,
                    close = { menuState = menuState.close() },
                    onAnimationEnd = { if (menuState == SideMenuState.CLOSING) menuState = SideMenuState.CLOSED }
                )
            }
        }
    }
}

@Composable
private fun SideMenu(menuState: SideMenuState, username: String?, current: String, close: () -> Unit, onAnimationEnd: () -> Unit) {
    Overlay(
        Modifier
            .setVariable(OverlayVars.BackgroundColor, Colors.Transparent)
            .onClick { close() }
    ) {
        key(menuState) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(clamp(12.cssRem, 50.percent, 16.cssRem))
                    .align(Alignment.CenterEnd)
                    .padding(top = 1.cssRem, leftRight = 1.cssRem)
                    .gap(1.25.cssRem)
                    .backgroundColor(ColorMode.current.toSitePalette().nearBackground)
                    .animation(
                        SideMenuSlideInAnim.toAnimation(
                            duration = 200.ms,
                            timingFunction = if (menuState == SideMenuState.OPEN) AnimationTimingFunction.EaseOut else AnimationTimingFunction.EaseIn,
                            direction = if (menuState == SideMenuState.OPEN) AnimationDirection.Normal else AnimationDirection.Reverse,
                            fillMode = AnimationFillMode.Forwards
                        )
                    )
                    .borderRadius(topLeft = 2.cssRem)
                    .onClick { it.stopPropagation() }
                    .onAnimationEnd { onAnimationEnd() },
                horizontalAlignment = Alignment.End
            ) {
                IconButton(onClick = { close() }) { CloseIcon() }
                Column(Modifier.padding(right = 0.75.cssRem).gap(1.25.cssRem).fontSize(1.3.cssRem), horizontalAlignment = Alignment.End) {
                    MenuItems(username, current, full = true)
                    NavLink("/about", "About", current == "/about")
                    GhostButton("Search") { close(); Palette.open() }
                    AccountItems(username, compact = true)
                }
            }
        }
    }
}
