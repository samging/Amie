package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.example.app.auth.AuthClient
import org.example.app.components.ClientCache
import org.example.app.components.Http
import org.example.app.components.layouts.PageLayout
import org.example.app.components.widgets.*
import org.example.app.config.AmieConfig
import org.example.app.config.encodeURIComponent
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

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
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeKind by remember { mutableStateOf(BannerKind.INFO) }

    fun apply(json: String) {
        val obj = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return
        fullName = obj["fullName"]?.jsonPrimitive?.contentOrNull ?: ""
        email = obj["email"]?.jsonPrimitive?.contentOrNull ?: ""
        bio = obj["bio"]?.jsonPrimitive?.contentOrNull ?: ""
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
                    notice = "Profile saved."
                    noticeKind = BannerKind.SUCCESS
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

    PageLayout("Profile") {
        PageHeading("Your profile", username) {
            username?.let { LinkButton("Your packages", "/dashboard?username=${encodeURIComponent(it)}", primary = false) }
        }
        if (loading) {
            EmptyState("Loading profile…")
        } else {
            Card(Modifier.maxWidth(32.cssRem)) {
                Div(Modifier.display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).gap(0.8.cssRem).toAttrs()) {
                    Div { FieldLabel("Full name"); TextField(fullName, { fullName = it }, "Full name") }
                    Div { FieldLabel("Email"); TextField(email, { email = it }, "Email", InputType.Email) }
                    Div { FieldLabel("About you"); TextAreaField(bio, { bio = it }, "A few words about you") }
                    StatusBanner(notice, noticeKind)
                    Div { PrimaryButton(if (saving) "Saving…" else "Save changes", enabled = !saving) { save() } }
                }
            }
        }
    }
}
