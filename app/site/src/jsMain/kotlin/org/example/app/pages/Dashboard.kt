package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.coroutines.launch
import org.example.app.auth.AuthClient
import org.example.app.components.ClientCache
import org.example.app.components.Http
import org.example.app.components.layouts.PageLayout
import org.example.app.components.widgets.*
import org.example.app.config.AmieConfig
import org.example.app.config.encodeURIComponent
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import kotlin.js.Json

private fun parseList(text: String): List<Json> {
    if (text.isBlank()) return emptyList()
    val json = JSON.parse<dynamic>(text)
    return if (js("Array.isArray(json)") as Boolean) json.unsafeCast<Array<Json>>().toList() else emptyList()
}

/** One author's packages and device statuses; the owner can edit, delete and upload. */
@NoLiveLiterals
@Page("dashboard")
@Composable
fun Dashboard() {
    val ctx = rememberPageContext()
    val scope = rememberCoroutineScope()
    val username = ctx.route.params["username"]?.trim().orEmpty()

    var loggedInUser by remember { mutableStateOf<String?>(null) }
    var packages by remember { mutableStateOf<List<Json>>(emptyList()) }
    var devices by remember { mutableStateOf<List<Json>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeKind by remember { mutableStateOf(BannerKind.INFO) }

    suspend fun load(refresh: Boolean) {
        if (username.isEmpty()) return
        loading = true
        try {
            val suffix = if (refresh) "&refresh=1" else ""
            val packageText = ClientCache.cached("packages:user:$username", ClientCache.MINUTE, refresh) {
                Http.get(AmieConfig.api("repo-by-username?username=${encodeURIComponent(username)}$suffix")).takeIf { it.ok }?.text ?: "[]"
            }
            packages = parseList(packageText)
            val deviceText = ClientCache.cached("devices:$username", ClientCache.MINUTE / 2, refresh) {
                Http.get(AmieConfig.api("get-device-status?username=${encodeURIComponent(username)}$suffix")).takeIf { it.ok }?.text ?: "[]"
            }
            devices = parseList(deviceText)
        } catch (e: Throwable) {
            notice = "Some of this page could not be loaded right now."
            noticeKind = BannerKind.ERROR
        } finally {
            loading = false
        }
    }

    LaunchedEffect(username) {
        loggedInUser = AuthClient.currentUser()
        load(refresh = false)
    }

    val isOwner = loggedInUser != null && loggedInUser == username

    PageLayout(if (username.isEmpty()) "Dashboard" else "$username's packages") {
        if (username.isEmpty()) {
            PageHeading("Dashboard")
            StatusBanner("Pick an author on the packages page, or sign in to see your own packages.", BannerKind.INFO)
            return@PageLayout
        }

        PageHeading(if (isOwner) "Your packages" else "Packages by $username", if (isOwner) "Signed in as $username" else null) {
            GhostButton(if (loading) "Refreshing…" else "Refresh", enabled = !loading) { scope.launch { load(refresh = true) } }
            if (isOwner) {
                LinkButton("Upload a package", "/navigateto?username=${encodeURIComponent(username)}")
                LinkButton("Profile", "/profile", primary = false)
            }
        }

        StatusBanner(notice, noticeKind, Modifier.margin(bottom = 1.cssRem))

        H2(Modifier.fontSize(14.px).margin(top = 0.px, bottom = 0.8.cssRem).toAttrs()) { Text("Devices") }
        if (devices.isEmpty()) {
            Card(Modifier.margin(bottom = 2.cssRem)) { MutedText("No devices have reported a status.") }
        } else {
            Table(DataTableStyle.toModifier().margin(bottom = 2.cssRem).toAttrs()) {
                Thead { Tr { Th { Text("Device") }; Th { Text("Name") }; Th { Text("Port") }; Th { Text("Endpoint") } } }
                Tbody {
                    for (device in devices) {
                        Tr {
                            Td { Text(device["deviceKey"] as? String ?: "–") }
                            Td { Text(device["name"] as? String ?: "–") }
                            Td { Text(device["port"] as? String ?: "–") }
                            Td { Text(device["deviceEndpoint"] as? String ?: "–") }
                        }
                    }
                }
            }
        }

        H2(Modifier.fontSize(14.px).margin(top = 0.px, bottom = 0.8.cssRem).toAttrs()) { Text("Packages") }
        when {
            loading && packages.isEmpty() -> EmptyState("Loading packages…")
            packages.isEmpty() -> Card { MutedText(if (isOwner) "You have not uploaded a package yet." else "$username has not published a package yet.") }
            else -> Table(DataTableStyle.toModifier().toAttrs()) {
                Thead { Tr { Th { Text("Name") }; Th { Text("Size") }; Th { Text("") } } }
                Tbody {
                    for (item in packages) {
                        val name = item["name"] as? String ?: "Unknown"
                        val path = item["path"] as? String ?: ""
                        val size = (item["size"] as? Number)?.toDouble() ?: 0.0
                        val downloadUrl = item["download_url"] as? String ?: item["downloadUrl"] as? String ?: ""
                        Tr {
                            Td { SpanText(name, Modifier.fontWeight(FontWeight.Medium)) }
                            Td { Text(formatSize(size)) }
                            Td {
                                Div(Modifier.display(DisplayStyle.Flex).gap(0.4.cssRem).justifyContent(JustifyContent.FlexEnd).flexWrap(FlexWrap.Wrap).toAttrs()) {
                                    GhostButton("View") {
                                        ctx.router.navigateTo("/view?package=${encodeURIComponent(path)}&username=${encodeURIComponent(username)}&from=dashboard&url=${encodeURIComponent(downloadUrl)}")
                                    }
                                    if (isOwner) {
                                        GhostButton("Replace") {
                                            ctx.router.navigateTo("/edit?package=${encodeURIComponent(path)}&username=${encodeURIComponent(username)}")
                                        }
                                        DangerButton("Delete") {
                                            scope.launch {
                                                notice = "Deleting $name…"
                                                noticeKind = BannerKind.INFO
                                                val token = AuthClient.accessToken()
                                                val result = runCatching {
                                                    Http.get(AmieConfig.api("delete-package?package=${encodeURIComponent(path)}&username=${encodeURIComponent(username)}&token=${encodeURIComponent(token.orEmpty())}"))
                                                }.getOrNull()
                                                if (result?.ok == true) {
                                                    ClientCache.removePrefix("packages:")
                                                    notice = "$name was deleted."
                                                    noticeKind = BannerKind.SUCCESS
                                                    load(refresh = true)
                                                } else {
                                                    notice = when (result?.status) {
                                                        401 -> "Please sign in again to delete packages."
                                                        403 -> "Only the author can delete this package."
                                                        else -> "$name could not be deleted."
                                                    }
                                                    noticeKind = BannerKind.ERROR
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
