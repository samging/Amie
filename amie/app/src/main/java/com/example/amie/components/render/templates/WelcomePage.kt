package com.example.amie.components.render.templates

import android.R
import android.provider.Settings
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import java.nio.file.WatchEvent
import androidx.navigation.NavHostController


@Composable
fun WelcomePage(navController: NavHostController) {
    val sessionModel = remember { SessionModel() }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF222222))) {

            Text(
                text = "amie",
                modifier = Modifier.align(Alignment.Center).offset(y = (-50).dp),
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
            verticalArrangement = Arrangement.Bottom
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                   navController.navigate("login")
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF30AFFF),
                    contentColor = Color(0xFFc7cbd4)
                ),
                enabled = !sessionModel.isLoading
            ) {
                Text("LOG IN", fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, letterSpacing = 1.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = {
                    navController.navigate("login")
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

            Row(
                modifier = Modifier.padding(top = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                TextButton(onClick = {
                    navController.navigate("login")
                }) {
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
