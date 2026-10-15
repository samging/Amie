package org.example.app.components.widgets

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.Overflow
import com.varabyte.kobweb.compose.css.Width
import com.varabyte.kobweb.compose.css.TextAlign
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.forms.ButtonStyle
import com.varabyte.kobweb.silk.components.forms.ButtonVars
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.addVariant
import com.varabyte.kobweb.silk.style.addVariantBase
import com.varabyte.kobweb.silk.style.selectors.active
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.selectors.focus
import com.varabyte.kobweb.silk.style.selectors.hover
import com.varabyte.kobweb.silk.style.toAttrs
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.silk.theme.colors.palette.background
import com.varabyte.kobweb.silk.theme.colors.palette.color
import com.varabyte.kobweb.silk.theme.colors.palette.toPalette
import org.example.app.toSitePalette
import org.jetbrains.compose.web.attributes.AttrsScope
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.value
import org.w3c.dom.HTMLInputElement
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import kotlin.js.Date

// ---- styles -------------------------------------------------------------------------------------------

val CardStyle = CssStyle {
    base {
        val palette = colorMode.toSitePalette()
        Modifier
            .fillMaxWidth()
            .backgroundColor(palette.nearBackground.toRgb().copyf(alpha = 0.85f))
            .border(2.px, LineStyle.Solid, palette.border)
            .borderRadius(0.px)
            .padding(1.25.cssRem)
            .transition(com.varabyte.kobweb.compose.css.Transition.of("transform", 200.ms), com.varabyte.kobweb.compose.css.Transition.of("box-shadow", 200.ms))
    }
    hover {
        Modifier.translate((-2).px, (-2).px).boxShadow(6.px, 6.px, 0.px, 0.px, colorMode.toSitePalette().border)
    }
}

val DataTableStyle = CssStyle {
    base {
        Modifier
            .fillMaxWidth()
            .styleModifier { property("border-collapse", "collapse") }
            .backgroundColor(colorMode.toSitePalette().nearBackground)
            .border(2.px, LineStyle.Solid, colorMode.toSitePalette().border)
            .borderRadius(0.px)
            .overflow(Overflow.Hidden)
    }
    cssRule(" th") {
        Modifier
            .padding(topBottom = 0.7.cssRem, leftRight = 0.9.cssRem)
            .textAlign(TextAlign.Start)
            .fontSize(9.px)
            .letterSpacing(0.px)
            .textTransform(com.varabyte.kobweb.compose.css.TextTransform.Uppercase)
            .color(colorMode.toSitePalette().muted)
            .borderBottom(1.px, LineStyle.Solid, colorMode.toSitePalette().border)
    }
    cssRule(" td") {
        Modifier
            .fontSize(11.px)
            .padding(topBottom = 0.7.cssRem, leftRight = 0.9.cssRem)
            .borderBottom(1.px, LineStyle.Solid, colorMode.toSitePalette().border)
            .styleModifier { property("vertical-align", "middle") }
    }
    cssRule(" tr:last-child td") {
        Modifier.borderBottom(0.px, LineStyle.None, Colors.Transparent)
    }
}

val TextInputStyle = CssStyle {
    base {
        val palette = colorMode.toSitePalette()
        Modifier
            .fillMaxWidth()
            .height(2.5.cssRem)
            .padding(topBottom = 0.px, leftRight = 0.8.cssRem)
            .borderRadius(0.px)
            .border(3.px, LineStyle.Solid, palette.border)
            .backgroundColor(Colors.Black)
            .color(colorMode.toPalette().color)
            .fontSize(10.px)
            .lineHeight(1.4)
            .styleModifier { property("box-sizing", "border-box") }
            .outline(0.px, LineStyle.None, Colors.Transparent)
    }
    focus {
        Modifier.border(2.px, LineStyle.Solid, colorMode.toSitePalette().brand.primary).boxShadow(3.px, 3.px, 0.px, 0.px, colorMode.toSitePalette().brand.primary)
    }
}

