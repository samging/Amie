package org.example.app.components.widgets

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.animation.Keyframes
import com.varabyte.kobweb.silk.style.animation.toAnimation
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.compose.css.AnimationIterationCount
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.w3c.dom.HTMLImageElement

/**
 * Character art shown around the site (NEKOPARA's Vanilla and Shigure, linked from their hosts). When an
 * image cannot be loaded, the drawn SVG mascot takes its place.
 */
val CharacterArt = listOf(
    "https://www.pngkey.com/png/full/794-7946587_nekopara-vanilla-png-anime-neko-full-body.png",
    "https://www.pngkey.com/png/full/46-468525_shigure-maid-nekopara-shigure-cosplay.png",
    "https://static.wikia.nocookie.net/nekopara/images/d/d5/Vanilla.png/revision/latest?cb=20260515045408",
)

/** The mascot bobs gently, like she is floating. */
val MascotFloat = Keyframes {
    from { Modifier.translateY(0.px) }
    50.percent { Modifier.translateY((-10).px) }
    to { Modifier.translateY(0.px) }
}

val MascotStyle = CssStyle {
    base {
        Modifier
            .display(DisplayStyle.Block)
            .animation(MascotFloat.toAnimation(duration = 4.s, timingFunction = AnimationTimingFunction.EaseInOut, iterationCount = AnimationIterationCount.Infinite))
    }
    cssRule(CSSMediaQuery.MediaFeature("prefers-reduced-motion", StylePropertyValue("reduce"))) {
        Modifier.styleModifier { property("animation", "none") }
    }
}

/**
 * Amie's mascots: original anime-style characters drawn as inline SVG. [variant] picks the girl:
 * 0 is the engineer with the headset and a board, 1 the one with the twin tails and a tablet.
 */
@Composable
fun Mascot(variant: Int = 0, size: Int = 220, modifier: Modifier = Modifier) {
    val art = CharacterArt.getOrNull(variant % CharacterArt.size)
    var failed by remember(variant) { mutableStateOf(false) }
    if (art != null && !failed) {
        Img(art, "Amie mascot", MascotStyle.toModifier().width(size.px).height((size * 1.5).toInt().px)
            .objectFit(com.varabyte.kobweb.compose.css.ObjectFit.Contain)
            .styleModifier { property("filter", "drop-shadow(0 12px 24px rgba(0,0,0,0.45))") }
            .then(modifier).toAttrs {
                attr("loading", "lazy")
                attr("referrerpolicy", "no-referrer")
                ref { element ->
                    (element as HTMLImageElement).asDynamic().onerror = { failed = true }
                    onDispose { }
                }
            })
        return
    }
    val svg = if (variant % 2 == 0) ENGINEER_SVG else NAVIGATOR_SVG
    Div(MascotStyle.toModifier().width(size.px).height((size * 1.15).toInt().px).then(modifier).toAttrs {
        attr("role", "img")
        attr("aria-label", "Amie mascot")
        ref { element ->
            element.innerHTML = svg
            onDispose { }
        }
    })
}

