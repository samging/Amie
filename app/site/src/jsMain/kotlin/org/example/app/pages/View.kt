package org.example.app.pages

import androidx.compose.runtime.*
import androidx.compose.runtime.NoLiveLiterals
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import kotlinx.browser.window
import kotlinx.coroutines.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import kotlin.js.Json
import com.varabyte.kobweb.compose.css.Overflow
import com.varabyte.kobweb.compose.ui.graphics.Color
import io.ktor.http.Url
import org.w3c.dom.Element

@JsModule("highlight.js")
@JsNonModule
external object hljs {
    fun highlightElement(element: Element)
}

@Composable
fun HighlightedCode(code: String, language: String? = null) {
    var codeElement by remember { mutableStateOf<Element?>(null) }
    
    // Explicitly re-trigger highlighting when code or element changes
    LaunchedEffect(code, codeElement) {
        val element = codeElement
        if (element != null && code.isNotEmpty()) {
            console.log("--- [HighlightedCode: LaunchedEffect] START ---")
            console.log("DEBUG: Highlighting element for language: $language")
            try {
                // Ensure the DOM has settled before highlighting
                delay(50)
                hljs.highlightElement(element)
                console.log("DEBUG: highlightElement called successfully")
            } catch (e: Exception) {
                console.error("ERROR: Highlight.js error: ${e.message}")
            }
            console.log("--- [HighlightedCode: LaunchedEffect] END ---")
        }
    }

    Pre(attrs = Modifier
        .margin(0.px)
        .fillMaxWidth()
        .toAttrs()
    ) {
        Code(attrs = {
            if (language != null) classes("language-$language")
            ref { element ->
                codeElement = element
                onDispose { codeElement = null }
            }
        }) {
            Text(code)
        }
    }
}

