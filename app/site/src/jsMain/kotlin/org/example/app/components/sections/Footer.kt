package org.example.app.components.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.varabyte.kobweb.compose.css.TextAlign
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.components.navigation.Link
import com.varabyte.kobweb.silk.components.navigation.UncoloredLinkVariant
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import org.example.app.auth.AuthSession
import org.example.app.components.Platform
import org.example.app.components.widgets.KeyHint
import org.example.app.config.encodeURIComponent
import org.example.app.toSitePalette
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

val FooterStyle = CssStyle.base {
    Modifier
        .backgroundColor(colorMode.toSitePalette().nearBackground)
        .borderTop(1.px, LineStyle.Solid, colorMode.toSitePalette().border)
        .padding(topBottom = 1.25.cssRem, leftRight = 10.percent)
        .color(colorMode.toSitePalette().muted)
        .fontSize(10.px)
        .lineHeight(2)
}

@Composable
private fun FooterLink(path: String, text: String) {
    val palette = ColorMode.current.toSitePalette()
    Link(path, text, Modifier.setVariable(com.varabyte.kobweb.silk.style.vars.color.ColorVar, palette.brand.primary), variant = UncoloredLinkVariant)
}

@Composable
fun Footer(modifier: Modifier = Modifier) {
    val username = remember { AuthSession.username }
    Box(FooterStyle.toModifier().then(modifier), contentAlignment = Alignment.Center) {
        Div(Modifier.textAlign(TextAlign.Center).toAttrs()) {
            Span {
                SpanText("Amie · Arduino board management and package store · ")
                FooterLink("/docs", "Docs")
                SpanText(" · ")
                FooterLink("/downloads", "Downloads")
                SpanText(" · ")
                FooterLink("/about", "About")
                SpanText(" · ")
                if (username != null) FooterLink("/dashboard?username=${encodeURIComponent(username)}", "Your packages")
                else FooterLink("/loginpage", "Sign in")
            }
            Div(Modifier.fontSize(8.px).margin(top = 0.4.cssRem).toAttrs()) {
                Text("Press ")
                KeyHint("${Platform.commandKey} K")
                Text(" to search packages, authors and docs from any page.")
            }
        }
    }
}
