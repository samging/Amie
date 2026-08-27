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

private const val DASHBOARD_URL = "http://localhost:8080/dashboard"
private const val DELETE_PACKAGE_URL = "http://localhost:8081/api/delete-package"
private const val NAVIGATE_TO_UPLOAD = "/navigateto?username="

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

@NoLiveLiterals
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
        println("--- [Dashboard: LaunchedEffect] START ---")
        username = ctx.route.params["username"] ?: ""
        println("DEBUG: Username from route: '$username'")
        
        val token = window.localStorage.getItem("auth_token")
        if (token != null) {
            try {
                println("DEBUG: Auth token found, validating...")
                val headers = js("{}")
                headers["Authorization"] = "Bearer $token"
                val options = js("{}")
                options["headers"] = headers

                val response = window.fetch(DASHBOARD_URL, options).await()
                println("DEBUG: Dashboard auth check status: ${response.status}")
                if (response.ok) {
                    val text = response.text().await()
                    loggedInUser = text.substringAfter("dashboard, ").substringBefore(" (ID:")
                    println("DEBUG: Logged in user: $loggedInUser")
                } else if (response.status == 401.toShort()) {
                    println("INFO: Auth token expired, clearing and redirecting.")
                    window.localStorage.removeItem("auth_token")
                    ctx.router.navigateTo("/loginpage")
                }
            } catch (e: Exception) {
                console.error("ERROR: Auth check failed: ${e.message}")
            }
        }

        if (username.isNotEmpty()) {
            if (refreshCounter == 0 && DashboardCache.packageData.containsKey(username)) {
                loadedPackages = DashboardCache.packageData[username] ?: emptyList()
                deviceStatuses = DashboardCache.deviceData[username] ?: emptyList()
                println("DEBUG: Loaded from cache for $username (${loadedPackages.size} packages, ${deviceStatuses.size} devices)")
            } else {
                isLoading = true
                try {
                    // 1. Fetch Packages
                    println("DEBUG: Fetching packages for $username via Kobweb API proxy")
                    val pkgResponse = window.api.get("repo-by-username?username=$username")
                    val pkgText = pkgResponse?.decodeToString() ?: ""
                    println("DEBUG: Package Response Length: ${pkgText.length}")
                    if (pkgText.isNotEmpty()) {
                        val json = JSON.parse<dynamic>(pkgText)
                        loadedPackages = if (js("Array.isArray(json)") as Boolean) (json as Array<Json>).toList() else listOf(json as Json)
                        DashboardCache.packageData = DashboardCache.packageData.toMutableMap().apply { put(username, loadedPackages) }
                        println("DEBUG: Updated loadedPackages (${loadedPackages.size} items)")
                    }

                    // 2. Fetch Device Statuses
                    println("DEBUG: Fetching device statuses for $username")
                    val deviceResponse = window.api.get("get-device-status?username=$username")
                    val deviceText = deviceResponse?.decodeToString() ?: ""
                    println("DEBUG: Device Response Length: ${deviceText.length}")
                    if (deviceText.isNotEmpty()) {
                        val json = JSON.parse<dynamic>(deviceText)
                        deviceStatuses = if (js("Array.isArray(json)") as Boolean) (json as Array<Json>).toList() else emptyList()
                        DashboardCache.deviceData = DashboardCache.deviceData.toMutableMap().apply { put(username, deviceStatuses) }
                        println("DEBUG: Updated deviceStatuses (${deviceStatuses.size} items)")
                    }
                } catch (e: Exception) {
                    console.error("ERROR: Error fetching dashboard data: ${e.message}")
                } finally {
                    isLoading = false
                }
            }
        }
        println("--- [Dashboard: LaunchedEffect] END ---")
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
                                                // FULLY encode the path to avoid router issues with slashes
                                                val encodedPath = js("encodeURIComponent")(path) as String
                                                val downloadUrl = item["download_url"] as? String ?: item["downloadUrl"] as? String ?: ""
                                                val encodedUrl = if (downloadUrl.isNotEmpty()) js("encodeURIComponent")(downloadUrl) as String else ""
                                                val viewRoute = "/view?package=$encodedPath&username=$username&from=dashboard&url=$encodedUrl"
                                                println("DEBUG: Navigating to View route: $viewRoute")
                                                ctx.router.navigateTo(viewRoute)
                                            }
                                        })
                                    ) { Text("View") }
                                    
                                    if (username == loggedInUser) {
                                        Button(
                                            attrs = Modifier.margin(right = 5.px).toAttrs({
                                                onClick {
                                                    println("DEBUG: Navigating to Edit route for path: $path")
                                                    ctx.router.navigateTo("/edit?package=$path&username=$username")
                                                }
                                            })
                                        ) { Text("Edit") }
                                        
                                        Button(
                                            attrs = Modifier.toAttrs({
                                                onClick {
                                                    scope.launch {
                                                        println("--- [Dashboard: Delete] START ---")
                                                        try {
                                                            delResp = "Deleting $name..."
                                                            val token = window.localStorage.getItem("auth_token")
                                                            val deleteUrl = "$DELETE_PACKAGE_URL?package=$path&username=$username&token=$token"
                                                            println("DEBUG: Calling Delete API: $deleteUrl")
                                                            val response = window.fetch(deleteUrl).await()
                                                            println("DEBUG: Delete response status: ${response.status}")
                                                            if (response.ok) {
                                                                DashboardCache.clear(username)
                                                                delResp = "Successfully deleted $name"
                                                                refreshCounter++
                                                                println("DEBUG: Deletion successful, refreshCounter incremented")
                                                            } else {
                                                                delResp = "Failed to delete: ${response.statusText}"
                                                            }
                                                        } catch(e: Exception) {
                                                            console.error("ERROR: Failed to delete package: ${e.message}")
                                                            delResp = "Failed to delete package: ${e.message}"
                                                        } finally {
                                                            println("--- [Dashboard: Delete] END ---")
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
                println("DEBUG: Navigating to Upload page for $username")
                ctx.router.navigateTo("$NAVIGATE_TO_UPLOAD$username")
            }
        })) {
            Text("Upload New Package")
        }
    }
}
