package org.example.app.components.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.animation.Keyframes
import com.varabyte.kobweb.silk.style.animation.toAnimation
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.toAttrs
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.browser.window
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div

/** Short confirmations that appear in a corner and leave by themselves ("Link copied"). */
object Toasts {
    class Toast(val id: Int, val message: String, val kind: BannerKind)

    val items = mutableStateListOf<Toast>()
    private var nextId = 0

    fun show(message: String, kind: BannerKind = BannerKind.INFO, durationMs: Int = 2800) {
        val toast = Toast(nextId++, message, kind)
        items.add(toast)
        while (items.size > 3) items.removeAt(0)
        window.setTimeout({ items.remove(toast) }, durationMs)
    }
}

val ToastIn = Keyframes {
    from { Modifier.opacity(0).translateY(8.px) }
    to { Modifier.opacity(1).translateY(0.px) }
}

val ToastHostStyle = CssStyle.base {
    Modifier
        .position(Position.Fixed).bottom(1.25.cssRem).right(1.25.cssRem).zIndex(60)
        .display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).gap(0.5.cssRem)
        .maxWidth(22.cssRem)
        .styleModifier { property("pointer-events", "none") }
}

val ToastStyle = CssStyle {
    base {
        Modifier
            .animation(ToastIn.toAnimation(duration = 180.ms, timingFunction = AnimationTimingFunction.EaseOut))
            .boxShadow(4.px, 4.px, 0.px, 0.px, Colors.White)
    }
    cssRule(CSSMediaQuery.MediaFeature("prefers-reduced-motion", StylePropertyValue("reduce"))) {
        Modifier.styleModifier { property("animation", "none") }
    }
}

@Composable
fun ToastHost() {
    if (Toasts.items.isEmpty()) return
    Div(ToastHostStyle.toAttrs()) {
        for (toast in Toasts.items) {
            key(toast.id) {
                StatusBanner(toast.message, toast.kind, ToastStyle.toModifier())
            }
        }
    }
}
