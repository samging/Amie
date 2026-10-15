package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import kotlinx.browser.window
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.example.app.auth.AuthClient
import org.example.app.auth.AuthSession
import org.example.app.auth.SessionSource
import org.example.app.components.Catalog
import org.example.app.components.BadgeRow
import org.example.app.components.PublicationsEditor
import org.example.app.components.ClientCache
import org.example.app.components.Http
import org.example.app.components.layouts.PageLayout
import org.example.app.components.widgets.*
import org.example.app.config.AmieConfig
import org.example.app.config.encodeURIComponent
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import kotlin.js.Date

private const val BIO_LIMIT = 280
private const val NICK_LIMIT = 32
private const val AVATAR_MAX_BYTES = 512 * 1024
private val AVATAR_TYPES = setOf("image/png", "image/jpeg", "image/gif", "image/webp")

private fun initials(fullName: String, username: String): String {
    val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> username.take(2).uppercase()
    }
}

/** The signed-in person's profile on the backend; cached for five minutes, refreshed after a save. */
@NoLiveLiterals
@Page("profile")
@Composable
fun ProfilePage() {
    val ctx = rememberPageContext()
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf<String?>(null) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var github by remember { mutableStateOf("") }
    var huggingFace by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(listOf("", "", "", "", "", "")) }
    var avatarPending by remember { mutableStateOf(false) }
    var badges by remember { mutableStateOf(listOf<String>()) }
    var hasAvatar by remember { mutableStateOf(false) }
    var avatarVersion by remember { mutableStateOf("") }
    var avatarBusy by remember { mutableStateOf(false) }
    var fileInput by remember { mutableStateOf<org.w3c.dom.HTMLInputElement?>(null) }
    var avatarNotice by remember { mutableStateOf<String?>(null) }
    var avatarNoticeKind by remember { mutableStateOf(BannerKind.INFO) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeKind by remember { mutableStateOf(BannerKind.INFO) }
    var packageCount by remember { mutableStateOf<Int?>(null) }
    var packageSize by remember { mutableStateOf(0.0) }

    fun apply(json: String) {
        val obj = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return
        fullName = obj["fullName"]?.jsonPrimitive?.contentOrNull ?: ""
        email = obj["email"]?.jsonPrimitive?.contentOrNull ?: ""
        bio = obj["bio"]?.jsonPrimitive?.contentOrNull ?: ""
        nickname = obj["nickname"]?.jsonPrimitive?.contentOrNull ?: ""
        hasAvatar = obj["hasAvatar"]?.jsonPrimitive?.contentOrNull == "true"
        avatarVersion = obj["avatarVersion"]?.jsonPrimitive?.contentOrNull ?: ""
        avatarPending = obj["avatarPending"]?.jsonPrimitive?.contentOrNull == "true"
        github = obj["github"]?.jsonPrimitive?.contentOrNull ?: ""
        huggingFace = obj["huggingFace"]?.jsonPrimitive?.contentOrNull ?: ""
        badges = runCatching { (obj["badges"] as? kotlinx.serialization.json.JsonArray)?.map { it.jsonPrimitive.content } }.getOrNull() ?: emptyList()
        saved = listOf(fullName, email, bio, nickname, github, huggingFace)
    }

    suspend fun reloadProfile(user: String) {
        ClientCache.remove("profile:$user")
        val result = Http.get(AmieConfig.api("profile?username=${encodeURIComponent(user)}&refresh=1"), Http.bearer(AuthClient.accessToken()), noStore = true)
        if (result.ok) { ClientCache.put("profile:$user", result.text, 5 * ClientCache.MINUTE); apply(result.text) }
    }

    fun uploadAvatar(file: org.w3c.files.File) {
        val user = username ?: return
        if (file.type !in AVATAR_TYPES) { avatarNotice = "Choose a PNG, JPEG, GIF or WebP picture."; avatarNoticeKind = BannerKind.ERROR; return }
        if (file.size.toDouble() > AVATAR_MAX_BYTES) { avatarNotice = "The picture must be at most 512 KB."; avatarNoticeKind = BannerKind.ERROR; return }
        scope.launch {
            avatarBusy = true
            avatarNotice = null
            try {
                val form = org.w3c.xhr.FormData()
                form.append("file", file, file.name)
                val result = Http.upload(AmieConfig.api("avatar?username=${encodeURIComponent(user)}"), form, Http.bearer(AuthClient.accessToken())) {}
                if (result.ok) {
                    reloadProfile(user)
                    Toasts.show("Profile picture updated.", BannerKind.SUCCESS)
                } else {
                    avatarNotice = when (result.status) {
                        400 -> "That file was not accepted: it must really be a PNG, JPEG, GIF or WebP, at most 2048 pixels wide."
                        413 -> "The picture must be at most 512 KB."
                        401 -> "Your session has ended. Sign in again to change your picture."
                        else -> "The picture could not be saved right now."
                    }
                    avatarNoticeKind = BannerKind.ERROR
                }
            } catch (e: Throwable) {
                avatarNotice = "The picture could not be saved right now."
                avatarNoticeKind = BannerKind.ERROR
            } finally {
                avatarBusy = false
            }
        }
    }

    fun removeAvatar() {
        val user = username ?: return
        scope.launch {
            avatarBusy = true
            val result = runCatching { Http.postForm(AmieConfig.api("avatar-delete?username=${encodeURIComponent(user)}"), emptyMap(), Http.bearer(AuthClient.accessToken())) }.getOrNull()
            if (result?.ok == true || result?.status == 404) { reloadProfile(user); Toasts.show("Profile picture removed.", BannerKind.SUCCESS) }
            else { avatarNotice = "The picture could not be removed right now."; avatarNoticeKind = BannerKind.ERROR }
            avatarBusy = false
        }
    }

    LaunchedEffect(Unit) {
        val user = AuthClient.currentUser()
        if (user == null) {
            ctx.router.navigateTo("/loginpage?next=${encodeURIComponent("/profile")}")
            return@LaunchedEffect
        }
        username = user
        try {
            val json = ClientCache.cached("profile:$user", 5 * ClientCache.MINUTE) {
                val result = Http.get(AmieConfig.api("profile?username=${encodeURIComponent(user)}"), Http.bearer(AuthClient.accessToken()), noStore = true)
                if (!result.ok) throw IllegalStateException()
                result.text
            }
            apply(json)
        } catch (e: Throwable) {
            notice = "Your profile could not be loaded right now."
            noticeKind = BannerKind.ERROR
        } finally {
            loading = false
        }
        val mine = runCatching { Catalog.load() }.getOrDefault(Catalog.stale()).filter { it.author == user }
        packageCount = mine.size
        packageSize = mine.sumOf { it.size }
    }

    fun save() {
        val user = username ?: return
        scope.launch {
            saving = true
            notice = null
            try {
                val token = AuthClient.accessToken()
                val body = """{"fullName":${JSON.stringify(fullName)},"email":${JSON.stringify(email)},"bio":${JSON.stringify(bio)},"nickname":${JSON.stringify(nickname)},"github":${JSON.stringify(github)},"huggingFace":${JSON.stringify(huggingFace)}}"""
                val result = Http.postJson(AmieConfig.api("profile?username=${encodeURIComponent(user)}"), body, Http.bearer(token))
                if (result.ok) {
                    reloadProfile(user)
                    saved = listOf(fullName, email, bio, nickname, github, huggingFace)
                    notice = "Profile saved."
                    noticeKind = BannerKind.SUCCESS
                    Toasts.show("Profile saved.", BannerKind.SUCCESS)
                } else {
                    notice = when (result.status) {
                        401 -> "Your session has ended. Sign in again to change your profile."
                        403 -> "Only you can change your own profile."
                        400 -> "The profile could not be saved: check the email address, the nickname (2 to 32 letters, digits, spaces, dots, dashes or underscores) and the GitHub / Hugging Face usernames."
                        409 -> "That nickname is already taken by someone else."
                        429 -> "You are saving quickly; wait a moment and try again."
                        else -> "The profile could not be saved right now. Please try again in a moment."
                    }
                    noticeKind = BannerKind.ERROR
                }
            } catch (e: Throwable) {
                notice = "The profile could not be saved."
                noticeKind = BannerKind.ERROR
            } finally {
                saving = false
            }
        }
    }

    val dirty = listOf(fullName, email, bio, nickname, github, huggingFace) != saved
    val session = remember { AuthSession.current() }
    val user = username

    PageLayout("Profile") {
        PageHeading("Your profile", username) {
            user?.let {
                LinkButton("Your packages", "/dashboard?username=${encodeURIComponent(it)}", primary = false)
                CopyButton(window.location.origin + "/dashboard?username=${encodeURIComponent(it)}", label = "Copy public link", toast = "Link to your public package page copied.")
            }
        }
        if (loading) {
            SkeletonRows(rows = 4, label = "Loading profile…")
        } else if (user != null) {
            Div(Modifier.display(DisplayStyle.Flex).alignItems(AlignItems.Center).gap(1.cssRem).margin(bottom = 1.5.cssRem).flexWrap(FlexWrap.Wrap).toAttrs()) {
                if (avatarPending) {
                    // The owner's own preview of a picture nobody else sees yet; served with the bearer, never cached
                    PendingAvatarPreview(user, size = 3.5.cssRem)
                } else {
                    Avatar(PublicProfile(username = user, fullName = fullName, nickname = nickname, hasAvatar = hasAvatar, avatarVersion = avatarVersion), size = 3.5.cssRem, modifier = Modifier.boxShadow(4.px, 4.px, 0.px, 0.px, Colors.White))
                }
                Div {
                    Div(Modifier.fontSize(13.px).toAttrs()) { Text(saved[3].ifBlank { fullName.ifBlank { user } }) }
                    MutedText(if (fullName.isBlank() && saved[3].isBlank()) "Add a nickname or your name below so people know who publishes your packages." else "@$user")
                    BadgeRow(badges, Modifier.margin(top = 0.3.cssRem))
                }
                Div(Modifier.display(DisplayStyle.Flex).gap(0.5.cssRem).flexWrap(FlexWrap.Wrap).alignItems(AlignItems.Center).toAttrs()) {
                    Input(InputType.File, attrs = Modifier.display(DisplayStyle.None).toAttrs {
                        attr("accept", AVATAR_TYPES.joinToString(","))
                        attr("aria-label", "Choose a profile picture")
                        ref { element -> fileInput = element; onDispose { fileInput = null } }
                        onChange { event -> event.target.files?.item(0)?.let { uploadAvatar(it) }; event.target.value = "" }
                    })
                    GhostButton(if (avatarBusy) "Working…" else if (hasAvatar) "Change picture" else "Add a picture", enabled = !avatarBusy) { fileInput?.click() }
                    if ((hasAvatar || avatarPending) && !avatarBusy) GhostButton("Remove picture") { removeAvatar() }
                }
            }
            if (avatarPending) StatusBanner("Your picture is waiting for an administrator's approval. Only you can see it until then.", BannerKind.INFO, Modifier.margin(bottom = 0.8.cssRem))
            StatusBanner(avatarNotice, avatarNoticeKind, Modifier.margin(bottom = 1.cssRem))
            HintText("PNG, JPEG, GIF or WebP, at most 512 KB. An administrator approves it before it is shown next to your packages and notes.", Modifier.margin(bottom = 1.2.cssRem))

            StatRow {
                StatTile("Packages", packageCount?.toString() ?: "…", if ((packageCount ?: 0) > 0) formatSize(packageSize) + " in the store" else "none published yet")
                StatTile("Signed in with", if (session?.source == SessionSource.KEYCLOAK) "Single sign-on" else "Local account", if (session?.source == SessionSource.KEYCLOAK) "shared with the desktop app" else "this site's own account")
                session?.takeIf { it.expiresAt > 0 }?.let {
                    val left = it.expiresAt - Date.now()
                    StatTile("Session", if (left > 0) "renews itself" else "expired", if (left > 0) "token valid for ${(left / 60_000).toInt().coerceAtLeast(1)} min" else "sign in again")
                }
            }

            Card(Modifier.maxWidth(32.cssRem)) {
                Div(Modifier.display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).gap(0.8.cssRem).toAttrs()) {
                    Div {
                        FieldLabel("Nickname")
                        TextField(nickname, { if (it.length <= NICK_LIMIT) nickname = it }, "How you want to be called", attrs = { attr("autocomplete", "nickname"); attr("maxlength", NICK_LIMIT.toString()) })
                        HintText("Shown instead of @$user. 2 to 32 letters, digits, spaces, dots, dashes or underscores; it cannot be someone else's name.", Modifier.margin(top = 0.2.cssRem))
                    }
                    Div { FieldLabel("Full name"); TextField(fullName, { fullName = it }, "Full name", attrs = { attr("autocomplete", "name") }) }
                    Div { FieldLabel("Email"); TextField(email, { email = it }, "Email", InputType.Email, attrs = { attr("autocomplete", "email") }) }
                    Div {
                        FieldLabel("GitHub username")
                        TextField(github, { if (it.length <= 64) github = it }, "octocat", attrs = { attr("autocomplete", "off"); attr("spellcheck", "false") })
                    }
                    Div {
                        FieldLabel("Hugging Face username")
                        TextField(huggingFace, { if (it.length <= 80) huggingFace = it }, "your-handle", attrs = { attr("autocomplete", "off"); attr("spellcheck", "false") })
                        HintText("Your public repositories and models appear on your package page. Just the usernames; nothing is verified or signed in.", Modifier.margin(top = 0.2.cssRem))
                    }
                    Div {
                        FieldLabel("About you")
                        TextAreaField(bio, { if (it.length <= BIO_LIMIT) bio = it }, "A few words about you")
                        HintText("${bio.length} / $BIO_LIMIT characters", Modifier.margin(top = 0.2.cssRem))
                    }
                    StatusBanner(notice, noticeKind)
                    Div(Modifier.display(DisplayStyle.Flex).gap(0.6.cssRem).flexWrap(FlexWrap.Wrap).alignItems(AlignItems.Center).toAttrs()) {
                        PrimaryButton(if (saving) "Saving…" else "Save changes", enabled = !saving && dirty) { save() }
                        if (dirty && !saving) GhostButton("Discard") { fullName = saved[0]; email = saved[1]; bio = saved[2]; nickname = saved[3]; github = saved[4]; huggingFace = saved[5]; notice = null }
                        if (!dirty && notice == null) MutedText("Nothing to save.", Modifier.fontSize(8.px))
                    }
                }
            }

            PublicationsEditor(user)

            Card(Modifier.maxWidth(32.cssRem).margin(top = 1.5.cssRem)) {
                Div(Modifier.fontSize(12.px).margin(bottom = 0.4.cssRem).toAttrs()) { Text("Account") }
                MutedText(if (session?.source == SessionSource.KEYCLOAK) "Password and email changes happen on the sign-in service; signing out here also signs you out there." else "This account exists only on this site.")
                Div(Modifier.margin(top = 0.8.cssRem).display(DisplayStyle.Flex).gap(0.5.cssRem).flexWrap(FlexWrap.Wrap).toAttrs()) {
                    if (session?.source == SessionSource.KEYCLOAK) LinkButton("Change password", AmieConfig.authUrl(scope = "openid", extra = "&kc_action=UPDATE_PASSWORD"), primary = false)
                    DangerButton("Sign out") { AuthClient.signOut() }
                }
            }
        }
    }
}
