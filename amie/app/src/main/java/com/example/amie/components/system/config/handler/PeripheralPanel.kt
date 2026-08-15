package com.example.amie.components.system.config.handler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.amie.data.remote.parser.DeviceManagerJson
import com.example.amie.data.remote.parser.DeviceManager
import com.example.amie.data.remote.parser.DeviceManagerCsv

import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

/**
 * A device discovery and management panel that provides an input field for querying peripheral profiles
 * and an optional trigger action to persist hardware configuration profiles.
 *
 * @param name The descriptive entity context title (e.g., "Printer", "Bluetooth Node") used to dynamic label texts.
 * @param modifier The layout [Modifier] cleanly mapped to decorate and size the root [Column] outer layout container.
 * @param hideButton Flags whether the action confirmation button should be omitted entirely from the horizontal row layout. Defaults to `false`.
 * @param valueOf The initial string value bound to seed the text input field upon first composition.
 * @param onValueChange Emits real-time string value changes upstream whenever the user modifies the text input field.
 * @param customText The display label rendered inside the text layout of the confirmation action button. Defaults to "Connect".
 * @param keyQuery The system configuration query lookup key parameter string passed directly down to the storage layer block.
 * @param writeId The contextual subsystem targeted folder or profile group identifier key. Pass `null` with caution.
 * @param username Optional username to prefix the value if keyQuery is "deviceEndpoint".
 */
@Composable
fun PeripheralPanel(name: String,
                    modifier: Modifier = Modifier,
                    hideButton: Boolean = false,
                    valueOf: String = "",
                    onValueChange: (String) -> Unit = {},
                    customText: String = "Connect",
                    keyQuery: String = "",
                    writeId: String? = null,
                    deviceManager: DeviceManager? = null,
                    username: String? = null) {
    val dummyDevices = listOf("dev1", "dev2", "dev3")
    val context = LocalContext.current
    val configFile = File(context.filesDir, "componentSettings.json")

    var buffer by remember { mutableStateOf(valueOf) }
    var selectedDevice by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf("") }

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Search for ${name}:",
            color = Color(0xFF878e9c),
            fontSize = 14.sp,
            fontFamily = FontFamily.SansSerif
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            OutlinedTextField(
                value = buffer,
                onValueChange = { line ->
                    buffer = line
                    onValueChange(line)
                },
                label = { Text(text = name, color = Color(0xFF878e9c)) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFFc7cbd4),
                    unfocusedTextColor = Color(0xFFc7cbd4).copy(alpha = 0.7f),
                    focusedContainerColor = Color(0xFF171b23),
                    unfocusedContainerColor = Color(0xFF171b23),
                    cursorColor = Color(0xFFc7cbd4),
                    focusedBorderColor = Color(0xFF262b36),
                    unfocusedBorderColor = Color(0xFF262b36).copy(alpha = 0.5f)
                )
            )

            if (!hideButton) {
                Button(
                    onClick = {
                        if (buffer.isNotEmpty()) {
                            // Pre-process value if it's an endpoint and username is present
                            val finalValue = if (keyQuery == "deviceEndpoint" && !username.isNullOrEmpty()) {
                                if (buffer.startsWith(username)) buffer else "${username}${buffer}"
                            } else {
                                buffer
                            }

                            selectedDevice = finalValue
                            errorMessage = ""
                            onValueChange(finalValue)

                            if (deviceManager is DeviceManagerJson) {
                                deviceManager.writeConfig(
                                    writeId.toString(),
                                    listOf(keyQuery),
                                    listOf(finalValue),
                                    configFile = configFile
                                )
                            } else if (deviceManager is DeviceManagerCsv) {
                                deviceManager.writeConfig(
                                    writeId.toString(),
                                    listOf(keyQuery),
                                    listOf(finalValue),
                                    configFile = configFile
                                )
                            }
                        } else {
                            selectedDevice = null
                            errorMessage = "Couldn't find device name"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF262b36),
                        contentColor = Color(0xFFc7cbd4)
                    )
                ) {
                    Text(text = customText, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (selectedDevice != null) {
            Text(
                text = "Connected safely to: $selectedDevice",
                color = Color(0xFFc7cbd4),
                fontSize = 13.sp
            )

        } else if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = Color(0xFF878e9c),
                fontSize = 13.sp
            )
        }
    }
}