package com.example.amie.data.remote.parser

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DeviceManagerFactoryTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `create should return DeviceManagerJson for json extension`() {
        val file = File(tempDir, "test.json")
        val manager = DeviceManagerFactory.create(file)
        assertTrue(manager is DeviceManagerJson)
    }

    @Test
    fun `create should return DeviceManagerCsv for csv extension`() {
        val file = File(tempDir, "test.csv")
        val manager = DeviceManagerFactory.create(file)
        assertTrue(manager is DeviceManagerCsv)
    }

    @Test
    fun `create should throw exception for unknown extension`() {
        val file = File(tempDir, "test.txt")
        assertThrows(IllegalArgumentException::class.java) {
            DeviceManagerFactory.create(file)
        }
    }
}
