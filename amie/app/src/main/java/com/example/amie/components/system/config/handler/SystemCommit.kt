package com.example.amie.components.system.config.handler
import androidx.compose.runtime.Composable
import com.example.amie.data.remote.parser.DeviceManagerJson
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.ui.platform.LocalContext
import java.io.File

/**
 * A specialized action button component designed to execute low-level system configuration writes
 * and subsequently trigger a navigation or lifecycle redirect callback upon successful execution.
 *
 * @param modifier The layout [Modifier] intended for extending configuration settings on the root container (Currently unmapped).
 * @param indexDevice The unique target device profile identifier or system dictionary index to be written.
 * @param keyValues A sequential list of configuration keys to be modified or appended inside the device configuration payload.
 * @param valuesOf A matching sequential list of configuration values corresponding structurally to the keys provided in [keyValues].
 * @param redirectOnOk A navigation or state-clearing callback function invoked immediately after the configuration transaction finishes.
 */
@Composable
fun SystemCommit(modifier: Modifier, indexDevice: String, keyValues: List<String>, valuesOf: List<String>, redirectOnOk: () -> Unit) {
    val sysConfig = DeviceManagerJson()
    val context = LocalContext.current
    val configFile = File(context.filesDir, "componentSettings.json")

    Column() {
        Button(
            modifier = Modifier.padding(start = 15.dp),
            onClick = {
            sysConfig.writeConfig(
                indexDevice = indexDevice,
                keyValue = keyValues,
                valueOf = valuesOf,
                configFile = configFile
            )
            redirectOnOk()
        }
        ) {
            Text("Connect")

        }
    }
}
