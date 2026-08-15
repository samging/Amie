package org.example.app.pages

import androidx.compose.runtime.*
import androidx.compose.runtime.NoLiveLiterals
import com.varabyte.kobweb.browser.api
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
import com.varabyte.kobweb.compose.ui.graphics.Colors

@NoLiveLiterals
@Page("view")
@Composable
fun ViewPage() {
    val ctx = rememberPageContext()
    val packageName = ctx.route.params["package"] ?: ""
    val username = ctx.route.params["username"] ?: ""
    val from = ctx.route.params["from"] ?: ""
    
    var packageData by remember { mutableStateOf<Json?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Use AbortController to kill the request if the user navigates back
    val abortController = remember { js("new AbortController()") }
    var deferredJob by remember { mutableStateOf<Deferred<Unit>?>(null) }

    LaunchedEffect(packageName) {
        if (packageName.isNotEmpty()) {
            val job = async {
                console.log("DEBUG: Fetching details for $packageName...")
                isLoading = true
                try {
                    val encodedPath = js("encodeURIComponent")(packageName) as String
                    
                    val options = js("{}")
                    options["signal"] = abortController.signal
                    
                    val response = window.fetch("/api/view?package=$encodedPath&username=$username", options).await()
                    if (response.ok) {
                        val responseText = response.text().await()
                        packageData = JSON.parse<Json>(responseText)
                    } else {
                        error = "Failed to load package details: ${response.statusText}"
                    }
                } catch (e: Exception) {
                    // Rethrow cancellation related exceptions to be caught by the outer try-catch
                    if (e is CancellationException || e.asDynamic().name == "AbortError") {
                        throw e
                    }
                    error = "Failed to load package details: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
            
            deferredJob = job
            
            try {
                console.log("DEBUG: Waiting for job to complete...")
                job.await()
                console.log("DEBUG: Job completed successfully.")
            } catch (e: Exception) {
                console.log("DEBUG: Caught exception in job.await(): ${e::class.simpleName} - ${e.message}")
                withContext(NonCancellable) {
                    console.log("DEBUG: Cancellation detected. Navigating back to $from...")
                    if (from == "aboutss") {
                        ctx.router.navigateTo("/aboutss")
                    } else {
                        ctx.router.navigateTo("/dashboard?username=$username")
                    }
                }
            } finally {
                isLoading = false
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            abortController.abort()
        }
    }

    Div(Modifier.padding(24.px).toAttrs()) {
        H1 { Text("Package Details") }
        
        Div(Modifier.margin(bottom = 20.px).toAttrs()) {
            Button(Modifier.margin(right = 10.px).toAttrs({
                onClick { 
                    console.log("DEBUG: Back button clicked. Aborting fetch and cancelling coroutine.")
                    abortController.abort() // Signal the fetch to stop
                    
                    if (deferredJob != null) {
                        console.log("DEBUG: Cancelling deferred job...")
                        deferredJob?.cancel() // This should trigger the catch block in LaunchedEffect
                    }
                    
                    // Always try to navigate directly as well to avoid being "frozen"
                    console.log("DEBUG: Performing immediate navigation as fallback.")
                    if (from == "aboutss") {
                        ctx.router.navigateTo("/aboutss")
                    } else {
                        ctx.router.navigateTo("/dashboard?username=$username")
                    }
                }
            })) {
                Text("← Back")
            }
        }

        if (isLoading) {
            P { Text("Loading details for $packageName...") }
        } else if (error != null) {
            P(Modifier.color(org.jetbrains.compose.web.css.Color.red).toAttrs()) {
                Text(error!!)
            }
        } else if (packageData != null) {
            val name = packageData!!["name"] as? String ?: "Unknown"
            val sizeInBytes = (packageData!!["size"] as? Number)?.toDouble() ?: 0.0
            val sizeDisplay = if (sizeInBytes > 1024 * 1024) {
                "${(sizeInBytes / (1024.0 * 1024.0)).asDynamic().toFixed(2)} MB"
            } else {
                "${(sizeInBytes / 1024.0).asDynamic().toFixed(2)} KB"
            }
            
            val downloadUrl = packageData!!["download_url"] as? String ?: ""
            val htmlUrl = packageData!!["html_url"] as? String ?: ""
            val base64Content = packageData!!["content"] as? String

            Div {
                P { B { Text("Name: ") }; Text(name) }
                P { B { Text("Size: ") }; Text(sizeDisplay) }
                
                if (base64Content != null) {
                    H3 { Text("File Content:") }
                    Div(
                        Modifier
                            .fillMaxWidth()
                            .height(400.px)
                            .overflow(Overflow.Auto)
                            .padding(12.px)
                            .backgroundColor(Colors.WhiteSmoke)
                            .border(1.px, LineStyle.Solid, org.jetbrains.compose.web.css.Color.lightgray)
                            .borderRadius(4.px)
                            .toAttrs()
                    ) {
                        Pre(attrs = Modifier.margin(0.px).toAttrs()) {
                            val decoded = try {
                                // GitHub content often has newlines in base64, remove them
                                val sanitized = base64Content.replace("\n", "").replace("\r", "")
                                val binaryString = window.atob(sanitized)
                                val len = binaryString.length
                                
                                // Create the buffer using direct JS calls to avoid variable mangling issues
                                val bytes = js("new Uint8Array(len)")
                                for (i in 0 until len) {
                                    bytes[i] = binaryString[i].code
                                }
                                val decoder = js("new TextDecoder()")
                                decoder.decode(bytes) as String
                            } catch (e: Exception) {
                                "Unable to decode content (likely binary or non-text format)"
                            }
                            Text(decoded)
                        }
                    }
                } else if (sizeInBytes > 1024 * 1024) {
                    P(Modifier.color(org.jetbrains.compose.web.css.Color.gray).toAttrs()) {
                        Text("Note: Content is too large to display directly (> 1MB). Please use the download link below.")
                    }
                }

                Div(Modifier.margin(top = 20.px).toAttrs()) {
                    if (downloadUrl.isNotEmpty()) {
                        A(href = downloadUrl, attrs = Modifier.margin(right = 10.px).toAttrs()) {
                            Button { Text("Download Binary") }
                        }
                    }
                }
            }
        }
    }
}
