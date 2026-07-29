package com.example.amie.util

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class LogReaderInstrumentationTest {

    @Test
    fun testReadLogFileOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testFile = File(context.cacheDir, "test.txt")
        testFile.writeText("Line A\nLine B")
        
        val result = readLogFile(testFile.absolutePath)
        
        assertEquals(listOf("Line A", "Line B"), result)
        testFile.delete()
    }
}
