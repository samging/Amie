package com.example.amie.components.terminal.window.controller

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf

//using locally implemented library:
import com.example.amie.util.readLogFile
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.LaunchedEffect
import com.example.amie.data.remote.parser.DeviceManagerJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortInvalidPortException
import java.io.Serial
import java.util.Scanner
import org.slf4j.LoggerFactory

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle

/**
 * A standard terminal emulator window component that displays recent system logs and provides
 * an optional interactive command input line.
 */
@Composable
fun TerminalWindow(
    title: String,
    deviceId: String,
    modifier: Modifier = Modifier,
    logFilePath: String = "/data/local/tmp/logs.txt",
    content: SnapshotStateList<String> = remember(logFilePath) {
        readLogFile(logFilePath).toMutableStateList()
    },
    allowCmd: Boolean? = false
) {
    val deviceManagerJson = remember { DeviceManagerJson() }
    val logger = LoggerFactory.getLogger("TerminalWindow")
    val portName = try {
        deviceManagerJson.getDevicePort(deviceId)
    } catch (e: Exception) {
        logger.error("Error getting port name", e)
        throw IllegalArgumentException("Error getting port name")
    }

    LaunchedEffect(portName) {
        withContext(Dispatchers.IO) {
            try {
                val serialPort = SerialPort.getCommPort(portName)
                serialPort.baudRate = 9600
                serialPort.numDataBits = 8
                serialPort.numStopBits = SerialPort.ONE_STOP_BIT
                serialPort.parity = SerialPort.NO_PARITY

                logger.info("Opening serial port: $portName")
                if (serialPort.openPort()) {
                    serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0)
                    try {
                        val scanner = Scanner(serialPort.inputStream)
                        while (scanner.hasNextLine()) {
                            val line = scanner.nextLine()
                            withContext(Dispatchers.Main) {
                                content.add(line)
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Error reading from serial port", e)
                    } finally {
                        serialPort.closePort()
                    }
                } else {
                    logger.error("Failed to open serial port: $portName")
                }
            } catch (e: SerialPortInvalidPortException) {
                logger.error("Invalid serial port descriptor: $portName", e)
            } catch (e: Exception) {
                logger.error("Unexpected error opening serial port: $portName", e)
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF262b36), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101319)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = Color(0xFF171b23))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF878e9c)) // Muted "live" indicator
                )
                Text(
                    text = "CONSOLE: $title",
                    color = Color(0xFF878e9c),
                    fontSize = 10.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Logs Display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 300.dp)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                for (line in content.takeLast(50)) {
                    Text(
                        text = "> $line",
                        color = Color(0xFFc7cbd4), // Primary Text
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }

            // Command Input
            if (allowCmd == true) {
                var textInput by remember { mutableStateOf("") }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        textStyle = TextStyle(
                            color = Color(0xFFc7cbd4),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        ),
                        placeholder = { 
                            Text("Enter command...", color = Color(0xFF878e9c), fontSize = 14.sp) 
                        },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFc7cbd4),
                            unfocusedTextColor = Color(0xFFc7cbd4),
                            focusedContainerColor = Color(0xFF171b23),
                            unfocusedContainerColor = Color(0xFF171b23),
                            focusedBorderColor = Color(0xFF262b36),
                            unfocusedBorderColor = Color(0xFF262b36).copy(alpha = 0.5f),
                            cursorColor = Color(0xFFc7cbd4)
                        ),
                        singleLine = true,
                        trailingIcon = {
                            if (textInput.isNotEmpty()) {
                                IconButton(onClick = {
                                    content.add("USER: $textInput")
                                    textInput = ""
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Send command",
                                        tint = Color(0xFFc7cbd4)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}