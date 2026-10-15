package org.example.app.components.sections

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.css.AnimationIterationCount
import com.varabyte.kobweb.compose.css.Overflow
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.animation.Keyframes
import com.varabyte.kobweb.silk.style.animation.toAnimation
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.toAttrs
import org.example.app.config.encodeURIComponent
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div

/*
 * A quiet pixel night behind every page: black, with two layers of small square stars that drift very slowly
 * and twinkle a little, a floor grid so faint it only reads as texture, and a shooting star once in a while.
 * Drawn with CSS and two tiny inline SVG tiles, so nothing is downloaded, and it stands still for people who
 * prefer reduced motion.
 */

/** A small deterministic generator, so every visit draws the same sky. */
private class Dice(seed: Int) {
    private var state = seed
    fun next(bound: Int): Int {
        state = (state * 1103515245 + 12345) and 0x7fffffff
        return (state shr 8) % bound
    }
}

private fun svgUrl(svg: String): String = "url(\"data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}\")"

private fun Double.fixed(digits: Int): String = asDynamic().toFixed(digits) as String

/** A square tile of [count] pixel stars, [pixel] px each, that repeats without a seam. */
private fun starTile(seed: Int, size: Int, count: Int, pixel: Int, colors: List<String>): String {
    val dice = Dice(seed)
    val cells = size / pixel
    val rects = StringBuilder()
    repeat(count) {
        val x = dice.next(cells) * pixel
        val y = dice.next(cells) * pixel
        val color = colors[dice.next(colors.size)]
        val opacity = 0.35 + dice.next(55) / 100.0
        rects.append("<rect x='$x' y='$y' width='$pixel' height='$pixel' fill='$color' fill-opacity='${opacity.fixed(2)}'/>")
    }
    return svgUrl("<svg xmlns='http://www.w3.org/2000/svg' width='$size' height='$size' shape-rendering='crispEdges'>$rects</svg>")
}

private const val FAR_TILE = 360
private const val NEAR_TILE = 520

private val STAR_COLORS = listOf("#FFFFFF", "#FFFFFF", "#DCE8FF", "#F3E4FF")
private val FAR_STARS = starTile(7, FAR_TILE, 34, 2, STAR_COLORS)
private val NEAR_STARS = starTile(99, NEAR_TILE, 10, 3, STAR_COLORS)

private val reducedMotion = CSSMediaQuery.MediaFeature("prefers-reduced-motion", StylePropertyValue("reduce"))

val PixelDriftFar = Keyframes {
    from { Modifier.translate(0.px, 0.px) }
    to { Modifier.translate(FAR_TILE.px, FAR_TILE.px) }
}

val PixelDriftNear = Keyframes {
    from { Modifier.translate(0.px, 0.px) }
    to { Modifier.translate(NEAR_TILE.px, NEAR_TILE.px) }
}

val PixelTwinkle = Keyframes {
    from { Modifier.opacity(0.35) }
    50.percent { Modifier.opacity(0.7) }
    to { Modifier.opacity(0.35) }
}

val PixelGridScroll = Keyframes {
    from { Modifier.styleModifier { property("background-position", "0 0, 0 0") } }
    to { Modifier.styleModifier { property("background-position", "0 48px, 0 0") } }
}

val PixelShootingStar = Keyframes {
    from { Modifier.opacity(0).transform { translate(0.px, 0.px); rotate(27.deg) } }
    2.percent { Modifier.opacity(0.55).transform { translate(3.vw, 1.5.vh); rotate(27.deg) } }
    10.percent { Modifier.opacity(0).transform { translate(40.vw, 20.vh); rotate(27.deg) } }
    to { Modifier.opacity(0).transform { translate(40.vw, 20.vh); rotate(27.deg) } }
}

val PixelBackdropStyle = CssStyle.base {
    Modifier
        .position(Position.Fixed).top(0.px).left(0.px)
        .width(100.vw).height(100.vh)
        .zIndex(-1)
        .backgroundColor(Color.rgb(0x0A0A0A))
        .overflow(Overflow.Hidden)
        .styleModifier { property("pointer-events", "none") }
}

