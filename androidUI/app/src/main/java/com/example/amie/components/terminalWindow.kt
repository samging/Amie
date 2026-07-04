package com.example.amie.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import android.R.attr.onClick
import androidx.compose.material3.Button
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf

//using locally implemented library:
import com.example.amie.testFiles.readLogFile
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.graphics.RectangleShape

/**
 * A standard terminal emulator window component that displays recent system logs and provides
 * an optional interactive command input line.
 * * This component reads underlying log files directly from the local device storage and displays
 * the tail end of the log stream.
 * * ### Layout & Styling
 * The window is styled as a flat, full-width [Card] using a [RectangleShape]. The header bar
 * is distinctively styled in [Color.Black] with monospace typography to mimic a CLI environment.
 *
 * ### Performance Warning
 * > **Disk I/O on Main Thread:** The current implementation invokes [readLogFile] directly inside
 * > the standard composition phase using `remember`. This blocks the UI thread during disk reads.
 * > Consider moving this side-effect into a `LaunchedEffect` or a proper ViewModel architecture.
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
    content: SnapshotStateList<String> = remember { mutableStateListOf("No logs")},
    allowCmd: Boolean? = false
) {
    val terminalLogs = remember {
        readLogFile("/data/local/tmp/logs.txt").toMutableStateList()
    };

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
                    .fillMaxHeight()
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