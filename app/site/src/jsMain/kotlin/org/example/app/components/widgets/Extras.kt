package org.example.app.components.widgets

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.Overflow
import com.varabyte.kobweb.compose.css.TextAlign
import com.varabyte.kobweb.compose.css.TextTransform
import com.varabyte.kobweb.compose.css.Transition
import com.varabyte.kobweb.compose.css.UserSelect
import com.varabyte.kobweb.compose.css.WhiteSpace
import com.varabyte.kobweb.compose.css.Width
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.components.navigation.Link
import com.varabyte.kobweb.silk.components.navigation.UncoloredLinkVariant
import com.varabyte.kobweb.silk.components.navigation.UndecoratedLinkVariant
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.animation.Keyframes
import com.varabyte.kobweb.silk.style.animation.toAnimation
import com.varabyte.kobweb.silk.style.selectors.hover
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.compose.css.AnimationIterationCount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.app.components.Clipboard
import org.example.app.toSitePalette
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Nav
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import kotlin.js.Date

// ---- small labels -------------------------------------------------------------------------------------

enum class BadgeTone { OUTLINE, FILLED, MUTED }

/** A small uppercase label: a file kind, a platform, a state. */
@Composable
fun Badge(text: String, tone: BadgeTone = BadgeTone.OUTLINE, modifier: Modifier = Modifier, color: Tone? = null) {
    val palette = ColorMode.current.toSitePalette()
    val base = Modifier
        .display(DisplayStyle.InlineBlock).fontSize(8.px).lineHeight(1)
        .padding(0.3.cssRem, 0.45.cssRem).textTransform(TextTransform.Uppercase).whiteSpace(WhiteSpace.NoWrap)
    if (color != null) {
        // A coloured badge: the tone's tint behind, its edge around, the theme's text colour on top
        val dark = ColorMode.current == ColorMode.DARK
        SpanText(text, base.backgroundColor(color.tint(if (dark) 0.28f else 0.22f)).border(2.px, LineStyle.Solid, color.edge()).color(if (dark) Colors.White else Color.rgb(0x151A22)).then(modifier))
        return
    }
    val toned = when (tone) {
        BadgeTone.OUTLINE -> base.border(2.px, LineStyle.Solid, palette.border).color(Colors.White)
        BadgeTone.FILLED -> base.border(2.px, LineStyle.Solid, Colors.White).backgroundColor(Colors.White).color(Colors.Black)
        BadgeTone.MUTED -> base.border(2.px, LineStyle.Solid, palette.muted).color(palette.muted)
    }
    SpanText(text, toned.then(modifier))
}

/** A key on the keyboard, as a hint next to an action. */
@Composable
fun KeyHint(text: String, modifier: Modifier = Modifier) {
    val palette = ColorMode.current.toSitePalette()
    SpanText(
        text,
        Modifier.display(DisplayStyle.InlineBlock).fontSize(8.px).lineHeight(1).padding(0.25.cssRem, 0.4.cssRem)
            .border(1.px, LineStyle.Solid, palette.muted).color(palette.muted).whiteSpace(WhiteSpace.NoWrap).then(modifier)
    )
}

val ChipStyle = CssStyle {
    base {
        Modifier
            .fontSize(9.px).lineHeight(1).padding(0.5.cssRem, 0.7.cssRem)
            .border(2.px, LineStyle.Solid, colorMode.toSitePalette().border)
            .backgroundColor(Colors.Black).color(Colors.White)
            .cursor(Cursor.Pointer).userSelect(UserSelect.None).whiteSpace(WhiteSpace.NoWrap)
            .display(DisplayStyle.LegacyInlineFlex).alignItems(AlignItems.Center).gap(0.4.cssRem)
            .transition(Transition.of("background-color", 120.ms))
    }
    hover { Modifier.backgroundColor(Color.rgb(0x262626)) }
}

/** A toggle in a row of filters; the selected one is filled. */
@Composable
fun Chip(text: String, selected: Boolean, count: Int? = null, onClick: () -> Unit) {
    val palette = ColorMode.current.toSitePalette()
    Div(
        ChipStyle.toModifier()
            .then(if (selected) Modifier.backgroundColor(Colors.White).color(Colors.Black) else Modifier)
            .tabIndex(0)
            .onClick { onClick() }
            .onKeyDown { if (it.key == "Enter" || it.key == " ") { it.preventDefault(); onClick() } }
            .toAttrs {
                attr("role", "button")
                attr("aria-pressed", selected.toString())
            }
    ) {
        Text(text)
        if (count != null) Span(Modifier.color(if (selected) Color.rgb(0x555555) else palette.muted).toAttrs()) { Text(count.toString()) }
    }
}

