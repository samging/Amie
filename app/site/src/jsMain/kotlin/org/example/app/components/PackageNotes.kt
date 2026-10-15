package org.example.app.components

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.WhiteSpace
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.toAttrs
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.example.app.auth.AuthClient
import org.example.app.components.widgets.*
import org.example.app.config.AmieConfig
import org.example.app.config.encodeURIComponent
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** One note as the site's `/api/notes` lists it. */
@Serializable
data class Note(
    val id: Long = 0,
    val packagePath: String = "",
    val author: String = "",
    val authorNickname: String = "",
    val authorHasAvatar: Boolean = false,
    val text: String = "",
    val createdAt: String = "",
)

private val noteJson = Json { ignoreUnknownKeys = true }
const val NOTE_LIMIT = 2000

/**
 * The notes people left on a package, newest first, and a box to add one when signed in. The author or
 * the package's owner can remove a note. Every note is rendered as a text node inside a `white-space:
 * pre-wrap` paragraph: line breaks are kept, markup is shown as typed and never interpreted, which is
 * what keeps a note of `<img onerror=…>` harmless. Links in notes are not made clickable on purpose.
 */
@Composable
fun PackageNotes(packagePath: String, owner: String) {
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf<List<Note>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var me by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeKind by remember { mutableStateOf(BannerKind.INFO) }

    suspend fun load(refresh: Boolean = false) {
        try {
            val text = ClientCache.cached("notes:$packagePath", ClientCache.MINUTE / 2, refresh) {
                val result = Http.get(AmieConfig.api("notes?package=${encodeURIComponent(packagePath)}" + (if (refresh) "&refresh=1" else "")), noStore = true)
                if (!result.ok) throw IllegalStateException()
                result.text
            }
            notes = noteJson.decodeFromString(ListSerializer(Note.serializer()), text)
            failed = false
        } catch (e: Throwable) {
            failed = true
            notes = notes ?: emptyList()
        }
    }

    LaunchedEffect(packagePath) {
        me = AuthClient.currentUser()
        load()
    }

    fun post() {
        val text = draft.trim()
        if (text.isEmpty() || posting) return
        scope.launch {
            posting = true
            notice = null
            try {
                val body = """{"text":${JSON.stringify(text)}}"""
                val result = Http.postJson(AmieConfig.api("notes?package=${encodeURIComponent(packagePath)}"), body, Http.bearer(AuthClient.accessToken()))
                if (result.ok) {
                    draft = ""
                    load(refresh = true)
                    Toasts.show("Note added.", BannerKind.SUCCESS)
                } else {
                    notice = when (result.status) {
                        401 -> "Your session has ended. Sign in again to add a note."
                        404 -> "This package is no longer in the store."
                        413 -> "The note is too long."
                        429 -> "You are posting quickly; wait a moment and try again."
                        400 -> "The note could not be saved: it may be empty or too long."
                        else -> "The note could not be saved right now."
                    }
                    noticeKind = BannerKind.ERROR
                }
            } catch (e: Throwable) {
                notice = "The note could not be saved right now."
                noticeKind = BannerKind.ERROR
            } finally {
                posting = false
            }
        }
    }

    fun remove(note: Note) {
        scope.launch {
            val result = runCatching {
                Http.postForm(AmieConfig.api("notes-delete?id=${note.id}&package=${encodeURIComponent(packagePath)}"), emptyMap(), Http.bearer(AuthClient.accessToken()))
            }.getOrNull()
            if (result?.ok == true || result?.status == 404) {
                notes = notes?.filter { it.id != note.id }
                ClientCache.remove("notes:$packagePath")
                Toasts.show("Note removed.", BannerKind.SUCCESS)
            } else {
                Toasts.show(if (result?.status == 403) "Only the author or the package's owner can remove a note." else "The note could not be removed right now.", BannerKind.ERROR)
            }
        }
    }

    SectionHeading("Notes", Modifier.margin(top = 2.cssRem)) {
        notes?.let { MutedText(plural(it.size, "note", "notes"), Modifier.fontSize(9.px)) }
        GhostButton("Refresh") { scope.launch { load(refresh = true) } }
    }

    if (me != null) {
        Card(Modifier.margin(bottom = 1.cssRem)) {
            Div(Modifier.display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).gap(0.5.cssRem).toAttrs()) {
                TextAreaField(draft, { if (it.length <= NOTE_LIMIT) draft = it }, "Leave a note for the author or other people using this package", Modifier.minHeight(5.cssRem))
                StatusBanner(notice, noticeKind)
                Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.6.cssRem).flexWrap(FlexWrap.Wrap).toAttrs()) {
                    PrimaryButton(if (posting) "Posting…" else "Post note", enabled = !posting && draft.isNotBlank()) { post() }
                    HintText("${draft.length} / $NOTE_LIMIT · plain text, shown as you typed it")
                }
            }
        }
    } else {
        Card(Modifier.margin(bottom = 1.cssRem)) {
            Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.8.cssRem).flexWrap(FlexWrap.Wrap).toAttrs()) {
                MutedText("Sign in to leave a note on this package.")
                LinkButton("Sign in", "/loginpage?next=${encodeURIComponent("/view?package=$packagePath")}", primary = false)
            }
        }
    }

    when {
        notes == null -> SkeletonRows(rows = 2, label = "Loading notes…")
        failed && notes!!.isEmpty() -> Card { MutedText("The notes could not be loaded right now.") }
        notes!!.isEmpty() -> Card { MutedText("No notes yet.") }
        else -> Div(Modifier.display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).gap(0.6.cssRem).toAttrs { attr("role", "list") }) {
            for (note in notes!!) {
                val profile = PublicProfile(username = note.author, nickname = note.authorNickname, hasAvatar = note.authorHasAvatar)
                Card(Modifier.padding(0.7.cssRem, 1.cssRem)) {
                    Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.FlexStart).gap(0.8.cssRem).toAttrs { attr("role", "listitem") }) {
                        Avatar(profile, size = 2.cssRem)
                        Div(Modifier.minWidth(0.px).flexGrow(1).toAttrs()) {
                            Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(0.5.cssRem).flexWrap(FlexWrap.Wrap).fontSize(9.px).toAttrs()) {
                                A("/dashboard?username=${encodeURIComponent(note.author)}", attrs = Modifier.fontWeight(com.varabyte.kobweb.compose.css.FontWeight.SemiBold).toAttrs()) { Text(profile.displayName) }
                                if (note.authorNickname.isNotEmpty()) MutedText("@${note.author}", Modifier.fontSize(8.px))
                                if (note.author == owner) Badge("author", color = SiteTones.ofName(owner))
                                MutedText(formatRelative(note.createdAt), Modifier.fontSize(8.px))
                                if (me != null && (me == note.author || me == owner)) {
                                    Span(Modifier.styleModifier { property("margin-left", "auto") }.toAttrs()) {
                                        ConfirmDangerButton("Remove", "Remove this note?", "Yes, remove") { remove(note) }
                                    }
                                }
                            }
                            // The note is a text node: whatever it contains is shown, never interpreted
                            P(Modifier.margin(top = 0.4.cssRem, bottom = 0.px).fontSize(10.px).lineHeight(1.7).whiteSpace(WhiteSpace.PreWrap)
                                .styleModifier { property("overflow-wrap", "anywhere") }.toAttrs()) { Text(note.text) }
                        }
                    }
                }
            }
        }
    }
}
