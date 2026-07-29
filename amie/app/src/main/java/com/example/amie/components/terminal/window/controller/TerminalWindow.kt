package com.example.amie.components.terminal.window.controller

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf

//using locally implemented library:
import com.example.amie.util.readLogFile
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.graphics.RectangleShape

/**
 * A standard terminal emulator window component that displays recent system logs and provides
 * an optional interactive command input line.
 *
 * @param title The descriptive title displayed in the terminal's header bar (e.g., "System Logs").
 * @param modifier The [Modifier] to be applied to the outer layout container of this terminal window.
 * @param content The initial fallback log list to use if no persistent logs are discovered.
 * Defaults to a "No logs" state list.
 * @param allowCmd Dictates whether the interactive command input field and submit action
 * should be rendered at the bottom of the window. Pass `null` or `false` to keep it read-only.
 */
@Composable
fun TerminalWindow(
    title: String,
    modifier: Modifier = Modifier,
    logFilePath: String = "/data/local/tmp/logs.txt",
    content: SnapshotStateList<String> = remember(logFilePath) {
        readLogFile(logFilePath).toMutableStateList()
    },
    allowCmd: Boolean? = false
) {
    val terminalLogs = content

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = Color.Black) // Slightly lighter header bar
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "Terminal window: ${title}",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 16.dp)
                )

            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .weight(1f)
            ) {
                for ( c in terminalLogs.takeLast(5)) {
                    Text(c)
                }
                if ( allowCmd ?: false ) {

                    var textInput by remember { mutableStateOf("") }
                    Spacer(modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { newValue -> textInput = newValue },
                        label = { Text("Enter terminal command") },
                        placeholder = { Text("Input field") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),

                        trailingIcon = {
                            if (textInput.isNotEmpty()) {
                                IconButton(onClick = {
                                    textInput = ""
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Send command"
                                    )
                                }
                            }
                        },
                    )
                }
            }
            Column() {
                Button(onClick = { terminalLogs.add("someting1") }) {
                }
            }

        }
    }
}