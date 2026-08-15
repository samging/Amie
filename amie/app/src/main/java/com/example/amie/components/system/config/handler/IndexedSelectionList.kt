package com.example.amie.components.system.config.handler

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amie.components.render.templates.Client
import com.example.amie.components.render.templates.RestType

@Composable
fun IndexedSelectionList(
    name: String,
    modifier: Modifier,
    activeFields: Map<Int, String>,
    currentlyActive: List<Int>
) {
    var response by remember { mutableStateOf(emptyMap<String, String>()) }
    var doPrefetch by remember { mutableStateOf(true) }
    val client = remember { Client() }

    LaunchedEffect(client, doPrefetch) {
        if (doPrefetch) {
            val result = client.rest("list-disk", restType = RestType.GET)
            if (result == null) {
                client.handleLoginError("Failed to get doPrefetch")
            } else {
                response = result
            }
            doPrefetch = false
        }
    }

    val selectedIds = remember {
        mutableStateListOf<Int>().apply { addAll(currentlyActive) }
    }

    val verticalScroller = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(verticalScroller)
            .padding(top = 10.dp)
            .fillMaxSize()
    ) {
        for ((id, _) in activeFields) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF171b23))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isSelected = id in selectedIds
                    Text(
                        color = if (isSelected) Color(0xFFc7cbd4) else Color(0xFF878e9c),
                        text = "[$id]",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )

                    if (doPrefetch) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFc7cbd4)
                        )
                        Text(
                            text = " Fetching...",
                            fontSize = 12.sp,
                            color = Color(0xFF878e9c)
                        )
                    } else {
                        val description = response[id.toString()] ?: ""
                        Text(
                            text = " $description",
                            color = Color(0xFFc7cbd4),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Button(
                    modifier = Modifier
                        .height(32.dp)
                        .width(80.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF262b36),
                        contentColor = Color(0xFFc7cbd4)
                    ),
                    onClick = {
                        if (id !in selectedIds) {
                            selectedIds.add(id)
                        } else {
                            selectedIds.remove(id)
                        }
                    }
                ) {
                    Text(
                        text = if (id in selectedIds) "DROP" else "ADD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
