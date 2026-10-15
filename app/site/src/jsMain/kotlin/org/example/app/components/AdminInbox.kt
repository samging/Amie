package org.example.app.components

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.ObjectFit
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.components.navigation.Link
import com.varabyte.kobweb.silk.components.navigation.UncoloredLinkVariant
import com.varabyte.kobweb.silk.components.navigation.UndecoratedLinkVariant
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.app.auth.AuthClient
import org.example.app.auth.AuthSession
import org.example.app.components.widgets.*
import org.example.app.config.AmieConfig
import org.example.app.config.encodeURIComponent
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** The admin's inbox as `/api/admin-inbox` answers it. */
@Serializable data class PendingPicture(val username: String = "", val nickname: String = "", val contentType: String = "", val bytes: Int = 0, val kind: String = "avatar") {
    val isBackground: Boolean get() = kind == "background"
    val what: String get() = if (isBackground) "background" else "picture"
}
@Serializable data class AdminInbox(val pendingAvatars: Int = 0, val notesLastDay: Long = 0, val total: Long = 0, val pending: List<PendingPicture> = emptyList(), val notes: List<Note> = emptyList())

private val inboxJson = Json { ignoreUnknownKeys = true }
val INBOX_RED: Color = Color.rgb(0xD64545)

/** Loads the inbox; null when the person is not an admin or the backend is unreachable. Cached half a minute. */
suspend fun loadAdminInbox(refresh: Boolean = false): AdminInbox? {
    if (!AuthSession.isAdmin) return null
    return try {
        if (refresh) ClientCache.remove("admin:inbox")
        val text = ClientCache.cached("admin:inbox", ClientCache.MINUTE / 2) {
            val result = Http.get(AmieConfig.api("admin-inbox"), Http.bearer(AuthClient.accessToken()), noStore = true)
            if (!result.ok) throw IllegalStateException()
            result.text
        }
        inboxJson.decodeFromString(AdminInbox.serializer(), text)
    } catch (e: Throwable) { null }
}

/** A red counter; nothing when the count is zero. */
@Composable
fun RedCount(count: Long, label: String) {
    if (count <= 0) return
    Span(
        Modifier.display(DisplayStyle.InlineBlock).minWidth(1.2.cssRem).padding(0.15.cssRem, 0.4.cssRem).fontSize(8.px).lineHeight(1.4)
            .backgroundColor(INBOX_RED).color(Colors.White).fontWeight(FontWeight.Bold).textAlign(com.varabyte.kobweb.compose.css.TextAlign.Center)
            .border(2.px, LineStyle.Solid, Colors.White).toAttrs { attr("aria-label", "$count $label"); attr("title", "$count $label") }
    ) { Text(if (count > 99) "99+" else count.toString()) }
}

/** The header's "Inbox" link with its red count, for administrators only. */
@Composable
fun AdminInboxLink(active: Boolean) {
    if (!AuthSession.isAdmin) return
    var inbox by remember { mutableStateOf<AdminInbox?>(null) }
    LaunchedEffect(Unit) { inbox = loadAdminInbox() }
    Link(
        "/admin#inbox", Modifier.display(DisplayStyle.LegacyInlineFlex).alignItems(AlignItems.Center).gap(0.3.cssRem).fontSize(10.px).lineHeight(1).color(Colors.White).padding(topBottom = 0.4.cssRem)
            .borderBottom(2.px, LineStyle.Solid, if (active) Colors.White else Colors.Transparent),
        variant = UndecoratedLinkVariant.then(UncoloredLinkVariant)
    ) {
        Text("Inbox")
        inbox?.let { RedCount(it.total, "items waiting") }
    }
}

/**
 * A picture waiting for approval, fetched with the admin's bearer as a blob (the picture is not public yet).
 * [large] shows it at full size; otherwise a thumbnail that opens the large view on click.
 */