// ---- numbers at a glance ------------------------------------------------------------------------------

@Composable
fun StatRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Div(Modifier.fillMaxWidth().display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.6.cssRem).margin(bottom = 1.5.cssRem).then(modifier).toAttrs()) { content() }
}

/** One number with its label, for a row of them at the top of a page. */
@Composable
fun StatTile(label: String, value: String, hint: String? = null, modifier: Modifier = Modifier) {
    val palette = ColorMode.current.toSitePalette()
    Div(
        Modifier.minWidth(9.cssRem).flexGrow(1).padding(0.9.cssRem, 1.cssRem)
            .border(2.px, LineStyle.Solid, palette.border)
            .backgroundColor(palette.nearBackground.toRgb().copyf(alpha = 0.85f))
            .then(modifier).toAttrs()
    ) {
        Div(Modifier.fontSize(8.px).color(palette.muted).textTransform(TextTransform.Uppercase).margin(bottom = 0.45.cssRem).toAttrs()) { Text(label) }
        Div(Modifier.fontSize(16.px).lineHeight(1.3).whiteSpace(WhiteSpace.NoWrap).toAttrs()) { Text(value) }
        if (hint != null) Div(Modifier.fontSize(8.px).color(palette.muted).margin(top = 0.4.cssRem).lineHeight(1.6).toAttrs()) { Text(hint) }
    }
}

// ---- loading ------------------------------------------------------------------------------------------

val SkeletonPulse = Keyframes {
    from { Modifier.opacity(0.35) }
    50.percent { Modifier.opacity(0.85) }
    to { Modifier.opacity(0.35) }
}

val SkeletonStyle = CssStyle {
    base {
        Modifier.height(0.9.cssRem).backgroundColor(Color.rgb(0x2E2E2E))
            .animation(SkeletonPulse.toAnimation(duration = 1.4.s, timingFunction = AnimationTimingFunction.EaseInOut, iterationCount = AnimationIterationCount.Infinite))
    }
    cssRule(CSSMediaQuery.MediaFeature("prefers-reduced-motion", StylePropertyValue("reduce"))) {
        Modifier.styleModifier { property("animation", "none") }
    }
}

/** Grey bars in the shape of the table that is about to appear, so the page does not jump. */
@Composable
fun SkeletonRows(rows: Int = 4, label: String = "Loading…") {
    val palette = ColorMode.current.toSitePalette()
    Div(
        Modifier.fillMaxWidth().border(2.px, LineStyle.Solid, palette.border).padding(1.cssRem)
            .backgroundColor(palette.nearBackground.toRgb().copyf(alpha = 0.85f))
            .toAttrs { attr("role", "status"); attr("aria-label", label) }
    ) {
        repeat(rows) { index ->
            Div(Modifier.display(DisplayStyle.Flex).gap(1.cssRem).alignItems(AlignItems.Center).margin(bottom = if (index == rows - 1) 0.px else 0.9.cssRem).toAttrs()) {
                Div(SkeletonStyle.toModifier().width(38.percent).toAttrs())
                Div(SkeletonStyle.toModifier().width(18.percent).toAttrs())
                Div(SkeletonStyle.toModifier().width(12.percent).toAttrs())
                Div(SkeletonStyle.toModifier().width(16.percent).styleModifier { property("margin-left", "auto") }.toAttrs())
            }
        }
    }
}

/** A bar that fills from left to right; [fraction] runs from 0 to 1. */
@Composable
fun ProgressBar(fraction: Double, label: String? = null, modifier: Modifier = Modifier) {
    val palette = ColorMode.current.toSitePalette()
    val percent = (fraction.coerceIn(0.0, 1.0) * 100).toInt()
    Div(Modifier.fillMaxWidth().then(modifier).toAttrs {
        attr("role", "progressbar"); attr("aria-valuenow", percent.toString()); attr("aria-valuemin", "0"); attr("aria-valuemax", "100")
    }) {
        if (label != null) {
            Div(Modifier.display(DisplayStyle.Flex).justifyContent(JustifyContent.SpaceBetween).fontSize(9.px).color(palette.muted).margin(bottom = 0.35.cssRem).toAttrs()) {
                Span { Text(label) }
                Span { Text("$percent%") }
            }
        }
        Div(Modifier.fillMaxWidth().height(0.9.cssRem).border(2.px, LineStyle.Solid, Colors.White).backgroundColor(Colors.Black).toAttrs()) {
            Div(Modifier.height(100.percent).width(percent.percent).backgroundColor(Colors.White).transition(Transition.of("width", 150.ms)).toAttrs())
        }
    }
}