// Hair, eyes and clothes use the palette's blues and the accent yellow so they fit both color modes.
private const val ENGINEER_SVG = """
<svg viewBox="0 0 200 230" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="hairA" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5A7BFF"/><stop offset="1" stop-color="#2B45B8"/></linearGradient>
    <linearGradient id="eyeA" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#7FD3FF"/><stop offset="1" stop-color="#1F5FD6"/></linearGradient>
    <radialGradient id="blush" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#FF9FB3" stop-opacity="0.8"/><stop offset="1" stop-color="#FF9FB3" stop-opacity="0"/></radialGradient>
  </defs>
  <!-- ponytail -->
  <path d="M146 78 C182 70 188 120 170 160 C165 172 150 176 148 162 C156 132 150 108 134 96 Z" fill="url(#hairA)"/>
  <!-- body -->
  <path d="M60 150 C60 128 140 128 140 150 L146 212 L54 212 Z" fill="#1E2A44"/>
  <path d="M86 146 L100 176 L114 146 Z" fill="#F3DB5B"/>
  <path d="M74 154 L100 206 L126 154" fill="none" stroke="#2F7DE1" stroke-width="3" stroke-linejoin="round"/>
  <!-- neck -->
  <rect x="90" y="124" width="20" height="26" rx="8" fill="#FFE1C9"/>
  <!-- face -->
  <path d="M58 86 C58 44 142 44 142 86 C142 118 124 140 100 140 C76 140 58 118 58 86 Z" fill="#FFE8D4"/>
  <!-- hair front -->
  <path d="M54 92 C50 40 150 36 148 92 C140 72 128 66 118 72 C116 60 108 58 100 66 C92 56 80 58 76 72 C66 68 58 78 54 92 Z" fill="url(#hairA)"/>
  <path d="M60 96 C62 78 68 70 76 74 C70 86 70 96 72 106 Z" fill="url(#hairA)"/>
  <path d="M140 96 C138 78 132 70 124 74 C130 86 130 96 128 106 Z" fill="url(#hairA)"/>
  <!-- headset -->
  <path d="M56 92 C50 60 150 60 144 92" fill="none" stroke="#9AA4B2" stroke-width="5" stroke-linecap="round"/>
  <rect x="48" y="86" width="14" height="24" rx="6" fill="#2A313D" stroke="#9AA4B2" stroke-width="2"/>
  <rect x="138" y="86" width="14" height="24" rx="6" fill="#2A313D" stroke="#9AA4B2" stroke-width="2"/>
  <path d="M138 106 C132 124 118 130 108 130" fill="none" stroke="#9AA4B2" stroke-width="3" stroke-linecap="round"/>
  <circle cx="108" cy="130" r="3.5" fill="#F3DB5B"/>
  <!-- eyes -->
  <ellipse cx="82" cy="100" rx="9" ry="12" fill="#fff"/>
  <ellipse cx="118" cy="100" rx="9" ry="12" fill="#fff"/>
  <ellipse cx="83" cy="102" rx="6.5" ry="9.5" fill="url(#eyeA)"/>
  <ellipse cx="119" cy="102" rx="6.5" ry="9.5" fill="url(#eyeA)"/>
  <circle cx="85" cy="98" r="2.4" fill="#fff"/>
  <circle cx="121" cy="98" r="2.4" fill="#fff"/>
  <path d="M72 90 C78 84 88 84 92 88" fill="none" stroke="#2B45B8" stroke-width="2.5" stroke-linecap="round"/>
  <path d="M108 88 C112 84 122 84 128 90" fill="none" stroke="#2B45B8" stroke-width="2.5" stroke-linecap="round"/>
  <!-- blush and mouth -->
  <circle cx="74" cy="114" r="9" fill="url(#blush)"/>
  <circle cx="126" cy="114" r="9" fill="url(#blush)"/>
  <path d="M94 120 C98 125 102 125 106 120" fill="none" stroke="#C96A7A" stroke-width="2" stroke-linecap="round"/>
  <!-- arm and board -->
  <path d="M138 160 C156 168 160 186 150 196" fill="none" stroke="#FFE1C9" stroke-width="12" stroke-linecap="round"/>
  <g transform="translate(118 180) rotate(-12)">
    <rect x="0" y="0" width="52" height="34" rx="4" fill="#0F6E4F" stroke="#063D2C" stroke-width="2"/>
    <rect x="6" y="6" width="14" height="10" rx="1" fill="#2A313D"/>
    <rect x="26" y="5" width="20" height="6" rx="1" fill="#C8C8C8"/>
    <circle cx="10" cy="26" r="3" fill="#F3DB5B"/>
    <rect x="22" y="22" width="24" height="7" rx="1" fill="#C8C8C8"/>
  </g>
</svg>
"""

