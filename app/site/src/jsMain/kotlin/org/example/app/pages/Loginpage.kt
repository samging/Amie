package org.example.app.pages

import androidx.compose.runtime.*
import androidx.compose.runtime.NoLiveLiterals
import com.varabyte.kobweb.browser.api
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import kotlinx.browser.window
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection


@Serializable
data class GenericApiResponse(
    val statusCode: Int,
    val headers: String,
    val body: JsonObject? = null,
    val requestBody: JsonObject? = null
)

@NoLiveLiterals
@Page("loginpage")
@Composable
fun Loginpage() {

    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loadedPackages by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var packagesListed by remember {mutableStateOf<List<String>>(emptyList())}

    val scope = rememberCoroutineScope()
    val ctx = rememberPageContext()

    Div(
        attrs = Modifier
            .fillMaxSize()
            .padding(24.px)
            .display(DisplayStyle.Flex)
            .flexDirection(FlexDirection.Column)
            .alignItems(AlignItems.Center)
            .toAttrs()
    ) {
        H1 {
            Text("Login Page")
        }
        Div() {
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


        Div() {
            Button(
                attrs = Modifier.toAttrs {
                    onClick {
                        if (name.isNotEmpty() && password.isNotEmpty()) {
                            scope.launch {
                                val requestBody = Json.encodeToString(
                                    mapOf(
                                        "username" to name,
                                        "password" to password
                                    )
                                )

                                try {
                                    val responseBytes = window.api.post(
                                        apiPath = "login",
                                        body = requestBody.encodeToByteArray()
                                    )
                                    println(responseBytes.decodeToString())
                                    ctx.router.navigateTo("/dashboard?username=$name") //broken as hell!

                                    val response = Json.decodeFromString<GenericApiResponse>(responseBytes.decodeToString())
                                    println(response)
                                    println("Username: ${response.requestBody?.get("username")?.jsonPrimitive?.contentOrNull ?: "name not found"}")
                                    println("Password: ${response.requestBody?.get("password")?.jsonPrimitive?.contentOrNull ?: "name not found"}")

                                    if (response.statusCode == 200) {
                                        val token = response.body?.get("token")?.jsonPrimitive?.contentOrNull ?: "No Auth Token"
                                        val user = response.requestBody?.get("username")?.jsonPrimitive?.contentOrNull ?: "name not found"

                                        println("TOKEN: $token")
                                        if (token != null) {
                                            window.localStorage.setItem("username", user)
                                            window.localStorage.setItem("token", token)
                                            for(i in 0 until window.localStorage.length) println("WINDOW ATTACHMENTS: ${window.localStorage.key(i)}")

                                            ctx.router.navigateTo("/dashboard?username=$user")
                                        } else {
                                            println("Token missing in response")
                                        }
                                    } else {
                                        println("Login failed with status: ${response.statusCode}")
                                    }
                                } catch (e: Exception) {
                                    println("Error: ${e.message}")
                                }
                            }
                        } else {
                            println("Please enter both username and password")
                        }
                    }
                }
            ) {
                Text("Login")
            }
        }

        Div() {

            Button(
                attrs = Modifier.toAttrs {
                    onClick {
                        ctx.router.navigateTo("/register")
                    }
                }
            )
            {
                Text("Register")
            }
        }

    }
}
