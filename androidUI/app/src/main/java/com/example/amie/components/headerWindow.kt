package com.example.amie.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import android.R.attr.maxWidth
import android.widget.Button
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.paddingFrom
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size


/**
 * A standard top application bar/header window component that provides uniform structural navigation,
 * including an optional back button and a conditional action button to append nested sub-components.
 *
 * This layout establishes a locked structural height banner designed to span the upper bounds of
 * the parent view canvas.
 *
 * ### Layout & Screen Responsiveness Notes
 * * **Fixed Dimensions:** Operates on a strict horizontal bounds with a hardcoded vertical ceiling of `48.dp`.
 * * **Adaptive Layout Dependencies:** Explicitly queries [LocalConfiguration] to read the runtime environment bounds.
 *
 * ### Architectural & Layout Warnings
 * > introduces unexpected layout breaks. On wide viewports (like tablets, foldables, or landscape layouts),
 * > Use a flexible layout pattern like a weighted [Spacer] (`Modifier.weight(1f)`) between the two elements instead.
 * >
 * > are completely omitted from the layout rendering pipeline.
 * >
 * > This makes the header un-customizable by its parent, meaning changes to padding, size constraints,
 * > or rendering shapes will be ignored.
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
fun headerWindow(name:String,
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
    val screenWidth = configuration.screenWidthDp.dp

    Box(
        modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .background(Color.Gray)
        .padding(top = 0.dp)
    ) {
        Row() {
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
            Spacer(modifier = Modifier.width(screenWidth * 0.75f))
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