package org.example.app.components.widgets

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import kotlinx.coroutines.await
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Img
import kotlin.js.Promise

@JsModule("qrcode")
@JsNonModule
private external object QRCodeLib {
    fun toDataURL(text: String, options: dynamic = definedExternally): Promise<String>
}

private val rendered = HashMap<String, String>()

/** Renders [text] as a QR code image; each distinct text is rendered once per page load. */
@Composable
fun QrCode(text: String, size: Int = 220, modifier: Modifier = Modifier) {
    var dataUrl by remember(text) { mutableStateOf(rendered[text]) }
    LaunchedEffect(text) {
        if (dataUrl == null) {
            val options = js("{}")
            options.width = size
            options.margin = 1
            options.errorCorrectionLevel = "M"
            dataUrl = runCatching { QRCodeLib.toDataURL(text, options).await() }.getOrNull()?.also { rendered[text] = it }
        }
    }
    dataUrl?.let { url ->
        Img(url, "QR code", Modifier.size(size.px).borderRadius(12.px).then(modifier).toAttrs())
    }
}
