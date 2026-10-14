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
import kotlinx.serialization.builtins.ListSerializer
import org.example.app.auth.AuthClient
import org.example.app.auth.AuthSession
import org.example.app.auth.jwtJson
import org.example.app.components.ClientCache
import org.example.app.components.Http
import org.example.app.components.layouts.PageLayout
import org.example.app.components.widgets.*
import org.example.app.config.AmieConfig
import org.example.app.config.encodeURIComponent
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import kotlin.js.Json

private class StoredPackage(val author: String, val name: String, val path: String, val size: Double)

/** Administration: remove any package or build, publish application builds. Needs the Keycloak admin role. */
@NoLiveLiterals
@Page("admin")
@Composable
fun AdminPage() {
    val ctx = rememberPageContext()
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf("checking") } // checking | denied | ready
    var username by remember { mutableStateOf<String?>(null) }
    var jars by remember { mutableStateOf<List<JarInfo>>(emptyList()) }
    var packages by remember { mutableStateOf<List<StoredPackage>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeKind by remember { mutableStateOf(BannerKind.INFO) }
    var releaseFile by remember { mutableStateOf<File?>(null) }
    var publishing by remember { mutableStateOf(false) }

    suspend fun bearer(): Map<String, String> = Http.bearer(AuthClient.accessToken())

    suspend fun loadAll() {
        loading = true
        try {
            val jarText = Http.get(AmieConfig.api("jars?refresh=1")).takeIf { it.ok }?.text ?: "[]"
            jars = runCatching { jwtJson.decodeFromString(ListSerializer(JarInfo.serializer()), jarText) }.getOrDefault(emptyList())

            val found = mutableListOf<StoredPackage>()
            val rootText = Http.get(AmieConfig.api("list-github?refresh=1")).takeIf { it.ok }?.text ?: "[]"
            val root = JSON.parse<dynamic>(rootText)
            val items: List<Json> = if (js("Array.isArray(root)") as Boolean) root.unsafeCast<Array<Json>>().toList() else emptyList()
            for (item in items) {
                val name = item["name"] as? String ?: continue
                val path = item["path"] as? String ?: ""
                if (item["type"] == "file") {
                    found += StoredPackage("", name, path, (item["size"] as? Number)?.toDouble() ?: 0.0)
                } else if (item["type"] == "dir") {
                    val userText = Http.get(AmieConfig.api("repo-by-username?username=${encodeURIComponent(name)}&refresh=1")).takeIf { it.ok }?.text ?: "[]"
                    val user = JSON.parse<dynamic>(userText)
                    val files: List<Json> = if (js("Array.isArray(user)") as Boolean) user.unsafeCast<Array<Json>>().toList() else emptyList()
                    for (file in files) {
                        found += StoredPackage(name, file["name"] as? String ?: "?", file["path"] as? String ?: "", (file["size"] as? Number)?.toDouble() ?: 0.0)
                    }
                }
            }
            packages = found
        } catch (e: Throwable) {
            notice = "Some lists could not be loaded."
            noticeKind = BannerKind.ERROR
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        val user = AuthClient.currentUser()
        if (user == null) {
            ctx.router.navigateTo("/loginpage?next=${encodeURIComponent("/admin")}")
            return@LaunchedEffect
        }
        username = user
        val me = runCatching { Http.get(AmieConfig.api("admin-me"), bearer(), noStore = true) }.getOrNull()
        if (me?.ok == true) {
            state = "ready"
            loadAll()
        } else {
            state = "denied"
        }
    }

    fun deleteJar(jar: JarInfo) {
        scope.launch {
            notice = "Removing ${jar.name}…"; noticeKind = BannerKind.INFO
            val result = runCatching { Http.postForm(AmieConfig.api("admin-delete-jar?name=${encodeURIComponent(jar.name)}"), emptyMap(), bearer()) }.getOrNull()
            if (result?.ok == true) {
                ClientCache.remove("jars:list")
                notice = "${jar.name} was removed."; noticeKind = BannerKind.SUCCESS
                loadAll()
            } else {
                notice = "${jar.name} could not be removed."; noticeKind = BannerKind.ERROR
            }
        }
    }

    fun deletePackage(pkg: StoredPackage) {
        scope.launch {
            notice = "Removing ${pkg.name}…"; noticeKind = BannerKind.INFO
            val result = runCatching { Http.postForm(AmieConfig.api("admin-delete-package?package=${encodeURIComponent(pkg.path)}"), emptyMap(), bearer()) }.getOrNull()
            if (result?.ok == true) {
                ClientCache.removePrefix("packages:")
                notice = "${pkg.name} was removed."; noticeKind = BannerKind.SUCCESS
                loadAll()
            } else {
                notice = "${pkg.name} could not be removed."; noticeKind = BannerKind.ERROR
            }
        }
    }

    fun publish() {
        val file = releaseFile ?: return
        scope.launch {
            publishing = true
            notice = "Publishing ${file.name}…"; noticeKind = BannerKind.INFO
            try {
                val result = Http.postBody(AmieConfig.api("admin-release-upload?fileName=${encodeURIComponent(file.name)}"), file, bearer() + ("Content-Type" to "application/octet-stream"))
                if (result.ok) {
                    ClientCache.remove("jars:list")
                    notice = "${file.name} is now on the downloads page."; noticeKind = BannerKind.SUCCESS
                    releaseFile = null
                    loadAll()
                } else {
                    val reason = runCatching { (JSON.parse<Json>(result.text))["error"] as? String }.getOrNull()
                    notice = "The build was not accepted" + (reason?.takeIf { it != "unavailable" }?.let { ": $it" } ?: ".")
                    noticeKind = BannerKind.ERROR
                }
            } catch (e: Throwable) {
                notice = "The build could not be uploaded."; noticeKind = BannerKind.ERROR
            } finally {
                publishing = false
            }
        }
    }

    PageLayout("Admin") {
        PageHeading("Administration", username?.let { "Signed in as $it" }) {
            if (state == "ready") GhostButton(if (loading) "Refreshing…" else "Refresh", enabled = !loading) { scope.launch { loadAll() } }
        }

        when (state) {
            "checking" -> EmptyState("Checking your access…")
            "denied" -> Card(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(1.cssRem).flexWrap(FlexWrap.Wrap)) {
                Mascot(variant = 1, size = 120)
                Div {
                    Div(Modifier.fontSize(12.px).margin(bottom = 0.5.cssRem).toAttrs()) { Text("This page is for administrators") }
                    MutedText("Sign in with an account that has the admin role.")
                    Div(Modifier.margin(top = 0.6.cssRem).toAttrs()) { GhostButton("Switch account") { AuthClient.signOut() } }
                }
            }
            else -> {
                StatusBanner(notice, noticeKind, Modifier.margin(bottom = 1.cssRem))

                // Publish a build
                Card(Modifier.margin(bottom = 2.cssRem)) {
                    H2(Modifier.fontSize(14.px).margin(top = 0.px, bottom = 0.6.cssRem).toAttrs()) { Text("Publish an application build") }
                    MutedText("A dmg, msi, deb, zip or jar of the Amie desktop app. It appears under “Amie application” on the downloads page.")
                    Div(Modifier.display(DisplayStyle.Flex).gap(0.6.cssRem).alignItems(AlignItems.Center).flexWrap(FlexWrap.Wrap).margin(top = 0.8.cssRem).toAttrs()) {
                        Input(InputType.File, attrs = {
                            attr("accept", ".dmg,.msi,.deb,.zip,.jar,.exe")
                            onChange { evt -> releaseFile = (evt.target as HTMLInputElement).files?.item(0) }
                        })
                        PrimaryButton(if (publishing) "Publishing…" else "Publish", enabled = releaseFile != null && !publishing) { publish() }
                    }
                }

                // Bucket contents
                H2(Modifier.fontSize(14.px).margin(top = 0.px, bottom = 0.8.cssRem).toAttrs()) { Text("Downloads (bucket)") }
                if (jars.isEmpty()) {
                    Card(Modifier.margin(bottom = 2.cssRem)) { MutedText(if (loading) "Loading…" else "The bucket holds no builds or package jars.") }
                } else {
                    Table(DataTableStyle.toModifier().margin(bottom = 2.cssRem).toAttrs()) {
                        Thead { Tr { Th { Text("Name") }; Th { Text("Kind") }; Th { Text("Size") }; Th { Text("Updated") }; Th { Text("") } } }
                        Tbody {
                            for (jar in jars) {
                                Tr {
                                    Td { SpanText(jar.name, Modifier.fontWeight(FontWeight.Medium)) }
                                    Td { Text(jar.kind) }
                                    Td { Text(formatSize(jar.size.toDouble())) }
                                    Td { Text(formatDate(jar.lastModified)) }
                                    Td {
                                        Div(Modifier.display(DisplayStyle.Flex).gap(0.4.cssRem).justifyContent(JustifyContent.FlexEnd).toAttrs()) {
                                            LinkButton("Download", AmieConfig.downloadUrl(jar.name, jar.kind, jar.size), download = true, primary = false)
                                            DangerButton("Remove") { deleteJar(jar) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Package store
                H2(Modifier.fontSize(14.px).margin(top = 0.px, bottom = 0.8.cssRem).toAttrs()) { Text("Package store") }
                if (packages.isEmpty()) {
                    Card { MutedText(if (loading) "Loading…" else "The package store is empty or could not be read.") }
                } else {
                    Table(DataTableStyle.toModifier().toAttrs()) {
                        Thead { Tr { Th { Text("Author") }; Th { Text("File") }; Th { Text("Size") }; Th { Text("") } } }
                        Tbody {
                            for (pkg in packages) {
                                Tr {
                                    Td { Text(pkg.author.ifEmpty { "–" }) }
                                    Td { SpanText(pkg.name, Modifier.fontWeight(FontWeight.Medium)) }
                                    Td { Text(formatSize(pkg.size)) }
                                    Td {
                                        Div(Modifier.display(DisplayStyle.Flex).justifyContent(JustifyContent.FlexEnd).toAttrs()) {
                                            DangerButton("Remove") { deletePackage(pkg) }
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
