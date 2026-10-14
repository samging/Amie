package org.example.app

import com.varabyte.kobweb.compose.css.ScrollBehavior
import com.varabyte.kobweb.compose.css.TextAlign
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.silk.components.forms.ButtonStyle
import com.varabyte.kobweb.silk.components.forms.ButtonVars
import com.varabyte.kobweb.silk.components.layout.HorizontalDividerStyle
import com.varabyte.kobweb.silk.init.InitSilk
import com.varabyte.kobweb.silk.init.InitSilkContext
import com.varabyte.kobweb.silk.init.registerStyleBase
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.addVariantBase
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.theme.colors.palette.color
import com.varabyte.kobweb.silk.theme.colors.palette.toPalette
import com.varabyte.kobweb.silk.theme.modifyStyleBase
import org.jetbrains.compose.web.css.*

@InitSilk
fun initSiteStyles(ctx: InitSilkContext) {
    ctx.stylesheet.registerStyle("html") {
        cssRule(CSSMediaQuery.MediaFeature("prefers-reduced-motion", StylePropertyValue("no-preference"))) {
            Modifier.scrollBehavior(ScrollBehavior.Smooth)
        }
    }

    // One pixel font for the whole site; small sizes and a tall line height keep it readable
    ctx.stylesheet.registerStyleBase("body") {
        Modifier
            .fontFamily("Press Start 2P", "monospace")
            .fontSize(12.px)
            .lineHeight(1.9)
            // The site is always dark; the animated backdrop sits on this base color
            .backgroundColor(com.varabyte.kobweb.compose.ui.graphics.Color.rgb(0x0A0A0A))
            .color(com.varabyte.kobweb.compose.ui.graphics.Color.rgb(0xFFFFFF))
    }

    // Form controls do not inherit the page font by default
    listOf("input", "textarea", "button", "select").forEach { tag ->
        ctx.stylesheet.registerStyleBase(tag) { Modifier.fontFamily("Press Start 2P", "monospace") }
    }
    ctx.stylesheet.registerStyleBase("h1") { Modifier.fontSize(24.px).lineHeight(1.45).fontWeight(400).margin(topBottom = 0.5.cssRem) }
    ctx.stylesheet.registerStyleBase("h2") { Modifier.fontSize(16.px).lineHeight(1.5).fontWeight(400) }
    ctx.stylesheet.registerStyleBase("h3") { Modifier.fontSize(13.px).lineHeight(1.6).fontWeight(400) }
    ctx.stylesheet.registerStyleBase("h4") { Modifier.fontSize(12.px).lineHeight(1.6).fontWeight(400) }
    ctx.stylesheet.registerStyleBase("b") { Modifier.fontWeight(400).color(com.varabyte.kobweb.compose.ui.graphics.Color.rgb(0xFFFFFF)) }
    ctx.stylesheet.registerStyleBase("strong") { Modifier.fontWeight(400).color(com.varabyte.kobweb.compose.ui.graphics.Color.rgb(0xFFFFFF)) }

    // Plain anchors follow the brand color instead of the browser's default blue
    ctx.stylesheet.registerStyleBase("a") {
        Modifier.color(com.varabyte.kobweb.compose.ui.graphics.Color.rgb(0xFFFFFF))
            .textDecorationLine(com.varabyte.kobweb.compose.css.TextDecorationLine.Underline)
    }
    ctx.stylesheet.registerStyleBase("a:hover") {
        Modifier.color(com.varabyte.kobweb.compose.ui.graphics.Color.rgb(0xD9D9D9))
    }

    ctx.theme.modifyStyleBase(HorizontalDividerStyle) {
        Modifier.fillMaxWidth()
    }
}

val HeadlineTextStyle = CssStyle.base {
    Modifier
        .fontSize(28.px)
        .textAlign(TextAlign.Start)
        .lineHeight(1.5)
}

val SubheadlineTextStyle = CssStyle.base {
    Modifier
        .fontSize(11.px)
        .lineHeight(1.9)
        .textAlign(TextAlign.Start)
        .color(colorMode.toPalette().color.toRgb().copyf(alpha = 0.75f))
}

val CircleButtonVariant = ButtonStyle.addVariantBase {
    Modifier.padding(0.px).borderRadius(50.percent)
}

val UncoloredButtonVariant = ButtonStyle.addVariantBase {
    Modifier.setVariable(ButtonVars.BackgroundDefaultColor, Colors.Transparent)
}
