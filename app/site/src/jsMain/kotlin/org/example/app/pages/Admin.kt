package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.WhiteSpace
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.attrsModifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import org.example.app.auth.AuthClient
import org.example.app.auth.jwtJson
import org.example.app.components.Catalog
import org.example.app.components.CatalogEntry
import org.example.app.components.ClientCache
import org.example.app.components.Http
import org.example.app.components.PackageKind
import org.example.app.components.Platform
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
    var packages by remember { mutableStateOf<List<CatalogEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeKind by remember { mutableStateOf(BannerKind.INFO) }
    var releaseFile by remember { mutableStateOf<File?>(null) }
    var publishing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0.0) }
    var bucketFilter by remember { mutableStateOf("") }
    var storeFilter by remember { mutableStateOf("") }

    suspend fun bearer(): Map<String, String> = Http.bearer(AuthClient.accessToken())

    suspend fun loadAll() {
        loading = true
        try {
            val jarText = Http.get(AmieConfig.api("jars?refresh=1")).takeIf { it.ok }?.text ?: "[]"
            jars = runCatching { jwtJson.decodeFromString(ListSerializer(JarInfo.serializer()), jarText) }.getOrDefault(emptyList())
            packages = Catalog.load(refresh = true)
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
                ClientCache.removePrefix("packages:")
                notice = "${jar.name} was removed."; noticeKind = BannerKind.SUCCESS
                Toasts.show("${jar.name} was removed.", BannerKind.SUCCESS)
                loadAll()
            } else {
                notice = "${jar.name} could not be removed."; noticeKind = BannerKind.ERROR
            }
        }
    }

    fun deletePackage(pkg: CatalogEntry) {
        scope.launch {
            notice = "Removing ${pkg.name}…"; noticeKind = BannerKind.INFO
            val result = runCatching { Http.postForm(AmieConfig.api("admin-delete-package?package=${encodeURIComponent(pkg.path)}"), emptyMap(), bearer()) }.getOrNull()
            if (result?.ok == true) {
                ClientCache.removePrefix("packages:")
                ClientCache.remove("jars:list")
                notice = "${pkg.name} was removed."; noticeKind = BannerKind.SUCCESS
                Toasts.show("${pkg.name} was removed.", BannerKind.SUCCESS)
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
            progress = 0.0
            notice = "Publishing ${file.name}…"; noticeKind = BannerKind.INFO
            try {
                val result = Http.postBody(AmieConfig.api("admin-release-upload?fileName=${encodeURIComponent(file.name)}"), file, bearer() + ("Content-Type" to "application/octet-stream"))
                if (result.ok) {
                    ClientCache.remove("jars:list")
                    notice = "${file.name} is now on the downloads page."; noticeKind = BannerKind.SUCCESS
                    Toasts.show("${file.name} was published.", BannerKind.SUCCESS)
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

    val builds = jars.filter { it.kind == "application" }
    val visibleJars = jars.filter { bucketFilter.isBlank() || it.name.contains(bucketFilter.trim(), ignoreCase = true) }
    val visiblePackages = packages.filter { storeFilter.isBlank() || it.name.contains(storeFilter.trim(), ignoreCase = true) || it.author.contains(storeFilter.trim(), ignoreCase = true) }

    PageLayout("Admin") {
        PageHeading("Administration", username?.let { "Signed in as $it" }) {
            if (state == "ready") GhostButton(if (loading) "Refreshing…" else "Refresh", enabled = !loading) { scope.launch { loadAll() } }
        }

        when (state) {
            "checking" -> SkeletonRows(rows = 3, label = "Checking your access…")
            "denied" -> Card(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(1.cssRem).flexWrap(FlexWrap.Wrap)) {
                Div {
                    Div(Modifier.fontSize(12.px).margin(bottom = 0.5.cssRem).toAttrs()) { Text("This page is for administrators") }
                    MutedText("Sign in with an account that has the admin role.")
                    Div(Modifier.margin(top = 0.6.cssRem).display(DisplayStyle.Flex).gap(0.5.cssRem).flexWrap(FlexWrap.Wrap).toAttrs()) {
                        GhostButton("Switch account") { AuthClient.signOut() }
                        LinkButton("Back to the packages", "/", primary = false)
                    }
                }
            }
            else -> {
                StatusBanner(notice, noticeKind, Modifier.margin(bottom = 1.cssRem))

                StatRow {
                    StatTile("App builds", builds.size.toString(), builds.mapNotNull { it.lastModified }.maxOrNull()?.let { "latest ${formatRelative(it)}" })
                    StatTile("Package jars", (jars.size - builds.size).toString(), "in the bucket listing")
                    StatTile("Store files", packages.size.toString(), plural(packages.map { it.author }.filter { it.isNotEmpty() }.distinct().size, "author"))
                    StatTile("Store size", formatSize(packages.sumOf { it.size }))
                }

                // Publish a build
                Card(Modifier.margin(bottom = 2.cssRem)) {
                    SectionHeading("Publish an application build")
                    MutedText("A dmg, msi, deb, zip or jar of the Amie desktop app. It appears under “Amie application” on the downloads page, and visitors on a matching system see it first.")
                    Div(Modifier.display(DisplayStyle.Flex).gap(0.6.cssRem).alignItems(AlignItems.Center).flexWrap(FlexWrap.Wrap).margin(top = 0.8.cssRem).toAttrs()) {
                        Input(InputType.File, attrs = {
                            attr("accept", ".dmg,.msi,.deb,.zip,.jar,.exe")
                            attr("aria-label", "Build file")
                            onChange { evt -> releaseFile = (evt.target as HTMLInputElement).files?.item(0) }
                        })
                        PrimaryButton(if (publishing) "Publishing…" else "Publish", enabled = releaseFile != null && !publishing) { publish() }
                    }
                    releaseFile?.let { file ->
                        HintText("${file.name} · ${formatSize(file.size.toDouble())} · for ${Platform.platformOf(file.name) ?: "any system"}", Modifier.margin(top = 0.5.cssRem))
                    }
                    if (publishing) HintText("Large builds take a while; keep this page open.", Modifier.margin(top = 0.3.cssRem))
                }

                // Bucket contents
                SectionHeading("Downloads (bucket)") {
                    if (jars.size > 4) TextField(bucketFilter, { bucketFilter = it }, "Filter…", modifier = Modifier.width(14.cssRem).height(2.cssRem).attrsModifier { attr("aria-label", "Filter bucket") })
                }
                if (jars.isEmpty()) {
                    Card(Modifier.margin(bottom = 2.cssRem)) { MutedText(if (loading) "Loading…" else "The bucket holds no builds or package jars.") }
                } else if (visibleJars.isEmpty()) {
                    EmptyAction("Nothing matches “${bucketFilter.trim()}”.", modifier = Modifier.margin(bottom = 2.cssRem), action = { GhostButton("Clear the filter") { bucketFilter = "" } })
                } else {
                    TableScroll(Modifier.margin(bottom = 2.cssRem)) {
                        Table(DataTableStyle.toModifier().toAttrs()) {
                            Thead { Tr { Th { Text("Name") }; Th { Text("Kind") }; Th { Text("Size") }; Th { Text("Updated") }; Th { Text("") } } }
                            Tbody {
                                for (jar in visibleJars) {
                                    Tr {
                                        Td { SpanText(jar.name, Modifier.fontWeight(FontWeight.Medium)) }
                                        Td { Badge(if (jar.kind == "application") (Platform.platformOf(jar.name) ?: "build") else "package", if (jar.kind == "application") BadgeTone.FILLED else BadgeTone.OUTLINE) }
                                        Td { Text(formatSize(jar.size.toDouble())) }
                                        Td { SpanText(formatRelative(jar.lastModified), Modifier.whiteSpace(WhiteSpace.NoWrap).attrsModifier { attr("title", formatDate(jar.lastModified)) }) }
                                        Td {
                                            Div(Modifier.display(DisplayStyle.Flex).gap(0.4.cssRem).justifyContent(JustifyContent.FlexEnd).flexWrap(FlexWrap.Wrap).toAttrs()) {
                                                LinkButton("Download", AmieConfig.downloadUrl(jar.name, jar.kind, jar.size), download = true, primary = false)
                                                ConfirmDangerButton("Remove", "Remove ${jar.name} from the bucket?", "Yes, remove") { deleteJar(jar) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Package store
                SectionHeading("Package store") {
                    if (packages.size > 4) TextField(storeFilter, { storeFilter = it }, "Filter by name or author…", modifier = Modifier.width(16.cssRem).height(2.cssRem).attrsModifier { attr("aria-label", "Filter store") })
                }
                if (packages.isEmpty()) {
                    Card { MutedText(if (loading) "Loading…" else "The package store is empty or could not be read.") }
                } else if (visiblePackages.isEmpty()) {
                    EmptyAction("Nothing matches “${storeFilter.trim()}”.", action = { GhostButton("Clear the filter") { storeFilter = "" } })
                } else {
                    TableScroll {
                        Table(DataTableStyle.toModifier().toAttrs()) {
                            Thead { Tr { Th { Text("Author") }; Th { Text("File") }; Th { Text("Kind") }; Th { Text("Size") }; Th { Text("") } } }
                            Tbody {
                                for (pkg in visiblePackages) {
                                    Tr {
                                        Td { if (pkg.author.isEmpty()) Text("–") else A("/dashboard?username=${encodeURIComponent(pkg.author)}") { Text(pkg.author) } }
                                        Td { A(pkg.viewUrl, attrs = Modifier.fontWeight(FontWeight.Medium).toAttrs()) { Text(pkg.name) } }
                                        Td { Badge(pkg.kind.label) }
                                        Td { Text(formatSize(pkg.size)) }
                                        Td {
                                            Div(Modifier.display(DisplayStyle.Flex).gap(0.4.cssRem).justifyContent(JustifyContent.FlexEnd).flexWrap(FlexWrap.Wrap).toAttrs()) {
                                                GhostButton("View") { ctx.router.navigateTo(pkg.viewUrl) }
                                                ConfirmDangerButton("Remove", "Remove ${pkg.name} for everyone?", "Yes, remove") { deletePackage(pkg) }
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
