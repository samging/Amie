package org.example.app.pages

import androidx.compose.runtime.*
import androidx.compose.runtime.NoLiveLiterals
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
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.files.File
import org.w3c.files.get
import org.w3c.xhr.FormData

@NoLiveLiterals
@Page
@Composable
fun EditPage() {
    val ctx = rememberPageContext()
    val packageName = ctx.route.params["package"] ?: ""
    val username = ctx.route.params["username"] ?: ""
    
    var fileName by remember { mutableStateOf<String?>(null) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var uploadStatus by remember { mutableStateOf<String?>(null) }
    var showPopup by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    if (showPopup) {
        LaunchedEffect(showPopup) {
            delay(5000)
            showPopup = false
        }
    }

    Div(Modifier.padding(24.px).toAttrs()) {
        H1 { Text("Update Package: $packageName") }
        P { Text("Editing as user: $username") }

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
                            showPopup = true
                        }
                    }
                }
        ) {
            if (fileName != null) {
                P { Text("New file selected: $fileName") }
            } else {
                Text("Drop the new version of your file here")
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
                    Text("New version of $fileName ready for submission!")
                }
            }
        }

        if (selectedFile != null) {
            Button(
                attrs = Modifier.margin(top = 20.px).toAttrs {
                    onClick {
                        scope.launch {
                            uploadStatus = "Updating..."
                            try {
                                val formData = FormData()
                                formData.append("file", selectedFile!!)
                                formData.append("filename", packageName)
                                formData.append("username", username)

                                val options = js("{}")
                                options["method"] = "POST"
                                options["body"] = formData
                                val headers = js("{}")
                                headers["Authorization"] = "Bearer ${window.localStorage.getItem("auth_token")}"
                                options["headers"] = headers

                                val response = window.fetch("http://localhost:8080/edit", options).await()
                                
                                if (response.ok) {
                                    val result = response.text().await()
                                    uploadStatus = "Success: $result"
                                    delay(2000)
                                    ctx.router.navigateTo("/dashboard?username=$username")
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
                Text("Submit Update")
            }
        }

        if (uploadStatus != null) {
            P(Modifier.margin(top = 10.px).toAttrs()) {
                Text(uploadStatus!!)
            }
        }
        
        Button(
            attrs = Modifier.margin(top = 10.px).toAttrs {
                onClick {
                    ctx.router.navigateTo("/dashboard?username=$username")
                }
            }
        ) {
            Text("Cancel")
        }
    }
}
