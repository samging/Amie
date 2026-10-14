package org.example.app.components.sections

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.css.AnimationIterationCount
import com.varabyte.kobweb.compose.css.functions.radialGradient
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.animation.Keyframes
import com.varabyte.kobweb.silk.style.animation.toAnimation
import com.varabyte.kobweb.silk.style.toAttrs
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div

val DriftA = Keyframes {
    from { Modifier.translate(0.px, 0.px).scale(1.0) }
    33.percent { Modifier.translate(120.px, (-60).px).scale(1.15) }
    66.percent { Modifier.translate((-60).px, 90.px).scale(0.95) }
    to { Modifier.translate(0.px, 0.px).scale(1.0) }
}

val DriftB = Keyframes {
    from { Modifier.translate(0.px, 0.px).scale(1.0) }
    40.percent { Modifier.translate((-140).px, 70.px).scale(1.1) }
    70.percent { Modifier.translate(80.px, (-80).px).scale(0.9) }
    to { Modifier.translate(0.px, 0.px).scale(1.0) }
}

val Aurora = Keyframes {
    from { Modifier.styleModifier { property("background-position", "0% 50%") } }
    50.percent { Modifier.styleModifier { property("background-position", "100% 50%") } }
    to { Modifier.styleModifier { property("background-position", "0% 50%") } }
}

val AuroraStyle = CssStyle {
    base {
        Modifier
            .position(Position.Absolute)
            .top(0.px).left(0.px)
            .width(100.percent).height(100.percent)
            .styleModifier {
                property("background", "linear-gradient(120deg, rgba(47,125,225,0.16), rgba(201,106,158,0.12), rgba(243,219,91,0.08), rgba(47,125,225,0.16))")
                property("background-size", "300% 300%")
            }
            .animation(Aurora.toAnimation(duration = 24.s, timingFunction = AnimationTimingFunction.EaseInOut, iterationCount = AnimationIterationCount.Infinite))
    }
    cssRule(CSSMediaQuery.MediaFeature("prefers-reduced-motion", StylePropertyValue("reduce"))) {
        Modifier.styleModifier { property("animation", "none") }
    }
}

val Twinkle = Keyframes {
    from { Modifier.opacity(0.25) }
    50.percent { Modifier.opacity(0.7) }
    to { Modifier.opacity(0.25) }
}

val BackdropStyle = CssStyle {
    base {
        Modifier
            .position(Position.Fixed)
            .top(0.px).left(0.px)
            .width(100.vw).height(100.vh)
            .zIndex(-1)
            .backgroundColor(Color.rgb(0x0A0A0A))
            .overflow(com.varabyte.kobweb.compose.css.Overflow.Hidden)
            .styleModifier { property("pointer-events", "none") }
    }
}

val BlobStyle = CssStyle {
    base {
        Modifier
            .position(Position.Absolute)
            .borderRadius(50.percent)
            .styleModifier { property("filter", "blur(70px)") }
            .styleModifier { property("will-change", "transform") }
    }
    cssRule(CSSMediaQuery.MediaFeature("prefers-reduced-motion", StylePropertyValue("reduce"))) {
        Modifier.styleModifier { property("animation", "none") }
    }
}

val StarsStyle = CssStyle {
    base {
        Modifier
            .position(Position.Absolute)
            .top(0.px).left(0.px)
            .width(100.percent).height(100.percent)
            .styleModifier {
                property(
                    "background-image",
                    "radial-gradient(circle at 12% 18%, rgba(255,255,255,0.35) 0 1.2px, transparent 2px)," +
                        "radial-gradient(circle at 70% 30%, rgba(255,255,255,0.3) 0 1px, transparent 2px)," +
                        "radial-gradient(circle at 40% 75%, rgba(255,255,255,0.3) 0 1.4px, transparent 2px)," +
                        "radial-gradient(circle at 85% 80%, rgba(255,255,255,0.25) 0 1px, transparent 2px)," +
                        "radial-gradient(circle at 25% 50%, rgba(255,255,255,0.3) 0 1px, transparent 2px)," +
                        "radial-gradient(circle at 55% 10%, rgba(255,255,255,0.25) 0 1.2px, transparent 2px)," +
                        "radial-gradient(circle at 92% 45%, rgba(255,255,255,0.3) 0 1px, transparent 2px)"
                )
                property("background-size", "900px 700px")
            }
            .animation(Twinkle.toAnimation(duration = 6.s, timingFunction = AnimationTimingFunction.EaseInOut, iterationCount = AnimationIterationCount.Infinite))
    }
    cssRule(CSSMediaQuery.MediaFeature("prefers-reduced-motion", StylePropertyValue("reduce"))) {
        Modifier.styleModifier { property("animation", "none") }
    }
}

/** Soft drifting color blobs and faint stars behind every page; still on reduced-motion devices. */
@Composable
fun AnimatedBackground() {
    val dark = ColorMode.current.isDark
    val alpha = if (dark) 0.28f else 0.18f
    Div(BackdropStyle.toAttrs()) {
        Div(AuroraStyle.toAttrs())
        if (dark) Div(StarsStyle.toAttrs())
        Div(
            BlobStyle.toModifier()
                .top((-10).percent).left((-10).percent).size(42.vw)
                .backgroundColor(Color.rgb(0x2F7DE1).toRgb().copyf(alpha = alpha))
                .animation(DriftA.toAnimation(duration = 26.s, timingFunction = AnimationTimingFunction.EaseInOut, iterationCount = AnimationIterationCount.Infinite))
                .toAttrs()
        )
        Div(
            BlobStyle.toModifier()
                .top(30.percent).right((-12).percent).size(38.vw)
                .backgroundColor(Color.rgb(0xC96A9E).toRgb().copyf(alpha = alpha))
                .animation(DriftB.toAnimation(duration = 32.s, timingFunction = AnimationTimingFunction.EaseInOut, iterationCount = AnimationIterationCount.Infinite))
                .toAttrs()
        )
        Div(
            BlobStyle.toModifier()
                .bottom((-15).percent).left(25.percent).size(36.vw)
                .backgroundColor(Color.rgb(0xF3DB5B).toRgb().copyf(alpha = alpha * 0.7f))
                .animation(DriftA.toAnimation(duration = 38.s, timingFunction = AnimationTimingFunction.EaseInOut, iterationCount = AnimationIterationCount.Infinite, direction = AnimationDirection.Reverse))
                .toAttrs()
        )
    }
}