private const val NAVIGATOR_SVG = """
<svg viewBox="0 0 200 230" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="hairB" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F7B7D2"/><stop offset="1" stop-color="#C96A9E"/></linearGradient>
    <linearGradient id="eyeB" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFE27A"/><stop offset="1" stop-color="#C98A00"/></linearGradient>
    <radialGradient id="blushB" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#FF9FB3" stop-opacity="0.8"/><stop offset="1" stop-color="#FF9FB3" stop-opacity="0"/></radialGradient>
  </defs>
  <!-- twin tails -->
  <path d="M56 80 C24 90 18 140 36 172 C42 182 56 180 54 166 C44 140 48 112 64 98 Z" fill="url(#hairB)"/>
  <path d="M144 80 C176 90 182 140 164 172 C158 182 144 180 146 166 C156 140 152 112 136 98 Z" fill="url(#hairB)"/>
  <!-- body -->
  <path d="M60 150 C60 128 140 128 140 150 L146 212 L54 212 Z" fill="#2F7DE1"/>
  <path d="M60 150 C60 128 140 128 140 150 L140 160 L60 160 Z" fill="#1E2A44"/>
  <circle cx="100" cy="172" r="4" fill="#F3DB5B"/>
  <circle cx="100" cy="190" r="4" fill="#F3DB5B"/>
  <!-- neck -->
  <rect x="90" y="124" width="20" height="26" rx="8" fill="#FFE1C9"/>
  <!-- face -->
  <path d="M58 86 C58 44 142 44 142 86 C142 118 124 140 100 140 C76 140 58 118 58 86 Z" fill="#FFE8D4"/>
  <!-- hair front -->
  <path d="M54 92 C50 40 150 36 148 92 C142 70 130 64 120 70 C112 60 104 62 100 70 C94 60 82 60 78 70 C68 66 58 76 54 92 Z" fill="url(#hairB)"/>
  <path d="M60 96 C62 78 68 70 76 74 C70 86 70 96 72 106 Z" fill="url(#hairB)"/>
  <path d="M140 96 C138 78 132 70 124 74 C130 86 130 96 128 106 Z" fill="url(#hairB)"/>
  <!-- ribbons -->
  <path d="M52 80 L40 70 L46 86 L34 90 Z" fill="#2F7DE1"/>
  <path d="M148 80 L160 70 L154 86 L166 90 Z" fill="#2F7DE1"/>
  <!-- eyes -->
  <ellipse cx="82" cy="100" rx="9" ry="12" fill="#fff"/>
  <ellipse cx="118" cy="100" rx="9" ry="12" fill="#fff"/>
  <ellipse cx="83" cy="102" rx="6.5" ry="9.5" fill="url(#eyeB)"/>
  <ellipse cx="119" cy="102" rx="6.5" ry="9.5" fill="url(#eyeB)"/>
  <circle cx="85" cy="98" r="2.4" fill="#fff"/>
  <circle cx="121" cy="98" r="2.4" fill="#fff"/>
  <path d="M72 90 C78 84 88 84 92 88" fill="none" stroke="#A54C7E" stroke-width="2.5" stroke-linecap="round"/>
  <path d="M108 88 C112 84 122 84 128 90" fill="none" stroke="#A54C7E" stroke-width="2.5" stroke-linecap="round"/>
  <!-- blush and smile -->
  <circle cx="74" cy="114" r="9" fill="url(#blushB)"/>
  <circle cx="126" cy="114" r="9" fill="url(#blushB)"/>
  <path d="M92 119 C97 126 103 126 108 119" fill="none" stroke="#C96A7A" stroke-width="2" stroke-linecap="round"/>
  <!-- tablet -->
  <path d="M62 162 C48 172 46 190 58 198" fill="none" stroke="#FFE1C9" stroke-width="12" stroke-linecap="round"/>
  <g transform="translate(40 176) rotate(8)">
    <rect x="0" y="0" width="54" height="36" rx="5" fill="#161B24" stroke="#9AA4B2" stroke-width="2"/>
    <rect x="6" y="6" width="42" height="24" rx="2" fill="#0D1117"/>
    <path d="M10 24 L18 16 L26 22 L34 12 L44 20" fill="none" stroke="#5A7BFF" stroke-width="2"/>
    <circle cx="44" cy="20" r="2" fill="#F3DB5B"/>
  </g>
</svg>
"""
