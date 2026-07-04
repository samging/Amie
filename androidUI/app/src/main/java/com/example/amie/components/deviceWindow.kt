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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalConfiguration
import org.w3c.dom.Text
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment

/**
 * A hardware connectivity dashboard utility panel that displays peripheral connection paths and
 * provides a 4-button configuration matrix grid (Manage, Connect, Configure, Disconnect).
 *
 * This layout organizes operational statuses and actions inside an explicit grid constructed from
 * nested [Row] and [Column] systems.
 *
 * ### UI Grid Layout Architecture
 * * **Row 1 (Status Bar):** Displays the evaluated [deviceEdnpoint] paired against the formatted [endPort] string.
 * Renders inside a solid `Color.Green` background block using standard `24.sp` typography scaling.
 * * **Row 2 (Action Bar A):** Draws two flat rectangular buttons side-by-side invoking [onManage] and [onConnectPage].
 * * **Row 3 (Action Bar B):** Draws two flat rectangular buttons side-by-side invoking [onConfigure] and [onDisconnect].
 *
 * ### Architectural & Responsive Layout Warnings
 * > `height(115.dp)`. However, the nested [Box] wrapper containers scale dynamically based on screen geometry
 * > properties (`screenHeight * 0.1f`). On tall devices or screens with large display zoom profiles, the inner
 * > action buttons **will expand and get completely clipped** by the rigid outer `115.dp` ceiling wrapper.
 * > Consider removing the hardcoded root height constraints.
 * >
 * > type safety guarantees and forces type checks at runtime. Strongly consider replacing them with a unified
 * > `sealed class`, an `enum`, or explicitly typed properties.
 * >
 * > are currently bypassed in the code lifecycle. The component cannot be customized externally by its parent layout.
 *
 * @param name An operational or descriptive identifier for this control dashboard panel scope (Currently unused).
 * @param modifier The layout [Modifier] intended for extending customization settings on the root container (Currently unmapped).
 * @param deviceEdnpoint A generic wrapper tracking the targeted hardware connection node. Evaluates [String] or [Int] data types.
 * @param endPort A generic wrapper tracking the targeted hardware port address. Renders prefixed by standard "COM" branding identifiers.
 * @param onManage Executed immediately when the user clicks the "Manage" system action block.
 * @param onConfigure Executed immediately when the user clicks the "Configure" system action block.
 * @param onConnectPage Executed immediately when the user clicks the "Connect" system action block.
 * @param onDisconnect Executed immediately when the user clicks the "Disconnect" system action block (Defaults to an empty block).
 */
@Composable
fun buttonConfigure(name:String,
                    modifier: Modifier = Modifier,
                    deviceEdnpoint: Any?,
                    endPort: Any? = null,
                    onManage: () -> Unit,
                    onConfigure: () -> Unit,
                    onConnectPage: () -> Unit,
                    onDisconnect: () -> Unit = {}) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp

    val calculateWidth = screenWidth * 0.5f
    val phoneHeight = screenHeight * 0.1f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp) //suma velikosti columns a rows uvnitr tedy sum(inner elements) == height
            .background(Color.LightGray)
    ) {

        Column() {

            Column(modifier = Modifier.fillMaxWidth()
                .height(30.dp).border(width = 2.dp, color = Color.Black)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .background(Color.Green),
                        contentAlignment = Alignment.Center
                    ) {
                        when (deviceEdnpoint) {
                            is String -> Text(text="${deviceEdnpoint}", fontSize = 24.sp)
                            is Int ->  Text(text="${deviceEdnpoint}", fontSize = 24.sp)
                            else -> Text("Unexpected Type!")
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .background(Color.Green),
                        contentAlignment = Alignment.Center
                    ) {
                        when (endPort) {
                            is String -> Text(text="COM${endPort}", fontSize = 24.sp,textAlign = TextAlign.Center,modifier = Modifier.fillMaxWidth())
                            is Int ->  Text(text="COM${endPort}", fontSize = 24.sp,textAlign = TextAlign.Center,modifier = Modifier.fillMaxWidth())
                            else -> Text("Unexpected Type!")
                        }
                    }
                }

            }

            Column(modifier = Modifier.fillMaxWidth()
                .height(40.dp)
            ) {

                Row(modifier = Modifier.fillMaxWidth()) {
                    Box (
                            modifier = Modifier
                            .weight(1f)
                            .height(phoneHeight.dp)
                            .background(Color.DarkGray)
                    ) {
                        Button(
                            onClick = { onManage() },
                                    modifier = Modifier.width(calculateWidth.dp).padding(1.dp),
                            shape = RectangleShape,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        ) {
                            Text("Manage")
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(phoneHeight.dp)
                            .background(Color.DarkGray)
                    ) {
                        Button(
                            onClick = { onConnectPage() },
                            modifier = Modifier.width(calculateWidth.dp).padding(1.dp),
                            shape = RectangleShape,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        ) {
                            Text("Connect")
                        }
                    }

                }
            }

            Column(modifier = Modifier.fillMaxWidth()
                    .height(40.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(phoneHeight.dp)
                                .background(Color.DarkGray)
                        ) {
                            Button(
                                onClick = { onConfigure() },
                                modifier = Modifier.width(calculateWidth.dp).padding(1.dp),
                                shape = RectangleShape,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            ) {
                                Text("Configure")
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(phoneHeight.dp)
                                .background(Color.DarkGray)
                        ) {
                            Button(
                                onClick = { onDisconnect() },
                                modifier = Modifier.width(calculateWidth.dp).padding(1.dp),
                                shape = RectangleShape,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            ) {
                                Text("Disconnect")
                            }
                        }
                    }
                }


            }
        }

}



