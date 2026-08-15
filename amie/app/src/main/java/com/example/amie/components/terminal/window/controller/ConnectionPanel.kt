package com.example.amie.components.terminal.window.controller
//[4] Connection page

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A connectivity dashboard row component designed to display specific endpoint connection paths,
 * network link health, and attach nested terminal telemetry feeds.
 */
@Composable
fun ConnectionPanel(name: String,
                    deviceId: String,
                    modifier: Modifier = Modifier,
                    endPoint: Int? = null,
                    status: Boolean,
                    logFilePath: String = "/data/local/tmp/logs.txt",
                    connectionRedirect: () -> Unit) {

    Column(modifier = modifier.fillMaxSize().background(Color(0xFF101319)).padding(16.dp)) {
        Text(
            text = "Device Connection",
            color = Color(0xFFc7cbd4),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Configuration Rows
        val rowModifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color = Color(0xFF171b23))
            .border(width = 1.dp, color = Color(0xFF262b36), shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)

        Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
            Text("Endpoint: $endPoint", color = Color(0xFFc7cbd4), fontSize = 16.sp)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { connectionRedirect() }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Manage settings",
                    tint = Color(0xFF878e9c)
                )
            }
        }

        Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
            Text("Status: ${if (status) "Connected" else "Disconnected"}", 
                 color = Color(0xFFc7cbd4), 
                 fontSize = 16.sp,
                 fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF262b36),
                    contentColor = Color(0xFFc7cbd4)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(if (status) "Disconnect" else "Connect", fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).border(1.dp, Color(0xFF262b36), RoundedCornerShape(8.dp))) {
            TerminalWindow(title = name, deviceId = deviceId, modifier = Modifier, logFilePath = logFilePath)
        }
    }
}
