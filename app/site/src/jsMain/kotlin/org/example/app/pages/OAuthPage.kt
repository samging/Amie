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
import org.example.app.config.AmieConfig
import org.example.app.config.encodeURIComponent

/**
 * Where Keycloak sends the browser back after the hosted sign-in or registration page: exchanges the
 * code for tokens (verified in the browser) and continues to the dashboard.
 */
@NoLiveLiterals
@Page("oauthpage")
@Composable
fun OAuthPage() {
    val ctx = rememberPageContext()
    val url = Url(window.location.href)
    val code = url.parameters["code"]
    val oauthError = url.parameters["error_description"] ?: url.parameters["error"]

    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(code) {
        when {
            oauthError != null -> {
                failed = true
                message = "Sign-in was cancelled."
            }
            code != null -> {
                message = "Finishing sign-in…"
                when (val result = AuthClient.exchangeCode(code)) {
                    is SignIn.Success -> {
                        window.history.replaceState(null, "", "/oauthpage")
                        ctx.router.navigateTo("/dashboard?username=${encodeURIComponent(result.session.username)}")
                    }
                    is SignIn.Failure -> {
                        failed = true
                        message = result.message
                    }
                }
            }
            else -> {
                val user = AuthClient.currentUser()
                if (user != null) ctx.router.navigateTo("/dashboard?username=${encodeURIComponent(user)}")
                else window.location.href = AmieConfig.authUrl()
            }
        }
    }

    PageLayout("Sign in") {
        PageHeading("Signing you in")
        StatusBanner(message, if (failed) BannerKind.ERROR else BannerKind.INFO)
        if (failed) {
            LinkButton("Try again", "/loginpage")
        }
    }
}
