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
 * A scrollable container displaying a list of active fields that allows users to interactively
 * toggle selection states for individual indexed items.
 *
 * Each row renders an item index badge, a textual description, and a localized [Button]
 * that acts as a selection toggle.
 *
 * ### UI Architecture & Selection Behaviors
 * * **Visual Highlighting:** When an item's ID is active within the tracking state, its numeric index badge
 * renders in an accent color (`0xFF4CAF50`). Otherwise, it renders using standard text colors.
 * * **State Mutation:** Clicking the "Opt" button dynamically adds or removes that row's ID from the tracking list.
 *
 * ### Architectural & State Warnings
 * > via `remember { mutableStateListOf<Int>().apply { addAll(currentlyActive) } }`. Because it uses `remember`
 * > without a key, **updates to the parent property [currentlyActive] will be completely ignored** after the initial
 * > composition. If you want external changes to update the selection, use `remember(currentlyActive)` or handle
 * > selection entirely through state hoisting.
 * >
 * > changes if the parent passes a brand new map reference.
 * >
 * > leaving the outer [Column] hardcoded to `fillMaxSize()`. This prevents the parent layout from setting custom
 * > constraints, paddings, or margins.
 *
 * @param name An operational or descriptive identifier for this page scope (Currently unused in layout).
 * @param modifier The layout [Modifier] intended for extending configuration settings on the root layout container (Currently unmapped).
 * @param activeFields A map containing the definitive integer indices linked to their respective text descriptions.
 * @param currentlyActive The initial list of integer IDs that should be marked as selected upon the first composition.
 */
@Composable
fun scrollablePage(name: String,
                   modifier: Modifier,
                   activeFields: Map<Int,String>,
                   currentlyActive: List<Int>){


    val activeList = remember {
        activeFields
    }
    val selectedIds = remember {
        mutableStateListOf<Int>().apply { addAll(currentlyActive) }
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
                    if (id in selectedIds) {
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
                    Button(
                        modifier = Modifier.height(20.dp).width(100.dp),
                        contentPadding = PaddingValues(0.dp),
                        onClick = { if (id !in selectedIds) {
                            selectedIds.add(id)
                        } else {
                            selectedIds.remove(id)
                        }
                        } ) {
                        Text(text = "Opt ${id}",
                            fontSize = 10.sp,
                            maxLines = 1)

                }
            }
        }
    }
}
