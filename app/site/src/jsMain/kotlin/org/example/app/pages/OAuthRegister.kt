package org.example.app.pages

import androidx.compose.runtime.*
import androidx.compose.runtime.NoLiveLiterals
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import kotlinx.browser.window
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.*

@NoLiveLiterals
@Page("oauthregister")
@Composable
fun OAuthRegister() {
    val registrationUrl = "http://192.168.1.114:8080/realms/master/protocol/openid-connect/registrations" +
            "?client_id=amie" +
            "&response_type=code" +
            "&scope=openid" +
            "&redirect_uri=http://192.168.1.114:8081/oauthpage"

    LaunchedEffect(Unit) {
        println("Redirecting to Keycloak OIDC registration: $registrationUrl")
        window.location.href = registrationUrl
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(24.px),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            H1 { Text("SSO Registration") }
            P { Text("Redirecting to Keycloak registration page...") }

            Button(
                attrs = Modifier.margin(top = 16.px).toAttrs {
                    onClick { window.location.href = registrationUrl }
                }
            ) {
                Text("Click here if not redirected automatically")
            }

            Button(
                attrs = Modifier.margin(top = 12.px).toAttrs {
                    onClick { window.location.href = "/loginpage" }
                }
            ) {
                Text("Cancel")
            }
        }
    }
}
