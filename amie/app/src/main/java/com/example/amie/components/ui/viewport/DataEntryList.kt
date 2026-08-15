package com.example.amie.components.ui.viewport

import android.content.Context
import android.hardware.usb.UsbManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

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
                  username: String = "",
                  modifier: Modifier,
                  activeFields: Map<Int,String> = mapOf(1 to "this"),
                  currentlyActive: List<Int?> = listOf(-1)){

    val context: Context = LocalContext.current
    var names by remember { mutableStateOf(emptyList<Any>()) }
    var fetched by remember { mutableStateOf(false) }
    var fetchProblem by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        if (!fetched) {
            try {
                withTimeoutOrNull(10.seconds) {
                    println("fetching!!")
                    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                    val allDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

                    if (allDrivers.isNotEmpty()) {
                        val tempNames = mutableListOf<Any>()
                        for (dev in allDrivers) {
                            tempNames.addAll(dev.ports)
                        }
                        names = tempNames
                    }
                } ?: run {
                    println("Timed out waiting for USB devices")
                    fetchProblem = true
                }
            } catch (e: SecurityException) {
                println("SecurityException: ${e.message}")
                fetchProblem = true
            } catch (e: Exception) {
                println("Exception: ${e.message}")
                fetchProblem = true
            } finally {
                fetched = true
            }
        }
    }

    val verticalScroller = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(verticalScroller)
            .padding(top = 10.dp)
            .fillMaxSize()
    ) {
        if (!fetched) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFFc7cbd4))
                Text(
                    text = " Scanning for USB devices...",
                    modifier = Modifier.padding(start = 8.dp),
                    color = Color(0xFF878e9c)
                )
            }
        }

        if (fetchProblem) {
            Text(
                "Scanning for USB devices timed out or failed",
                color = Color(0xFF878e9c),
                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        if (fetched && !fetchProblem && names.isEmpty()) {
            Text(
                "No USB devices found",
                color = Color(0xFF878e9c),
                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        for ((id, description) in activeFields) {
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween)
            {
                Row {
                    val isHighlighted = id in currentlyActive
                    Text(
                        color = if (isHighlighted) Color(0xFFc7cbd4) else Color(0xFF878e9c),
                        text = "[$id] ",
                        fontFamily = FontFamily.Monospace
                    )
                    
                    Text(
                        text = description,
                        modifier = Modifier.padding(end = 8.dp),
                        color = if (id in currentlyActive) Color(0xFFc7cbd4) else Color(0xFF878e9c)
                    )

                    if (fetched && !fetchProblem) {
                        for (portName in names) {
                            Text(
                                text = "($portName)",
                                color = Color(0xFF262b36),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
