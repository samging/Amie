package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import kotlinx.coroutines.launch
import org.example.app.components.Http
import org.example.app.components.layouts.PageLayout
import org.example.app.components.widgets.*
import org.example.app.config.AmieConfig
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

/** Creates one of the backend's own accounts (the Keycloak registration lives on /oauthregister). */
@NoLiveLiterals
@Page("register")
@Composable
fun RegisterPage() {
    val ctx = rememberPageContext()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        error = when {
            name.isBlank() -> "Choose a username."
            password.length < 6 -> "Use a password of at least 6 characters."
            password != confirm -> "The passwords do not match."
            else -> null
        }
        if (error != null) return
        scope.launch {
            busy = true
            try {
                val body = """{"username":${JSON.stringify(name.trim())},"password":${JSON.stringify(password)}}"""
                val result = Http.postJson(AmieConfig.api("register"), body)
                when (result.status) {
                    200 -> ctx.router.navigateTo("/loginpage?local=1")
                    409 -> error = "That username is already taken."
                    else -> error = "The account could not be created right now."
                }
            } catch (e: Throwable) {
                error = "The account could not be created right now."
            } finally {
                busy = false
            }
        }
    }

    PageLayout("Create account") {
        PageHeading("Create a local account", "For single sign-on accounts use the Keycloak registration instead.")
        Card(Modifier.maxWidth(28.cssRem)) {
            Div(Modifier.display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).gap(0.8.cssRem).toAttrs()) {
                Div { FieldLabel("Username"); TextField(name, { name = it }, "Username") }
                Div { FieldLabel("Password"); TextField(password, { password = it }, "Password", InputType.Password) }
                Div { FieldLabel("Repeat password"); TextField(confirm, { confirm = it }, "Repeat password", InputType.Password, onEnter = { if (!busy) submit() }) }
                StatusBanner(error, BannerKind.ERROR)
                Div(Modifier.display(DisplayStyle.Flex).gap(0.6.cssRem).flexWrap(FlexWrap.Wrap).toAttrs()) {
                    PrimaryButton(if (busy) "Creating…" else "Create account", enabled = !busy) { submit() }
                    LinkButton("Register with single sign-on", "/oauthregister", primary = false)
                }
                A("/loginpage") { Text("Already have an account? Sign in") }
            }
        }
    }
}