/** Numbered steps of a flow, with the ones already done ticked off. */
@Composable
fun Steps(labels: List<String>, current: Int, modifier: Modifier = Modifier) {
    val palette = ColorMode.current.toSitePalette()
    Div(Modifier.display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).alignItems(AlignItems.Center).gap(0.5.cssRem).margin(bottom = 1.25.cssRem).then(modifier).toAttrs {
        attr("aria-label", "Progress: step ${current + 1} of ${labels.size}")
    }) {
        labels.forEachIndexed { index, label ->
            val done = index < current
            val active = index == current
            Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.45.cssRem).fontSize(9.px)
                .color(if (done || active) Colors.White else palette.muted).toAttrs {
                    if (active) attr("aria-current", "step")
                }) {
                Div(
                    Modifier.size(1.5.cssRem).display(DisplayStyle.Flex).alignItems(AlignItems.Center).justifyContent(JustifyContent.Center)
                        .fontSize(8.px).lineHeight(1)
                        .border(2.px, LineStyle.Solid, if (done || active) Colors.White else palette.muted)
                        .backgroundColor(if (done) Colors.White else Colors.Black)
                        .color(if (done) Colors.Black else Colors.White).toAttrs()
                ) { Text(if (done) "✓" else (index + 1).toString()) }
                Text(label)
            }
            if (index < labels.lastIndex) {
                Div(Modifier.width(1.5.cssRem).height(2.px).backgroundColor(if (index < current) Colors.White else palette.muted).toAttrs())
            }
        }
    }
}

// ---- navigation aids ----------------------------------------------------------------------------------

class Crumb(val label: String, val href: String? = null)

/** Where the page sits: "Packages › demo › amie-demo.jar", every part but the last a link. */
@Composable
fun Breadcrumbs(vararg crumbs: Crumb, modifier: Modifier = Modifier) {
    val palette = ColorMode.current.toSitePalette()
    Nav(attrs = Modifier.fontSize(9.px).lineHeight(1.8).margin(bottom = 1.cssRem).color(palette.muted)
        .display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).alignItems(AlignItems.Center).gap(0.5.cssRem).then(modifier).toAttrs { attr("aria-label", "Breadcrumb") }) {
        crumbs.forEachIndexed { index, crumb ->
            if (index > 0) SpanText("›")
            if (crumb.href != null) {
                Link(crumb.href, crumb.label, Modifier.color(Colors.White), variant = UndecoratedLinkVariant.then(UncoloredLinkVariant))
            } else {
                SpanText(crumb.label, Modifier.color(Colors.White).styleModifier { property("word-break", "break-all") })
            }
        }
    }
}

/** The heading above a block of a page, the same size everywhere. */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier, id: String? = null, actions: (@Composable () -> Unit)? = null) {
    Div(Modifier.fillMaxWidth().display(DisplayStyle.Flex).alignItems(AlignItems.Center).justifyContent(JustifyContent.SpaceBetween)
        .flexWrap(FlexWrap.Wrap).gap(0.6.cssRem).margin(top = 0.px, bottom = 0.8.cssRem).then(modifier).toAttrs()) {
        H2(Modifier.fontSize(14.px).margin(0.px).toAttrs { if (id != null) id(id) }) { Text(text) }
        if (actions != null) Div(Modifier.display(DisplayStyle.Flex).gap(0.5.cssRem).flexWrap(FlexWrap.Wrap).alignItems(AlignItems.Center).toAttrs()) { actions() }
    }
}

/** Lets a wide table scroll sideways on a narrow screen instead of pushing the page out. */
@Composable
fun TableScroll(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Div(Modifier.fillMaxWidth().styleModifier { property("overflow-x", "auto"); property("-webkit-overflow-scrolling", "touch") }.then(modifier).toAttrs()) {
        content()
    }
}

/** A friendlier empty block: what is missing and the one thing to do about it. */
@Composable
fun EmptyAction(title: String, text: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Card(Modifier.display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).alignItems(AlignItems.FlexStart).gap(0.5.cssRem).then(modifier)) {
        Div(Modifier.fontSize(12.px).toAttrs()) { Text(title) }
        if (text != null) MutedText(text)
        if (action != null) Div(Modifier.margin(top = 0.4.cssRem).display(DisplayStyle.Flex).gap(0.5.cssRem).flexWrap(FlexWrap.Wrap).toAttrs()) { action() }
    }
}

// ---- actions ------------------------------------------------------------------------------------------

