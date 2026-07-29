package com.example.amie.components.terminal.window.controller
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier


/**
 * This component acts as a clean abstraction container, dynamically formatting and forwarding configuration
 * properties down to an embedded [TerminalWindow].
 *
 * @param name A descriptive name or identifier tracking this configuration session block (Currently unused).
 * @param modifier The layout [Modifier] intended for extending structural behaviors on the root view container (Currently unmapped).
 * @param endPointNumber The explicit numerical identifier tracking the system configuration network endpoint.
 * @param allowCmd Dictates whether interactive command input and submission elements are accessible within the trailing terminal window view.
 */
@Composable
fun ConfigurationPanel(name: String,
                       modifier: Modifier = Modifier,
                       endPointNumber: Int? = null,
                       allowCmd: Boolean? = null,
                       logFilePath: String = "/data/local/tmp/logs.txt") {

    Column(modifier = modifier.fillMaxWidth()) {
        TerminalWindow(
            title = if (endPointNumber != null) "Endpoint configuration $endPointNumber" else "",
            modifier = Modifier,
            allowCmd = allowCmd,
            logFilePath = logFilePath
        )
    }
}


