package org.example.app.pages

import androidx.compose.runtime.*
import androidx.compose.runtime.NoLiveLiterals
import com.varabyte.kobweb.browser.api
import com.varabyte.kobweb.compose.css.TextDecorationLine
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.navigation.Route
import com.varabyte.kobweb.silk.components.text.SpanText
import kotlinx.browser.window
import kotlinx.coroutines.launch
import kotlinx.coroutines.await
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import kotlin.js.Json
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLQueryComponent

private const val DASHBOARD_URL = "http://localhost:8080/dashboard"
private const val LIST_GITHUB_URL = "http://localhost:8080/list-github"
private const val QUERY_URL = "http://localhost:8080/query?query="

private object ExplorerCache {
    var packages: List<Json>? = null
}

@NoLiveLiterals
@Page("/")
@Composable
fun IndexPage() {
    var text by remember { mutableStateOf("") }
    var loadedPackages by remember { mutableStateOf<List<Json>>(emptyList()) }
    var loggedInUser by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val ctx = rememberPageContext()

    LaunchedEffect(Unit) {
        println("--- [IndexPage: LaunchedEffect] START ---")
        isLoading = true

        val token = window.localStorage.getItem("auth_token")
        if (token != null) {
            try {
                println("DEBUG: Auth token found in localStorage, validating...")
                val headers = js("{}")
                headers["Authorization"] = "Bearer $token"
                val options = js("{}")
                options["headers"] = headers

                val response = window.fetch(DASHBOARD_URL, options).await()
                println("DEBUG: Dashboard auth check status: ${response.status}")
                if (response.ok) {
                    val text = response.text().await()
                    loggedInUser = text.substringAfter("dashboard, ").substringBefore(" (ID:")
                    println("DEBUG: Logged in user identified as: $loggedInUser")
                } else if (response.status == 401.toShort()) {
                    println("INFO: Auth token expired or invalid, clearing.")
                    window.localStorage.removeItem("auth_token")
                    loggedInUser = null
                }
            } catch (e: Exception) {
                console.error("ERROR: Auth check failed in Index: ${e.message}")
            }
        }

        try {
            if (ExplorerCache.packages != null) {
                loadedPackages = ExplorerCache.packages!!
                println("DEBUG: Loaded Explorer data from cache (${loadedPackages.size} items)")
            } else {
                println("DEBUG: Fetching package list from $LIST_GITHUB_URL")
                val response = window.fetch(LIST_GITHUB_URL).await()
                println("DEBUG: Package list fetch status: ${response.status}")
                if (response.ok) {
                    val json = response.json().await().unsafeCast<Array<Json>>()
                    loadedPackages = json.toList()
                    ExplorerCache.packages = loadedPackages
                    println("DEBUG: Package list loaded successfully (${loadedPackages.size} items)")
                }
            }
        } catch (e: Exception) {
            console.error("ERROR: Connection failed in Index: ${e.message}")
        } finally {
            isLoading = false
            println("--- [IndexPage: LaunchedEffect] END ---")
        }
    }

    Div(
        attrs = Modifier.padding(24.px).fillMaxWidth().backgroundColor(Color.black).toAttrs()
    ) {
        Div(Modifier.margin(bottom = 10.px).display(DisplayStyle.Flex).justifyContent(JustifyContent.SpaceBetween).toAttrs()) {
            Img(src = "/svglogo.svg", attrs = Modifier.size(width = 652.px, height = 142.px).toAttrs())
            val loggedInUsername = window.localStorage.getItem("username")
            for (i in 0 until window.localStorage.length) {
                println(window.localStorage.key(i))
            }
            if (loggedInUsername != null) {
                SpanText(text = "Under Username: $loggedInUsername", modifier = Modifier.textDecorationLine(TextDecorationLine.Underline))
            } else {
                Text("Log in to upload packages")
            }

            Div(attrs =
                Modifier
                    .display(DisplayStyle.Flex).gap(4.px)
                    .flexDirection(FlexDirection.Column)
                    .toAttrs()
            ) {
                if (loggedInUser != null) {
                    Button(attrs = Modifier.backgroundColor(Color.lightgray).toAttrs {
                        onClick {
                            println("DEBUG: Navigating to dashboard for $loggedInUser")
                            ctx.router.navigateTo("/dashboard?username=$loggedInUser")
                        }
                    }) {
                        Text("Logged in as $loggedInUser")
                    }
                } else {
                    Button(attrs = Modifier.backgroundColor(Color.lightgray).toAttrs {
                        onClick {
                            println("DEBUG: Navigating to login page")
                            ctx.router.navigateTo("/loginpage")
                        }
                    }) {
                        Text("Login")
                    }
                }
            }
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
                        placeholder("Search for package by extension (e.g. *py)")
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
                            println("--- [IndexPage: Search] START ---")
                            println("DEBUG: Search query: '$text'")
                            isLoading = true
                            try {
                                val encodedText = js("encodeURIComponent")(text) as String
                                val searchUrl = "$QUERY_URL$encodedText"
                                println("DEBUG: Fetching search results from: $searchUrl")
                                val response = window.fetch(searchUrl).await()
                                println("DEBUG: Search response status: ${response.status}")
                                if (response.ok) {
                                    val json = response.json().await()
                                    if (js("Array.isArray(json)") as Boolean) {
                                        loadedPackages = (json as Array<Json>).toList()
                                    } else if (json != null) {
                                        loadedPackages = listOf(json.unsafeCast<Json>())
                                    } else {
                                        loadedPackages = emptyList()
                                    }
                                    ExplorerCache.packages = loadedPackages
                                    println("DEBUG: Search results updated (${loadedPackages.size} items)")
                                }
                            } catch (e: Exception) {
                                console.error("ERROR: Search failed: ${e.message}")
                            } finally {
                                isLoading = false
                                println("--- [IndexPage: Search] END ---")
                            }
                        }
                    }
                }
            ) {
                Text("Search")
            }
        }

        if (isLoading) {
            P { Text("Fetching data...") }
        } else {
            val authors = loadedPackages.filter { it["type"] == "dir" }
            val binaries = loadedPackages.filter { it["type"] == "file" }

            if (authors.isNotEmpty()) {
                Table(
                    attrs = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 40.px)
                        .border(1.px, LineStyle.Solid, Color.lightgray)
                        .borderRadius(8.px)
                        .toAttrs()
                ) {
                    Thead {
                        Tr {
                            Th(attrs = Modifier.padding(12.px).toAttrs { style { property("text-align", "left") } }) { Text("Author") }
                            Th(attrs = Modifier.padding(12.px).toAttrs { style { property("text-align", "center") } }) { Text("Actions") }
                        }
                    }
                    Tbody {
                        for (item in authors) {
                            val rawName = item["name"] as? String ?: "Unknown"
                            Tr(attrs = Modifier.borderTop(1.px, LineStyle.Solid, Color.lightgray).toAttrs()) {
                                Td(attrs = Modifier.padding(12.px).toAttrs()) {
                                    B { Img(src = "usericon.svg", attrs = Modifier.size(40.px).toAttrs())
                                        Text("$rawName") }
                                    Text(" (Author)")
                                }
                                Td(attrs = Modifier.padding(12.px).toAttrs { style { property("text-align", "center") } }) {
                                    Button(attrs = Modifier.toAttrs {
                                        onClick {
                                            println("DEBUG: Navigating to dashboard for author: $rawName")
                                            ctx.router.navigateTo("/dashboard?username=$rawName")
                                        }
                                    }) {
                                        Text("View Author's Packages")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (binaries.isNotEmpty()) {
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
                            Th(attrs = Modifier.padding(12.px).toAttrs { style { property("text-align", "left") } }) { Text("Size") }
                            Th(attrs = Modifier.padding(12.px).toAttrs { style { property("text-align", "center") } }) { Text("Actions") }
                        }
                    }
                    Tbody {
                        for (item in binaries) {
                            val rawName = item["name"] as? String ?: "Unknown"
                            val path = item["path"] as? String ?: ""
                            val owner = path.substringAfter("uploads/").substringBefore("/")

                            val sizeInBytes = (item["size"] as? Number)?.toDouble() ?: 0.0
                            val sizeDisplay = if (sizeInBytes > 0) {
                                if (sizeInBytes > 1024 * 1024) {
                                    "${(sizeInBytes / (1024.0 * 1024.0)).asDynamic().toFixed(2)} MB"
                                } else {
                                    "${(sizeInBytes / 1024.0).asDynamic().toFixed(2)} KB"
                                }
                            } else "-"

                            Tr(attrs = Modifier.borderTop(1.px, LineStyle.Solid, Color.lightgray).toAttrs()) {
                                Td(attrs = Modifier.padding(12.px).toAttrs()) {
                                    when {
                                        rawName.endsWith(".py") -> Img(src = "python.svg", attrs = Modifier.size(40.px).toAttrs())
                                        rawName.endsWith(".json") -> Img(src = "json.svg", attrs = Modifier.size(40.px).toAttrs())
                                        else ->  { Text("📄") }
                                    }
                                    Text("$rawName")
                                }
                                Td(attrs = Modifier.padding(12.px).toAttrs()) { Text(sizeDisplay) }
                                Td(attrs = Modifier.padding(12.px).toAttrs { style { property("text-align", "center") } }) {
                                    val downloadUrl = item["download_url"] as? String ?: item["downloadUrl"] as? String ?: ""
                                    
                                    Button(
                                        attrs = Modifier.margin(right = 5.px).toAttrs({
                                            onClick {
                                                println("PATH: $path")
                                                val encodedPath = if (path.isNotEmpty()) path.encodeURLParameter() as String else ""
                                                println("E path ${encodedPath}")

                                                val encodedUrl = if (downloadUrl.isNotEmpty()) downloadUrl.encodeURLParameter() as String else ""
                                                println("E url: ${encodedUrl}")

                                                println("DEBUG: Navigating to View route for: ${encodedPath} + ${encodedUrl}")

                                                val viewRoute = "/view?package=$encodedPath&username=$owner&from=aboutss&url=$encodedUrl"
                                                val parsedUrl = io.ktor.http.Url(viewRoute)
                                                val decodedUrl = parsedUrl.parameters["url"] ?: ""

                                                println("EPIC MAGIC: $decodedUrl")

                                                val queryString = viewRoute.substringAfter('?', missingDelimiterValue = "")
                                                val searchParams = js("new URLSearchParams(queryString)")
                                                val pkgParam = searchParams.get("package") as? String

                                                ctx.router.navigateTo(viewRoute)

                                            }
                                        })
                                    ) { Text("View") }

                                    if (downloadUrl.isNotEmpty()) {
                                        A(href = downloadUrl, attrs = Modifier.margin(right = 5.px).toAttrs {
                                            onClick { println("DEBUG: User clicking Download link for: $downloadUrl") }
                                        }) {
                                            Button {
                                                Text("Download")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (authors.isEmpty() && binaries.isEmpty()) {
                P { Text("No items found.") }
            }
        }

        Button(
            attrs = Modifier.margin(top = 40.px).toAttrs {
                onClick {
                    println("DEBUG: Navigating to Upload page")
                    ctx.router.navigateTo("/navigateto")
                }
            }
        ) {
            Text("Go to Upload")
        }
    }
}