@NoLiveLiterals
@Page("view")
@Composable
fun ViewPage() {
    val ctx = rememberPageContext()
    println("CTX: ${ctx.route}")
    println("CTX-PARAMS: ${ctx.route.params}")
    val urlCtx = Url("${ctx.route}")
    println("URL-PACKAGE: ${urlCtx.parameters["url"]}")

    val packageName = urlCtx.parameters["package"] ?: ""
    val username = urlCtx.parameters["username"] ?: ""

    val from = urlCtx.parameters["from"] ?: ""
    val directUrl = urlCtx.parameters["url"] ?: ""

    println("DEBUG: Route Params - package: '$packageName', username: '$username', from: '$from', url: '$directUrl' ")


    var packageData by remember { mutableStateOf<Json?>(null) }
    var rawTextContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val abortController = remember { js("new AbortController()") }
    var deferredJob by remember { mutableStateOf<Deferred<Unit>?>(null) }

    LaunchedEffect(directUrl) {
        if (directUrl.isNotEmpty()) {
            val job = async {
                println("--- [ViewPage: LaunchedEffect] START ---")
                println("DEBUG: Route Params - package: '$packageName', username: '$username', url: '$directUrl'")
                isLoading = true
                error = null

                try {
                    val encodedPath = packageName.split("/").joinToString("/") {
                        js("encodeURIComponent")(it) as String 
                    }
                    println("Encoded path: $encodedPath")

                    val options = js("{}")
                    options["signal"] = abortController.signal
                    
                    val token = window.localStorage.getItem("token")
                    if (token != null) {
                        val headers = js("{}")
                        headers["Authorization"] = "Bearer $token"
                        options["headers"] = headers
                        println("DEBUG: Auth token found and attached to headers")
                    } else {
                        println("DEBUG: No auth token found in localStorage")
                    }
                    
                    val apiCallUrl = "/api/view?package=$encodedPath&username=$username"

                    println("DEBUG: Fetching from API: $apiCallUrl")
                    val response = window.fetch(apiCallUrl, options).await()
                    println("DEBUG: API Fetch Status: ${response.status} ${response.statusText}")
                    
                    if (response.ok) {
                        val responseText = response.text().await()
                        println("DEBUG: API Response received (Length: ${responseText.length})")
                        val json = JSON.parse<Json>(responseText)
                        packageData = json
                        
                        val base64Content = json["content"] as? String
                        if (base64Content != null && base64Content.isNotEmpty()) {
                            try {
                                println("DEBUG: Found base64 content in API response, decoding...")
                                val sanitized = base64Content.replace(Regex("\\s+"), "")
                                val binaryString = window.atob(sanitized)
                                val len = binaryString.length
                                val bytes = js("new Uint8Array(len)")
                                for (i in 0 until len) {
                                    bytes[i] = binaryString[i].code
                                }
                                val decoder = js("new TextDecoder()")
                                rawTextContent = decoder.decode(bytes) as String
                                println("DEBUG: Content decoded successfully (Length: ${rawTextContent?.length})")
                            } catch (e: Exception) {
                                console.error("ERROR: Base64 decoding failed: ${e.message}")
                            }
                        } else {
                            println("DEBUG: No content found in API response metadata")
                        }
                    } else {
                        console.warn("DEBUG: API fetch failed with status ${response.status}")
                    }

                    if (rawTextContent == null && directUrl.isNotEmpty()) {
                        println("DEBUG: rawTextContent is null, trying direct fetch from directUrl: $directUrl")
                        try {
                            val directResponse = window.fetch(directUrl, js("{ signal: abortController.signal }")).await()
                            println("DEBUG: Direct Fetch Status: ${directResponse.status}")
                            if (directResponse.ok) {
                                rawTextContent = directResponse.text().await()
                                println("DEBUG: Content loaded from direct URL (Length: ${rawTextContent?.length})")
                            } else {
                                console.warn("DEBUG: Direct fetch failed with status ${directResponse.status}")
                            }
                        } catch (e: Exception) {
                            console.warn("DEBUG: Direct fetch error: ${e.message}")
                        }
                    }

                    if (rawTextContent == null && error == null) {
                        println("DEBUG: Content could not be loaded from API or direct URL")
                        error = "Unable to load file content. The repository might be private or the file is missing."
                    }

                } catch (e: Exception) {
                    if (e is CancellationException || e.asDynamic().name == "AbortError") {
                        println("DEBUG: Fetch operation cancelled")
                        throw e
                    }
                    console.error("ERROR: Failed to load package details: ${e.message}")
                    error = "Failed to load package details: ${e.message}"
                } finally {
                    isLoading = false
                    println("--- [ViewPage: LaunchedEffect] END ---")
                }
            }
            
            deferredJob = job
            try {
                job.await()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            println("DEBUG: ViewPage disposing, aborting fetch...")
            abortController.abort()
        }
    }

    Div(Modifier.padding(24.px).toAttrs()) {
        H1 { Text("Package Details") }
        
        Div(Modifier.margin(bottom = 20.px).toAttrs()) {
            Button(Modifier.margin(right = 10.px).toAttrs({
                onClick { 
                    println("DEBUG: Back button clicked, navigating back to $from")
                    abortController.abort()
                    deferredJob?.cancel()
                    
                    ctx.router.navigateTo("/")
                }
            })) {
                Text("← Back")
            }
        }

        if (isLoading && rawTextContent == null) {
            P { Text("Loading details for $packageName...") }
        } else if (error != null && rawTextContent == null) {
            P(Modifier.color(org.jetbrains.compose.web.css.Color.red).toAttrs()) {
                Text(error!!)
            }
        } else {
            val name = packageData?.get("name") as? String ?: packageName.substringAfterLast("/")
            val sizeInBytes = (packageData?.get("size") as? Number)?.toDouble() ?: 0.0
            val sizeDisplay = if (sizeInBytes > 0) {
                if (sizeInBytes > 1024 * 1024) {
                    "${(sizeInBytes / (1024.0 * 1024.0)).asDynamic().toFixed(2)} MB"
                } else {
                    "${(sizeInBytes / 1024.0).asDynamic().toFixed(2)} KB"
                }
            } else "Unknown"
            
            val downloadUrl = (packageData?.get("download_url") as? String) ?: directUrl

            Div {
                P { B { Text("Name: ") }; Text(name) }
                P { B { Text("Size: ") }; Text(sizeDisplay) }
                
                if (rawTextContent != null) {
                    println("DEBUG: Rendering content in Highlighting viewer")
                    H3 { Text("File Content:") }
                    Div(
                        Modifier
                            .fillMaxWidth()
                            .height(600.px)
                            .overflow(Overflow.Auto)
                            .padding(12.px)
                            .backgroundColor(Color.rgb(13, 17, 23)) // GitHub dark background color
                            .border(1.px, LineStyle.Solid, org.jetbrains.compose.web.css.Color.lightgray)
                            .borderRadius(4.px)
                            .toAttrs()
                    ) {
                        val extension = name.substringAfterLast(".", "")
                        val lang = when (extension.lowercase()) {
                            "py" -> "python"
                            "kt" -> "kotlin"
                            "js" -> "javascript"
                            "ts" -> "typescript"
                            "html" -> "xml"
                            "css" -> "css"
                            "json" -> "json"
                            "md" -> "markdown"
                            "c" -> "c"
                            "cpp" -> "cpp"
                            "h" -> "c"
                            "sh" -> "bash"
                            "yaml" -> "yaml"
                            "yml" -> "yaml"
                            "xml" -> "xml"
                            else -> null
                        }
                        
                        HighlightedCode(rawTextContent!!, lang)
                    }
                } else if (sizeInBytes > 1024 * 1024) {
                    println("DEBUG: Content too large to display (>1MB)")
                    P(Modifier.color(org.jetbrains.compose.web.css.Color.gray).toAttrs()) {
                        Text("Note: Content is too large to display directly (> 1MB). Please use the download link below.")
                    }
                } else if (isLoading) {
                    P { Text("Fetching content...") }
                }

                Div(Modifier.margin(top = 20.px).toAttrs()) {
                    if (downloadUrl.isNotEmpty()) {
                        A(href = downloadUrl, attrs = Modifier.margin(right = 10.px).toAttrs()) {
                            Button { Text("Download Raw File") }
                        }
                    }
                }
            }
        }
    }
}
