package com.example.amie.components

import android.R
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.nio.file.WatchEvent
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import android.graphics.Color.green
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
//making this loose coupled
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.graphics.component1
import androidx.core.graphics.component2

/**
 * A scrollable container that renders a structured list of key-value item pairs (IDs and descriptions).
 * * Provides a vertical scrolling canvas designed to present configuration fields, logs, or mapped index records
 * in a uniform list format.
 *
 * ### UI & Layout Architecture
 * * **Scrolling Behavior:** Inherits a vertical scroll modifiers pattern utilizing [rememberScrollState] to handle
 * dynamic content heights gracefully within a bounded container.
 * * **Typography:** Uses a strict [FontFamily.Monospace] styling for numeric identifiers to preserve clean, tabular text alignment.
 *
 * ### Performance & State Warnings
 * > raw map read-throughs if structural stability isn't a concern.
 * >
 * > an empty list on every iteration loop. Consequently, the success color (`0xFF4CAF50`) code path is unreachable.
 * >
 * > are initialized but never read or bound by the child layout components.
 *
 * @param name A descriptive name or identifier for the page (Currently unused in layout).
 * @param modifier The [Modifier] instance used to apply layout adjustments or sizing decorations to the root container.
 * @param activeFields A map containing the definitive integer indices linked to their respective text descriptions.
 * @param currentlyActive A list of targeted numeric indices intended for active highlighting states (Defaults to `[-1]`).
 */
@Composable
fun scrollableImutPage(name: String,
                   modifier: Modifier,
                   activeFields: Map<Int,String>,
                   currentlyActive: List<Int?> = listOf(-1)){


    val activeList = remember {
        activeFields
    }
    val selectedIds = remember {
        mutableStateListOf<Int?>().apply { addAll(currentlyActive) }
    }

    val verticalScroller = rememberScrollState()

    Column(
        modifier = Modifier.verticalScroll(verticalScroller).padding(top=10.dp).fillMaxSize()
    ) {
        for ((id, description) in activeList) {
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween)
            {
                Row() {
                    if (id in listOf<Int>()) {
                        Text(
                            color = Color(0xFF4CAF50),
                            text = "[${id}]",
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Text(text = "[${id}]", fontFamily = FontFamily.Monospace)
                    }
                    Text(" ${description}")
                }
            }
        }
    }
}
