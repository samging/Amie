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

private const val MIN_PASSWORD = 6

/** A rough measure of how hard a password is to guess, as words the person can act on. */
private fun passwordStrength(password: String): Pair<Int, String> {
    if (password.isEmpty()) return 0 to ""
    var score = 0
    if (password.length >= MIN_PASSWORD) score++
    if (password.length >= 12) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return when {
        password.length < MIN_PASSWORD -> 0 to "Too short: at least $MIN_PASSWORD characters."
        score <= 2 -> 1 to "Weak. Longer is better than clever."
        score == 3 -> 2 to "Fair. A digit or a symbol would help."
        else -> 3 to "Strong."
    }
}

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
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val (strength, strengthText) = passwordStrength(password)
    val mismatch = confirm.isNotEmpty() && confirm != password
    val nameProblem = when {
        name.isEmpty() -> null
        name.trim().length < 3 -> "At least 3 characters."
        !Regex("[A-Za-z0-9._-]+").matches(name.trim()) -> "Letters, digits, dots, dashes and underscores only."
        else -> null
    }

    fun submit() {
        error = when {
            name.isBlank() -> "Choose a username."
            nameProblem != null -> "Username: $nameProblem"
            password.length < MIN_PASSWORD -> "Use a password of at least $MIN_PASSWORD characters."
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
                    200 -> {
                        Toasts.show("Your account was created. Sign in to continue.", BannerKind.SUCCESS)
                        ctx.router.navigateTo("/loginpage?local=1")
                    }
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
        PageHeading("Create a local account", "An account that lives only on this site. For single sign-on with the desktop app, register with Keycloak instead.")
        Card(Modifier.maxWidth(28.cssRem)) {
            Div(Modifier.display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).gap(0.8.cssRem).toAttrs()) {
                Div {
                    FieldLabel("Username")
                    TextField(name, { name = it }, "Username", attrs = { attr("autocomplete", "username"); attr("autocapitalize", "none"); attr("spellcheck", "false") })
                    if (nameProblem != null) HintText(nameProblem, Modifier.margin(top = 0.3.cssRem))
                }
                Div {
                    FieldLabel("Password")
                    Div(Modifier.display(DisplayStyle.Flex).gap(0.5.cssRem).alignItems(AlignItems.Center).toAttrs()) {
                        TextField(password, { password = it }, "Password", if (showPassword) InputType.Text else InputType.Password, attrs = { attr("autocomplete", "new-password") })
                        GhostButton(if (showPassword) "Hide" else "Show") { showPassword = !showPassword }
                    }
                    if (password.isNotEmpty()) {
                        Div(Modifier.display(DisplayStyle.Flex).gap(0.3.cssRem).margin(top = 0.5.cssRem).toAttrs { attr("aria-hidden", "true") }) {
                            repeat(3) { index ->
                                Div(Modifier.height(0.5.cssRem).flexGrow(1).border(2.px, LineStyle.Solid, com.varabyte.kobweb.compose.ui.graphics.Colors.White)
                                    .backgroundColor(if (index < strength) com.varabyte.kobweb.compose.ui.graphics.Colors.White else com.varabyte.kobweb.compose.ui.graphics.Colors.Black).toAttrs())
                            }
                        }
                        HintText(strengthText, Modifier.margin(top = 0.3.cssRem))
                    }
                }
                Div {
                    FieldLabel("Repeat password")
                    TextField(confirm, { confirm = it }, "Repeat password", if (showPassword) InputType.Text else InputType.Password, onEnter = { if (!busy) submit() }, attrs = { attr("autocomplete", "new-password") })
                    if (mismatch) HintText("The passwords do not match yet.", Modifier.margin(top = 0.3.cssRem))
                }
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
