package com.example.amie.components.system.config.handler

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
//making this loose coupled
import androidx.compose.foundation.layout.Arrangement
import kotlin.collections.iterator

/**
 * A scrollable container displaying a list of active fields that allows users to interactively
 * toggle selection states for individual indexed items.
 *
 * @param name An operational or descriptive identifier for this page scope (Currently unused in layout).
 * @param modifier The layout [Modifier] intended for extending configuration settings on the root layout container (Currently unmapped).
 * @param activeFields A map containing the definitive integer indices linked to their respective text descriptions.
 * @param currentlyActive The initial list of integer IDs that should be marked as selected upon the first composition.
 */
@Composable
fun IndexedSelectionList(name: String,
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
