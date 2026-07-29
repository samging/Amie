package com.example.amie.components.terminal.window.controller
//[4] Connection page

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp

/**
 * A connectivity dashboard row component designed to display specific endpoint connection paths,
 * network link health, and attach nested terminal telemetry feeds.
 *
 * @param name The unique application or section title string mapped to header tag the internal [TerminalWindow].
 * @param modifier The layout [Modifier] intended for extending customization configurations onto the root element (Currently unmapped).
 * @param endController An operational controller instance (Currently unused).
 * @param endPoint The active host or target network routing index address displayed dynamically in the upper row header.
 * @param status Monitors the current active network state indicator (e.g., `true` for connected, `false` for offline).
 * @param connectionRedirect Triggered immediately when the user clicks the settings gear icon to navigate or reconfigure connection properties.
 */
@Composable
fun ConnectionPanel(name: String,
                    modifier: Modifier = Modifier,
                    endController: Int?,
                    endPoint: Int? = null,
                    status: Boolean,
                    logFilePath: String = "/data/local/tmp/logs.txt",
                    connectionRedirect: () -> Unit) {

    Column(modifier = modifier.fillMaxWidth()) {

        Row(modifier = Modifier.background(color = Color.White).fillMaxWidth().border(width = 1.dp, color = Color.Gray),
            verticalAlignment = Alignment.CenterVertically)
        {

            Text("Endpoint: $endPoint", fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { connectionRedirect() },
                modifier = Modifier.width(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Manage settings"
                )
            }
        }

        Row(modifier = Modifier.background(color = Color.White).fillMaxWidth().border(width = 1.dp, color = Color.Gray), verticalAlignment = Alignment.CenterVertically) {
            Text("Status: $status", fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { },
                modifier = Modifier.width(150.dp)
            ) {
                Text("Disconnect")
            }
        }

        TerminalWindow(title = name, modifier = Modifier, logFilePath = logFilePath)
    }
}