/** Copies [text] and says so; the label flips to [done] for a moment. */
@Composable
fun CopyButton(
    text: String,
    label: String = "Copy link",
    done: String = "Copied",
    toast: String? = "Link copied to the clipboard.",
    primary: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    plain: Boolean = false
) {
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    val click: () -> Unit = {
        scope.launch {
            if (Clipboard.copy(text)) {
                copied = true
                toast?.let { Toasts.show(it, BannerKind.SUCCESS) }
                delay(1800)
                copied = false
            } else {
                Toasts.show("Copying is blocked in this browser. Select the text and copy it by hand.", BannerKind.ERROR)
            }
        }
    }
    if (plain) PlainButton(if (copied) done else label, modifier, enabled, click)
    else if (primary) PrimaryButton(if (copied) done else label, modifier, enabled, onClick = click)
    else GhostButton(if (copied) done else label, modifier, enabled, onClick = click)
}

/**
 * A destructive button that asks once: the first click shows the question with Yes and Cancel beside it,
 * and the question goes away by itself after a few seconds.
 */
@Composable
fun ConfirmDangerButton(
    text: String,
    question: String = "Are you sure?",
    confirmText: String = "Yes, delete",
    enabled: Boolean = true,
    plain: Boolean = false,
    onConfirm: () -> Unit
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(7000)
            armed = false
        }
    }
    if (!armed) {
        DangerButton(text, enabled = enabled, plain = plain) { armed = true }
    } else {
        Div(Modifier.display(DisplayStyle.LegacyInlineFlex).alignItems(AlignItems.Center).gap(0.5.cssRem).flexWrap(FlexWrap.Wrap).toAttrs { attr("role", "alertdialog") }) {
            MutedText(question, Modifier.whiteSpace(WhiteSpace.NoWrap))
            DangerButton(confirmText, plain = plain) { armed = false; onConfirm() }
            GhostButton("Cancel", plain = plain) { armed = false }
        }
    }
}

/** A drop-down in the site's style. [options] are value to label. */
@Composable
fun SelectField(value: String, options: List<Pair<String, String>>, onChange: (String) -> Unit, modifier: Modifier = Modifier, ariaLabel: String? = null) {
    Select(attrs = TextInputStyle.toModifier().width(Width.FitContent).minWidth(10.cssRem).cursor(Cursor.Pointer).then(modifier).toAttrs {
        if (ariaLabel != null) attr("aria-label", ariaLabel)
        onChange { event -> onChange(event.value ?: "") }
    }) {
        options.forEach { (optionValue, label) ->
            Option(optionValue, attrs = { if (optionValue == value) selected() }) { Text(label) }
        }
    }
}

/** An icon drawn as inline SVG (strokes follow the text color). */
@Composable
fun InlineSvg(svg: String, size: Int = 18, label: String? = null, modifier: Modifier = Modifier) {
    Div(Modifier.size(size.px).display(DisplayStyle.InlineBlock).lineHeight(0).then(modifier).toAttrs {
        if (label != null) { attr("role", "img"); attr("aria-label", label) } else attr("aria-hidden", "true")
        ref { element ->
            element.innerHTML = svg
            onDispose { }
        }
    })
}

const val SEARCH_SVG = """<svg viewBox="0 0 24 24" width="100%" height="100%" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="square" shape-rendering="crispEdges" xmlns="http://www.w3.org/2000/svg"><circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.5 15.5 L21 21"/></svg>"""

// ---- formatting ---------------------------------------------------------------------------------------

/** "just now", "5 min ago", "yesterday"; the exact date once it is older than a month. */
fun formatRelative(iso: String?): String {
    if (iso.isNullOrBlank()) return "–"
    val date = Date(iso)
    if (date.getTime().isNaN()) return iso
    val minutes = (Date.now() - date.getTime()) / 60_000.0
    return when {
        minutes < 0 -> formatDate(iso)
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes.toInt()} min ago"
        minutes < 60 * 24 -> "${(minutes / 60).toInt()} h ago"
        minutes < 60 * 48 -> "yesterday"
        minutes < 60 * 24 * 30 -> "${(minutes / (60 * 24)).toInt()} days ago"
        else -> date.toLocaleDateString()
    }
}

/** "3 packages", "1 author" */
fun plural(count: Int, singular: String, plural: String = singular + "s"): String =
    "$count " + if (count == 1) singular else plural

/** Centered helper text under a block, e.g. "Typing filters the list". */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    val palette = ColorMode.current.toSitePalette()
    Div(Modifier.fontSize(8.px).lineHeight(1.9).color(palette.muted).textAlign(TextAlign.Start).then(modifier).toAttrs()) { Text(text) }
}

/** Keeps the browser's own overflow for a block of pre-formatted text. */
val CodeBlockScroll: Modifier = Modifier.overflow(Overflow.Auto)
