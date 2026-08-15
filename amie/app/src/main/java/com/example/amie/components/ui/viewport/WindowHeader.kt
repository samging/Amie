package com.example.amie.components.ui.viewport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A standard top application bar/header window component that provides uniform structural navigation,
 * including an optional back button and a conditional action button to append nested sub-components.
 */
@Composable
fun WindowHeader(name:String,
                 showUser: Boolean = false,
                 user: String = "",
                 modifier: Modifier = Modifier,
                 showOnBack: Boolean = true,
                 onBack: () -> Unit,
                 addComponent: Boolean = false,
                 addComponentNav: () -> Unit = {}) {

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color(0xFF101319))
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button
            if (showOnBack) {
                IconButton(
                    onClick = { onBack() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = "Go back",
                        tint = Color.White
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(16.dp))
            }

            // User Chip
            if (showUser && user.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF262b36))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "User icon",
                        tint = Color(0xFFc7cbd4),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = user,
                        color = Color(0xFFc7cbd4),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.size(16.dp))
            }

            // Title
            Text(
                text = name,
                color = Color(0xFFc7cbd4),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.weight(1f))

            // Add Component Button
            if (addComponent) {
                IconButton(
                    onClick = { addComponentNav() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create component",
                        tint = Color(0xFFc7cbd4)
                    )
                }
            }
        }
        
        // Bottom subtle border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF262b36))
                .align(Alignment.BottomCenter)
        )
    }
}
