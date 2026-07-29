package com.example.amie.util

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class PortReaderTest {

    @Test
    fun testReadAndroidUsbPorts() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val ports = readAndroidUsbPorts(appContext)
        
        // On emulator or most CI devices, it will be empty
        // We just want to make sure it doesn't crash and returns the expected default if empty
        if (ports.size == 1) {
            assertTrue(ports[0] == "No USB Serial Ports Detected")
        } else {
            assertTrue(ports.isNotEmpty())
        }
    }
}
