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
import com.varabyte.kobweb.core.rememberPageContext
import kotlinx.browser.window
import kotlinx.coroutines.launch
import kotlinx.coroutines.await
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection

@NoLiveLiterals
@Page("profile")
@Composable
fun ProfilePage() {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val ctx = rememberPageContext()
    val username = window.localStorage.getItem("username") ?: ""

    LaunchedEffect(Unit) {
        if (username.isEmpty()) {
            ctx.router.navigateTo("/loginpage")
            return@LaunchedEffect
        }

        isLoading = true
        try {
            val response = window.fetch("http://localhost:8080/profile?username=$username").await()
            if (response.ok) {
                val jsonText = response.text().await()
                val json = Json.parseToJsonElement(jsonText).jsonObject
                fullName = json["fullName"]?.jsonPrimitive?.content ?: ""
                email = json["email"]?.jsonPrimitive?.content ?: ""
                bio = json["bio"]?.jsonPrimitive?.content ?: ""
            } else {
                statusMessage = "Error fetching profile: ${response.statusText}"
            }
        } catch (e: Exception) {
            statusMessage = "Failed to load profile: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Div(
        attrs = Modifier
            .fillMaxSize()
            .padding(24.px)
            .display(DisplayStyle.Flex)
            .flexDirection(FlexDirection.Column)
            .alignItems(AlignItems.Center)
            .toAttrs()
    ) {
        H1 { Text("User Profile: $username") }

        if (isLoading) {
            P { Text("Loading profile...") }
        } else {
            Div(Modifier.margin(top = 10.px).toAttrs()) {
                Text("Full Name:")
                Br()
                Input(
                    type = InputType.Text,
                    attrs = Modifier
                        .width(300.px)
                        .padding(8.px)
                        .toAttrs {
                            placeholder("Full Name")
                            value(fullName)
                            onInput { fullName = it.value }
                        }
                )
            }

            Div(Modifier.margin(top = 10.px).toAttrs()) {
                Text("Email:")
                Br()
                Input(
                    type = InputType.Email,
                    attrs = Modifier
                        .width(300.px)
                        .padding(8.px)
                        .toAttrs {
                            placeholder("Email")
                            value(email)
                            onInput { email = it.value }
                        }
                )
            }

            Div(Modifier.margin(top = 10.px).toAttrs()) {
                Text("Bio:")
                Br()
                TextArea(
                    attrs = Modifier
                        .width(300.px)
                        .height(100.px)
                        .padding(8.px)
                        .toAttrs {
                            placeholder("Tell us about yourself")
                            value(bio)
                            onInput { bio = it.value }
                        }
                )
            }

            Button(
                attrs = Modifier.margin(top = 20.px).toAttrs {
                    onClick {
                        scope.launch {
                            statusMessage = "Saving..."
                            try {
                                val requestBody = Json.encodeToString(
                                    mapOf(
                                        "fullName" to fullName,
                                        "email" to email,
                                        "bio" to bio
                                    )
                                )
                                val token = window.localStorage.getItem("auth_token") ?: window.localStorage.getItem("token")
                                val options = js("{}")
                                options["method"] = "POST"
                                options["body"] = requestBody
                                val headers = js("{}")
                                headers["Content-Type"] = "application/json"
                                headers["Authorization"] = "Bearer $token"
                                options["headers"] = headers

                                val response = window.fetch("http://localhost:8080/profile?username=$username", options).await()
                                if (response.ok) {
                                    statusMessage = "Profile updated successfully!"
                                } else {
                                    statusMessage = "Update failed: ${response.statusText}"
                                }
                            } catch (e: Exception) {
                                statusMessage = "Error: ${e.message}"
                            }
                        }
                    }
                }
            ) {
                Text("Save Changes")
            }
        }

        if (statusMessage != null) {
            P(Modifier.margin(top = 20.px).toAttrs()) { Text(statusMessage!!) }
        }

        Button(
            attrs = Modifier.margin(top = 20.px).toAttrs {
                onClick {
                    ctx.router.navigateTo("/dashboard?username=$username")
                }
            }
        ) {
            Text("Back to Dashboard")
        }
    }
}