@Composable
fun PendingPicture(username: String, large: Boolean = false, onOpen: (() -> Unit)? = null, kind: String = "avatar") {
    var objectUrl by remember { mutableStateOf<String?>(null) }
    var size by remember { mutableStateOf("") }
    LaunchedEffect(username) {
        try {
            val token = AuthClient.accessToken()
            val headers = js("{}")
            headers["Authorization"] = "Bearer $token"
            val init = org.w3c.fetch.RequestInit(method = "GET", headers = headers)
            val response = window.fetch(AmieConfig.api((if (kind == "background") "background-preview" else "avatar-preview") + "?username=${encodeURIComponent(username)}"), init).await()
            if (response.ok) objectUrl = org.w3c.dom.url.URL.createObjectURL(response.blob().await())
        } catch (e: Throwable) { objectUrl = null }
    }
    DisposableEffect(objectUrl) { val url = objectUrl; onDispose { url?.let { runCatching { org.w3c.dom.url.URL.revokeObjectURL(it) } } } }
    val url = objectUrl
    if (url == null) {
        Div(Modifier.size(if (large) 16.cssRem else 4.cssRem).display(DisplayStyle.Flex).alignItems(AlignItems.Center).justifyContent(JustifyContent.Center).border(2.px, LineStyle.Dashed, Colors.White).toAttrs()) { MutedText("…") }
        return
    }
    Img(
        src = url, alt = "Picture uploaded by $username, awaiting approval",
        attrs = Modifier.then(if (large) Modifier.styleModifier { property("max-width", "min(90vw, 40rem)"); property("min-width", "min(80vw, 16rem)"); property("min-height", "12rem"); property("image-rendering", "auto") }.maxHeight(70.vh) else if (kind == "background") Modifier.width(7.cssRem).height(4.cssRem).cursor(Cursor.Pointer) else Modifier.size(4.cssRem).cursor(Cursor.Pointer))
            .border(2.px, LineStyle.Dashed, INBOX_RED).objectFit(ObjectFit.Contain).backgroundColor(Colors.Black)
            .toAttrs {
                attr("title", if (large) "" else "Open at full size")
                if (onOpen != null) { attr("role", "button"); attr("tabindex", "0"); onClick { onOpen() }; onKeyDown { if (it.key == "Enter" || it.key == " ") { it.preventDefault(); onOpen() } } }
                ref { img -> img.onload = { size = "${img.naturalWidth} × ${img.naturalHeight} px"; Unit }; onDispose { img.onload = null } }
            }
    )
    if (large && size.isNotEmpty()) MutedText(size, Modifier.fontSize(8.px).margin(top = 0.3.cssRem))
}

/** The full-size view of one waiting picture with Approve and Reject, over the page; Escape or the backdrop closes it. */
@Composable
fun PendingPictureViewer(picture: PendingPicture, onApprove: () -> Unit, onReject: () -> Unit, onClose: () -> Unit) {
    DisposableEffect(Unit) {
        val handler: (org.w3c.dom.events.Event) -> Unit = { e -> if ((e as? org.w3c.dom.events.KeyboardEvent)?.key == "Escape") onClose() }
        window.addEventListener("keydown", handler)
        onDispose { window.removeEventListener("keydown", handler) }
    }
    Div(
        Modifier.position(Position.Fixed).top(0.px).left(0.px).right(0.px).bottom(0.px).zIndex(50)
            .backgroundColor(Color.rgba(0, 0, 0, 0.85f)).display(DisplayStyle.Flex).alignItems(AlignItems.Center).justifyContent(JustifyContent.Center).padding(1.cssRem)
            .toAttrs { attr("role", "dialog"); attr("aria-modal", "true"); attr("aria-label", "Picture awaiting approval"); onClick { onClose() } }
    ) {
        Card(Modifier.maxWidth(36.cssRem).onClick { it.stopPropagation() }) {
            Div(Modifier.display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).alignItems(AlignItems.Center).gap(0.6.cssRem).toAttrs()) {
                PendingPicture(picture.username, large = true, kind = picture.kind)
                Div(Modifier.textAlign(com.varabyte.kobweb.compose.css.TextAlign.Center).toAttrs()) {
                    A("/dashboard?username=${encodeURIComponent(picture.username)}", attrs = Modifier.fontWeight(FontWeight.Medium).toAttrs()) { Text(picture.nickname.ifEmpty { picture.username }) }
                    MutedText(listOfNotNull(picture.what, picture.nickname.takeIf { it.isNotEmpty() }?.let { "@${picture.username}" }, picture.contentType, formatSize(picture.bytes.toDouble())).joinToString(" · "), Modifier.fontSize(8.px))
                }
                Div(Modifier.display(DisplayStyle.Flex).gap(0.5.cssRem).flexWrap(FlexWrap.Wrap).justifyContent(JustifyContent.Center).toAttrs()) {
                    PrimaryButton("Approve") { onApprove() }
                    DangerButton("Reject") { onReject() }
                    GhostButton("Close") { onClose() }
                }
            }
        }
    }
}

/** One waiting picture as a card: thumbnail (click to view), who, Approve / Reject. */
@Composable
fun PendingPictureCard(picture: PendingPicture, onView: () -> Unit, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(Modifier.padding(0.7.cssRem, 1.cssRem).minWidth(16.cssRem)) {
        Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.8.cssRem).toAttrs()) {
            PendingPicture(picture.username, onOpen = onView, kind = picture.kind)
            Div(Modifier.minWidth(0.px).toAttrs()) {
                A("/dashboard?username=${encodeURIComponent(picture.username)}", attrs = Modifier.fontWeight(FontWeight.Medium).toAttrs()) { Text(picture.nickname.ifEmpty { picture.username }) }
                MutedText(listOfNotNull(picture.what, picture.nickname.takeIf { it.isNotEmpty() }?.let { "@${picture.username}" }, picture.contentType, formatSize(picture.bytes.toDouble())).joinToString(" · "), Modifier.fontSize(8.px))
                Div(Modifier.display(DisplayStyle.Flex).gap(0.4.cssRem).margin(top = 0.5.cssRem).flexWrap(FlexWrap.Wrap).toAttrs()) {
                    GhostButton("View") { onView() }
                    PrimaryButton("Approve") { onApprove() }
                    ConfirmDangerButton("Reject", "Remove this picture?", "Yes, remove") { onReject() }
                }
            }
        }
    }
}

