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

// Simple in-memory cache to prevent re-fetching on every navigation
private object DashboardCache {
    var packageData: Map<String, List<Json>> = emptyMap()
    var deviceData: Map<String, List<Json>> = emptyMap()
    var lastUsername: String? = null
    
    fun clear(username: String) {
        packageData = packageData.toMutableMap().apply { remove(username) }
        deviceData = deviceData.toMutableMap().apply { remove(username) }
    }
}

@Page("dashboard")
@Composable
fun Dashboard() {
    val ctx = rememberPageContext()
    var username by remember { mutableStateOf("") }
    var loggedInUser by remember { mutableStateOf<String?>(null) }
    var loadedPackages by remember { mutableStateOf<List<Json>>(emptyList()) }
    var deviceStatuses by remember { mutableStateOf<List<Json>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var delResp by remember { mutableStateOf("") }
    var refreshCounter by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(ctx.route, refreshCounter) {
        username = ctx.route.params["username"] ?: ""
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
                    // Extract username from "Welcome to your dashboard, user (ID: 1)!"
                    loggedInUser = text.substringAfter("dashboard, ").substringBefore(" (ID:")
                }
            } catch (e: Exception) {
                println("Auth check failed: $e")
            }
        }

        if (username.isNotEmpty()) {
            if (refreshCounter == 0 && DashboardCache.packageData.containsKey(username)) {
                loadedPackages = DashboardCache.packageData[username] ?: emptyList()
                deviceStatuses = DashboardCache.deviceData[username] ?: emptyList()
                println("DEBUG: Loaded from cache for $username")
            } else {
                isLoading = true
                try {
                    // 1. Fetch Packages
                    val pkgResponse = window.api.getBytes("repo-by-username?username=$username")
                    val pkgText = pkgResponse.decodeToString()
                    if (pkgText.isNotEmpty()) {
                        val json = JSON.parse<dynamic>(pkgText)
                        loadedPackages = if (js("Array.isArray(json)") as Boolean) (json as Array<Json>).toList() else listOf(json as Json)
                        DashboardCache.packageData = DashboardCache.packageData.toMutableMap().apply { put(username, loadedPackages) }
                    }

                    // 2. Fetch Device Statuses
                    val deviceResponse = window.api.getBytes("get-device-status?username=$username")
                    val deviceText = deviceResponse.decodeToString()
                    if (deviceText.isNotEmpty()) {
                        val json = JSON.parse<dynamic>(deviceText)
                        deviceStatuses = if (js("Array.isArray(json)") as Boolean) (json as Array<Json>).toList() else emptyList()
                        DashboardCache.deviceData = DashboardCache.deviceData.toMutableMap().apply { put(username, deviceStatuses) }
                    }
                } catch (e: Exception) {
                    println("Error fetching dashboard data: ${e.message}")
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Div(Modifier.padding(24.px).toAttrs()) {
        H1 { Text("Logged into: $username") }

        if (delResp.isNotEmpty()) {
            P(Modifier.color(Color.blue).toAttrs()) { Text(delResp) }
        }

        H3 { Text("Your Devices Status:") }
        if (deviceStatuses.isEmpty()) {
            P { Text("No device status information available.") }
        } else {
            Table(Modifier.fillMaxWidth().border(1.px, LineStyle.Solid, Color.lightgray).borderRadius(8.px).toAttrs()) {
                Thead {
                    Tr {
                        Th(Modifier.padding(12.px).toAttrs { style { property("text-align", "left") } }) { Text("Device Key") }
                        Th(Modifier.padding(12.px).toAttrs { style { property("text-align", "left") } }) { Text("Name") }
                        Th(Modifier.padding(12.px).toAttrs { style { property("text-align", "left") } }) { Text("Port") }
                        Th(Modifier.padding(12.px).toAttrs { style { property("text-align", "left") } }) { Text("Endpoint") }
                    }
                }
                Tbody {
                    for (device in deviceStatuses) {
                        Tr(Modifier.borderTop(1.px, LineStyle.Solid, Color.lightgray).toAttrs()) {
                            Td(Modifier.padding(12.px).toAttrs()) { Text(device["deviceKey"] as? String ?: "-") }
                            Td(Modifier.padding(12.px).toAttrs()) { Text(device["name"] as? String ?: "-") }
                            Td(Modifier.padding(12.px).toAttrs()) { Text(device["port"] as? String ?: "-") }
                            Td(Modifier.padding(12.px).toAttrs()) { Text(device["deviceEndpoint"] as? String ?: "-") }
                        }
                    }
                }
            }
        }

        Hr(Modifier.margin(topBottom = 30.px).toAttrs())

        H3 { Text("Your Uploaded Packages:") }

        if (isLoading) {
            P { Text("Loading data...") }
        } else {
            if (loadedPackages.isEmpty()) {
                P { Text("No packages found for your account.") }
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
                            Th(attrs = Modifier.padding(12.px).toAttrs { style { property("text-align", "left") } }) { Text("Size") }
                            Th(attrs = Modifier.padding(12.px).toAttrs { style { property("text-align", "left") } }) { Text("Author") }
                            Th(attrs = Modifier.padding(12.px).toAttrs { style { property("text-align", "center") } }) { Text("Actions") }
                        }
                    }
                    Tbody {
                        for (item in loadedPackages) {
                            val name = item["name"] as? String ?: "Unknown"
                            val path = item["path"] as? String ?: ""
                            val sizeInBytes = (item["size"] as? Number)?.toDouble() ?: 0.0
                            val sizeDisplay = if (sizeInBytes > 1024 * 1024) {
                                "${(sizeInBytes / (1024.0 * 1024.0)).asDynamic().toFixed(2)} MB"
                            } else {
                                "${(sizeInBytes / 1024.0).asDynamic().toFixed(2)} KB"
                            }

                            Tr(attrs = Modifier.borderTop(1.px, LineStyle.Solid, Color.lightgray).toAttrs()) {
                                Td(attrs = Modifier.padding(12.px).toAttrs()) { Text(name) }
                                Td(attrs = Modifier.padding(12.px).toAttrs()) { Text(sizeDisplay) }
                                Td(attrs = Modifier.padding(12.px).toAttrs()) { Text(username) }
                                Td(attrs = Modifier.padding(12.px).toAttrs { style { property("text-align", "center") } }) {
                                    Button(
                                        attrs = Modifier.margin(right = 5.px).toAttrs({
                                            onClick {
                                                ctx.router.navigateTo("/view?package=$path&username=$username&from=dashboard")
                                            }
                                        })
                                    ) { Text("View") }
                                    
                                    if (username == loggedInUser) {
                                        Button(
                                            attrs = Modifier.margin(right = 5.px).toAttrs({
                                                onClick {
                                                    ctx.router.navigateTo("/edit?package=$path&username=$username")
                                                }
                                            })
                                        ) { Text("Edit") }
                                        
                                        Button(
                                            attrs = Modifier.toAttrs({
                                                onClick {
                                                    scope.launch {
                                                        try {
                                                            delResp = "Deleting $name..."
                                                            val token = window.localStorage.getItem("auth_token")
                                                            val response = window.fetch("http://localhost:8081/api/delete-package?package=$path&username=$username&token=$token").await()
                                                            if (response.ok) {
                                                                DashboardCache.clear(username)
                                                                delResp = "Successfully deleted $name"
                                                                refreshCounter++
                                                            } else {
                                                                delResp = "Failed to delete: ${response.statusText}"
                                                            }
                                                        } catch(e: Exception) {
                                                            delResp = "Failed to delete package: ${e.message}"
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
        
        Button(attrs = Modifier.margin(top = 20.px).toAttrs({
            onClick {
                ctx.router.navigateTo("/navigateto?username=$username")
            }
        })) {
            Text("Upload New Package")
        }
    }
}
