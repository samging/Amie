package com.example.amie.components.render.templates

import android.provider.Settings
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class UserLogin(
    val username: String = "",
    val password: String = ""
)

@Serializable
data class HttpError<T>(val message: T?) {
    companion object {
        inline fun <T> build(block: () -> T?): HttpError<T> = HttpError(block())
    }
}

class SessionModel {
    var isLoading by mutableStateOf(false)
    var validateResponse by mutableStateOf(false)
    var dialogResponse by mutableStateOf(false)
    var fatalDialogResponse by mutableStateOf(false)
    var lastErrorMessage by mutableStateOf<String?>(null)

    val client = HttpClient(Android) {
        install(HttpTimeout)
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }

    suspend fun login(username: String = "", password: String = ""): Map<String, String> {
        isLoading = true
        val hostIp = "192.168.1.103"
        
        println("DEBUG: Testing internet connectivity via google.com...")
        try {
            client.get("https://www.google.com")
            println("DEBUG: Internet check successful")
        } catch (e: Exception) {
            println("DEBUG: Internet check FAILED: ${e.message}")
        }

        println("DEBUG: Testing local server connectivity via http://$hostIp:8080/list-disk...")
        try {
            val testResponse = client.get("http://$hostIp:8080/list-disk")
            println("DEBUG: Local server check successful: ${testResponse.status}")
        } catch (e: Exception) {
            println("DEBUG: Local server check FAILED: ${e.message}")
        }

        println("DEBUG: Starting login attempt for user: $username at http://$hostIp:8080/login")
        try {
            val response = client.post("http://$hostIp:8080/login") {
                timeout {
                    requestTimeoutMillis = 15_000L
                    connectTimeoutMillis = 15_000L
                    socketTimeoutMillis = 15_000L
                }
                contentType(ContentType.Application.Json)
                setBody(UserLogin(username, password))
            }
            
            println("DEBUG: Received response with status: ${response.status}")
            
            if (!response.status.isSuccess()) {
                val error = try {
                    response.body<String>()
                } catch (e: Exception) {
                    "Error code: ${response.status}"
                }
                println("DEBUG: Login failed: $error")
                handleLoginError(error)
                return emptyMap()
            } else {
                println("DEBUG: Login successful")
                validateResponse = true
                return try {
                    response.body<Map<String, String>>()
                } catch (e: Exception) {
                    mapOf("status" to "success")
                }
            }
        } catch (e: Exception) {
            println("DEBUG: Exception during login: ${e.message}")
            e.printStackTrace()
            handleLoginError("Connection failed: ${e.message}")
            return emptyMap()
        } finally {
            isLoading = false
            println("DEBUG: Login attempt finished")
        }
    }

    suspend fun handleLoginError(errorMessage: String?) {
        lastErrorMessage = errorMessage
        dialogResponse = true
        val hostIp = "192.168.1.103"
        
        try {
            val tweakResponse = client.post("http://$hostIp:8080/handle-login-error") {
                timeout {
                    requestTimeoutMillis = 9_000L
                    connectTimeoutMillis = 9_000L
                    socketTimeoutMillis = 9_000L
                }
                contentType(ContentType.Application.Json)
                setBody(mapOf("error" to (errorMessage ?: "Unknown"), "username" to "unknown_user"))
            }

            if (!tweakResponse.status.isSuccess()) {
                fatalDialogResponse = true
            }
        } catch (e: Exception) {
            println("Failed to log error to server: ${e.message}")
        }
    }
}

@Composable
fun PrintErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Error", color = Color(0xFFc7cbd4), fontWeight = FontWeight.Bold)
        },
        text = {
            Text(text = message, color = Color(0xFF878e9c))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = Color(0xFFc7cbd4))
            }
        },
        containerColor = Color(0xFF171b23),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101319).copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = Color(0xFFc7cbd4),
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Connecting...",
                color = Color(0xFF878e9c),
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun LoginPage(onLoginSuccess: (String) -> Unit) {
    val sessionModel = remember { SessionModel() }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val interactionSrouceScnd = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isFocusedScnd by interactionSrouceScnd.collectIsFocusedAsState()
    var passwordVisible by remember { mutableStateOf(false) }


    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF222222))) {
        Text(
            text = "AMIE",
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (70).dp),
            style = TextStyle(
                color = Color.White,
                fontSize = 120.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.sp
            )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                singleLine = true,
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFFc7cbd4),
                    unfocusedTextColor = Color(0xFFc7cbd4).copy(alpha = 0.7f),
                    focusedContainerColor = Color(0xFF171b23),
                    unfocusedContainerColor = Color(0xFF171b23),
                    cursorColor = Color(0xFFc7cbd4),
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color(0xFF262b36).copy(alpha = 0.5f)
                ),
                label = {
                    Text("Username", color = if (isFocused) Color.White else Color(0xFF878e9c), fontSize = 14.sp)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                interactionSource = interactionSrouceScnd,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    val description = if (passwordVisible) "Hide password" else "Show password"

                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = description, tint = if (isFocusedScnd) Color.White else Color(0xFF878e9c))
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFFc7cbd4),
                    unfocusedTextColor = Color(0xFFc7cbd4).copy(alpha = 0.7f),
                    focusedContainerColor = Color(0xFF171b23),
                    unfocusedContainerColor = Color(0xFF171b23),
                    cursorColor = Color(0xFFc7cbd4),
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color(0xFF262b36).copy(alpha = 0.5f)
                ),
                label = {
                    Text("Password", color = if (isFocusedScnd) Color.White else Color(0xFF878e9c), fontSize = 14.sp)
                }
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {
                    scope.launch {
                        val token = sessionModel.login(username, password)
                        if (token.isNotEmpty()) {
                            onLoginSuccess(username)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF30AFFF),
                    contentColor = Color(0xFFc7cbd4)
                ),
                enabled = !sessionModel.isLoading
            ) {
                Text("Continue", fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, letterSpacing = 1.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = {
                    val androidId: String = Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ANDROID_ID
                    )
                    scope.launch {
                        val token: Map<String, String> = sessionModel.login("guest-${androidId}", "")
                        if (token.isNotEmpty()) {
                            onLoginSuccess("guest-${androidId}")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF878e9c)
                ),
                border = BorderStroke(1.dp, Color(0xFF262b36)),
                enabled = !sessionModel.isLoading
            ) {
                Text("GUEST ACCESS", fontSize = 11.sp, fontFamily = FontFamily.SansSerif, letterSpacing = 1.sp)
            }

            // Developer / Force actions
            Row(
                modifier = Modifier.padding(top = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                TextButton(onClick = { onLoginSuccess("forced-success") }) {
                    Text("OVERRIDE", color = Color(0xFF878e9c).copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.SansSerif)
                }

                TextButton(onClick = { 
                    scope.launch { sessionModel.handleLoginError("Manual failure trigger") } 
                }) {
                    Text("FAILSAFE", color = Color(0xFF878e9c).copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.SansSerif)
                }
            }
        }

        if (sessionModel.isLoading) {
            LoadingOverlay()
        }

        if (sessionModel.dialogResponse) {
            PrintErrorDialog(
                message = sessionModel.lastErrorMessage ?: "Unknown error",
                onDismiss = { sessionModel.dialogResponse = false }
            )
        }
    }
}
