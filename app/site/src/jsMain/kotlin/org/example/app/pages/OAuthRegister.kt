package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.core.Page
import kotlinx.browser.window
import org.example.app.components.layouts.PageLayout
import org.example.app.components.widgets.*
import org.example.app.config.AmieConfig

/** Sends the browser to the realm's own registration page, which comes back to /oauthpage signed in. */
@NoLiveLiterals
@Page("oauthregister")
@Composable
fun OAuthRegister() {
    LaunchedEffect(Unit) { window.location.href = AmieConfig.authUrl(register = true) }

    PageLayout("Create account") {
        PageHeading("Create an account")
        StatusBanner("Taking you to the registration page…", BannerKind.INFO)
        LinkButton("Open the registration page", AmieConfig.authUrl(register = true))
    }
}
