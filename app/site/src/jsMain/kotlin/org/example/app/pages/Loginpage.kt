package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.navigation.Link
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.breakpoint.Breakpoint
import com.varabyte.kobweb.silk.style.toAttrs
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import kotlinx.browser.window
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.example.app.auth.AuthClient
import org.example.app.auth.DevicePoll
import org.example.app.auth.DeviceStart
import org.example.app.auth.SignIn
import org.example.app.components.layouts.PageLayout
import org.example.app.components.widgets.*
import org.example.app.config.AmieConfig
import org.example.app.config.encodeURIComponent
import org.example.app.toSitePalette
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import kotlin.js.Date

val LoginGridStyle = CssStyle {
    base { Modifier.fillMaxWidth().display(DisplayStyle.Grid).gridTemplateColumns { size(1.fr) }.gap(1.5.cssRem) }
    Breakpoint.MD { Modifier.gridTemplateColumns { size(1.fr); size(1.fr) } }
}

/** Where to go after a successful sign-in: the `next` parameter, or the person's dashboard. */
private fun destination(next: String?, username: String): String =
    next?.takeIf { it.startsWith("/") && !it.startsWith("//") } ?: "/dashboard?username=${encodeURIComponent(username)}"

@NoLiveLiterals
@Page("loginpage")
@Composable
fun Loginpage() {
    val ctx = rememberPageContext()
    val scope = rememberCoroutineScope()
    val palette = ColorMode.current.toSitePalette()
    val next = ctx.route.params["next"]

    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var useLocalAccount by remember { mutableStateOf(false) }

    // "Scan to sign in"
    var device by remember { mutableStateOf<DeviceStart?>(null) }
    var deviceFallback by remember { mutableStateOf(false) }
    var deviceMessage by remember { mutableStateOf<String?>(null) }
    var pollJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        AuthClient.currentUser()?.let { ctx.router.navigateTo(destination(next, it)); return@LaunchedEffect }
    }

    fun finish(result: SignIn) {
        when (result) {
            is SignIn.Success -> ctx.router.navigateTo(destination(next, result.session.username))
            is SignIn.Failure -> error = result.message
        }
    }

    fun startDeviceFlow() {
        pollJob?.cancel()
        deviceMessage = null
        pollJob = scope.launch {
            val started = AuthClient.startDeviceSignIn()
            if (started == null) {
                deviceFallback = true
                return@launch
            }
            device = started
            val deadline = Date.now() + started.expiresIn * 1000.0
            while (isActive && Date.now() < deadline) {
                AuthClient.waitInterval(started.interval)
                when (val poll = AuthClient.pollDeviceSignIn(started.deviceCode)) {
                    is DevicePoll.Pending -> continue
                    is DevicePoll.Done -> {
                        when (val outcome = poll.result) {
                            is SignIn.Success -> ctx.router.navigateTo(destination(next, outcome.session.username))
                            is SignIn.Failure -> deviceMessage = outcome.message
                        }
                        return@launch
                    }
                }
            }
            if (isActive) deviceMessage = "The code expired. Show a new one."
        }
    }

    LaunchedEffect(Unit) { startDeviceFlow() }
    DisposableEffect(Unit) { onDispose { pollJob?.cancel() } }

    PageLayout("Sign in") {
        PageHeading("Sign in", "Use your Amie account to upload and manage packages.")

        Div(LoginGridStyle.toAttrs()) {
            // Credentials
            Card {
                H2(Modifier.fontSize(14.px).margin(top = 0.px, bottom = 1.2.cssRem).toAttrs()) {
                    Text(if (useLocalAccount) "Local account" else "Username and password")
                }
                Div(Modifier.display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).gap(1.cssRem).toAttrs()) {
                    Div { FieldLabel("Username"); TextField(name, { name = it }, "Username", onEnter = {}) }
                    Div {
                        FieldLabel("Password")
                        TextField(password, { password = it }, "Password", InputType.Password, onEnter = {
                            if (!busy) scope.launch {
                                busy = true; error = null
                                finish(if (useLocalAccount) AuthClient.signInLocal(name, password) else AuthClient.signInWithPassword(name, password))
                                busy = false
                            }
                        })
                    }
                    StatusBanner(error, BannerKind.ERROR)
                    PrimaryButton(if (busy) "Signing in…" else "Sign in", enabled = !busy) {
                        scope.launch {
                            busy = true; error = null
                            finish(if (useLocalAccount) AuthClient.signInLocal(name, password) else AuthClient.signInWithPassword(name, password))
                            busy = false
                        }
                    }
                }

                Hr(Modifier.margin(topBottom = 1.2.cssRem).border(0.px, LineStyle.None, palette.border).borderTop(1.px, LineStyle.Solid, palette.border).toAttrs())

                if (!useLocalAccount) {
                    Div(Modifier.display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).gap(0.9.cssRem).alignItems(AlignItems.FlexStart).toAttrs()) {
                        LinkButton("Sign in with browser (SSO)", AmieConfig.authUrl(), primary = false)
                        LinkButton("Create account", AmieConfig.authUrl(register = true), primary = false)
                        Div(Modifier.display(DisplayStyle.Flex).gap(1.cssRem).flexWrap(FlexWrap.Wrap).fontSize(10.px).toAttrs()) {
                            A(AmieConfig.authUrl(scope = "openid", extra = "&kc_action=UPDATE_PASSWORD")) { Text("Forgot password?") }
                            A("#", attrs = { onClick { it.preventDefault(); useLocalAccount = true; error = null } }) { Text("Use a local account instead") }
                        }
                    }
                } else {
                    Div(Modifier.display(DisplayStyle.Flex).gap(1.cssRem).flexWrap(FlexWrap.Wrap).fontSize(10.px).toAttrs()) {
                        Link("/register", "Create a local account")
                        A("#", attrs = { onClick { it.preventDefault(); useLocalAccount = false; error = null } }) { Text("Back to single sign-on") }
                    }
                }
            }

            // QR
            Card(Modifier.display(DisplayStyle.Flex).flexDirection(FlexDirection.Column).alignItems(AlignItems.Center).gap(0.8.cssRem)) {
                Mascot(variant = 1, size = 110)
                H2(Modifier.fontSize(13.px).margin(0.px).toAttrs()) { Text("Sign in with your phone") }
                val current = device
                when {
                    current != null -> {
                        QrCode(current.verificationUriComplete ?: current.verificationUri)
                        MutedText("Scan the code, sign in on your phone, and this page continues by itself.")
                        Div(Modifier.fontSize(10.px).lineHeight(1.9).textAlign(com.varabyte.kobweb.compose.css.TextAlign.Center).toAttrs()) {
                            Text("Or open ")
                            A(current.verificationUri, attrs = { attr("target", "_blank") }) { Text(current.verificationUri.removePrefix("https://").removePrefix("http://")) }
                            Text(" and enter ")
                            Span(Modifier.fontWeight(FontWeight.Bold).fontFamily("monospace").toAttrs()) { Text(current.userCode) }
                        }
                        StatusBanner(deviceMessage, BannerKind.ERROR)
                        if (deviceMessage != null) GhostButton("Show a new code") { startDeviceFlow() }
                    }
                    deviceFallback -> {
                        QrCode(AmieConfig.authUrl())
                        MutedText("Scan to open the sign-in page on your phone.")
                    }
                    else -> MutedText("Preparing a code…")
                }
            }
        }
    }
}
