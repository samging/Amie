package com.example.amie.components.system.config.handler

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.amie.data.remote.parser.DeviceManager
import com.example.amie.data.remote.parser.DeviceManagerCsv
import com.example.amie.data.remote.parser.DeviceManagerJson
import java.io.File

/**
 * A specialized action button component designed to execute low-level system configuration writes
 * and subsequently trigger a navigation or lifecycle redirect callback upon successful execution.
 */
@Composable
fun SystemCommit(
    modifier: Modifier = Modifier,
    indexDevice: String,
    keyValues: List<String>,
    valuesOf: List<String>,
    deviceManager: DeviceManager,
    redirectOnOk: () -> Unit
) {
    val context = LocalContext.current
    val configFile = File(context.filesDir, "componentSettings.json")

    Column(modifier = modifier) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (deviceManager is DeviceManagerJson) {
                    deviceManager.writeConfig(
                        indexDevice = indexDevice,
                        keyValue = keyValues,
                        valueOf = valuesOf,
                        configFile = configFile
                    )
                } else if (deviceManager is DeviceManagerCsv) {
                    deviceManager.writeConfig(
                        indexDevice = indexDevice,
                        keyValue = keyValues,
                        valueOf = valuesOf,
                        configFile = configFile
                    )
                }
                redirectOnOk()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF262b36),
                contentColor = Color(0xFFc7cbd4)
            )
        ) {
            Text("Connect", fontWeight = FontWeight.Bold)
        }
    }
}
