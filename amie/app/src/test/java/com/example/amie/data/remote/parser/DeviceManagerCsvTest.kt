package com.example.amie.data.remote.parser

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DeviceManagerCsvTest {

    @TempDir
    lateinit var tempDir: File
    
    private lateinit var configFile: File
    private lateinit var deviceManager: DeviceManagerCsv

    @BeforeEach
    fun setup() {
        configFile = File(tempDir, "test_config.csv")
        deviceManager = DeviceManagerCsv(configFile)
    }

    @Test
    fun `load should parse CSV format correctly`() {
        configFile.writeText("1,Alpha,COM1,ws://host\n2,Beta,COM2,")
        
        deviceManager.load(configFile)
        
        assertEquals(2, deviceManager.count())
        assertEquals("Alpha", deviceManager.getDevice("1")?.name)
        assertEquals("COM2", deviceManager.getDevice("2")?.port)
        assertNull(deviceManager.getDevice("2")?.deviceEndpoint)
    }

    @Test
    fun `writeConfig should create file and persist data`() {
        deviceManager.writeConfig("1", listOf("name", "port"), listOf("NewDev", "COM3"))
        
        assertTrue(configFile.exists())
        val content = configFile.readText()
        assertTrue(content.contains("1,NewDev,COM3,"))
    }

    @Test
    fun `generateAddId should increment based on max numeric ID`() {
        configFile.writeText("1,D1,P1\n5,D5,P5\n2,D2,P2")
        deviceManager.load(configFile)
        
        assertEquals("6", deviceManager.generateAddId())
    }

    @Test
    fun `deleteById should remove entry from file`() {
        configFile.writeText("1,D1,P1\n2,D2,P2")
        deviceManager.load(configFile)
        
        deviceManager.deleteById("1")
        
        val content = configFile.readText()
        assertFalse(content.contains("D1"))
        assertTrue(content.contains("D2"))
    }

    @Test
    fun `load should skip malformed lines`() {
        configFile.writeText("1,Valid,P1\nMalformedLine\n3,AlsoValid,P3")
        deviceManager.load(configFile)
        
        assertEquals(2, deviceManager.count())
        assertNotNull(deviceManager.getDevice("1"))
        assertNotNull(deviceManager.getDevice("3"))
    }

    @Test
    fun `generateAddId should handle non-numeric keys`() {
        configFile.writeText("abc,Name,Port\n10,Ten,P10")
        deviceManager.load(configFile)
        
        // Should find 10 as max and return 11
        assertEquals("11", deviceManager.generateAddId())
    }

    @Test
    fun `parseConfig should return requested labels`() {
        configFile.writeText("1,Dev1,Port1,End1")
        deviceManager.load(configFile)
        
        assertEquals(listOf("Dev1"), deviceManager.parseConfig("name"))
        assertEquals(listOf("Port1"), deviceManager.parseConfig("port"))
        assertEquals(listOf("End1"), deviceManager.parseConfig("deviceEndpoint"))
        assertEquals(listOf(""), deviceManager.parseConfig("invalid"))
    }

    @Test
    fun `parseConfigByTargetId should return error for missing ID`() {
        deviceManager.load(configFile)
        val result = deviceManager.parseConfigByTargetId("name", "999")
        assertTrue(result[0].contains("Error"))
    }
}
