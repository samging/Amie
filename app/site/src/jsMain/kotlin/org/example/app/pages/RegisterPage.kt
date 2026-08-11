package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.browser.api
import com.varabyte.kobweb.browser.http.bodyOf
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.overlay.Overlay
import com.varabyte.kobweb.silk.components.overlay.OverlayVars
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Page("register")
@Composable
fun RegisterPage() {

    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val ctx = rememberPageContext()

    Div(
        attrs = Modifier.padding(24.px).toAttrs()
    ) {
        H1 {
            Text("Register Page")
        }
        Div(){
            Input(
                type = InputType.Text,
                attrs = Modifier
                    .width(250.px)
                    .padding(8.px)
                    .toAttrs {
                        placeholder("Username")
                        value(name)
                        onInput { event ->
                            name = event.value
                        }
                    }
            )
        }

        Div() {
            Input(
                type = InputType.Password,
                attrs = Modifier
                    .width(250.px)
                    .padding(8.px)
                    .toAttrs {
                        placeholder("Password")
                        value(password)
                        onInput { event ->
                            password = event.value
                        }
                    }
            )
        }

        Button(
            onClick = {
                if (name.isNotEmpty() && password.isNotEmpty()) {
                    scope.launch {
                        val requestBody = Json.encodeToString(mapOf("username" to name, "password" to password))

                        try {
                            val response = window.api.post("register", bodyOf(requestBody))
                            when (response.status.toInt()) {
                                200 -> {
                                    ctx.router.navigateTo("/loginpage")
                                }
                                409 -> {
                                    // Manually getting text since bodyText() might be tricky with imports
                                    errorMessage = "Username is already assigned to different account"
                                }
                                else -> {
                                    println("Registration failed with status: ${response.status}")
                                }
                            }
                        } catch (e: Exception) {
                            println("Error: ${e.message}")
                        }
                    }
                } else {
                    println("Please enter both username and password")
                }
            }
        ) {
            Text("Register")
        }
    }

    if (errorMessage != null) {
        Overlay(
            Modifier
                .setVariable(OverlayVars.BackgroundColor, com.varabyte.kobweb.compose.ui.graphics.Colors.Transparent)
                .onClick { errorMessage = null }
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .padding(16.px)
                    .borderRadius(8.px)
                    .backgroundColor(com.varabyte.kobweb.compose.ui.graphics.Color.rgb(255, 230, 230))
                    .border(
                        1.px,
                        org.jetbrains.compose.web.css.LineStyle.Solid,
                        com.varabyte.kobweb.compose.ui.graphics.Color.rgb(255, 77, 77)
                    )
            ) {
                Column(Modifier.gap(8.px)) {
                    SpanText("⚠️ Conflict Error (409)")
                    SpanText(errorMessage ?: "")

                    Button(
                        onClick = { errorMessage = null }
                    ) {
                        SpanText("Close")
                    }
                }
            }
        }
    }
}
