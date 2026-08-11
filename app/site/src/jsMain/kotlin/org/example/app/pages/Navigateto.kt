package org.example.app.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.overlay.Overlay
import com.varabyte.kobweb.silk.components.overlay.OverlayVars
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.await
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.files.File
import org.w3c.files.get
import org.w3c.xhr.FormData

@Page
@Composable
fun Navigateto() {
    var fileName by remember { mutableStateOf<String?>(null) }
    var fileSize by remember {mutableStateOf(0.0)}
    var fileSuffix by remember { mutableStateOf<String?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var packageName by remember { mutableStateOf("") }
    var packageDescription by remember { mutableStateOf("") }
    var progLanguage by remember { mutableStateOf("") }
    var showPopup by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var uploadStatus by remember { mutableStateOf<String?>(null) }

    
    val scope = rememberCoroutineScope()
    val ctx = rememberPageContext()
    val username = ctx.route.params["username"] ?: ""
    var loggedInUser by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val token = window.localStorage.getItem("auth_token")
        if (token != null) {
            try {
                val headers = js("{}")
                headers["Authorization"] = "Bearer $token"
                val options = js("{}")
                options["headers"] = headers

                val response = window.fetch("http://localhost:8080/dashboard", options).await()
                if (response.ok) {
                    val text = response.text().await()
                    loggedInUser = text.substringAfter("dashboard, ").substringBefore(" (ID:")
                } else {
                    ctx.router.navigateTo("/loginpage")
                }
            } catch (e: Exception) {
                println("Auth check failed in Navigateto: $e")
                ctx.router.navigateTo("/loginpage")
            }
        } else {
            ctx.router.navigateTo("/loginpage")
        }
    }

    if (showPopup) {
        LaunchedEffect(showPopup) {
            delay(5000)
            showPopup = false
        }
    }
    
    Div(Modifier.padding(24.px).toAttrs()) {
        H1 { Text("Upload your binary") }
        P { Text("Uploading as user: $username") }
    }

    H3 { Text("Package Name: $packageName") }
    Input(
        type = InputType.Text,
        attrs = Modifier
            .width(250.px)
            .padding(8.px)
            .toAttrs {
                placeholder("Package name")
                value(packageName)

                onInput { evt ->
                    packageName = evt.value
                }
            }
    )

    H3 { Text("Programming Language: ") }
    Input(
        type = InputType.Text,
        attrs = Modifier
            .width(250.px)
            .padding(8.px)
            .toAttrs {
                placeholder("e.g. Kotlin, Python, etc.")
                value(progLanguage)
                onInput { evt ->
                    progLanguage = evt.value
                }
            }
    )

    H3 { Text("Package Description: ") }
    Input(
        type = InputType.Text,
        attrs = Modifier
            .width(250.px)
            .padding(8.px)
            .toAttrs {
                placeholder("Description for usage")
                value(packageDescription)
                onInput { evt ->
                    packageDescription = evt.value
                }
            }
    )

    Div(
        attrs = Modifier
            .id("dropzone")
            .fillMaxWidth()
            .height(150.px)
            .margin(top = 20.px)
            .border(2.px, LineStyle.Dashed, if (isDragging) Colors.Blue else Colors.Gray)
            .borderRadius(8.px)
            .padding(20.px)
            .display(DisplayStyle.Flex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.Center)
            .toAttrs {
                onDragOver { evt ->
                    evt.preventDefault()
                    isDragging = true
                }
                onDragLeave { _ ->
                    isDragging = false
                }
                onDrop { evt ->
                    evt.preventDefault()
                    isDragging = false

                    val file = evt.dataTransfer?.files?.item(0) as? File
                    if (file != null) {
                        selectedFile = file
                        fileName = file.name
                        fileSize = file.size.toDouble()
                        fileSuffix = file.name.substringAfterLast(".")
                        showPopup = true
                    }
                }
            }
    ) {
        if (fileName != null) {
            P {
                Text("Selected file: $fileName")
            }
        } else {
            Text("Drop your binary file here")
        }
    }

    if (showPopup && fileName != null) {
        Overlay(
            Modifier
                .setVariable(OverlayVars.BackgroundColor, Colors.Transparent)
                .onClick { showPopup = false }
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .margin(top = 20.px)
                    .padding(12.px)
                    .backgroundColor(Colors.LightGreen)
                    .borderRadius(8.px)
                    .boxShadow(0.px, 4.px, 12.px, color = Colors.Black.toRgb().copyf(alpha = 0.2f))
            ) {
                Text("File $fileName prepared for submission!")
            }
        }
    }


    if (selectedFile != null) {
        Button(
            attrs = Modifier.margin(top = 20.px).toAttrs {
                onClick {
                    if (progLanguage.isBlank()) {
                        uploadStatus = "Please specify a programming language."
                        return@onClick
                    }
                    scope.launch {
                        uploadStatus = "Uploading..."
                        try {
                            val targetUser = loggedInUser ?: username
                            val formData = FormData()
                            formData.append("file", selectedFile!!)
                            formData.append("username", targetUser)
                            formData.append("progLanguage", progLanguage!!)

                            val options = js("{}")
                            options["method"] = "POST"
                            options["body"] = formData
                            val headers = js("{}")
                            headers["Authorization"] = "Bearer ${window.localStorage.getItem("auth_token")}"
                            options["headers"] = headers

                            val response = window.fetch("http://localhost:8080/upload", options).await()
                            
                            if (response.ok) {
                                val result = response.text().await()
                                uploadStatus = "Success: $result"
                                delay(2000)
                                ctx.router.navigateTo("/dashboard?username=$targetUser")
                            } else {
                                uploadStatus = "Error: ${response.statusText}"
                            }
                        } catch (e: Exception) {
                            uploadStatus = "Failed: ${e.message}"
                        }
                    }
                }
            }
        ) {
            Text("Submit Package")
        }

        Div( attrs = Modifier.fontSize(12.px).margin(top = 10.px).toAttrs()) {
            P { Text("File: $fileName") }
            P { Text("Size: ${(fileSize / 1024).toInt()} KB") }
            P { Text("Type: $fileSuffix") }
        }
    }

    if (uploadStatus != null) {
        P(Modifier.margin(top = 10.px).toAttrs()) {
            Text(uploadStatus!!)
        }
    }
}
