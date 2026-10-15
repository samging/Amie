package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.WhiteSpace
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.attrsModifier
import com.varabyte.kobweb.compose.ui.styleModifier
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
import org.example.app.components.Note
import org.example.app.components.BADGE_LABELS
import org.example.app.components.PendingPictureCard
import org.example.app.components.PendingPictureViewer
import kotlinx.serialization.Serializable
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

/** One account as the admin panel lists it (`/api/admin-users`). */
@Serializable data class AdminProfile(val username: String = "", val fullName: String = "", val email: String = "", val nickname: String = "", val hasAvatar: Boolean = false, val avatarVersion: String = "", val avatarPending: Boolean = false, val badges: List<String> = emptyList())
typealias PendingAvatar = org.example.app.components.PendingPicture
@Serializable data class AdminUser(val profile: AdminProfile = AdminProfile(), val notes: Long = 0, val hasPassword: Boolean = false)

/**
 * Administration, for a Keycloak account with the admin role: remove any package or build, publish
 * application builds, and moderate people (nicknames, pictures, whole accounts) and notes. The role is
 * verified by the site's server and again by the backend on every action.
 */
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
    var people by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var latestNotes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var peopleFilter by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<List<PendingAvatar>>(emptyList()) }
    var viewing by remember { mutableStateOf<PendingAvatar?>(null) }
    val tab = rememberTab("inbox", listOf("inbox", "people", "store", "publish"))

    suspend fun bearer(): Map<String, String> = Http.bearer(AuthClient.accessToken())

    suspend fun loadAll() {
        loading = true
        try {
            val jarText = Http.get(AmieConfig.api("jars?refresh=1")).takeIf { it.ok }?.text ?: "[]"
            jars = runCatching { jwtJson.decodeFromString(ListSerializer(JarInfo.serializer()), jarText) }.getOrDefault(emptyList())
            packages = Catalog.load(refresh = true)
            val peopleText = Http.get(AmieConfig.api("admin-users"), bearer(), noStore = true).takeIf { it.ok }?.text ?: "[]"
            people = runCatching { jwtJson.decodeFromString(ListSerializer(AdminUser.serializer()), peopleText) }.getOrDefault(emptyList())
            val notesText = Http.get(AmieConfig.api("admin-notes?limit=100"), bearer(), noStore = true).takeIf { it.ok }?.text ?: "[]"
            latestNotes = runCatching { jwtJson.decodeFromString(ListSerializer(Note.serializer()), notesText) }.getOrDefault(emptyList())
            val pendingText = Http.get(AmieConfig.api("admin-avatars"), bearer(), noStore = true).takeIf { it.ok }?.text ?: "[]"
            pending = runCatching { jwtJson.decodeFromString(ListSerializer(PendingAvatar.serializer()), pendingText) }.getOrDefault(emptyList())
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

    fun userAction(user: AdminUser, action: String, done: String) {
        scope.launch {
            val result = runCatching { Http.postForm(AmieConfig.api("admin-user-action?username=${encodeURIComponent(user.profile.username)}&action=$action"), emptyMap(), bearer()) }.getOrNull()
            if (result?.ok == true) {
                ClientCache.remove("profile:${user.profile.username}")
                Toasts.show(done, BannerKind.SUCCESS)
                loadAll()
            } else {
                notice = when (result?.status) { 403 -> "Your account lost the admin role."; 404 -> "That account is already gone."; 400 -> "That action is not allowed on this account."; else -> "The action could not be carried out right now." }
                noticeKind = BannerKind.ERROR
            }
        }
    }

    fun badgeAction(user: AdminUser, badge: String, grant: Boolean) {
        scope.launch {
            val result = runCatching { Http.postForm(AmieConfig.api("admin-badge?username=${encodeURIComponent(user.profile.username)}&badge=$badge&action=${if (grant) "grant" else "revoke"}"), emptyMap(), bearer()) }.getOrNull()
            if (result?.ok == true) { ClientCache.remove("profile:${user.profile.username}"); Toasts.show(if (grant) "Badge granted." else "Badge revoked.", BannerKind.SUCCESS); loadAll() }
            else { notice = "The badge could not be changed right now."; noticeKind = BannerKind.ERROR }
        }
    }

    fun deleteNote(note: Note) {
        scope.launch {
            val result = runCatching { Http.postForm(AmieConfig.api("admin-note-delete?id=${note.id}"), emptyMap(), bearer()) }.getOrNull()
            if (result?.ok == true || result?.status == 404) {
                latestNotes = latestNotes.filter { it.id != note.id }
                ClientCache.remove("notes:${note.packagePath}")
                Toasts.show("Note removed.", BannerKind.SUCCESS)
            } else {
                notice = "The note could not be removed right now."; noticeKind = BannerKind.ERROR
            }
        }
    }

    val builds = jars.filter { it.kind == "application" }
    val visiblePeople = people.filter { peopleFilter.isBlank() || it.profile.username.contains(peopleFilter.trim(), ignoreCase = true) || it.profile.nickname.contains(peopleFilter.trim(), ignoreCase = true) || it.profile.fullName.contains(peopleFilter.trim(), ignoreCase = true) }
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
                    StatTile("Inbox", pending.size.toString(), if (pending.isEmpty()) "nothing to approve" else plural(pending.size, "picture") + " to approve")
                    StatTile("People", people.size.toString(), plural(people.count { it.profile.hasAvatar }, "picture") + " · " + plural(people.count { it.profile.nickname.isNotEmpty() }, "nickname"))
                    StatTile("Store", packages.size.toString(), formatSize(packages.sumOf { it.size }) + " · " + plural(packages.map { it.author }.filter { it.isNotEmpty() }.distinct().size, "author"))
                    StatTile("App builds", builds.size.toString(), builds.mapNotNull { it.lastModified }.maxOrNull()?.let { "latest ${formatRelative(it)}" } ?: "none published")
                }

                TabBar(
                    listOf(
                        Tab("inbox", "Inbox", pending.size, alert = true),
                        Tab("people", "People", people.size),
                        Tab("store", "Store & downloads", packages.size + jars.size),
                        Tab("publish", "Publish a build"),
                    ),
                    tab.value
                ) { tab.value = it }

                if (tab.value == "inbox") {
                // Pictures waiting for approval
                SectionHeading("Pictures and backgrounds waiting for approval")
                MutedText("A picture is shown next to a person's packages and notes only after one of you approves it. Reject removes it.", Modifier.margin(bottom = 0.6.cssRem))
                if (pending.isEmpty()) {
                    Card(Modifier.margin(bottom = 2.cssRem)) { MutedText(if (loading) "Loading…" else "No pictures are waiting.") }
                } else {
                    Div(Modifier.display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.8.cssRem).margin(bottom = 2.cssRem).toAttrs { attr("id", "inbox") }) {
                        for (p in pending) {
                            PendingPictureCard(
                                p, onView = { viewing = p },
                                onApprove = { viewing = null; userAction(AdminUser(AdminProfile(username = p.username)), if (p.isBackground) "approve-background" else "approve-avatar", "${p.what.replaceFirstChar { it.uppercase() }} of ${p.username} approved.") },
                                onReject = { viewing = null; userAction(AdminUser(AdminProfile(username = p.username)), if (p.isBackground) "clear-background" else "clear-avatar", "${p.what.replaceFirstChar { it.uppercase() }} of ${p.username} removed.") }
                            )
                        }
                    }
                    viewing?.let { p ->
                        PendingPictureViewer(
                            p,
                            onApprove = { viewing = null; userAction(AdminUser(AdminProfile(username = p.username)), if (p.isBackground) "approve-background" else "approve-avatar", "${p.what.replaceFirstChar { it.uppercase() }} of ${p.username} approved.") },
                            onReject = { viewing = null; userAction(AdminUser(AdminProfile(username = p.username)), if (p.isBackground) "clear-background" else "clear-avatar", "${p.what.replaceFirstChar { it.uppercase() }} of ${p.username} removed.") },
                            onClose = { viewing = null }
                        )
                    }
                }

                }
                if (tab.value == "publish") {
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

                }
                if (tab.value == "store") {
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
                                                LinkButton("Download", AmieConfig.downloadUrl(jar.name, jar.kind, jar.size), download = true, plain = true)
                                                ConfirmDangerButton("Remove", "Remove ${jar.name} from the bucket?", "Yes, remove", plain = true) { deleteJar(jar) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                }
                if (tab.value == "people") {
                // People
                SectionHeading("People") {
                    if (people.size > 4) TextField(peopleFilter, { peopleFilter = it }, "Filter by name…", modifier = Modifier.width(14.cssRem).height(2.cssRem).attrsModifier { attr("aria-label", "Filter people") })
                }
                MutedText("Every account the backend knows. A nickname or picture that breaks the rules can be taken away; removing an account deletes its devices, notes and activity, not its files in the store.", Modifier.margin(bottom = 0.6.cssRem))
                if (people.isEmpty()) {
                    Card(Modifier.margin(bottom = 2.cssRem)) { MutedText(if (loading) "Loading…" else "No accounts, or the list could not be read.") }
                } else if (visiblePeople.isEmpty()) {
                    EmptyAction("Nobody matches “${peopleFilter.trim()}”.", modifier = Modifier.margin(bottom = 2.cssRem), action = { GhostButton("Clear the filter") { peopleFilter = "" } })
                } else {
                    TableScroll(Modifier.margin(bottom = 2.cssRem)) {
                        Table(DataTableStyle.toModifier().toAttrs()) {
                            Thead { Tr { Th { Text("") }; Th { Text("Account") }; Th { Text("Nickname") }; Th { Text("Badges") }; Th { Text("Sign-in") }; Th { Text("Notes") }; Th { Text("") } } }
                            Tbody {
                                for (person in visiblePeople) {
                                    val p = person.profile
                                    Tr {
                                        Td { Avatar(PublicProfile(username = p.username, fullName = p.fullName, nickname = p.nickname, hasAvatar = p.hasAvatar, avatarVersion = p.avatarVersion), size = 1.8.cssRem) }
                                        Td {
                                            A("/dashboard?username=${encodeURIComponent(p.username)}", attrs = Modifier.fontWeight(FontWeight.Medium).toAttrs()) { Text(p.username) }
                                            if (p.fullName.isNotEmpty()) MutedText(p.fullName, Modifier.fontSize(8.px))
                                        }
                                        Td { if (p.nickname.isEmpty()) MutedText("–") else Text(p.nickname) }
                                        Td {
                                            // One chip per badge in the catalogue: filled when granted; click toggles
                                            Div(Modifier.display(DisplayStyle.Flex).gap(0.3.cssRem).flexWrap(FlexWrap.Wrap).toAttrs()) {
                                                for ((key, labelAndMeaning) in BADGE_LABELS) {
                                                    val has = key in p.badges
                                                    Span(Modifier.toAttrs { attr("title", labelAndMeaning.second) }) { ToneChip(labelAndMeaning.first, has, SiteTones.ofBadge(key)) { badgeAction(person, key, !has) } }
                                                }
                                            }
                                        }
                                        Td { Badge(if (person.hasPassword) "local" else "sso", if (person.hasPassword) BadgeTone.OUTLINE else BadgeTone.MUTED) }
                                        Td { Text(person.notes.toString()) }
                                        Td {
                                            Div(Modifier.display(DisplayStyle.Flex).gap(0.4.cssRem).justifyContent(JustifyContent.FlexEnd).flexWrap(FlexWrap.Wrap).toAttrs()) {
                                                if (p.nickname.isNotEmpty()) GhostButton("Clear nickname") { userAction(person, "clear-nickname", "Nickname of ${p.username} removed.") }
                                                if (p.hasAvatar) GhostButton("Remove picture") { userAction(person, "clear-avatar", "Picture of ${p.username} removed.") }
                                                if (p.username != username) ConfirmDangerButton("Delete account", "Delete the account ${p.username} with its devices, notes and activity?", "Yes, delete") { userAction(person, "delete", "Account ${p.username} deleted.") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                }
                if (tab.value == "inbox") {
                // Notes
                SectionHeading("Latest notes")
                if (latestNotes.isEmpty()) {
                    Card(Modifier.margin(bottom = 2.cssRem)) { MutedText(if (loading) "Loading…" else "Nobody has left a note yet.") }
                } else {
                    Div(Modifier.display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).gap(0.5.cssRem).margin(bottom = 2.cssRem).toAttrs()) {
                        for (note in latestNotes) {
                            Card(Modifier.padding(0.6.cssRem, 0.9.cssRem)) {
                                Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.5.cssRem).flexWrap(FlexWrap.Wrap).fontSize(9.px).toAttrs()) {
                                    A("/dashboard?username=${encodeURIComponent(note.author)}", attrs = Modifier.fontWeight(FontWeight.Medium).toAttrs()) { Text(note.authorNickname.ifEmpty { note.author }) }
                                    MutedText("on", Modifier.fontSize(8.px))
                                    A("/view?package=${encodeURIComponent(note.packagePath)}") { Text(note.packagePath.removePrefix("uploads/")) }
                                    MutedText(formatRelative(note.createdAt), Modifier.fontSize(8.px))
                                    Span(Modifier.styleModifier { property("margin-left", "auto") }.toAttrs()) { ConfirmDangerButton("Remove", "Remove this note?", "Yes, remove", plain = true) { deleteNote(note) } }
                                }
                                // Text node: shown as typed, never interpreted
                                P(Modifier.margin(top = 0.3.cssRem, bottom = 0.px).fontSize(9.px).whiteSpace(WhiteSpace.PreWrap).styleModifier { property("overflow-wrap", "anywhere") }.toAttrs()) { Text(note.text.take(600) + if (note.text.length > 600) "…" else "") }
                            }
                        }
                    }
                }

                }
                if (tab.value == "store") {
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
                                                GhostButton("View", plain = true) { ctx.router.navigateTo(pkg.viewUrl) }
                                                ConfirmDangerButton("Remove", "Remove ${pkg.name} for everyone?", "Yes, remove", plain = true) { deletePackage(pkg) }
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
