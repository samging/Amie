package org.example.app.components.layouts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.ColumnScope
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.animation.Keyframes
import com.varabyte.kobweb.silk.style.animation.toAnimation
import com.varabyte.kobweb.compose.ui.styleModifier
import org.jetbrains.compose.web.css.*
import com.varabyte.kobweb.silk.style.breakpoint.Breakpoint
import com.varabyte.kobweb.silk.style.toAttrs
import kotlinx.browser.document
import org.example.app.components.sections.AnimatedBackground
import org.example.app.components.sections.Footer
import org.example.app.components.sections.NavHeader
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.fr
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.dom.Div

val FadeInUp = Keyframes {
    from { Modifier.opacity(0).translateY(14.px) }
    to { Modifier.opacity(1).translateY(0.px) }
}

val PageContentStyle = CssStyle {
    base {
        Modifier.fillMaxSize().padding(leftRight = 1.cssRem, top = 2.cssRem, bottom = 3.cssRem)
            .animation(FadeInUp.toAnimation(duration = 500.ms, timingFunction = AnimationTimingFunction.EaseOut))
    }
    cssRule(CSSMediaQuery.MediaFeature("prefers-reduced-motion", StylePropertyValue("reduce"))) {
        Modifier.styleModifier { property("animation", "none") }
    }
    Breakpoint.MD { Modifier.maxWidth(64.cssRem).padding(leftRight = 2.cssRem, top = 2.5.cssRem, bottom = 4.cssRem) }
}

/** Every page: the header with navigation, the content column, the footer. */
@Composable
fun PageLayout(title: String, content: @Composable ColumnScope.() -> Unit) {
    LaunchedEffect(title) {
        document.title = "Amie – $title"
    }

    AnimatedBackground()
    Box(
        Modifier
            .fillMaxWidth()
            .minHeight(100.vh)
            .gridTemplateRows { size(1.fr); size(minContent) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxSize().gridRow(1),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NavHeader()
            Div(PageContentStyle.toAttrs()) {
                Column(Modifier.fillMaxWidth()) { content() }
            }
        }
        Footer(Modifier.fillMaxWidth().gridRow(2))
    }
}
