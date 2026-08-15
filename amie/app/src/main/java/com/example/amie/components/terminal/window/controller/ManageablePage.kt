package com.example.amie.components.terminal.window.controller

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A master configuration panel interface that provides inline status summaries and discrete edit actions
 * for endpoints, port allocations, device identifiers, and attached plugin arrays.
 */
@Composable
fun ManageablePage(
    name: String,
    deviceId: String,
    modifier: Modifier = Modifier,
    deviceName: String? = null,
    portNumber: Int? = null,
    endPoint: Int? = null,
    content: SnapshotStateList<String>,
    configureEndpoint: () -> Unit,
    configurePort: () -> Unit,
    configureName: () -> Unit,
    configurePlugins: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101319))
            .padding(16.dp)
    ) {
        Text(
            text = "Device Configuration",
            color = Color(0xFFc7cbd4),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Common Modifier for configuration rows
        val rowModifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF171b23))
            .border(width = 1.dp, color = Color(0xFF262b36), shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)

        // Configuration Rows with Icons and Descriptions
        ConfigurationRow(
            label = "Endpoint",
            value = endPoint?.toString() ?: "N/A",
            description = "Logic controller identifier",
            icon = Icons.Default.Router,
            onEdit = configureEndpoint,
            modifier = rowModifier
        )

        ConfigurationRow(
            label = "Port",
            value = portNumber?.toString() ?: "N/A",
            description = "Active serial communication channel",
            icon = Icons.Default.Build,
            onEdit = configurePort,
            modifier = rowModifier
        )

        ConfigurationRow(
            label = "Device Name",
            value = deviceName ?: "Unknown",
            description = "Friendly system alias",
            icon = Icons.Default.DriveFileRenameOutline,
            onEdit = configureName,
            modifier = rowModifier
        )

        ConfigurationRow(
            label = "Code Plugins",
            value = "System",
            description = "Functional extension modules",
            icon = Icons.Default.Extension,
            onEdit = configurePlugins,
            modifier = rowModifier
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun ConfigurationRow(
    label: String,
    value: String,
    description: String,
    icon: ImageVector,
    onEdit: () -> Unit,
    modifier: Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF878e9c),
                    modifier = Modifier.padding(end = 12.dp).height(18.dp)
                )
                Text(
                    text = "$label: ",
                    color = Color(0xFF878e9c),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = value,
                    color = Color(0xFFc7cbd4),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif
                )
            }
            Text(
                text = description,
                color = Color(0xFF878e9c).copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(start = 30.dp, top = 2.dp)
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Edit $label",
                tint = Color(0xFF878e9c)
            )
        }
    }
}
