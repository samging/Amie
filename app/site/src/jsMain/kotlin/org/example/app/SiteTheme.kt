package org.example.app

import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.silk.init.InitSilk
import com.varabyte.kobweb.silk.init.InitSilkContext
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.silk.theme.colors.palette.background
import com.varabyte.kobweb.silk.theme.colors.palette.color

/**
 * The site's colors beyond Silk's background and text color.
 *
 * @property nearBackground A surface that stands out from the page background just a little (cards, the header).
 * @property border Hairlines around cards, table rows and inputs.
 * @property muted Secondary text.
 */
class SitePalette(
    val nearBackground: Color,
    val border: Color,
    val muted: Color,
    val success: Color,
    val successBackground: Color,
    val danger: Color,
    val dangerBackground: Color,
    val info: Color,
    val infoBackground: Color,
    val brand: Brand,
) {
    class Brand(
        val primary: Color = Color.rgb(0x2F7DE1),
        val primaryHover: Color = Color.rgb(0x2567BD),
        val accent: Color = Color.rgb(0xF3B23C),
        val onPrimary: Color = Color.rgb(0xFFFFFF),
    )
}

object SitePalettes {
    val light = SitePalette(
        nearBackground = Color.rgb(0xF4F6FA),
        border = Color.rgb(0xDDE2EA),
        muted = Color.rgb(0x5B6472),
        success = Color.rgb(0x1E7A46),
        successBackground = Color.rgb(0xE3F5EA),
        danger = Color.rgb(0xB42318),
        dangerBackground = Color.rgb(0xFCE8E6),
        info = Color.rgb(0x1F4E99),
        infoBackground = Color.rgb(0xE6EEFB),
        brand = SitePalette.Brand()
    )
    val dark = SitePalette(
        nearBackground = Color.rgb(0x101010),
        border = Color.rgb(0xF2F2F2),
        muted = Color.rgb(0xB8B8B8),
        success = Color.rgb(0xFFFFFF),
        successBackground = Color.rgb(0x1C1C1C),
        danger = Color.rgb(0xFFFFFF),
        dangerBackground = Color.rgb(0x1C1C1C),
        info = Color.rgb(0xFFFFFF),
        infoBackground = Color.rgb(0x1C1C1C),
        brand = SitePalette.Brand(
            primary = Color.rgb(0xFFFFFF),
            primaryHover = Color.rgb(0xD9D9D9),
            accent = Color.rgb(0xFFFFFF),
            onPrimary = Color.rgb(0x000000),
        )
    )
}

fun ColorMode.toSitePalette(): SitePalette = when (this) {
    ColorMode.LIGHT -> SitePalettes.light
    ColorMode.DARK -> SitePalettes.dark
}

@InitSilk
fun initTheme(ctx: InitSilkContext) {
    ctx.theme.palettes.light.background = Color.rgb(0xFAFAFA)
    ctx.theme.palettes.light.color = Color.rgb(0x151A22)
    ctx.theme.palettes.dark.background = Color.rgb(0x0A0A0A)
    ctx.theme.palettes.dark.color = Color.rgb(0xFFFFFF)
}