/** Shared pixel-art look: pixel font, square corners, thick border, hard stepped shadow. */
private fun Modifier.pixelButton(background: Color, foreground: Color, hoverBackground: Color): Modifier = this
    .setVariable(ButtonVars.BackgroundDefaultColor, background)
    .setVariable(ButtonVars.BackgroundHoverColor, hoverBackground)
    .setVariable(ButtonVars.BackgroundPressedColor, hoverBackground)
    .setVariable(ButtonVars.Color, foreground)
    .setVariable(ButtonVars.FontSize, 10.px)
    .setVariable(ButtonVars.Height, 2.5.cssRem)
    .setVariable(ButtonVars.PaddingHorizontal, 1.cssRem)
    .fontFamily("Press Start 2P", "monospace")
    .lineHeight(1)
    .whiteSpace(com.varabyte.kobweb.compose.css.WhiteSpace.NoWrap)
    .letterSpacing(0.px)
    .textTransform(com.varabyte.kobweb.compose.css.TextTransform.Uppercase)
    .borderRadius(0.px)
    .border(3.px, LineStyle.Solid, foreground)
    .boxShadow(4.px, 4.px, 0.px, 0.px, foreground)
    .styleModifier { property("image-rendering", "pixelated") }
    .transition(com.varabyte.kobweb.compose.css.Transition.of("transform", 80.ms), com.varabyte.kobweb.compose.css.Transition.of("box-shadow", 80.ms))

private val PixelButtonHover = Modifier.translate(2.px, 2.px)
private val PixelButtonPressed = Modifier.translate(4.px, 4.px)

val PrimaryButtonVariant = ButtonStyle.addVariant {
    base { Modifier.pixelButton(Colors.White, Colors.Black, Color.rgb(0xD9D9D9)).boxShadow(4.px, 4.px, 0.px, 0.px, Colors.White) }
    hover { PixelButtonHover.boxShadow(2.px, 2.px, 0.px, 0.px, Colors.White) }
    active { PixelButtonPressed.boxShadow(0.px, 0.px, 0.px, 0.px, Colors.White) }
}

val GhostButtonVariant = ButtonStyle.addVariant {
    base { Modifier.pixelButton(Colors.Black, Colors.White, Color.rgb(0x262626)) }
    hover { PixelButtonHover.boxShadow(2.px, 2.px, 0.px, 0.px, Colors.White) }
    active { PixelButtonPressed.boxShadow(0.px, 0.px, 0.px, 0.px, Colors.White) }
}

val DangerButtonVariant = ButtonStyle.addVariant {
    base { Modifier.pixelButton(Colors.Black, Colors.White, Colors.White).border(3.px, LineStyle.Dashed, Colors.White) }
    hover { PixelButtonHover.setVariable(ButtonVars.Color, Colors.Black).boxShadow(2.px, 2.px, 0.px, 0.px, Colors.White) }
    active { PixelButtonPressed.boxShadow(0.px, 0.px, 0.px, 0.px, Colors.White) }
}

// ---- widgets ------------------------------------------------------------------------------------------

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Div(CardStyle.toModifier().then(modifier).toAttrs()) { content() }
}

@Composable
fun PageHeading(title: String, subtitle: String? = null, actions: (@Composable () -> Unit)? = null) {
    Div(
        Modifier.fillMaxWidth().display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(1.cssRem)
            .alignItems(AlignItems.Center).justifyContent(JustifyContent.SpaceBetween).margin(bottom = 1.5.cssRem).toAttrs()
    ) {
        Div {
            H1(Modifier.margin(0.px).fontSize(22.px).lineHeight(1.5).toAttrs()) { Text(title) }
            if (subtitle != null) {
                P(Modifier.margin(top = 0.4.cssRem, bottom = 0.px).fontSize(11.px).color(ColorMode.current.toSitePalette().muted).toAttrs()) { Text(subtitle) }
            }
        }
        if (actions != null) {
            Div(Modifier.display(DisplayStyle.Flex).gap(0.6.cssRem).flexWrap(FlexWrap.Wrap).toAttrs()) { actions() }
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = { onClick() }, modifier, variant = PrimaryButtonVariant, enabled = enabled) { Text(text) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = { onClick() }, modifier, variant = GhostButtonVariant, enabled = enabled) { Text(text) }
}

@Composable
fun DangerButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = { onClick() }, modifier, variant = DangerButtonVariant, enabled = enabled) { Text(text) }
}

