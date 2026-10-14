package org.example.app.components.sections

import androidx.compose.runtime.*
import com.varabyte.kobweb.browser.dom.ElementTarget
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.functions.clamp
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.foundation.layout.Spacer
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.silk.components.graphics.Image
import com.varabyte.kobweb.silk.components.icons.CloseIcon
import com.varabyte.kobweb.silk.components.icons.HamburgerIcon
import com.varabyte.kobweb.silk.components.icons.MoonIcon
import com.varabyte.kobweb.silk.components.icons.SunIcon
import com.varabyte.kobweb.silk.components.navigation.Link
import com.varabyte.kobweb.silk.components.navigation.UncoloredLinkVariant
import com.varabyte.kobweb.silk.components.navigation.UndecoratedLinkVariant
import com.varabyte.kobweb.silk.components.overlay.Overlay
import com.varabyte.kobweb.silk.components.overlay.OverlayVars
import com.varabyte.kobweb.silk.components.overlay.PopupPlacement
import com.varabyte.kobweb.silk.components.overlay.Tooltip
import com.varabyte.kobweb.silk.components.text.SpanText
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
import org.example.app.components.widgets.GhostButton
import org.example.app.components.widgets.IconButton
import org.example.app.components.widgets.LinkButton
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

@Composable
private fun NavLink(path: String, text: String) {
    Link(path, text, Modifier.fontSize(10.px).lineHeight(1).color(Colors.White).padding(topBottom = 0.4.cssRem), variant = UndecoratedLinkVariant.then(UncoloredLinkVariant))
}

@Composable
private fun MenuItems(username: String?) {
    NavLink("/", "Packages")
    NavLink("/downloads", "Downloads")
    NavLink("/docs", "Docs")
    NavLink("/navigateto", "Upload")
    if (username != null) NavLink("/dashboard?username=$username", "Dashboard")
    if (AuthSession.isAdmin) NavLink("/admin", "Admin")
}

@Composable
private fun AccountItems(username: String?, compact: Boolean) {
    if (username != null) {
        Link("/profile", username, Modifier.fontSize(10.px).color(Colors.White), variant = UndecoratedLinkVariant.then(UncoloredLinkVariant))
        GhostButton("Sign out") { AuthClient.signOut() }
    } else {
        LinkButton("Sign in", "/loginpage")
    }
}

@Composable
private fun ColorModeButton() {
    var colorMode by ColorMode.currentState
    IconButton(onClick = { colorMode = colorMode.opposite }) {
        if (colorMode.isLight) MoonIcon() else SunIcon()
    }
    Tooltip(ElementTarget.PreviousSibling, "Toggle color mode", placement = PopupPlacement.BottomRight)
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

    Row(NavHeaderStyle.toModifier(), verticalAlignment = Alignment.CenterVertically) {
        Link("/", Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.6.cssRem), variant = UndecoratedLinkVariant.then(UncoloredLinkVariant)) {
            Image("/svglogo.svg", "Amie", Modifier.height(2.cssRem).display(DisplayStyle.Block))
        }

        Spacer()

        Row(Modifier.gap(1.4.cssRem).displayIfAtLeast(Breakpoint.MD), verticalAlignment = Alignment.CenterVertically) {
            MenuItems(username)
            AccountItems(username, compact = false)
        }

        Row(
            Modifier.fontSize(1.5.cssRem).gap(1.cssRem).displayUntil(Breakpoint.MD),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var menuState by remember { mutableStateOf(SideMenuState.CLOSED) }

            IconButton(onClick = { menuState = SideMenuState.OPEN }) { HamburgerIcon() }

            if (menuState != SideMenuState.CLOSED) {
                SideMenu(
                    menuState,
                    username,
                    close = { menuState = menuState.close() },
                    onAnimationEnd = { if (menuState == SideMenuState.CLOSING) menuState = SideMenuState.CLOSED }
                )
            }
        }
    }
}

@Composable
private fun SideMenu(menuState: SideMenuState, username: String?, close: () -> Unit, onAnimationEnd: () -> Unit) {
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
                    MenuItems(username)
                    AccountItems(username, compact = true)
                }
            }
        }
    }
}