private fun Modifier.tileLayer(tile: Int, image: String): Modifier = this
    .position(Position.Absolute).top((-tile).px).left((-tile).px)
    .styleModifier {
        property("width", "calc(100% + ${tile}px)")
        property("height", "calc(100% + ${tile}px)")
        property("background-image", image)
        property("background-size", "${tile}px ${tile}px")
        property("background-repeat", "repeat")
        property("image-rendering", "pixelated")
        property("will-change", "transform")
    }

val PixelStarsFarStyle = CssStyle {
    base {
        Modifier.tileLayer(FAR_TILE, FAR_STARS).opacity(0.4)
            .animation(PixelDriftFar.toAnimation(duration = 240.s, timingFunction = AnimationTimingFunction.Linear, iterationCount = AnimationIterationCount.Infinite))
    }
    cssRule(reducedMotion) { Modifier.styleModifier { property("animation", "none") } }
}

val PixelStarsNearStyle = CssStyle {
    base {
        Modifier.tileLayer(NEAR_TILE, NEAR_STARS)
            .animation(PixelDriftNear.toAnimation(duration = 160.s, timingFunction = AnimationTimingFunction.Linear, iterationCount = AnimationIterationCount.Infinite))
    }
    cssRule(reducedMotion) { Modifier.styleModifier { property("animation", "none") } }
}

val PixelTwinkleStyle = CssStyle {
    base {
        Modifier.position(Position.Absolute).top(0.px).left(0.px).width(100.percent).height(100.percent)
            .animation(PixelTwinkle.toAnimation(duration = 7.s, timingFunction = AnimationTimingFunction.EaseInOut, iterationCount = AnimationIterationCount.Infinite))
    }
    cssRule(reducedMotion) { Modifier.styleModifier { property("animation", "none") } }
}

/** The floor: a grid so faint it is texture rather than a picture, rolling slowly toward the viewer. */
val PixelGridStyle = CssStyle {
    base {
        Modifier.position(Position.Absolute).left((-50).percent).bottom((-6).vh).width(200.percent).height(46.vh).opacity(0.11)
            .styleModifier {
                property("background-image", "linear-gradient(rgba(200,190,255,0.8) 2px, rgba(0,0,0,0) 2px), linear-gradient(90deg, rgba(200,190,255,0.8) 2px, rgba(0,0,0,0) 2px)")
                property("background-size", "80px 48px, 80px 48px")
                property("transform-origin", "50% 100%")
                property("transform", "perspective(520px) rotateX(62deg)")
                property("mask-image", "linear-gradient(to bottom, rgba(0,0,0,0) 0%, #000 45%, #000 100%)")
                property("-webkit-mask-image", "linear-gradient(to bottom, rgba(0,0,0,0) 0%, #000 45%, #000 100%)")
            }
            .animation(PixelGridScroll.toAnimation(duration = 6.s, timingFunction = AnimationTimingFunction.Linear, iterationCount = AnimationIterationCount.Infinite))
    }
    cssRule(reducedMotion) { Modifier.styleModifier { property("animation", "none") } }
}

val PixelShootingStarStyle = CssStyle {
    base {
        Modifier.position(Position.Absolute).top(10.vh).left(8.vw).width(90.px).height(2.px).opacity(0)
            .styleModifier {
                property("background", "linear-gradient(90deg, rgba(255,255,255,0) 0 30%, rgba(255,255,255,0.3) 30% 60%, rgba(255,255,255,0.9) 60% 100%)")
                property("transform-origin", "100% 50%")
            }
            .animation(PixelShootingStar.toAnimation(duration = 26.s, timingFunction = AnimationTimingFunction.Linear, iterationCount = AnimationIterationCount.Infinite))
    }
    cssRule(reducedMotion) { Modifier.styleModifier { property("animation", "none") } }
}

/** The quiet pixel night behind every page; see the note at the top of this file. */
@Composable
fun AnimatedBackground() {
    Div(PixelBackdropStyle.toAttrs { attr("aria-hidden", "true") }) {
        Div(PixelStarsFarStyle.toAttrs())
        Div(PixelTwinkleStyle.toAttrs()) { Div(PixelStarsNearStyle.toAttrs()) }
        Div(PixelGridStyle.toAttrs())
        Div(PixelShootingStarStyle.toAttrs())
    }
}
