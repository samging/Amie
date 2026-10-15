package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.WhiteSpace
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.attrsModifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.example.app.auth.AuthClient
import org.example.app.components.ClientCache
import org.example.app.components.Http
import org.example.app.components.PackageKind
import org.example.app.components.layouts.PageLayout
import org.example.app.components.widgets.*
import org.example.app.components.BadgeRow
import org.example.app.components.LinkedAccountsView
import org.example.app.components.PublicationsView
import org.example.app.components.PackageStats
import org.example.app.components.ProfileBanner
import org.example.app.components.LikeButton
import org.example.app.components.loadStats
import org.example.app.components.loadMyLikes
import org.example.app.components.statsLabel
import org.example.app.components.trackDownload
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
    val uploaded = ctx.route.params["uploaded"]?.trim()
    val updated = ctx.route.params["updated"]?.trim()

    var loggedInUser by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<PublicProfile?>(null) }
    val tab = rememberTab("packages", listOf("packages", "devices", "more", "errors"))
    var packages by remember { mutableStateOf<List<Json>>(emptyList()) }
    var devices by remember { mutableStateOf<List<Json>>(emptyList()) }
    var errors by remember { mutableStateOf<List<Json>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeKind by remember { mutableStateOf(BannerKind.INFO) }
    var filter by remember { mutableStateOf("") }
    // Tree (folders as the store keeps them) or table; the choice is kept in this browser
    var treeView by remember { mutableStateOf(runCatching { window.localStorage.getItem("amie.packagesView") }.getOrNull() != "table") }
    var expanded by remember { mutableStateOf<Set<String>?>(null) }
    var sort by remember { mutableStateOf(runCatching { window.localStorage.getItem("amie.packagesSort") }.getOrNull() ?: "name") }
    var stats by remember { mutableStateOf<Map<String, PackageStats>>(emptyMap()) }
    var myLikes by remember { mutableStateOf<Set<String>>(emptySet()) }
    val flash = remember {
        when {
            !uploaded.isNullOrEmpty() -> "$uploaded was uploaded. It is in the list below and on the downloads page."
            !updated.isNullOrEmpty() -> "$updated was replaced with the new version."
            else -> null
        }
    }

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

    /** The errors the owner's app reported; only the owner's token can read them, so guests skip this. */
    suspend fun loadErrors() {
        if (username.isEmpty() || loggedInUser != username) return
        val token = AuthClient.accessToken() ?: return
        val text = runCatching {
            Http.get(AmieConfig.api("errors?username=${encodeURIComponent(username)}"), Http.bearer(token), noStore = true)
        }.getOrNull()?.takeIf { it.ok }?.text ?: "[]"
        errors = parseList(text)
    }

    LaunchedEffect(username) {
        if (flash != null) {
            // The confirmation is shown once; a reload should not repeat it
            runCatching { window.history.replaceState(null, "", "/dashboard?username=${encodeURIComponent(username)}") }
        }
        loggedInUser = AuthClient.currentUser()
        load(refresh = flash != null)
        loadErrors()
        profile = loadPublicProfile(username)
        stats = loadStats(username)
        myLikes = loadMyLikes()
    }

    fun likeChanged(path: String, liked: Boolean, count: Long) {
        myLikes = if (liked) myLikes + path else myLikes - path
        val old = stats[path] ?: PackageStats(path)
        stats = stats + (path to old.copy(likes = count))
        ClientCache.remove("stats:$username")
    }

    val isOwner = loggedInUser != null && loggedInUser == username
    val pageUrl = window.location.origin + "/dashboard?username=${encodeURIComponent(username)}"

    PageLayout(if (username.isEmpty()) "Dashboard" else "$username's packages") {
        if (username.isEmpty()) {
            PageHeading("Dashboard")
            EmptyAction(
                "Whose packages?",
                "Pick an author on the packages page, or sign in to see your own packages, devices and app errors.",
                action = {
                    LinkButton("Browse the packages", "/")
                    LinkButton("Sign in", "/loginpage?next=%2Fdashboard", primary = false)
                }
            )
            return@PageLayout
        }

        Breadcrumbs(Crumb("Packages", "/"), Crumb(username))
        PageHeading(if (isOwner) "Your packages" else "Packages by $username", if (isOwner) "Signed in as $username" else "Everything $username published") {
            GhostButton(if (loading) "Refreshing…" else "Refresh", enabled = !loading) { scope.launch { load(refresh = true); loadErrors() } }
            CopyButton(pageUrl, label = "Copy link", toast = "Link to this page copied.")
            if (isOwner) {
                LinkButton("Upload a package", "/navigateto?username=${encodeURIComponent(username)}")
                LinkButton("Profile", "/profile", primary = false)
            }
        }

        profile?.let { p ->
            ProfileBanner(p) {
                Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(1.cssRem).toAttrs()) {
                    Avatar(p, size = 3.5.cssRem)
                    Div {
                        Div(Modifier.fontSize(14.px).toAttrs()) { Text(p.displayName) }
                        MutedText(listOfNotNull(if (p.nickname.isNotEmpty()) "@${p.username}" else null, p.fullName.takeIf { it.isNotEmpty() }).joinToString(" · ").ifEmpty { "No profile details yet" }, Modifier.margin(top = 0.3.cssRem))
                    }
                }
                BadgeRow(p.badges)
            }
        }

        StatusBanner(flash, BannerKind.SUCCESS, Modifier.margin(bottom = 1.cssRem))
        StatusBanner(notice, noticeKind, Modifier.margin(bottom = 1.cssRem))

        if (!loading || packages.isNotEmpty()) {
            val totalSize = packages.sumOf { (it["size"] as? Number)?.toDouble() ?: 0.0 }
            val jars = packages.count { PackageKind.of(it["name"] as? String ?: "") == PackageKind.JAR }
            StatRow {
                StatTile("Packages", packages.size.toString(), if (jars > 0) plural(jars, "jar") + " for the app" else "none are jars")
                StatTile("Total size", formatSize(totalSize))
                StatTile("Devices", devices.size.toString(), if (devices.isEmpty()) "none reported yet" else "reported by the app")
                if (isOwner) StatTile("App errors", errors.size.toString(), errors.firstOrNull()?.let { "last one " + formatRelative(it["time"] as? String) } ?: "none reported")
            }
        }

        TabBar(
            buildList {
                add(Tab("packages", "Packages", packages.size))
                add(Tab("devices", "Devices", devices.size))
                add(Tab("more", "Links & publications"))
                if (isOwner) add(Tab("errors", "App errors", errors.size, alert = true))
            },
            tab.value
        ) { tab.value = it }

        if (tab.value == "devices") {
        SectionHeading("Devices", id = "devices")
        if (devices.isEmpty()) {
            EmptyAction(
                "No devices have reported a status.",
                if (isOwner) "Devices appear here once the Amie desktop app syncs them." else "$username's app has not synced a device yet.",
                Modifier.margin(bottom = 2.cssRem),
                action = { LinkButton("How devices work", "/docs/devices", primary = false) }
            )
        } else {
            TableScroll(Modifier.margin(bottom = 2.cssRem)) {
                Table(DataTableStyle.toModifier().toAttrs()) {
                    Thead { Tr { Th { Text("Device") }; Th { Text("Name") }; Th { Text("Port") }; Th { Text("Endpoint") } } }
                    Tbody {
                        for (device in devices) {
                            Tr {
                                Td { Text(device["deviceKey"] as? String ?: "–") }
                                Td { Text(device["name"] as? String ?: "–") }
                                Td { Text(device["port"] as? String ?: "–") }
                                Td {
                                    val endpoint = device["deviceEndpoint"] as? String
                                    if (endpoint.isNullOrBlank()) Text("–") else Code(attrs = Modifier.fontSize(9.px).toAttrs()) { Text(endpoint) }
                                }
                            }
                        }
                    }
                }
            }
        }

        }
        if (tab.value == "errors") {
        if (isOwner) {
            SectionHeading("Errors from your app", id = "errors") {
                if (errors.isNotEmpty()) {
                    ConfirmDangerButton("Clear log", "Remove every entry?", "Yes, clear") {
                        scope.launch {
                            val result = runCatching {
                                Http.postForm(AmieConfig.api("errors-clear?username=${encodeURIComponent(username)}"), emptyMap(), Http.bearer(AuthClient.accessToken()))
                            }.getOrNull()
                            if (result?.ok == true) {
                                notice = "The error log was cleared."
                                noticeKind = BannerKind.SUCCESS
                            } else {
                                notice = if (result?.status == 401) "Please sign in again to clear the error log." else "The error log could not be cleared."
                                noticeKind = BannerKind.ERROR
                            }
                            loadErrors()
                        }
                    }
                }
            }
            if (errors.isEmpty()) {
                Card(Modifier.margin(bottom = 2.cssRem)) { MutedText("Your app has not reported an error. Anything it logs as an error shows up here with the time it happened.") }
            } else {
                TableScroll(Modifier.margin(bottom = 2.cssRem)) {
                    Table(DataTableStyle.toModifier().toAttrs()) {
                        Thead { Tr { Th { Text("When") }; Th { Text("Kind") }; Th { Text("Message") }; Th { Text("") } } }
                        Tbody {
                            for (entry in errors) {
                                val time = entry["time"] as? String
                                val message = entry["message"] as? String ?: "–"
                                Tr {
                                    Td { SpanText(formatRelative(time), Modifier.whiteSpace(WhiteSpace.NoWrap).attrsModifier { attr("title", formatDate(time)) }) }
                                    Td { Badge(entry["error"] as? String ?: "error", BadgeTone.MUTED) }
                                    Td { SpanText(message, Modifier.styleModifier { property("word-break", "break-word") }) }
                                    Td {
                                        Div(Modifier.display(DisplayStyle.Flex).justifyContent(JustifyContent.FlexEnd).toAttrs()) {
                                            CopyButton(message, label = "Copy", done = "Copied", toast = "Error message copied.", plain = true)
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
        if (tab.value == "packages") {
        SectionHeading("Packages", id = "packages") {
            Div(Modifier.display(DisplayStyle.Flex).gap(0.4.cssRem).alignItems(AlignItems.Center).flexWrap(FlexWrap.Wrap).toAttrs { attr("role", "group"); attr("aria-label", "How the packages are shown") }) {
                Chip("Tree", treeView) { treeView = true; runCatching { window.localStorage.setItem("amie.packagesView", "tree") } }
                Chip("Table", !treeView) { treeView = false; runCatching { window.localStorage.setItem("amie.packagesView", "table") } }
            }
            SelectField(sort, listOf("name" to "Sort: name", "downloads" to "Sort: most downloaded", "likes" to "Sort: most liked"), { sort = it; runCatching { window.localStorage.setItem("amie.packagesSort", it) } }, Modifier.height(2.cssRem), ariaLabel = "Sort packages")
            if (packages.size > 4) {
                TextField(filter, { filter = it }, placeholder = "Filter by name…", modifier = Modifier.width(16.cssRem).height(2.cssRem).attrsModifier { attr("data-search", ""); attr("aria-label", "Filter packages") })
            }
        }
        val visible = packages.filter { filter.isBlank() || (it["name"] as? String ?: "").contains(filter.trim(), ignoreCase = true) }.let { list ->
            when (sort) {
                "downloads" -> list.sortedByDescending { stats[it["path"] as? String ?: ""]?.downloads ?: 0L }
                "likes" -> list.sortedByDescending { stats[it["path"] as? String ?: ""]?.likes ?: 0L }
                else -> list.sortedBy { (it["name"] as? String ?: "").lowercase() }
            }
        }
        val signedIn = loggedInUser != null
        when {
            loading && packages.isEmpty() -> SkeletonRows(rows = 3, label = "Loading packages…")
            packages.isEmpty() -> EmptyAction(
                if (isOwner) "You have not uploaded a package yet." else "$username has not published a package yet.",
                if (isOwner) "Upload a jar that exposes run(text) and close(); it is checked before it is stored." else null,
                action = { if (isOwner) LinkButton("Upload your first package", "/navigateto?username=${encodeURIComponent(username)}") else LinkButton("Browse other authors", "/", primary = false) }
            )
            visible.isEmpty() -> EmptyAction("Nothing matches “${filter.trim()}”.", action = { GhostButton("Clear the filter") { filter = "" } })
            treeView -> {
                // Folders are the language folders the store files packages under (uploads/<user>/<folder>/<file>)
                val folders = visible.groupBy { item ->
                    val path = item["path"] as? String ?: ""
                    path.removePrefix("uploads/").substringAfter('/', "").substringBeforeLast('/', "").ifEmpty { "files" }
                }.toList().sortedBy { it.first.lowercase() }
                val nodes = folders.map { (folder, items) ->
                    TreeNode("folder:$folder", folder, plural(items.size, "file") + " · " + formatSize(items.sumOf { (it["size"] as? Number)?.toDouble() ?: 0.0 }), children = items.map { item ->
                        val name = item["name"] as? String ?: "Unknown"
                        val path = item["path"] as? String ?: ""
                        val size = (item["size"] as? Number)?.toDouble() ?: 0.0
                        val downloadUrl = item["download_url"] as? String ?: item["downloadUrl"] as? String ?: ""
                        val kind = PackageKind.of(name)
                        val viewUrl = "/view?package=${encodeURIComponent(path)}&username=${encodeURIComponent(username)}&from=dashboard&url=${encodeURIComponent(downloadUrl)}"
                        TreeNode("file:$path", name, listOfNotNull(formatSize(size), statsLabel(stats[path])).joinToString(" · "), badge = kind.label, icon = { KindIcon(kind) }, href = viewUrl, actions = {
                            LikeButton(path, stats[path]?.likes ?: 0L, path in myLikes, signedIn) { liked, count -> likeChanged(path, liked, count) }
                            PackageActions(name, path, viewUrl, downloadUrl, isOwner, username, ctx, scope, onNotice = { text, kind -> notice = text; noticeKind = kind }, reload = { load(refresh = true) })
                        })
                    })
                }
                val open = expanded ?: nodes.map { it.id }.toSet()
                Card(Modifier.padding(0.4.cssRem, 0.6.cssRem)) {
                    TreeView(nodes, open, onToggle = { id -> expanded = if (id in open) open - id else open + id })
                }
            }
            else -> TableScroll {
                Table(DataTableStyle.toModifier().toAttrs()) {
                    Thead { Tr { Th { Text("Name") }; Th { Text("Kind") }; Th { Text("Size") }; Th { Text("") } } }
                    Tbody {
                        for (item in visible) {
                            val name = item["name"] as? String ?: "Unknown"
                            val path = item["path"] as? String ?: ""
                            val size = (item["size"] as? Number)?.toDouble() ?: 0.0
                            val downloadUrl = item["download_url"] as? String ?: item["downloadUrl"] as? String ?: ""
                            val kind = PackageKind.of(name)
                            val viewUrl = "/view?package=${encodeURIComponent(path)}&username=${encodeURIComponent(username)}&from=dashboard&url=${encodeURIComponent(downloadUrl)}"
                            Tr {
                                Td {
                                    Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.5.cssRem).toAttrs()) {
                                        KindIcon(kind)
                                        A(viewUrl, attrs = Modifier.fontWeight(FontWeight.Medium).textDecorationLine(com.varabyte.kobweb.compose.css.TextDecorationLine.None).toAttrs()) { Text(name) }
                                    }
                                }
                                Td { Badge(kind.label) }
                                Td { Text(formatSize(size)) }
                                Td {
                                    Div(Modifier.display(DisplayStyle.Flex).gap(0.4.cssRem).justifyContent(JustifyContent.FlexEnd).flexWrap(FlexWrap.Wrap).toAttrs()) {
                                        PackageActions(name, path, viewUrl, downloadUrl, isOwner, username, ctx, scope, onNotice = { text, kind -> notice = text; noticeKind = kind }, reload = { load(refresh = true) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (isOwner && packages.isNotEmpty()) {
            HintText("Replace keeps the file name and swaps the content for everyone; Delete asks once before it removes the file.", Modifier.margin(top = 0.8.cssRem))
        }

        }
        if (tab.value == "more") {
            val p = profile
            if (p != null && p.github.isEmpty() && p.huggingFace.isEmpty()) {
                EmptyAction(
                    "No linked accounts.",
                    if (isOwner) "Add your GitHub and Hugging Face usernames on your profile and their public repositories and models show up here." else "$username has not linked GitHub or Hugging Face.",
                    Modifier.margin(bottom = 1.5.cssRem),
                    action = { if (isOwner) LinkButton("Edit profile", "/profile", primary = false) }
                )
            }
            p?.let { LinkedAccountsView(username, it.github, it.huggingFace) }
            PublicationsView(username)
        }
    }
}

/** View, Download and, for the owner, Replace and Delete: the same buttons in the tree and in the table */
@Composable
private fun PackageActions(
    name: String, path: String, viewUrl: String, downloadUrl: String, isOwner: Boolean, username: String,
    ctx: com.varabyte.kobweb.core.PageContext, scope: kotlinx.coroutines.CoroutineScope,
    onNotice: (String, BannerKind) -> Unit, reload: suspend () -> Unit,
) {
    GhostButton("View", plain = true) { ctx.router.navigateTo(viewUrl) }
    if (downloadUrl.isNotEmpty()) LinkButton("Download", downloadUrl, download = true, plain = true, onClick = { trackDownload(path) })
    if (isOwner) {
        GhostButton("Replace", plain = true) {
            ctx.router.navigateTo("/edit?package=${encodeURIComponent(path)}&username=${encodeURIComponent(username)}")
        }
        ConfirmDangerButton("Delete", "Delete $name for everyone?", "Yes, delete", plain = true) {
            scope.launch {
                onNotice("Deleting $name…", BannerKind.INFO)
                val token = AuthClient.accessToken()
                val result = runCatching {
                    Http.get(AmieConfig.api("delete-package?package=${encodeURIComponent(path)}&username=${encodeURIComponent(username)}&token=${encodeURIComponent(token.orEmpty())}"))
                }.getOrNull()
                if (result?.ok == true) {
                    ClientCache.removePrefix("packages:")
                    ClientCache.remove("jars:list")
                    onNotice("$name was deleted.", BannerKind.SUCCESS)
                    Toasts.show("$name was deleted.", BannerKind.SUCCESS)
                    reload()
                } else {
                    onNotice(when (result?.status) {
                        401 -> "Please sign in again to delete packages."
                        403 -> "Only the author can delete this package."
                        else -> "$name could not be deleted."
                    }, BannerKind.ERROR)
                }
            }
        }
    }
}
