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

@Composable
private fun MenuItems(username: String?, current: String) {
    NavLink("/", "Packages", current == "/")
    NavLink("/downloads", "Downloads", current == "/downloads")
    NavLink("/docs", "Docs", current == "/docs")
    NavLink("/navigateto", "Upload", current == "/navigateto")
    if (username != null) NavLink("/dashboard?username=$username", "Dashboard", current == "/dashboard")
    if (AuthSession.isAdmin) NavLink("/admin", "Admin", current == "/admin")
    AdminInboxLink(active = false)
}

@Composable
private fun AccountItems(username: String?, compact: Boolean) {
    if (username != null) {
        Link("/profile", username, Modifier.fontSize(10.px).color(Colors.White), variant = UndecoratedLinkVariant.then(UncoloredLinkVariant))
        Tooltip(ElementTarget.PreviousSibling, "Your profile", placement = PopupPlacement.Bottom)
        GhostButton("Sign out") { AuthClient.signOut() }
    } else {
        LinkButton("Sign in", "/loginpage")
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
                    MenuItems(username, current)
                    NavLink("/about", "About", current == "/about")
                    GhostButton("Search") { close(); Palette.open() }
                    AccountItems(username, compact = true)
                }
            }
        }
    }
}