/**
 * The inbox itself: a red heading count, the pictures to approve (each viewable at full size) and the
 * notes of the last day, with links into the admin page. Shown on the profile page and on `/admin#inbox`.
 */
@Composable
fun AdminInboxPanel(modifier: Modifier = Modifier) {
    if (!AuthSession.isAdmin) return
    val scope = rememberCoroutineScope()
    var inbox by remember { mutableStateOf<AdminInbox?>(null) }
    var viewing by remember { mutableStateOf<PendingPicture?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { inbox = loadAdminInbox() }

    fun act(picture: PendingPicture, action: String, done: String) {
        scope.launch {
            val result = runCatching { Http.postForm(AmieConfig.api("admin-user-action?username=${encodeURIComponent(picture.username)}&action=$action"), emptyMap(), Http.bearer(AuthClient.accessToken())) }.getOrNull()
            if (result?.ok == true || result?.status == 404) {
                ClientCache.remove("profile:${picture.username}")
                viewing = null
                Toasts.show(done, BannerKind.SUCCESS)
                inbox = loadAdminInbox(refresh = true)
            } else { notice = "That could not be done right now."; }
        }
    }

    val box = inbox
    Card(modifier) {
        Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.5.cssRem).margin(bottom = 0.4.cssRem).toAttrs { attr("id", "inbox") }) {
            Div(Modifier.fontSize(12.px).toAttrs()) { Text("Inbox") }
            box?.let { RedCount(it.total, "items waiting") }
            Span(Modifier.styleModifier { property("margin-left", "auto") }.toAttrs()) { GhostButton("Refresh") { scope.launch { inbox = loadAdminInbox(refresh = true) } } }
        }
        StatusBanner(notice, BannerKind.ERROR)
        when {
            box == null -> SkeletonRows(rows = 2, label = "Checking the inbox…")
            box.total == 0L -> MutedText("Nothing is waiting: no pictures to approve and no notes in the last day.")
            else -> {
                if (box.pending.isNotEmpty()) {
                    Div(Modifier.fontSize(10.px).margin(topBottom = 0.5.cssRem).toAttrs()) { Text(plural(box.pending.size, "image") + " to approve") }
                    Div(Modifier.display(DisplayStyle.Flex).flexWrap(FlexWrap.Wrap).gap(0.6.cssRem).toAttrs()) {
                        for (p in box.pending) PendingPictureCard(p, onView = { viewing = p }, onApprove = { act(p, if (p.isBackground) "approve-background" else "approve-avatar", "${p.what.replaceFirstChar { it.uppercase() }} of ${p.username} approved.") }, onReject = { act(p, if (p.isBackground) "clear-background" else "clear-avatar", "${p.what.replaceFirstChar { it.uppercase() }} of ${p.username} removed.") })
                    }
                }
                if (box.notesLastDay > 0) {
                    Div(Modifier.fontSize(10.px).margin(topBottom = 0.5.cssRem).toAttrs()) { Text(plural(box.notesLastDay.toInt(), "note") + " in the last day") }
                    Div(Modifier.display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).gap(0.3.cssRem).fontSize(9.px).toAttrs()) {
                        for (note in box.notes) {
                            Div {
                                A("/dashboard?username=${encodeURIComponent(note.author)}", attrs = Modifier.fontWeight(FontWeight.Medium).toAttrs()) { Text(note.authorNickname.ifEmpty { note.author }) }
                                MutedText(" on ", Modifier.fontSize(8.px).display(DisplayStyle.Inline))
                                A("/view?package=${encodeURIComponent(note.packagePath)}") { Text(note.packagePath.removePrefix("uploads/")) }
                                MutedText(" · " + formatRelative(note.createdAt) + " · " + note.text.take(80) + (if (note.text.length > 80) "…" else ""), Modifier.fontSize(8.px).display(DisplayStyle.Inline))
                            }
                        }
                    }
                }
                Div(Modifier.margin(top = 0.8.cssRem).toAttrs()) { LinkButton("Open the admin page", "/admin", primary = false) }
            }
        }
    }
    viewing?.let { p -> PendingPictureViewer(p, onApprove = { act(p, if (p.isBackground) "approve-background" else "approve-avatar", "${p.what.replaceFirstChar { it.uppercase() }} of ${p.username} approved.") }, onReject = { act(p, if (p.isBackground) "clear-background" else "clear-avatar", "${p.what.replaceFirstChar { it.uppercase() }} of ${p.username} removed.") }, onClose = { viewing = null }) }
}
