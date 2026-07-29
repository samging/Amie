package com.example.amie.components.system.config.handler


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.amie.util.fetchFilesList
import androidx.compose.runtime.LaunchedEffect
/**
 * A search and management interface component used for querying remote packages, validating
 * dependencies, and displaying local connection/error states.
 *
 * @param name A descriptive identifier or label (Currently unused in layout).
 * @param modifier The [Modifier] to be applied to the root [Column] layout container.
 * @param customText The custom label text for an action (Defaults to "Connect"; currently unused).
 */
@Composable
fun RemotePackageConsole(name: String, modifier: Modifier = Modifier, customText: String = "Connect") {
    val dummyDevices = listOf("dev1", "dev2", "dev3")

    var buffer by remember { mutableStateOf("") }
    var selectedDevice by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    var packages by remember { mutableStateOf<String>("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        packages = fetchFilesList()
        isLoading = false
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "Search for package:")
        Spacer(modifier = Modifier.height(8.dp))

        var searchResult by remember { mutableStateOf<Boolean?>(null)}
        val packageNames: List<String> = listOf("package1", "package2", "package3","package4")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = buffer,
                onValueChange = { line -> buffer = line },
                label = { Text("Dependency Name") },
                modifier = Modifier.weight(1f)
            )


            IconButton(
                onClick = { searchResult = buffer in packages },
                modifier = Modifier
                    .width(48.dp)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search packages"
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        when(searchResult) {
            true -> Text(text = "Adding package", color = Color(0xFF4CAF50))
            false -> Text(text = "Package not found", color = Color.Red)
            null -> {}
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Text("Loading packages from server...")
        } else {
            Text(text = packages)
        }

        if (selectedDevice != null) {
            Text(text = "Connected safely to: $selectedDevice", color = Color(0xFF4CAF50))
        } else if (errorMessage.isNotEmpty()) {
            Text(text = errorMessage, color = Color.Red)
        }
    }
}