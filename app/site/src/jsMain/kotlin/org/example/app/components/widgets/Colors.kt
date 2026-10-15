package org.example.app.components.widgets

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.UserSelect
import com.varabyte.kobweb.compose.css.WhiteSpace
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import org.example.app.components.PackageKind
import org.example.app.toSitePalette
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * The site's colour: a small set of soft, mid-lightness tones that read on the black and on the white
 * theme. Each is used as a tint (low alpha background) and an edge (the solid colour on borders and dots),
 * never as a large fill, so pages stay quiet but things of different kinds stop looking the same.
 */
class Tone(val r: Int, val g: Int, val b: Int, val name: String) {
    fun edge(): Color = Color.rgb(r, g, b)
    fun tint(alpha: Float): Color = Color.rgba(r, g, b, alpha)
}

object SiteTones {
    val indigo = Tone(124, 138, 214, "indigo")
    val sand = Tone(201, 160, 92, "sand")
    val teal = Tone(104, 176, 166, "teal")
    val slate = Tone(142, 156, 176, "slate")
    val rose = Tone(196, 132, 132, "rose")
    val moss = Tone(136, 176, 128, "moss")
    val lavender = Tone(160, 130, 200, "lavender")
    val gold = Tone(214, 180, 96, "gold")
    val sky = Tone(110, 170, 210, "sky")
    val coral = Tone(214, 140, 110, "coral")
    val neutral = Tone(160, 160, 160, "neutral")

    /** The tones people get, in an order that keeps neighbours apart. */
    val people: List<Tone> = listOf(indigo, coral, teal, gold, lavender, moss, sky, rose, sand, slate)

    /** A stable tone for a name: the same author is always the same colour, on every page. */
    fun ofName(name: String): Tone {
        if (name.isEmpty()) return neutral
        var h = 7
        for (c in name) h = (h * 31 + c.code) and 0x7fffffff
        return people[h % people.size]
    }

    /** One tone per kind of file. */
    fun ofKind(kind: PackageKind): Tone = when (kind) {
        PackageKind.JAR -> indigo
        PackageKind.MODEL -> lavender
        PackageKind.JSON -> sand
        PackageKind.PYTHON -> moss
        PackageKind.TEXT -> slate
        PackageKind.OTHER -> neutral
    }

    /** One tone per badge administrators grant. */
    fun ofBadge(key: String): Tone = when (key) {
        "verified" -> sky
        "maintainer" -> indigo
        "contributor" -> moss
        "researcher" -> lavender
        "helper" -> teal
        "early" -> gold
        else -> neutral
    }
}

val PackageKind.tone: Tone get() = SiteTones.ofKind(this)

/** A small square in a tone, in front of a row or a chip */
@Composable
fun ToneDot(tone: Tone, size: CSSLengthValue = 9.px) {
    Span(Modifier.display(DisplayStyle.InlineBlock).width(size).height(size).flexShrink(0)
        .backgroundColor(tone.tint(0.55f)).border(1.px, LineStyle.Solid, tone.edge()).toAttrs { attr("aria-hidden", "true") }) {}
}

/** A filter chip tinted with its tone: outlined when off, filled with the tint when on; never black on white */
@Composable
fun ToneChip(text: String, selected: Boolean, tone: Tone, count: Int? = null, onClick: () -> Unit) {
    val palette = ColorMode.current.toSitePalette()
    Div(
        Modifier
            .fontSize(9.px).lineHeight(1).padding(0.45.cssRem, 0.65.cssRem)
            .border(2.px, LineStyle.Solid, if (selected) tone.edge() else tone.tint(0.45f))
            .backgroundColor(tone.tint(if (selected) 0.32f else 0.10f))
            .cursor(Cursor.Pointer).userSelect(UserSelect.None).whiteSpace(WhiteSpace.NoWrap)
            .display(DisplayStyle.LegacyInlineFlex).alignItems(AlignItems.Center).gap(0.4.cssRem)
            .tabIndex(0)
            .onClick { onClick() }
            .onKeyDown { if (it.key == "Enter" || it.key == " ") { it.preventDefault(); onClick() } }
            .toAttrs { attr("role", "button"); attr("aria-pressed", selected.toString()) }
    ) {
        ToneDot(tone, 7.px)
        Text(text)
        if (count != null) Span(Modifier.color(palette.muted).toAttrs()) { Text(count.toString()) }
    }
}

/**
 * A coloured header band for a card or a group: the tone's tint behind, its edge as the bottom line.
 * This is where the site puts colour; the rows below stay plain.
 */
@Composable
fun ToneBand(tone: Tone, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Div(
        Modifier.fillMaxWidth().display(DisplayStyle.Flex).alignItems(AlignItems.Center).justifyContent(JustifyContent.SpaceBetween).flexWrap(FlexWrap.Wrap).gap(0.6.cssRem)
            .padding(topBottom = 0.6.cssRem, leftRight = 0.9.cssRem)
            .backgroundColor(tone.tint(0.34f)).styleModifier { property("border-bottom", "3px solid rgb(${tone.r}, ${tone.g}, ${tone.b})") }
            .then(modifier).toAttrs()
    ) { content() }
}

/** Two letters on a tinted square: a person's mark when they have no picture. */
@Composable
fun InitialsMark(initials: String, tone: Tone, size: CSSLengthValue, modifier: Modifier = Modifier, label: String? = null) {
    val dark = ColorMode.current == ColorMode.DARK
    Div(
        Modifier.size(size).flexShrink(0).display(DisplayStyle.Flex).alignItems(AlignItems.Center).justifyContent(JustifyContent.Center)
            .backgroundColor(tone.tint(if (dark) 0.35f else 0.25f)).border(2.px, LineStyle.Solid, tone.edge())
            .color(if (dark) Color.rgb(0xFFFFFF) else Color.rgb(0x151A22)).fontSize(11.px).then(modifier)
            .toAttrs { if (label == null) attr("aria-hidden", "true") else attr("aria-label", label) }
    ) { Text(initials) }
}