/** A link that looks like the primary button; `download` makes the browser save the target. */
@Composable
fun LinkButton(text: String, href: String, download: Boolean = false, primary: Boolean = true, modifier: Modifier = Modifier) {
    A(href, attrs = Modifier.textDecorationLine(com.varabyte.kobweb.compose.css.TextDecorationLine.None).toAttrs {
        if (download) attr("download", "")
    }) {
        Button(onClick = {}, modifier, variant = if (primary) PrimaryButtonVariant else GhostButtonVariant) { Text(text) }
    }
}

@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    type: InputType<String> = InputType.Text,
    modifier: Modifier = Modifier,
    onEnter: (() -> Unit)? = null,
    attrs: (AttrsScope<HTMLInputElement>.() -> Unit)? = null
) {
    Input(type, attrs = TextInputStyle.toModifier().then(modifier).toAttrs {
        placeholder(placeholder)
        value(value)
        onInput { onValueChange(it.value) }
        if (onEnter != null) onKeyDown { if (it.key == "Enter") onEnter() }
        attrs?.invoke(this)
    })
}

@Composable
fun TextAreaField(value: String, onValueChange: (String) -> Unit, placeholder: String = "", modifier: Modifier = Modifier) {
    TextArea(attrs = TextInputStyle.toModifier().height(com.varabyte.kobweb.compose.css.Height.FitContent).minHeight(7.cssRem).padding(topBottom = 0.6.cssRem, leftRight = 0.8.cssRem).lineHeight(1.8).then(modifier).toAttrs {
        placeholder(placeholder)
        value(value)
        onInput { onValueChange(it.value) }
    })
}

@Composable
fun FieldLabel(text: String) {
    SpanText(text, Modifier.fontSize(9.px).display(DisplayStyle.Block).margin(bottom = 0.35.cssRem).color(ColorMode.current.toSitePalette().muted))
}

enum class BannerKind { INFO, SUCCESS, ERROR }

/** A line of feedback for the person: what happened, in plain words. */
@Composable
fun StatusBanner(message: String?, kind: BannerKind = BannerKind.INFO, modifier: Modifier = Modifier) {
    if (message.isNullOrBlank()) return
    val palette = ColorMode.current.toSitePalette()
    val (color, background) = when (kind) {
        BannerKind.INFO -> palette.info to palette.infoBackground
        BannerKind.SUCCESS -> palette.success to palette.successBackground
        BannerKind.ERROR -> palette.danger to palette.dangerBackground
    }
    Div(
        Modifier.fillMaxWidth().padding(0.75.cssRem, 1.cssRem).borderRadius(0.px)
            .border(2.px, if (kind == BannerKind.ERROR) LineStyle.Dashed else LineStyle.Solid, color)
            .backgroundColor(background).color(color).fontSize(11.px).lineHeight(1.8).then(modifier).toAttrs {
                attr("role", if (kind == BannerKind.ERROR) "alert" else "status")
            }
    ) { Text(message) }
}

@Composable
fun EmptyState(text: String) {
    Div(
        Modifier.fillMaxWidth().padding(2.cssRem).textAlign(TextAlign.Center).fontSize(11.px)
            .color(ColorMode.current.toSitePalette().muted).toAttrs()
    ) { Text(text) }
}

@Composable
fun MutedText(text: String, modifier: Modifier = Modifier) {
    SpanText(text, Modifier.color(ColorMode.current.toSitePalette().muted).fontSize(10.px).lineHeight(1.9).then(modifier))
}

// ---- formatting ---------------------------------------------------------------------------------------

fun formatSize(bytes: Double): String = when {
    bytes <= 0 -> "–"
    bytes >= 1024 * 1024 -> "${(bytes / (1024.0 * 1024.0)).asDynamic().toFixed(2)} MB"
    bytes >= 1024 -> "${(bytes / 1024.0).asDynamic().toFixed(1)} KB"
    else -> "${bytes.toInt()} B"
}

fun formatDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "–"
    val date = Date(iso)
    if (date.getTime().isNaN()) return iso
    return date.toLocaleDateString() + " " + date.toLocaleTimeString(locales = "default", options = dateLocaleOptions { hour = "2-digit"; minute = "2-digit" })
}
