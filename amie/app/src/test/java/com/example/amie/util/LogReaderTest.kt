package com.example.amie.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LogReaderTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `readLogFile should return lines when file exists`() {
        val logFile = File(tempDir, "test.log")
        val lines = listOf("Line 1", "Line 2", "Line 3")
        logFile.writeText(lines.joinToString("\n"))

        val result = readLogFile(logFile.absolutePath)

        assertEquals(lines, result)
    }

    @Test
    fun `readLogFile should return error message when file does not exist`() {
        val nonExistentPath = File(tempDir, "ghost.log").absolutePath
        val result = readLogFile(nonExistentPath)

        assertEquals(1, result.size)
        assertTrue(result[0].contains("Opening error"))
    }

    @Test
    fun `readLogFile should return empty list for empty file`() {
        val emptyFile = File(tempDir, "empty.log")
        emptyFile.createNewFile()

        val result = readLogFile(emptyFile.absolutePath)

        assertTrue(result.isEmpty())
    }
}
