package com.example.amie.components.ui.viewport

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
//making this loose coupled
import androidx.compose.foundation.layout.Arrangement
import kotlin.collections.iterator

/**
 * A scrollable container that renders a structured list of key-value item pairs (IDs and descriptions).
 * * Provides a vertical scrolling canvas designed to present configuration fields, logs, or mapped index records
 * in a uniform list format.
 *
 * @param name A descriptive name or identifier for the page (Currently unused in layout).
 * @param modifier The [Modifier] instance used to apply layout adjustments or sizing decorations to the root container.
 * @param activeFields A map containing the definitive integer indices linked to their respective text descriptions.
 * @param currentlyActive A list of targeted numeric indices intended for active highlighting states (Defaults to `[-1]`).
 */
@Composable
fun DataEntryList(name: String,
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
