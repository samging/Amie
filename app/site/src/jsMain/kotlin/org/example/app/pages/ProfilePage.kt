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
    var saved by remember { mutableStateOf(Triple("", "", "")) }
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
        saved = Triple(fullName, email, bio)
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
                val result = Http.get(AmieConfig.backend("profile?username=${encodeURIComponent(user)}"))
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
                val body = """{"fullName":${JSON.stringify(fullName)},"email":${JSON.stringify(email)},"bio":${JSON.stringify(bio)}}"""
                val result = Http.postJson(AmieConfig.backend("profile?username=${encodeURIComponent(user)}"), body, Http.bearer(token))
                if (result.ok) {
                    ClientCache.put("profile:$user", body, 5 * ClientCache.MINUTE)
                    saved = Triple(fullName, email, bio)
                    notice = "Profile saved."
                    noticeKind = BannerKind.SUCCESS
                    Toasts.show("Profile saved.", BannerKind.SUCCESS)
                } else {
                    notice = if (result.status == 401 || result.status == 403) "Please sign in again to change your profile." else "The profile could not be saved."
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

    val dirty = Triple(fullName, email, bio) != saved
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
                Div(
                    Modifier.size(3.5.cssRem).display(DisplayStyle.Flex).alignItems(AlignItems.Center).justifyContent(JustifyContent.Center)
                        .backgroundColor(Colors.White).color(Colors.Black).fontSize(14.px).border(3.px, LineStyle.Solid, Colors.White)
                        .boxShadow(4.px, 4.px, 0.px, 0.px, Colors.White).toAttrs { attr("aria-hidden", "true") }
                ) { Text(initials(fullName, user)) }
                Div {
                    Div(Modifier.fontSize(13.px).toAttrs()) { Text(fullName.ifBlank { user }) }
                    MutedText(if (fullName.isBlank()) "Add your name below so people know who publishes your packages." else "@$user")
                }
            }

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
                    Div { FieldLabel("Full name"); TextField(fullName, { fullName = it }, "Full name", attrs = { attr("autocomplete", "name") }) }
                    Div { FieldLabel("Email"); TextField(email, { email = it }, "Email", InputType.Email, attrs = { attr("autocomplete", "email") }) }
                    Div {
                        FieldLabel("About you")
                        TextAreaField(bio, { if (it.length <= BIO_LIMIT) bio = it }, "A few words about you")
                        HintText("${bio.length} / $BIO_LIMIT characters", Modifier.margin(top = 0.2.cssRem))
                    }
                    StatusBanner(notice, noticeKind)
                    Div(Modifier.display(DisplayStyle.Flex).gap(0.6.cssRem).flexWrap(FlexWrap.Wrap).alignItems(AlignItems.Center).toAttrs()) {
                        PrimaryButton(if (saving) "Saving…" else "Save changes", enabled = !saving && dirty) { save() }
                        if (dirty && !saving) GhostButton("Discard") { fullName = saved.first; email = saved.second; bio = saved.third; notice = null }
                        if (!dirty && notice == null) MutedText("Nothing to save.", Modifier.fontSize(8.px))
                    }
                }
            }

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
