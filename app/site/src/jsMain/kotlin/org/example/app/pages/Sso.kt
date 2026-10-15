package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import io.ktor.http.Url
import kotlinx.browser.window
import org.example.app.auth.AuthClient
import org.example.app.auth.SignIn
import org.example.app.components.layouts.PageLayout
import org.example.app.components.widgets.*
import org.example.app.config.encodeURIComponent

/** Where to continue once signed in: `next` when it is a path on this site, else the person's dashboard. */
private fun ssoDestination(next: String?, username: String): String =
    next?.takeIf { it.startsWith("/") && !it.startsWith("//") } ?: "/dashboard?username=${encodeURIComponent(username)}"

/**
 * Where the Amie desktop app sends the browser to sign in with the app's own session: `/sso?code=<one-time
 * code>&next=<path>`. The code is claimed from `/api/sso/claim`, the tokens are verified like any other
 * sign-in, and the browser continues to `next`. Without a code, a signed-in person is sent on and anyone
 * else to the sign-in page.
 */
@NoLiveLiterals
@Page("sso")
@Composable
fun SsoPage() {
    val ctx = rememberPageContext()
    val url = Url(window.location.href)
    val code = url.parameters["code"]
    val next = url.parameters["next"]

    var message by remember { mutableStateOf<String?>("Signing you in from the Amie app…") }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(code) {
        if (code.isNullOrEmpty()) {
            val user = AuthClient.currentUser()
            if (user != null) ctx.router.navigateTo(ssoDestination(next, user))
            else ctx.router.navigateTo("/loginpage" + (next?.let { "?next=${encodeURIComponent(it)}" } ?: ""))
            return@LaunchedEffect
        }
        when (val result = AuthClient.signInWithHandoff(code)) {
            is SignIn.Success -> {
                // The code is spent; keep it out of the history so a reload does not show an error
                window.history.replaceState(null, "", "/sso")
                ctx.router.navigateTo(ssoDestination(next, result.session.username))
            }
            is SignIn.Failure -> {
                failed = true
                message = result.message
            }
        }
    }

    PageLayout("Sign in") {
        PageHeading("Signing you in", "With the account you use in the Amie app")
        StatusBanner(message, if (failed) BannerKind.ERROR else BannerKind.INFO)
        if (failed) {
            LinkButton("Sign in here instead", "/loginpage" + (next?.let { "?next=${encodeURIComponent(it)}" } ?: ""))
        }
    }
}
