package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.Overflow
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import io.ktor.http.Url
import kotlinx.browser.window
import kotlinx.coroutines.delay
import org.example.app.components.ClientCache
import org.example.app.components.Http
import org.example.app.components.layouts.PageLayout
import org.example.app.components.widgets.*
import org.example.app.config.AmieConfig
import org.example.app.config.encodeURIComponent
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.Element
import kotlin.js.Json

@JsModule("highlight.js")
@JsNonModule
external object hljs {
    fun highlightElement(element: Element)
}

@Composable
fun HighlightedCode(code: String, language: String? = null) {
    var codeElement by remember { mutableStateOf<Element?>(null) }
    LaunchedEffect(code, codeElement) {
        val element = codeElement ?: return@LaunchedEffect
        if (code.isEmpty()) return@LaunchedEffect
        delay(50)
        runCatching { hljs.highlightElement(element) }
    }
    Pre(Modifier.margin(0.px).fillMaxWidth().toAttrs()) {
        Code(attrs = {
            if (language != null) classes("language-$language")
            ref { element ->
                codeElement = element
                onDispose { codeElement = null }
            }
        }) { Text(code) }
    }
}

private fun languageOf(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
    "py" -> "python"; "kt", "kts" -> "kotlin"; "js" -> "javascript"; "ts" -> "typescript"
    "html", "xml" -> "xml"; "css" -> "css"; "json" -> "json"; "md" -> "markdown"
    "c", "h" -> "c"; "cpp", "ino" -> "cpp"; "sh" -> "bash"; "yaml", "yml" -> "yaml"
    else -> null
}

private fun decodeBase64(content: String): String? = runCatching {
    val binary = window.atob(content.replace(Regex("\\s+"), ""))
    val bytes = js("new Uint8Array(binary.length)")
    for (i in 0 until binary.length) bytes[i] = binary[i].code
    js("new TextDecoder()").decode(bytes) as String
}.getOrNull()

/** One package file: its details and, for text files, its content with syntax highlighting. */
@NoLiveLiterals
@Page("view")
@Composable
fun ViewPage() {
    val ctx = rememberPageContext()
    val params = Url(window.location.href).parameters
    val packagePath = params["package"] ?: ""
    val username = params["username"] ?: ""
    val directUrl = params["url"] ?: ""

    var metadata by remember { mutableStateOf<Json?>(null) }
    var content by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(packagePath) {
        if (packagePath.isEmpty()) {
            loading = false
            error = "No file was given."
            return@LaunchedEffect
        }
        try {
            val encodedPath = packagePath.split("/").joinToString("/") { encodeURIComponent(it) }
            val text = ClientCache.cached("packages:view:$packagePath", 5 * ClientCache.MINUTE) {
                val result = Http.get(AmieConfig.api("view?package=$encodedPath"))
                if (!result.ok) throw IllegalStateException()
                result.text
            }
            val json = JSON.parse<Json>(text)
            metadata = json
            content = (json["content"] as? String)?.takeIf { it.isNotBlank() }?.let { decodeBase64(it) }
            if (content == null && directUrl.isNotEmpty()) {
                content = runCatching { Http.get(directUrl).takeIf { it.ok }?.text }.getOrNull()
            }
        } catch (e: Throwable) {
            error = "This file could not be loaded."
        } finally {
            loading = false
        }
    }

    val name = metadata?.get("name") as? String ?: packagePath.substringAfterLast("/")
    val size = (metadata?.get("size") as? Number)?.toDouble() ?: 0.0
    val downloadUrl = (metadata?.get("download_url") as? String) ?: directUrl
    val isBinary = name.endsWith(".jar", true) || name.endsWith(".onnx", true) || name.endsWith(".bin", true)

    PageLayout(name.ifEmpty { "Package" }) {
        PageHeading(name.ifEmpty { "Package" }, listOfNotNull(username.takeIf { it.isNotEmpty() }?.let { "by $it" }, formatSize(size).takeIf { size > 0 }).joinToString(" · ")) {
            if (username.isNotEmpty()) LinkButton("All packages by $username", "/dashboard?username=${encodeURIComponent(username)}", primary = false)
            if (downloadUrl.isNotEmpty()) LinkButton("Download", downloadUrl, download = true)
        }

        when {
            loading -> EmptyState("Loading…")
            error != null -> StatusBanner(error, BannerKind.ERROR)
            content != null && !isBinary -> Div(
                Modifier.fillMaxWidth().maxHeight(40.cssRem).overflow(Overflow.Auto).padding(0.75.cssRem)
                    .backgroundColor(Color.rgb(13, 17, 23)).borderRadius(12.px).toAttrs()
            ) { HighlightedCode(content!!, languageOf(name)) }
            isBinary -> Card { MutedText("This is a binary package. Use the download button to get it.") }
            size > 1024 * 1024 -> Card { MutedText("This file is too large to show here. Use the download button to get it.") }
            else -> Card { MutedText("There is no preview for this file.") }
        }
    }
}
