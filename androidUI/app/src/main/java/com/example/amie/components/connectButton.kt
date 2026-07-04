package com.example.amie.components
import androidx.compose.runtime.Composable
import com.example.amie.testFiles.systemConfigReader
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A specialized action button component designed to execute low-level system configuration writes
 * and subsequently trigger a navigation or lifecycle redirect callback upon successful execution.
 *
 * This component wraps a standalone "Connect" button inside a layout container. It is typically used
 * to commit queued peripheral or device settings to local persistent storage.
 *
 * ### Lifecycle & Event Side-Effects
 * * **Synchronous Configuration Commit:** On a click interaction, the component initializes an instance of
 * [systemConfigReader], writes the combined parameters directly to the filesystem, and then evaluates [redirectOnOk].
 *
 * ### Architectural & Performance Warnings
 * > Executing synchronous disk I/O routines within the main UI thread path can cause frame drops, visual stuttering,
 * > or trigger an Application Not Responding (ANR) dialog. Consider rewriting this block to execute inside an
 * > asynchronous coroutine scope (e.g., using a `rememberCoroutineScope` or moving the persistence layer to a ViewModel).
 * >
 * > This breaks standard Compose styling guidelines, preventing any parent container from customizing the structural padding,
 * > sizing, or boundary alignments of this component.
 *
 * @param modifier The layout [Modifier] intended for extending configuration settings on the root container (Currently unmapped).
 * @param indexDevice The unique target device profile identifier or system dictionary index to be written.
 * @param keyValues A sequential list of configuration keys to be modified or appended inside the device configuration payload.
 * @param valuesOf A matching sequential list of configuration values corresponding structurally to the keys provided in [keyValues].
 * @param redirectOnOk A navigation or state-clearing callback function invoked immediately after the configuration transaction finishes.
 */
@Composable
fun connectButtonWriter(modifier: Modifier, indexDevice: String, keyValues: List<String>, valuesOf: List<String>, redirectOnOk: () -> Unit) {
    Column() {
        Button(
            modifier = Modifier.padding(start = 15.dp),
            onClick = {
            val sysConfig = systemConfigReader()
            sysConfig.writeConfig(
                indexDevice = indexDevice,
                keyValue = keyValues,
                valueOf = valuesOf,
            )
            redirectOnOk()
        }
        ) {
            Text("Connect")

        }

        //Text(text = " ${indexDevice.toString()}\n |||| ${keyValues.toString()}\n |||| ${valuesOf.toString()}")
    }
}