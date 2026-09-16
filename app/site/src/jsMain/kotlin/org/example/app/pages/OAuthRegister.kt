package org.example.app.pages

import androidx.compose.runtime.*
import androidx.compose.runtime.NoLiveLiterals
import com.varabyte.kobweb.browser.http.tryFetch
import com.varabyte.kobweb.compose.css.functions.url
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.*

@NoLiveLiterals
@Page("oauthregister")
@Composable
fun OAuthRegister() {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Initializing...") }
    var regexUrlReg by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val host = window.location.hostname
    
    val registrationUrl = "http://$host:8080/realms/master/protocol/openid-connect/registrations" +
            "?client_id=amie" +
            "&response_type=code" +
            "&scope=openid" +
            "&redirect_uri=http://$host:8081/oauthpage"

    println("Registration URL: $registrationUrl")
    println("host URL: $host")

    val getHtml = "/api/sso-proxy"

    LaunchedEffect(Unit) {
        println("Redirecting to Keycloak OIDC registration: $registrationUrl")
        println("[][][] fetching...")
        try {
            val response = window.fetch(getHtml).await()

            if (response.ok) {
                val body = response.text().await()
                println("[][][] Successfully fetched registration HTML metadata ${body}")

                fun extractActionUrlRegex(htmlBody: String): String? {
                    val regex = """<form[^>]*id="kc-register-form"[^>]*action="([^"]+)"|action="([^"]+)"[^>]*id="kc-register-form"""".toRegex(RegexOption.IGNORE_CASE)
                    val matchResult = regex.find(htmlBody)
                    val rawUrl = matchResult?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
                    return rawUrl?.replace("&amp;", "&")
                }
                val meowmeow = extractActionUrlRegex(body)
                println("meow $meowmeow")
                if (meowmeow != null)  { regexUrlReg = meowmeow }
                statusMessage = "Ready for SSO Registration"
            } else {
                println("[][][] Fetch failed with status: ${response.status}")
                statusMessage = "Error: Keycloak unreachable (${response.status})"
            }
        } catch (e: Exception) {
            println("[][][] Fetch Error: ${e.message}")
            statusMessage = "CORS block detected. Ensure Keycloak permits origin http://$host:8081"
        }
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
            P { Text(statusMessage) }

            Div(Modifier.margin(top = 10.px).toAttrs()) {
                Input(
                    type = InputType.Text,
                    attrs = Modifier.width(250.px).padding(8.px).toAttrs {
                        placeholder("Username")
                        value(username)
                        onInput { username = it.value }
                    }
                )
            }

            Div(Modifier.margin(top = 10.px).toAttrs()) {
                Input(
                    type = InputType.Email,
                    attrs = Modifier.width(250.px).padding(8.px).toAttrs {
                        placeholder("Email")
                        value(email)
                        onInput { email = it.value }
                    }
                )
            }

            Div(Modifier.margin(top = 10.px).toAttrs()) {
                Input(
                    type = InputType.Password,
                    attrs = Modifier.width(250.px).padding(8.px).toAttrs {
                        placeholder("Password")
                        value(password)
                        onInput { password = it.value }
                    }
                )
            }

            Button(
                attrs = Modifier.margin(top = 16.px).toAttrs {
                    onClick {
                        scope.launch {
                            try {
                                println("[][][] Step 1: Submitting JSON payload to backend SSO proxy...")


                                val jsData = js("new FormData()")
                                jsData.append("firstName", ".")
                                jsData.append("lastName", ".")
                                jsData.append("email", email)
                                jsData.append("username", username)
                                jsData.append("password", password)
                                jsData.append("password-confirm", password)

                                val postRegister = window.fetch( "/api/sso-post-register", jsData).await()
                                    if (postRegister.status == 200 as Short) {
                                        println("we went with success, thats magic!")
                                    }
                                    else println("[][][] Error: ${postRegister.status}")


                            } catch (e: Exception) {
                                println("ERROR: ${e.message}")
                                statusMessage = "Error: ${e.message}"
                            }
                        }
                    }
                }
            ) {
                Text("Register")
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
