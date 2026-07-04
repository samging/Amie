package com.example.amie.components

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
import com.example.amie.testFiles.systemConfigReader

/**
 * A device discovery and management panel that provides an input field for querying peripheral profiles
 * and an optional trigger action to persist hardware configuration profiles.
 *
 * This UI component features a dual-state feedback system, rendering inline connection success states
 * or distinct string error readouts based on text criteria validations.
 *
 * ### UI Architecture & Data Synchronizations
 * * **Bidirectional State Sync:** Automatically mirrors changes to the input field down to both a local
 * mutable state `buffer` and the upstream state hoisted via [onValueChange].
 * * **Conditional Submission:** When [hideButton] is `false`, renders a trailing button built with a custom
 * label derived from [customText].
 *
 * ### Architectural & Threading Warnings
 * > to `systemConfigReader().writeConfig(...)`. Executing file system writes inside UI click listeners
 * > blocks the main UI thread, risking frames dropping or Application Not Responding (ANR) flags. Consider
 * > running this transaction within a coroutine scope (`LaunchedEffect` / ViewModel lifecycle).
 * >
 * > as `null`, this will serialize as the literal string `"null"` rather than resolving to a safe identifier or safe-failing.
 * >
 * > but never integrated into any selection menus or filtering components.
 *
 * @param name The descriptive entity context title (e.g., "Printer", "Bluetooth Node") used to dynamic label texts.
 * @param modifier The layout [Modifier] cleanly mapped to decorate and size the root [Column] outer layout container.
 * @param hideButton Flags whether the action confirmation button should be omitted entirely from the horizontal row layout. Defaults to `false`.
 * @param valueOf The initial string value bound to seed the text input field upon first composition.
 * @param onValueChange Emits real-time string value changes upstream whenever the user modifies the text input field.
 * @param customText The display label rendered inside the text layout of the confirmation action button. Defaults to "Connect".
 * @param keyQuery The system configuration query lookup key parameter string passed directly down to the storage layer block.
 * @param writeId The contextual subsystem targeted folder or profile group identifier key. Pass `null` with caution.
 */
@Composable
fun DeviceManage(name: String,
                 modifier: Modifier = Modifier,
                 hideButton: Boolean = false,
                 valueOf: String = "",
                 onValueChange: (String) -> Unit = {},
                 customText: String = "Connect",
                 keyQuery: String = "",
                 writeId: String? = null) {
    val dummyDevices = listOf("dev1", "dev2", "dev3")

    var buffer by remember { mutableStateOf(valueOf) }
    var selectedDevice by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf("") }

    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "Search for ${name}:")
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            OutlinedTextField(
                value = buffer,
                onValueChange = { line ->
                    buffer = line
                    onValueChange(line) },
                label = { Text("${name}") },
                modifier = Modifier.weight(1f)
            )

            if (!hideButton){
            Button(onClick = {
                if (buffer.isNotEmpty()) {
                    selectedDevice = buffer
                    errorMessage = ""
                    onValueChange(buffer)
                    systemConfigReader().writeConfig(
                        writeId.toString(),
                        listOf(keyQuery),
                        listOf(buffer))
                } else {
                    selectedDevice = null
                    errorMessage = "Couldn't find device name"
                }

            }) {
                Text(text = "${customText}")
            }
        }
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (selectedDevice != null) {
            Text(text = "Connected safely to: $selectedDevice", color = Color(0xFF4CAF50))

        } else if (errorMessage.isNotEmpty()) {
            Text(text = errorMessage, color = Color.Red)
        }
    }
}