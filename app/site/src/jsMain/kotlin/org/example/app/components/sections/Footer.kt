package org.example.app.components.sections

import androidx.compose.runtime.Composable
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
import org.example.app.toSitePalette
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.dom.Span

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
fun Footer(modifier: Modifier = Modifier) {
    Box(FooterStyle.toModifier().then(modifier), contentAlignment = Alignment.Center) {
        Span(Modifier.textAlign(TextAlign.Center).toAttrs()) {
            val palette = ColorMode.current.toSitePalette()
            SpanText("Amie · Arduino board management and package store · ")
            Link("/docs", "Docs", Modifier.setVariable(com.varabyte.kobweb.silk.style.vars.color.ColorVar, palette.brand.primary), variant = UncoloredLinkVariant)
            SpanText(" · ")
            Link("/downloads", "Downloads", Modifier.setVariable(com.varabyte.kobweb.silk.style.vars.color.ColorVar, palette.brand.primary), variant = UncoloredLinkVariant)
        }
    }
}
