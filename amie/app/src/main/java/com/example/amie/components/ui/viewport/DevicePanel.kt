package com.example.amie.components.ui.viewport

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
 * A hardware connectivity dashboard utility panel that displays peripheral connection paths and
 * provides an icon-based configuration matrix (Manage, Connect, Configure, Disconnect).
 */
@Composable
fun DevicePanel(
    name: String,
    modifier: Modifier = Modifier,
    deviceEdnpoint: Any?,
    endPort: Any? = null,
    onManage: () -> Unit,
    onConfigure: () -> Unit,
    onConnectPage: () -> Unit,
    onDisconnect: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF171b23))
            .border(width = 1.dp, color = Color(0xFF262b36), shape = RoundedCornerShape(12.dp))
    ) {
        Column {
            // Header Row with Name, Endpoint, Port inlined
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Color(0xFF262b36))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = name.uppercase(),
                        color = Color(0xFFc7cbd4),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 1.sp
                    )
                    
                    // Vertical Separator
                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(Color(0xFF000000).copy(alpha = 0.6f)))

                    // Endpoint Badge
                    BadgeLabel(text = deviceEdnpoint.toString())
                    
                    // Port Badge
                    BadgeLabel(text = "COM$endPort")
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = Color(0xFF878e9c)
                    )
                }
            }

            if (expanded) {
                // Action Grid with Icons and Descriptions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        ActionItem(
                            label = "Configure",
                            description = "Edit local settings",
                            icon = Icons.Default.Settings,
                            onClick = onConfigure,
                            modifier = Modifier.weight(1f)
                        )
                        ActionItem(
                            label = "Connect",
                            description = "Establish node link",
                            icon = Icons.Default.Link,
                            onClick = onConnectPage,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        ActionItem(
                            label = "Terminal",
                            description = "System shell access",
                            icon = Icons.Default.Terminal,
                            onClick = onManage,
                            modifier = Modifier.weight(1f)
                        )
                        ActionItem(
                            label = "Remove",
                            description = "Delete device profile",
                            icon = Icons.Default.DeleteSweep,
                            onClick = onDisconnect,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeLabel(text: String) {
    Surface(
        color = Color(0xFF3186a0).copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF4fccf3).copy(alpha = 0.4f))
    ) {
        Text(
            text = text,
            color = Color(0xFF4fccf3),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun ActionItem(
    label: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .padding(4.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp)),
        color = Color(0xFF262b36),
        contentColor = Color(0xFFc7cbd4)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF878e9c),
                modifier = Modifier.size(20.dp)
            )
            Column(
                modifier = Modifier.padding(start = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = Color(0xFFc7cbd4)
                )
                Text(
                    text = description,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.SansSerif,
                    color = Color(0xFF878e9c).copy(alpha = 0.7f)
                )
            }
        }
    }
}
