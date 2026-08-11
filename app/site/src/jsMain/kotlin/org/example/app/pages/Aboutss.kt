package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.browser.api
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import kotlinx.browser.window
import kotlinx.coroutines.launch
import kotlinx.coroutines.await
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.dom.*
import kotlin.js.Json

// In-memory cache for Explorer data
private object ExplorerCache {
    var packages: List<Json>? = null
}

@Page
@Composable
fun AboutPaged() {
    var text by remember { mutableStateOf("") }
    var loadedPackages by remember { mutableStateOf<List<Json>>(emptyList()) }
    var loggedInUser by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val ctx = rememberPageContext()

    LaunchedEffect(Unit) {
        isLoading = true
        
        val token = window.localStorage.getItem("auth_token")
        if (token != null) {
            try {
                val headers = js("{}")
                headers["Authorization"] = "Bearer $token"
                val options = js("{}")
                options["headers"] = headers
                
                val response = window.fetch("http://localhost:8080/dashboard", options).await()
                if (response.ok) {
                    val text = response.text().await()
                    loggedInUser = text.substringAfter("dashboard, ").substringBefore(" (ID:")
                }
            } catch (e: Exception) {
                println("Auth check failed in Aboutss: $e")
            }
        }

        try {
            if (ExplorerCache.packages != null) {
                loadedPackages = ExplorerCache.packages!!
                println("DEBUG: Loaded Explorer from cache")
            } else {
                val response = window.fetch("http://localhost:8080/list-github").await()
                if (response.ok) {
                    val json = response.json().await().unsafeCast<Array<Json>>()
                    loadedPackages = json.toList()
                    ExplorerCache.packages = loadedPackages
                }
            }
        } catch (e: Exception) {
            println("Connection failed: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    Div(
        attrs = Modifier.padding(24.px).fillMaxWidth().toAttrs()
    ) {
        Div(Modifier.margin(bottom = 10.px).toAttrs()) {
            Button(attrs = Modifier.toAttrs {
                onClick { ctx.router.navigateTo("/") }
            }) {
                Text("← Back to Home")
            }
        }

        H1 {
            Text("Package Explorer")
        }

        Div(Modifier.margin(bottom = 20.px).toAttrs()) {
            Input(
                type = InputType.Text,
                attrs = Modifier
                    .width(300.px)
                    .padding(10.px)
                    .borderRadius(4.px)
                    .border(1.px, LineStyle.Solid, Color.lightgray)
                    .toAttrs {
                        placeholder("Search for package by name")
                        value(text)
                        onInput { event ->
                            text = event.value
                        }
                    }
            )

            Button(
                attrs = Modifier.margin(left = 10.px).padding(topBottom = 10.px, leftRight = 20.px).toAttrs {
                    onClick {
                        scope.launch {
                            isLoading = true
                            try {
                                val encodedText = js("encodeURIComponent")(text) as String
                                val response = window.fetch("http://localhost:8080/query?query=$encodedText").await()
                                if (response.ok) {
                                    val json = response.json().await()
                                    
                                    if (js("Array.isArray(json)") as Boolean) {
                                        loadedPackages = (json as Array<Json>).toList()
                                    } else if (json != null) {
                                        loadedPackages = listOf(json.unsafeCast<Json>())
                                    } else {
                                        loadedPackages = emptyList()
                                    }
                                    ExplorerCache.packages = loadedPackages // Cache search results too if desired, or set to null
                                }
                            } catch (e: Exception) {
                                println("Search failed: $e")
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }
            ) {
                Text("Search")
            }
        }

        H3 { Text("Available Packages & Authors:") }

        if (isLoading) {
            P { Text("Fetching data...") }
        } else {
            if (loadedPackages.isEmpty()) {
                P { Text("No items found.") }
            } else {
                Table(
                    attrs = Modifier
                        .fillMaxWidth()
                        .border(1.px, LineStyle.Solid, Color.lightgray)
                        .borderRadius(8.px)
                        .toAttrs()
                ) {
                    Thead {
                        Tr {
                            Th(attrs = Modifier.padding(12.px).toAttrs { style { property("text-align", "left") } }) { Text("Name") }
                            Th(attrs = Modifier.padding(12.px).toAttrs { style { property("text-align", "left") } }) { Text("Category") }
                            Th(attrs = Modifier.padding(12.px).toAttrs { style { property("text-align", "left") } }) { Text("Size") }
                            Th(attrs = Modifier.padding(12.px).toAttrs { style { property("text-align", "center") } }) { Text("Actions") }
                        }
                    }
                    Tbody {
                        for (item in loadedPackages) {
                            val rawName = item["name"] as? String ?: "Unknown"
                            val type = item["type"] as? String ?: "file"
                            val isDir = type == "dir"
                            
                            val name = if (isDir) "👤 $rawName (Author)" else "📄 $rawName"
                            val category = if (isDir) "User Repository" else "Binary Package"
                            
                            val path = item["path"] as? String ?: ""
                            val owner = if (isDir) rawName else {
                                // Extract owner from path like uploads/username/...
                                path.substringAfter("uploads/").substringBefore("/")
                            }

                            val sizeInBytes = (item["size"] as? Number)?.toDouble() ?: 0.0
                            val sizeDisplay = if (sizeInBytes > 0) {
                                if (sizeInBytes > 1024 * 1024) {
                                    "${(sizeInBytes / (1024.0 * 1024.0)).asDynamic().toFixed(2)} MB"
                                } else {
                                    "${(sizeInBytes / 1024.0).asDynamic().toFixed(2)} KB"
                                }
                            } else "-"

                            Tr(attrs = Modifier.borderTop(1.px, LineStyle.Solid, Color.lightgray).toAttrs()) {
                                Td(attrs = Modifier.padding(12.px).toAttrs()) { Text(name) }
                                Td(attrs = Modifier.padding(12.px).toAttrs()) { Text(category) }
                                Td(attrs = Modifier.padding(12.px).toAttrs()) { Text(sizeDisplay) }
                                Td(attrs = Modifier.padding(12.px).toAttrs { style { property("text-align", "center") } }) {
                                    if (isDir) {
                                        Button(attrs = Modifier.margin(right = 5.px).toAttrs {
                                            onClick {
                                                ctx.router.navigateTo("/dashboard?username=$rawName")
                                            }
                                        }) {
                                            Text("View Author's Packages")
                                        }
                                    } else {
                                        val downloadUrl = item["download_url"] as? String ?: item["downloadUrl"] as? String ?: ""
                                        if (downloadUrl.isNotEmpty()) {
                                            A(href = downloadUrl, attrs = Modifier.margin(right = 5.px).toAttrs()) {
                                                Button { Text("Download") }
                                            }
                                        } else {
                                            Text("N/A")
                                        }
                                        
                                        Button(
                                            attrs = Modifier.margin(right = 5.px).toAttrs({
                                                onClick {
                                                    ctx.router.navigateTo("/view?package=$path&username=$owner&from=aboutss")
                                                }
                                            })
                                        ) { Text("View") }
                                    }
                                    
                                    if (owner == loggedInUser && !isDir) {
                                        Button(
                                            attrs = Modifier.margin(right = 5.px).toAttrs({
                                                onClick {
                                                    ctx.router.navigateTo("/edit?package=$path&username=$owner")
                                                }
                                            })
                                        ) { Text("Edit") }
                                        
                                        Button(
                                            attrs = Modifier.toAttrs({
                                                onClick {
                                                    scope.launch {
                                                        try {
                                                            window.api.getBytes("delete-package?package=$path&username=$owner")
                                                            ExplorerCache.packages = null // Invalidate cache
                                                            window.location.reload()
                                                        } catch(e: Exception) {
                                                            println("Delete failed: $e")
                                                        } 
                                                    }
                                                }
                                            })
                                        ) { Text("Delete") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Button(
            attrs = Modifier.margin(top = 20.px).toAttrs {
                onClick {
                    ctx.router.navigateTo("/navigateto")
                }
            }
        ) {
            Text("Go to Upload")
        }
    }
}
