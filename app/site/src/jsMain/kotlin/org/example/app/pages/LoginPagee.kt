package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.browser.api
import com.varabyte.kobweb.browser.http.bodyOf
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

@Page("loginpage")
@Composable
fun LoginPagee() {

    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loadedPackages by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var packagesListed by remember {mutableStateOf<List<String>>(emptyList())}

    val scope = rememberCoroutineScope()
    val ctx = rememberPageContext()

    Div(
        attrs = Modifier.padding(24.px).toAttrs()
    ) {
        H1 {
            Text("Login Page")
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
    }

    Div() {
        Button(
            attrs = Modifier.toAttrs {
                onClick {
                    if (name.isNotEmpty() && password.isNotEmpty()) {
                        scope.launch {
                            val requestBody = Json.encodeToString(mapOf("username" to name, "password" to password))
                            try {
                                val options = js("{}")
                                options["method"] = "POST"
                                options["body"] = requestBody
                                options["headers"] = js("{ 'Content-Type': 'application/json' }")

                                val response = window.fetch("http://localhost:8081/api/login", options).await()
                                if (response.ok) {
                                    val responseText = response.text().await()
                                    val token = JSON.parse<dynamic>(responseText).token as? String
                                    if (token != null) {
                                        window.localStorage.setItem("auth_token", token)
                                        window.localStorage.setItem("username", name)
                                    }
                                    ctx.router.navigateTo("/dashboard?username=$name")
                                } else {
                                    println("Login failed with status: ${response.status}")
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
}
