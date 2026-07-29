package com.example.amie.components.ui.viewport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.size


/**
 * A standard top application bar/header window component that provides uniform structural navigation,
 * including an optional back button and a conditional action button to append nested sub-components.
 *
 * @param name A descriptive name or text label tracking this structural scope (Currently unused).
 * @param modifier The layout [Modifier] intended for extending configuration settings on the root layout container (Currently unmapped).
 * @param endController An active upstream controller configuration target (Currently unused).
 * @param endPort A targeted endpoint network index or virtual assignment reference (Currently unused).
 * @param onConfigure A state modification handler lambda (Currently unused).
 * @param showOnBack Dictates whether the navigation back arrow element is visible on screen. Defaults to `true`.
 * @param onBack Triggered immediately when the user interacts with the leading navigation back chevron icon.
 * @param addComponent Dictates whether the generic trailing action icon button is visible on screen. Defaults to `false`.
 * @param addComponentNav Triggered immediately when the user interacts with the trailing action icon element.
 */
@Composable
fun WindowHeader(name:String,
                 modifier: Modifier = Modifier,
                 endController: Int?,
                 endPort: Int? = null,
                 onConfigure: () -> Unit  = {},
                 showOnBack: Boolean = true,
                 onBack: () -> Unit,
                 addComponent: Boolean = false,
                 addComponentNav: () -> Unit = {}) {
    val configuration = LocalConfiguration.current

    val screenHeight = configuration.screenHeightDp.dp

    Box(
        modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .background(Color.Gray)
        .padding(top = 0.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onBack() },
                modifier = Modifier.size(48.dp) //because it's row, I need margin or space there
            ) {
                if ( showOnBack ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = "Manage settings"
                    )
                }
            }
            Text(
                text = name,
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { addComponentNav() },
                modifier = Modifier.size(48.dp)
            ) {
                if ( addComponent ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create component"
                    )
                }
            }
        }
    }

}